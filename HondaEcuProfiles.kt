package com.revcore.android.core.comm

/**
 * One request frame plus the host-side timing used when it is sent.
 * [evidence] follows the reconstructed request, not a claim that the ECU
 * answers it.
 */
class EcuRequest(
    val id: String,
    val bytes: ByteArray,
    val label: String,
    val timeoutMs: Int,
    val sleepMs: Long,
    val status: String,
    val evidence: EvidenceStatus
) {
    /** Compact tag used in log/message text, e.g. dtc.read.7401 -> 74/01. */
    val shortTag: String
        get() = when {
            id.startsWith("dtc.read.") -> id.removePrefix("dtc.read.").chunked(2).joinToString("/")
            id.startsWith("dtc.clear.") -> id.removePrefix("dtc.clear.").chunked(2).joinToString("/")
            id.startsWith("live.table.") -> id.removePrefix("live.table.")
            else -> id
        }

    fun copyBytes(): ByteArray = bytes.copyOf()
}

/** Host wake timing. Measured values are host-side only, never bus duration. */
data class HondaWakeTiming(
    val lowMs: Long,
    val highMs: Long,
    val baudRate: Int,
    val evidence: EvidenceStatus
)

/**
 * A family-scoped wire strategy. Catalogs that are not evidenced for a family
 * stay empty instead of borrowing another family's frames.
 */
class HondaWireProfile(
    val family: HondaEcuFamily,
    val description: String,
    val requestHeader: Int,
    val identificationEvidence: EvidenceStatus,
    val testerPresent: EcuRequest?,
    val idTable00F0: EcuRequest?,
    val id7200: EcuRequest?,
    val partNumber71: EcuRequest?,
    val liveProbe: EcuRequest?,
    val dtcRead: List<EcuRequest>,
    val dtcClear: List<EcuRequest>,
    val liveDataTables: List<EcuRequest>,
    val liveDataServices: Set<Int>,
    val clearAckHeaders: Set<Int>,
    val baudCandidates: List<Int>,
    val notes: String,
    /**
     * Additional tables polled after the primary table succeeded (dat16/dat20/datD1).
     * They add speed/O2/gear fields the primary table does not carry.
     */
    val liveExtensionTables: List<EcuRequest> = emptyList()
) {
    val identification: List<EcuRequest>
        get() = listOfNotNull(testerPresent, idTable00F0, id7200, partNumber71, liveProbe)

    val hasIdentificationCatalog: Boolean get() = identification.isNotEmpty()
    val hasDtcCatalog: Boolean get() = dtcRead.isNotEmpty() || dtcClear.isNotEmpty()
    val hasLiveDataCatalog: Boolean get() = liveDataTables.isNotEmpty()
}

object HondaEcuProfiles {
    val baselineWake = HondaWakeTiming(
        lowMs = 70,
        highMs = 130,
        baudRate = 10_400,
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private fun request(
        id: String,
        frame: ByteArray,
        label: String,
        timeoutMs: Int,
        sleepMs: Long,
        status: String,
        evidence: EvidenceStatus
    ) = EcuRequest(id, frame, label, timeoutMs, sleepMs, status, evidence)

    private val testerPresent = request(
        id = "tester.present",
        frame = byteArrayOf(0xFE.toByte(), 0x04, 0x72, 0x8C.toByte()),
        label = "Tester present baseline FE 04 72 8C",
        timeoutMs = 600,
        sleepMs = 10,
        status = "TESTER_PRESENT",
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private val idTable00F0 = request(
        id = "id.00F0",
        frame = byteArrayOf(0x72, 0x05, 0x00, 0xF0.toByte(), 0x99.toByte()),
        label = "Query ID baseline 72 05 00 F0 99",
        timeoutMs = 800,
        sleepMs = 10,
        status = "ID_QUERY",
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private val id7200 = request(
        id = "id.7200",
        frame = byteArrayOf(0x72, 0x07, 0x72, 0x00, 0x00, 0x05, 0x10),
        label = "Query ID baseline 72 07 72 00 00 05 10",
        timeoutMs = 800,
        sleepMs = 10,
        status = "ID_QUERY",
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private val partNumber71 = request(
        id = "id.7100",
        frame = byteArrayOf(0x72, 0x05, 0x71, 0x00, 0x18),
        label = "Query ID baseline 72 05 71 00 18",
        timeoutMs = 800,
        sleepMs = 10,
        status = "ID_QUERY",
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private val liveProbe = request(
        id = "live.probe",
        frame = byteArrayOf(0x72, 0x05, 0x71, 0x17, 0x01),
        label = "Probe live table FLASHPRO 72 05 71 17 01",
        timeoutMs = 300,
        sleepMs = 5,
        status = "LIVE_PROBE",
        evidence = EvidenceStatus.RECONSTRUCTED
    )

    private val baselineDtcRead = listOf(
        request(
            "dtc.read.7401",
            frame = byteArrayOf(0x72, 0x05, 0x74, 0x01, 0x14),
            label = "Query DTC baseline 74/01",
            timeoutMs = 700,
            sleepMs = 0,
            status = "DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "dtc.read.7120",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x20, 0xF8.toByte()),
            label = "Query DTC baseline 71/20",
            timeoutMs = 700,
            sleepMs = 0,
            status = "DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "dtc.read.0000",
            frame = byteArrayOf(0x72, 0x05, 0x00, 0x00, 0x89.toByte()),
            label = "Query DTC baseline 00/00",
            timeoutMs = 700,
            sleepMs = 0,
            status = "DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        )
    )

    private val baselineDtcClear = listOf(
        request(
            "dtc.clear.6001",
            frame = byteArrayOf(0x72, 0x05, 0x60, 0x01, 0x28),
            label = "Clear DTC baseline 60/01 (read-only verification follows; no calibration write)",
            timeoutMs = 800,
            sleepMs = 0,
            status = "CLEAR_DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "dtc.clear.7402",
            frame = byteArrayOf(0x72, 0x05, 0x74, 0x02, 0x13),
            label = "Clear DTC baseline 74/02 (read-only verification follows; no calibration write)",
            timeoutMs = 800,
            sleepMs = 0,
            status = "CLEAR_DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "dtc.clear.04",
            frame = byteArrayOf(0x72, 0x04, 0x04, 0x86.toByte()),
            label = "Clear DTC baseline 04 (read-only verification follows; no calibration write)",
            timeoutMs = 800,
            sleepMs = 0,
            status = "CLEAR_DTC_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        )
    )

    /**
     * Primary live tables in the proven FLASHPRO probe order (17 -> 13 -> 11 -> 10).
     * Each frame is exactly five bytes; the previous six-byte frames declared in
     * table 0x00 were not present in the firmware.
     */
    private val baselineLiveTables = listOf(
        request(
            "live.table.17",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x17, 0x01),
            label = "Live data tabel 0x17 (FLASHPRO dat17)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "live.table.13",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x13, 0x05),
            label = "Live data tabel 0x13 (FLASHPRO dat13)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "live.table.11",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x11, 0x07),
            label = "Live data tabel 0x11 (FLASHPRO dat11)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "live.table.10",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x10, 0x08),
            label = "Live data tabel 0x10 (FLASHPRO dat10)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        )
    )

    /**
     * Extension tables always requested by FLASHPRO after a primary table works.
     * They contribute O2/STF (dat20), speed and ride-by-wire channels (dat16) and
     * gear state (datD1) without replacing the primary values.
     */
    private val baselineLiveExtensions = listOf(
        request(
            "live.table.16",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x16, 0x02),
            label = "Live data tambahan tabel 0x16 (FLASHPRO dat16)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_EXT_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "live.table.20",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0x20, 0xF8.toByte()),
            label = "Live data tambahan tabel 0x20 (FLASHPRO dat20)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_EXT_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        ),
        request(
            "live.table.D1",
            frame = byteArrayOf(0x72, 0x05, 0x71, 0xD1.toByte(), 0x47),
            label = "Live data tambahan tabel 0xD1 (FLASHPRO datD1)",
            timeoutMs = 300,
            sleepMs = 2,
            status = "LIVE_DATA_EXT_QUERY",
            evidence = EvidenceStatus.RECONSTRUCTED
        )
    )

    /**
     * Family-neutral catalog restored from the working RevCore baseline. It is
     * deliberately not labelled Keihin or Shindengen: the baseline build never
     * proved which family answered these frames.
     */
    val genericBaseline = HondaWireProfile(
        family = HondaEcuFamily.GENERIC_H99B_KLINE,
        description = "Restored RevCore baseline PGM-FI catalog (0x72 header)",
        requestHeader = HondaFrameCodec.HEADER_BASELINE,
        identificationEvidence = EvidenceStatus.RECONSTRUCTED,
        testerPresent = testerPresent,
        idTable00F0 = idTable00F0,
        id7200 = id7200,
        partNumber71 = partNumber71,
        liveProbe = liveProbe,
        dtcRead = baselineDtcRead,
        dtcClear = baselineDtcClear,
        liveDataTables = baselineLiveTables,
        liveDataServices = setOf(0x71, 0xC1, 0x72),
        clearAckHeaders = setOf(0x02, 0x72, 0x7E, 0x7C),
        baudCandidates = listOf(10_400, 9_600),
        notes = "Live-table frames and decoding follow the FLASHPRO firmware (5-byte requests, " +
            "17/13/11/10 probe order, 300 ms window). Identification/DTC catalogs remain the " +
            "RevCore baseline because the firmware's family-specific init frames are not proven " +
            "for these ECUs.",
        liveExtensionTables = baselineLiveExtensions
    )

    val keihinH99b = HondaWireProfile(
        family = HondaEcuFamily.KEIHIN_H99B_KLINE,
        description = "Keihin H99B family profile",
        requestHeader = HondaFrameCodec.HEADER_BASELINE,
        identificationEvidence = EvidenceStatus.UNRESOLVED,
        testerPresent = null,
        idTable00F0 = null,
        id7200 = null,
        partNumber71 = null,
        liveProbe = null,
        dtcRead = emptyList(),
        dtcClear = emptyList(),
        liveDataTables = emptyList(),
        liveDataServices = emptySet(),
        clearAckHeaders = emptySet(),
        baudCandidates = listOf(10_400),
        notes = "libecuwb_ui.so exposes honda_keihin_kwp_kline.rs (0x178ba68) and the H99B generic " +
            "implementation (0x178d944); exact Keihin frames, keys and timings remain unresolved. " +
            "No Keihin-specific frame is invented here."
    )

    val keihinKwp = HondaWireProfile(
        family = HondaEcuFamily.KEIHIN_KWP_KLINE,
        description = "Keihin KWP-over-K-Line family profile",
        requestHeader = HondaFrameCodec.HEADER_KWP_REQUEST_PRIMARY,
        identificationEvidence = EvidenceStatus.UNRESOLVED,
        testerPresent = null,
        idTable00F0 = null,
        id7200 = null,
        partNumber71 = null,
        liveProbe = null,
        dtcRead = emptyList(),
        dtcClear = emptyList(),
        liveDataTables = emptyList(),
        liveDataServices = emptySet(),
        clearAckHeaders = emptySet(),
        baudCandidates = listOf(10_400),
        notes = "Request helper 0x17a5e04 selects header 0x7D/0x7B and expects a 9-byte reply with " +
            ">= 2 payload bytes. Session subfunctions and keys are unresolved."
    )

    val shindengenH99b = HondaWireProfile(
        family = HondaEcuFamily.SHINDENGEN_H99B_KLINE,
        description = "Shindengen H99B family profile",
        requestHeader = HondaFrameCodec.HEADER_KWP_REQUEST_ALTERNATE,
        identificationEvidence = EvidenceStatus.UNRESOLVED,
        testerPresent = null,
        idTable00F0 = null,
        id7200 = null,
        partNumber71 = null,
        liveProbe = null,
        dtcRead = emptyList(),
        dtcClear = emptyList(),
        liveDataTables = emptyList(),
        liveDataServices = emptySet(),
        clearAckHeaders = emptySet(),
        baudCandidates = listOf(10_400),
        notes = "No dedicated Shindengen source module was found; it appears to share the generic H99B " +
            "engine. Shindengen-specific identification/live-data/DTC frames are unresolved and are " +
            "therefore not transmitted."
    )

    val densoH99b = HondaWireProfile(
        family = HondaEcuFamily.DENSO_H99B_KLINE,
        description = "Denso H99B family profile",
        requestHeader = HondaFrameCodec.HEADER_BASELINE,
        identificationEvidence = EvidenceStatus.UNRESOLVED,
        testerPresent = null,
        idTable00F0 = null,
        id7200 = null,
        partNumber71 = null,
        liveProbe = null,
        dtcRead = emptyList(),
        dtcClear = emptyList(),
        liveDataTables = emptyList(),
        liveDataServices = emptySet(),
        clearAckHeaders = emptySet(),
        baudCandidates = listOf(10_400),
        notes = "Protocol enum contains HondaDensoH99bKline; no reconstructed frames yet."
    )

    fun forFamily(family: HondaEcuFamily): HondaWireProfile = when (family) {
        HondaEcuFamily.KEIHIN_H99B_KLINE -> keihinH99b
        HondaEcuFamily.KEIHIN_KWP_KLINE -> keihinKwp
        HondaEcuFamily.SHINDENGEN_H99B_KLINE -> shindengenH99b
        HondaEcuFamily.DENSO_H99B_KLINE -> densoH99b
        HondaEcuFamily.GENERIC_H99B_KLINE, HondaEcuFamily.UNKNOWN -> genericBaseline
    }

    fun all(): List<HondaWireProfile> = listOf(
        genericBaseline,
        keihinH99b,
        keihinKwp,
        shindengenH99b,
        densoH99b
    )
}
