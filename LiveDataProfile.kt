package com.revcore.android.core.comm

/**
 * Declarative read-only live-data layout. Nothing here proves an ECU answers a
 * request; [verification] records how far the layout itself has been evidenced.
 *
 * Layouts in [HondaLiveDataProfiles] are transcribed from the FLASHPRO ESP32
 * firmware decompilation (decoder functions around 0x400d6284/0x400d75c8 and the
 * literal pool 0x400d02a8..0x400d02fc). The decoders are proven; whether a given
 * ECU answers a table is still decided by the wire transaction at runtime.
 */
enum class ProfileVerification {
    VERIFIED,
    RECONSTRUCTED,
    HYPOTHESIS
}

enum class FieldEndian { BIG, LITTLE }

/**
 * Sensors the firmware exposes across dat17/dat13/dat11/dat10/dat16/dat20/datD1.
 * Names follow the firmware labels; AP/TP work only on ride-by-wire tables.
 */
enum class LiveSensor {
    RPM,
    TPS_PERCENT,
    TPS_VOLTAGE,
    TPS_DEGREES,
    ECT,
    ECT_VOLTAGE,
    IAT,
    IAT_VOLTAGE,
    MAP,
    MAP_VOLTAGE,
    BATTERY,
    IGNITION_ADVANCE,
    INJECTOR,
    SPEED,
    MODE,
    O2,
    O2_RAW,
    AFR,
    STF,
    AP1_VOLTAGE,
    AP2_VOLTAGE,
    AP1_DEGREES,
    AP2_DEGREES,
    TP1_VOLTAGE,
    TP2_VOLTAGE,
    TP1_DEGREES,
    TP2_DEGREES,
    COR,
    GEAR_STATE,
    FLAG_A,
    FLAG_B,
    GEAR_POSITION
}

/**
 * One field inside a live-data payload.
 *
 * [alternateOffsets] supports the dat17 ECT pair: byte 12/13 is the primary
 * source and byte 8/9 is used only when the primary byte reads 0xFF. A 0xFF byte
 * is therefore only treated as "absent" for fields that declare alternates;
 * other fields keep their raw value and go through the normal range check.
 */
data class LiveFieldSpec(
    val sensor: LiveSensor,
    val offset: Int,
    val length: Int = 1,
    val endian: FieldEndian = FieldEndian.BIG,
    val signed: Boolean = false,
    val scale: Double = 1.0,
    val bias: Double = 0.0,
    val unit: String = "",
    val validMin: Double,
    val validMax: Double,
    val alternateOffsets: List<Int> = emptyList(),
    val note: String = ""
)

data class LiveTableProfile(
    val id: String,
    val tableId: String,
    val request: ByteArray,
    val payloadStart: Int,
    val minPayload: Int,
    val requiredSensors: Set<LiveSensor>,
    val fields: List<LiveFieldSpec>,
    val verification: ProfileVerification,
    val provenance: String,
    val notes: String = ""
) {
    /** Smallest complete frame (header, length, service, table, data, checksum). */
    val minFrameSize: Int get() = payloadStart + minPayload + 1
}

data class LiveParseResult(
    val profileId: String,
    val values: Map<LiveSensor, Double>,
    val available: Set<LiveSensor>,
    val rejected: Map<LiveSensor, String>,
    val dataValid: Boolean
)

object LiveDataFrameParser {
    /**
     * Parses an already de-framed payload. Framing, length and checksum are the
     * wire engine's responsibility; this function only applies offsets/scales and
     * range checks. An out-of-range or truncated field is omitted, never zeroed.
     */
    fun parse(payload: ByteArray, profile: LiveTableProfile): LiveParseResult {
        val values = mutableMapOf<LiveSensor, Double>()
        val available = mutableSetOf<LiveSensor>()
        val rejected = mutableMapOf<LiveSensor, String>()

        for (field in profile.fields) {
            val offsets = listOf(field.offset) + field.alternateOffsets
            var chosen: Int? = null
            var reason = "payload terlalu pendek"
            for (offset in offsets) {
                if (offset + field.length > payload.size) continue
                val useAlternateRule = field.alternateOffsets.isNotEmpty()
                if (useAlternateRule && field.length == 1 && (payload[offset].toInt() and 0xFF) == 0xFF) {
                    reason = "byte 0xFF pada offset utama"
                    continue
                }
                chosen = offset
                reason = ""
                break
            }
            if (chosen == null) {
                rejected[field.sensor] = reason
                continue
            }

            var raw = 0L
            for (i in 0 until field.length) {
                val index = chosen + if (field.endian == FieldEndian.BIG) i else field.length - 1 - i
                raw = (raw shl 8) or (payload[index].toLong() and 0xFF)
            }
            if (field.signed) {
                val shift = 64 - field.length * 8
                raw = (raw shl shift) shr shift
            }
            val value = raw * field.scale + field.bias
            if (value < field.validMin || value > field.validMax) {
                rejected[field.sensor] =
                    "nilai ${"%.3f".format(value)} di luar rentang ${field.validMin}..${field.validMax}"
                continue
            }
            values[field.sensor] = value
            available.add(field.sensor)
        }

        val dataValid = profile.requiredSensors.all { it in available }
        return LiveParseResult(profile.id, values, available, rejected, dataValid)
    }
}

/**
 * Live-data layouts proven from the FLASHPRO firmware. Payload index equals the
 * firmware response index minus 4 (header, length, service, table).
 *
 * Constants are the firmware literal pool values: 5/256 for ADC voltages, 0.5
 * for degrees, -40 °C bias, /10 battery, /200 injector, -64 + 0.5*raw ignition,
 * 13.142708 / 1000 lambda, 128.256516 STF and 50.993698 O2 voltage.
 */
object HondaLiveDataProfiles {
    private const val PROVENANCE =
        "FLASHPRO firmware decoder (ESP32 image 0x400d6284/0x400d75c8, literal pool 0x400d02a8..0x400d02fc)"

    private fun field(
        sensor: LiveSensor,
        offset: Int,
        length: Int = 1,
        scale: Double = 1.0,
        bias: Double = 0.0,
        min: Double,
        max: Double,
        unit: String = "",
        alternateOffsets: List<Int> = emptyList(),
        note: String = ""
    ) = LiveFieldSpec(
        sensor = sensor,
        offset = offset,
        length = length,
        endian = FieldEndian.BIG,
        signed = false,
        scale = scale,
        bias = bias,
        unit = unit,
        validMin = min,
        validMax = max,
        alternateOffsets = alternateOffsets,
        note = note
    )

    private const val ADC_SCALE = 5.0 / 256.0

    private fun rpm(offset: Int = 0) = field(LiveSensor.RPM, offset, length = 2, min = 0.0, max = 20_000.0, unit = "rpm")

    /** UI derivation: firmware exposes TPS voltage and angle, not a percentage. */
    private fun tpsPercent(offset: Int = 2) = field(
        LiveSensor.TPS_PERCENT, offset, scale = 100.0 / 255.0, min = 0.0, max = 100.0, unit = "%",
        note = "Derivasi UI dari sudut TPS; firmware hanya menghitung voltase dan derajat."
    )

    private fun tpsVoltage(offset: Int = 2) = field(LiveSensor.TPS_VOLTAGE, offset, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V")
    private fun tpsDegrees(offset: Int = 3) = field(LiveSensor.TPS_DEGREES, offset, scale = 0.5, min = 0.0, max = 127.5, unit = "deg")
    private fun ectVoltage(offset: Int = 4, alternate: List<Int> = emptyList()) =
        field(LiveSensor.ECT_VOLTAGE, offset, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V", alternateOffsets = alternate)
    private fun ect(offset: Int = 5, alternate: List<Int> = emptyList()) =
        field(LiveSensor.ECT, offset, bias = -40.0, min = -40.0, max = 150.0, unit = "C", alternateOffsets = alternate)
    private fun iatVoltage(offset: Int = 6) = field(LiveSensor.IAT_VOLTAGE, offset, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V")
    private fun iat(offset: Int = 7) = field(LiveSensor.IAT, offset, bias = -40.0, min = -40.0, max = 100.0, unit = "C")
    private fun mapVoltage(offset: Int = 8) = field(LiveSensor.MAP_VOLTAGE, offset, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V")
    private fun map(offset: Int = 9) = field(LiveSensor.MAP, offset, min = 0.0, max = 255.0, unit = "kPa")
    private fun battery(offset: Int) = field(LiveSensor.BATTERY, offset, scale = 0.1, min = 5.0, max = 20.0, unit = "V")
    private fun injector(offset: Int, length: Int = 2) =
        field(LiveSensor.INJECTOR, offset, length = length, scale = 1.0 / 200.0, min = 0.0, max = 50.0, unit = "ms")
    private fun ignition(offset: Int) =
        field(LiveSensor.IGNITION_ADVANCE, offset, scale = 0.5, bias = -64.0, min = -70.0, max = 70.0, unit = "deg")
    private fun speed(offset: Int) = field(LiveSensor.SPEED, offset, min = 0.0, max = 300.0, unit = "km/h")

    private fun profile(
        id: String,
        tableId: String,
        request: ByteArray,
        minPayload: Int,
        requiredSensors: Set<LiveSensor>,
        fields: List<LiveFieldSpec>,
        notes: String
    ) = LiveTableProfile(
        id = id,
        tableId = tableId,
        request = request,
        payloadStart = 4,
        minPayload = minPayload,
        requiredSensors = requiredSensors,
        fields = fields,
        verification = ProfileVerification.VERIFIED,
        provenance = PROVENANCE,
        notes = notes
    )

    /** `72 05 71 17 01`; ECT primary is response 12/13, fallback 8/9. */
    val table17 = profile(
        id = "flashpro.dat17",
        tableId = "17",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x17, 0x01),
        minPayload = 16,
        requiredSensors = setOf(LiveSensor.RPM),
        fields = listOf(
            rpm(),
            tpsVoltage(), tpsDegrees(), tpsPercent(),
            ectVoltage(offset = 8, alternate = listOf(4)),
            ect(offset = 9, alternate = listOf(5)),
            iatVoltage(), iat(),
            battery(10),
            injector(11),
            ignition(13),
            field(LiveSensor.MODE, 14, min = 0.0, max = 255.0),
            speed(15)
        ),
        notes = "ECT utama byte 12/13; byte 8/9 hanya dipakai saat byte 12 bernilai 0xFF. " +
            "Byte 18 adalah mode, byte 19 VSS."
    )

    /** `72 05 71 13 05`; no VSS/mode. */
    val table13 = profile(
        id = "flashpro.dat13",
        tableId = "13",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x13, 0x05),
        minPayload = 14,
        requiredSensors = setOf(LiveSensor.RPM),
        fields = listOf(
            rpm(),
            tpsVoltage(), tpsDegrees(), tpsPercent(),
            ectVoltage(), ect(),
            iatVoltage(), iat(),
            mapVoltage(), map(),
            battery(10),
            injector(11),
            ignition(13)
        ),
        notes = "Tidak ada VSS/mode pada dat13."
    )

    /** `72 05 71 11 07` */
    val table11 = profile(
        id = "flashpro.dat11",
        tableId = "11",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x11, 0x07),
        minPayload = 17,
        requiredSensors = setOf(LiveSensor.RPM),
        fields = listOf(
            rpm(),
            tpsVoltage(), tpsDegrees(), tpsPercent(),
            ectVoltage(), ect(),
            iatVoltage(), iat(),
            mapVoltage(), map(),
            battery(12),
            speed(13),
            injector(14),
            ignition(16)
        ),
        notes = "Layout dat11; identik dengan dat10."
    )

    /** `72 05 71 10 08` */
    val table10 = profile(
        id = "flashpro.dat10",
        tableId = "10",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x10, 0x08),
        minPayload = 17,
        requiredSensors = setOf(LiveSensor.RPM),
        fields = table11.fields,
        notes = "Layout dat11; identik dengan dat10."
    )

    /** `72 05 71 16 02`; ride-by-wire extension. */
    val table16 = profile(
        id = "flashpro.dat16",
        tableId = "16",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x16, 0x02),
        minPayload = 21,
        requiredSensors = emptySet(),
        fields = listOf(
            rpm(),
            field(LiveSensor.AP1_VOLTAGE, 2, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V"),
            field(LiveSensor.AP2_VOLTAGE, 3, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V"),
            field(LiveSensor.AP1_DEGREES, 4, scale = 0.5, min = 0.0, max = 127.5, unit = "deg"),
            field(LiveSensor.AP2_DEGREES, 5, scale = 0.5, min = 0.0, max = 127.5, unit = "deg"),
            field(LiveSensor.TP1_VOLTAGE, 6, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V"),
            field(LiveSensor.TP2_VOLTAGE, 7, scale = ADC_SCALE, min = 0.0, max = 5.0, unit = "V"),
            field(LiveSensor.TP1_DEGREES, 8, scale = 0.5, min = 0.0, max = 127.5, unit = "deg"),
            field(LiveSensor.TP2_DEGREES, 9, scale = 0.5, min = 0.0, max = 127.5, unit = "deg"),
            field(LiveSensor.COR, 10, min = 0.0, max = 255.0),
            ectVoltage(offset = 11),
            ect(offset = 12),
            iatVoltage(offset = 13),
            iat(offset = 14),
            mapVoltage(offset = 15),
            map(offset = 16),
            battery(17),
            speed(18),
            injector(19, length = 1),
            ignition(20)
        ),
        notes = "RPM di dat16 hanya dipakai jika tabel utama gagal; injektor satu byte /200."
    )

    /** `72 05 71 20 F8`; O2/STF/AFR. */
    val table20 = profile(
        id = "flashpro.dat20",
        tableId = "20",
        request = byteArrayOf(0x72, 0x05, 0x71, 0x20, 0xF8.toByte()),
        minPayload = 2,
        requiredSensors = emptySet(),
        fields = listOf(
            field(LiveSensor.O2_RAW, 0, length = 2, scale = 1.0 / 13_142.708, min = 0.0, max = 5.0, unit = "lambda"),
            field(LiveSensor.O2, 0, scale = 1.0 / 50.993698, min = 0.0, max = 5.0, unit = "V"),
            field(LiveSensor.STF, 1, scale = 1.0 / 128.256516, min = -2.0, max = 2.0),
            field(
                LiveSensor.AFR, 0,
                scale = -5.351 / 50.993698, bias = 17.7,
                min = 10.0, max = 20.0, unit = "AFR",
                note = "AFR = 17.7 - 5.351 * O2 voltage"
            )
        ),
        notes = "O2 voltage memakai byte 4, STF byte 5; AFR diturunkan dari O2 voltage."
    )

    /** `72 05 71 D1 47`; gear/flag extension. */
    val tableD1 = profile(
        id = "flashpro.datD1",
        tableId = "D1",
        request = byteArrayOf(0x72, 0x05, 0x71, 0xD1.toByte(), 0x47),
        minPayload = 7,
        requiredSensors = emptySet(),
        fields = listOf(
            field(LiveSensor.GEAR_STATE, 0, min = 0.0, max = 255.0),
            field(LiveSensor.FLAG_A, 4, min = 0.0, max = 255.0),
            field(LiveSensor.FLAG_B, 5, min = 0.0, max = 255.0),
            field(LiveSensor.GEAR_POSITION, 6, min = 0.0, max = 255.0)
        ),
        notes = "Byte 4 status netral/gigi (1=Netral, 0=OnGear, 2=state lain); " +
            "byte 8/9 flag ON/OFF (nama fisik DIREKONSTRUKSI); byte 10 posisi gigi."
    )

    val primaryTables: List<LiveTableProfile> = listOf(table17, table13, table11, table10)
    val extendedTables: List<LiveTableProfile> = listOf(table16, table20, tableD1)

    /** Probe order proven in the firmware: 17 -> 13 -> 11 -> 10, then extensions. */
    val tables: List<LiveTableProfile> = primaryTables + extendedTables

    fun forTable(tableId: String): LiveTableProfile? {
        val key = tableId.trim().uppercase()
        return tables.firstOrNull { it.tableId.equals(key, ignoreCase = true) }
    }

    /**
     * Engine default. dat11 is the first table that carries every core sensor
     * (RPM, TPS, ECT, IAT, MAP, battery, injector, ignition, VSS).
     */
    val baseline: LiveTableProfile = table11

    val all: List<LiveTableProfile> = tables
}
