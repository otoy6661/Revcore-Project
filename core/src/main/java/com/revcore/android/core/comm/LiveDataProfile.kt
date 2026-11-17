package com.revcore.android.core.comm

/**
 * Declarative read-only live-data layout. Nothing here proves an ECU answers a
 * request; [verification] records how far the layout itself has been evidenced.
 *
 * No built-in Honda live-data table is shipped with this repository. The
 * previously reconstructed tables were removed because their constant set and
 * table catalogue had unclear usage rights. [HondaLiveDataProfiles] is therefore
 * an empty catalogue: the parser and polling scaffolding remain, but a developer
 * must supply their own [LiveTableProfile] before any frame is requested.
 */
enum class ProfileVerification {
    /** Proven by a documented wire capture or an owned, verifiable source. */
    VERIFIED,
    /** Transcribed from a reconstruction whose provenance is recorded. */
    RECONSTRUCTED,
    /** Documented example only; never an ECU compatibility claim. */
    REFERENCE,
    /** Plausible but unproven; must not be presented as compatibility. */
    HYPOTHESIS
}

enum class FieldEndian { BIG, LITTLE }

/**
 * Per-parameter availability. A value that is not [VALID] is never displayed as
 * zero: the UI shows the state instead. [NOT_AVAILABLE_IN_KOEO] means the field is
 * only meaningful while the engine is running (for example an oxygen sensor).
 */
enum class LiveParameterState {
    VALID,
    NOT_RESPONDING,
    NOT_AVAILABLE_IN_KOEO,
    UNSUPPORTED,
    INVALID_RESPONSE,
    WAITING_FOR_DATA,
    STALE
}

/**
 * Pure state transitions shared by the wire engine, the UI and tests. A stale or
 * missing parameter never becomes a zero value; only its state changes.
 */
object LiveParameterStates {
    /**
     * Oxygen/trim values are only meaningful with the engine spinning. A field
     * that decoded while the engine was not proven running keeps its bytes in the
     * parser result but is reported as [LiveParameterState.NOT_AVAILABLE_IN_KOEO].
     */
    fun withEngineRunning(
        states: Map<LiveSensor, LiveParameterState>,
        engineRunning: Boolean,
        engineRunningOnly: Set<LiveSensor>
    ): Map<LiveSensor, LiveParameterState> {
        if (engineRunning) return states
        return states.mapValues { (sensor, state) ->
            if (sensor in engineRunningOnly && state == LiveParameterState.VALID) {
                LiveParameterState.NOT_AVAILABLE_IN_KOEO
            } else {
                state
            }
        }
    }

    fun validSensors(states: Map<LiveSensor, LiveParameterState>): Set<LiveSensor> =
        states.filterValues { it == LiveParameterState.VALID }.keys

    /** After a link loss only previously VALID values are marked STALE. */
    fun stale(states: Map<LiveSensor, LiveParameterState>): Map<LiveSensor, LiveParameterState> =
        states.mapValues { (_, state) ->
            if (state == LiveParameterState.VALID) LiveParameterState.STALE else state
        }

    /**
     * Sensors the reconstructed ECU layouts never publish. AFR is the notable
     * one: the firmware exposes a narrowband O2 voltage only, so an AFR row is
     * reported as [LiveParameterState.UNSUPPORTED] instead of being omitted (a
     * missing row invites a host-side derivation, which is exactly what this
     * state prevents). An existing entry always wins.
     */
    val notPublishedByEcu: Map<LiveSensor, LiveParameterState> = mapOf(
        LiveSensor.AFR to LiveParameterState.UNSUPPORTED
    )

    /**
     * Adds [notPublishedByEcu] to a non-empty state map without overriding a
     * sensor that actually decoded. Empty maps stay empty so the UI keeps
     * showing its "no data yet" message rather than a lone unsupported row.
     */
    fun withNotPublished(states: Map<LiveSensor, LiveParameterState>): Map<LiveSensor, LiveParameterState> {
        if (states.isEmpty()) return states
        val merged = states.toMutableMap()
        for ((sensor, state) in notPublishedByEcu) {
            if (sensor !in merged) merged[sensor] = state
        }
        return merged
    }
}

/**
 * Parameter identifiers understood by the parser. The names are generic
 * diagnostic concepts, not a claim that any particular ECU publishes them.
 * AP/TP channels describe ride-by-wire throttle inputs.
 */
enum class LiveSensor {
    RPM,
    TPS_PERCENT,
    TPS_VOLTAGE,
    TPS_DEGREES,
    TPS_ADC,
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
 * [alternateOffsets] supports a primary/fallback byte pair: the primary offset is
 * used unless it reads 0xFF, in which case the first alternate offset is tried.
 * A 0xFF byte is therefore only treated as "absent" for fields that declare
 * alternates; other fields keep their raw value and go through the normal range
 * check.
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
    /** Only meaningful while the engine is running (e.g. oxygen sensor output). */
    val engineRunningOnly: Boolean = false,
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
    val states: Map<LiveSensor, LiveParameterState>,
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
        val states = mutableMapOf<LiveSensor, LiveParameterState>()

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
                states[field.sensor] = if (reason.startsWith("payload")) {
                    LiveParameterState.NOT_RESPONDING
                } else {
                    LiveParameterState.INVALID_RESPONSE
                }
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
                states[field.sensor] = LiveParameterState.INVALID_RESPONSE
                continue
            }
            values[field.sensor] = value
            available.add(field.sensor)
            states[field.sensor] = LiveParameterState.VALID
        }

        // No single sensor gates the frame. At key-on/engine-off RPM can be absent
        // or invalid while TPS, ECT, IAT and battery are still perfectly usable.
        val dataValid = available.isNotEmpty()
        return LiveParseResult(profile.id, values, available, rejected, states, dataValid)
    }
}

/**
 * Built-in Honda live-data catalogue.
 *
 * Intentionally empty. The earlier catalogue was reconstructed from a
 * third-party tool image, and the usage rights of its table identifiers, request
 * frames and scaling constants have not been resolved. Publishing them would
 * redistribute material of uncertain provenance, so they are withheld until the
 * rights question is settled and a hardware capture confirms every value.
 *
 * The generic parser above remains fully usable: create a [LiveTableProfile]
 * that you own or that you have permission to publish, then either parse a
 * payload directly or register request frames in a [HondaWireProfile] that you
 * control. Nothing in this object transmits a frame.
 */
object HondaLiveDataProfiles {
    val primaryTables: List<LiveTableProfile> = emptyList()
    val extendedTables: List<LiveTableProfile> = emptyList()
    val tables: List<LiveTableProfile> = emptyList()
    val all: List<LiveTableProfile> = emptyList()

    fun forTable(tableId: String): LiveTableProfile? = null

    /** No default table exists; a caller must supply its own profile. */
    val baseline: LiveTableProfile? = null
}
