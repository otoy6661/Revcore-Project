package com.revcore.android.core.protocol

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.revcore.android.core.comm.CommEvidence
import com.revcore.android.core.comm.CommEvidenceEntry
import com.revcore.android.core.comm.CommLifecycle
import com.revcore.android.core.comm.EcuSessionManager
import com.revcore.android.core.comm.HondaEcuFamily
import com.revcore.android.core.comm.HondaEcuProfiles
import com.revcore.android.core.comm.HondaFrameCodec
import com.revcore.android.core.comm.HondaWireProfile
import com.revcore.android.core.comm.HondaLiveDataProfiles
import com.revcore.android.core.comm.KlineWireConfig
import com.revcore.android.core.comm.KlineWireEngine
import com.revcore.android.core.comm.LiveDataFrameParser
import com.revcore.android.core.comm.LiveParameterState
import com.revcore.android.core.comm.LiveParameterStates
import com.revcore.android.core.comm.LivePollPolicy
import com.revcore.android.core.comm.LiveSensor
import com.revcore.android.core.comm.LiveTableProfile
import com.revcore.android.core.ftdi.FtdiConstants
import com.revcore.android.core.ftdi.FtdiSerialDriver
import com.revcore.android.core.usb.UsbSerialLibraryTransport
import com.revcore.android.core.usb.UsbTransport
import com.revcore.android.data.ecu.EcmDatabase
import com.revcore.android.data.ecu.EcmModel
import com.revcore.android.data.ecu.EcuIdCanonicalization
import com.revcore.android.data.ecu.EcuIdCanonicalizer
import com.revcore.android.data.ecu.EcuIdentity
import com.revcore.android.data.ecu.IdResponseLayout
import com.revcore.android.data.ecu.IdentityMatchStatus
import com.revcore.android.data.ecu.IdentityResolution
import com.revcore.android.data.ecu.TempSensorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HondaProtocolEngine(private val usbManager: UsbManager) {
    private val tag = "HondaProtocolEngine"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val commLock = Mutex()

    private val wire = KlineWireEngine(
        logger = { direction, hexText, description -> log(direction, hexText, description) }
    )
    private val session = EcuSessionManager(wire)

    /** Lifecycle of the replacement core; mirrors the reference session model. */
    val commLifecycle: StateFlow<CommLifecycle> = session.lifecycle

    /** Family routed from evidenced response headers; UNKNOWN until proven. */
    val ecuFamily: StateFlow<HondaEcuFamily> = session.family

    /** Full evidence inventory (binary evidence / xref / reconstructed / hypothesis / unresolved). */
    val evidenceInventory: List<CommEvidenceEntry> get() = CommEvidence.entries

    private val activeProfile: HondaWireProfile
        get() = HondaEcuProfiles.forFamily(session.family.value)

    private var driver: UsbTransport? = null
    val activeTransport: UsbTransport?
        get() = driver

    /**
     * Incremented on every connect/disconnect. An identification coroutine that
     * started under an older generation is not allowed to publish its result.
     */
    @Volatile
    private var connectionGeneration = 0

    private val _connectionStep = MutableStateFlow(ConnectionPipelineStep.ADAPTER_DETECTED)
    val connectionStep: StateFlow<ConnectionPipelineStep> = _connectionStep.asStateFlow()

    private val _isAdapterOpen = MutableStateFlow(false)
    val isAdapterOpen: StateFlow<Boolean> = _isAdapterOpen.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isKlineInitialized = MutableStateFlow(false)
    val isKlineInitialized: StateFlow<Boolean> = _isKlineInitialized.asStateFlow()

    private val _isEcuIdentified = MutableStateFlow(false)
    val isEcuIdentified: StateFlow<Boolean> = _isEcuIdentified.asStateFlow()

    private val _isEcuAlive = MutableStateFlow(false)
    val isEcuAlive: StateFlow<Boolean> = _isEcuAlive.asStateFlow()

    private val _ignitionState = MutableStateFlow(IgnitionState.UNKNOWN)
    val ignitionState: StateFlow<IgnitionState> = _ignitionState.asStateFlow()

    private val _connectedModel = MutableStateFlow<EcmModel?>(null)
    val connectedModel: StateFlow<EcmModel?> = _connectedModel.asStateFlow()

    /** Identity fields exactly as received, plus normalization and full payload. */
    private val _ecuIdentity = MutableStateFlow<EcuIdentity?>(null)
    val ecuIdentity: StateFlow<EcuIdentity?> = _ecuIdentity.asStateFlow()

    /** Exact / partial / ambiguous / not-found result and every candidate. */
    private val _identityResolution = MutableStateFlow<IdentityResolution?>(null)
    val identityResolution: StateFlow<IdentityResolution?> = _identityResolution.asStateFlow()

    /**
     * Set only when a checksum-valid frame arrived but its identifier could not be
     * decoded safely. It is never inferred from a timeout; an ECU that simply does
     * not answer stays CONNECTED but unidentified.
     */
    private val _identificationError = MutableStateFlow<String?>(null)
    val identificationError: StateFlow<String?> = _identificationError.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
    val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()
    val telemetry: StateFlow<LiveTelemetry> get() = liveTelemetry

    private val _liveDataState = MutableStateFlow(LiveDataState.UNAVAILABLE)
    val liveDataState: StateFlow<LiveDataState> = _liveDataState.asStateFlow()

    /** True only when the active family profile has an evidenced live-data catalog. */
    val liveDataSupported: Boolean get() = activeProfile.hasLiveDataCatalog

    private val _activeDtcs = MutableStateFlow<List<HondaDtc>>(emptyList())
    val activeDtcs: StateFlow<List<HondaDtc>> = _activeDtcs.asStateFlow()

    private val _dtcReadState = MutableStateFlow(DtcReadState.NOT_PERFORMED)
    val dtcReadState: StateFlow<DtcReadState> = _dtcReadState.asStateFlow()

    private val _dtcReadDetail = MutableStateFlow("")
    val dtcReadDetail: StateFlow<String> = _dtcReadDetail.asStateFlow()

    private val _commLogs = MutableStateFlow<List<CommLogEntry>>(emptyList())
    val commLogs: StateFlow<List<CommLogEntry>> = _commLogs.asStateFlow()

    private var consecutiveLinkFailures = 0
    private var dtcReadSucceededWithCodes = false
    private var pidFallbackCooldownUntilMs = 0L
    private var cachedLiveTable: ByteArray? = null
    private var liveTableFailures = 0

    /**
     * Extra extension tables are polled one per cycle in rotation so a live poll
     * never waits on several timeouts. A table that misses twice is skipped for
     * 15 s and its previously parsed sensors are withdrawn.
     */
    private var nextExtensionPollMs = 0L
    private var extensionCursor = 0
    private var extensionTableFailures: MutableMap<String, Int> = mutableMapOf()
    private var extensionTableSkipUntil: MutableMap<String, Long> = mutableMapOf()
    private var extensionValues: Map<LiveSensor, Double> = emptyMap()
    private var extensionAvailable: Set<LiveSensor> = emptySet()
    private var extensionStates: Map<LiveSensor, LiveParameterState> = emptyMap()

    /** Sensors whose value is only meaningful while the engine is spinning. */
    private val engineRunningOnlySensors: Set<LiveSensor> =
        HondaLiveDataProfiles.tables
            .flatMap { it.fields }
            .filter { it.engineRunningOnly }
            .map { it.sensor }
            .toSet()

    /** Every sensor any reconstructed table can carry, used for the waiting state. */
    private val catalogSensors: Set<LiveSensor> =
        HondaLiveDataProfiles.tables.flatMap { it.fields }.map { it.sensor }.toSet()

    private fun log(direction: LogDirection, hex: String, description: String) {
        val timeStr = timeFormat.format(Date())
        val entry = CommLogEntry(timeStr, direction, hex, description)
        val current = _commLogs.value
        _commLogs.value = if (current.size >= 500) current.takeLast(499) + entry else current + entry
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format("%02X", it) }

    private fun buildFrame(service: Int, vararg params: Int): ByteArray =
        HondaFrameCodec.buildBaseline(service, *params)

    suspend fun connect(device: UsbDevice, preferNativeFtdi: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        val transport: UsbTransport = if (preferNativeFtdi && device.vendorId == FtdiConstants.VID_FTDI) {
            FtdiSerialDriver(usbManager, device)
        } else {
            UsbSerialLibraryTransport(usbManager, device)
        }

        log(
            LogDirection.SYS,
            "",
            "Membuka adapter ${device.deviceName} VID=0x%04X PID=0x%04X via ${transport.driverName} (${transport.chipTypeDescription})..."
                .format(device.vendorId, device.productId)
        )
        CommunicationSniffer.logEvent(
            CommEventCategory.SYSTEM,
            CommDataStage.BUS_EVENT,
            status = "OPENING",
            description = "USB open request: ${device.deviceName} VID=0x%04X PID=0x%04X driver=${transport.driverName} chip=${transport.chipTypeDescription}"
                .format(device.vendorId, device.productId)
        )

        if (!transport.open()) {
            CommunicationSniffer.recordUsbError()
            log(LogDirection.ERR, "", "Gagal membuka antarmuka USB adapter.")
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "FAILED",
                description = "Gagal membuka adapter USB ${device.deviceName}",
                errorDetails = "transport.open() returned false"
            )
            return@withContext false
        }

        driver = transport
        session.attach(transport)
        _isAdapterOpen.value = true
        _isConnected.value = true
        _isKlineInitialized.value = false
        _isEcuIdentified.value = false
        _isEcuAlive.value = false
        _identificationError.value = null
        _connectionStep.value = ConnectionPipelineStep.INTERFACE_CLAIMED
        CommunicationSniffer.setSessionStatus(CommSessionStatus.USB_OPEN, "Adapter USB terbuka; ECU belum diverifikasi")

        // Baseline RevCore performs the 70 ms LOW / 130 ms HIGH Honda wake pulse
        // immediately after the port opens, before the identification queries.
        performKlineWakeup(false)
        _connectionStep.value = ConnectionPipelineStep.TRANSFER_LAYER_ACTIVE

        log(
            LogDirection.SYS,
            "",
            "Adapter USB terbuka pada ${transport.currentBaudRate} 8N1. Wake pulse baseline selesai; ECU belum diverifikasi."
        )
        true
    }

    /**
     * Honda wake sequence used by the confirmed-working RevCore baseline:
     * purge, BREAK LOW 70 ms, BREAK HIGH 130 ms, purge. The fast-init request
     * strategies added later are deliberately not used here because the baseline
     * ECU answered after this pulse followed by the PGM-FI queries below.
     */
    suspend fun performKlineWakeup(forceBreakPulse: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        commLock.withLock {
            val d = driver ?: return@withLock false
            wakeupInternal(d, forceBreakPulse)
        }
    }

    private suspend fun wakeupInternal(d: UsbTransport, forceBreakPulse: Boolean): Boolean {
        _ignitionState.value = IgnitionState.CHECKING
        _connectionStep.value = ConnectionPipelineStep.KLINE_ATTEMPTED
        CommunicationSniffer.setSessionStatus(CommSessionStatus.INIT_ATTEMPTED, "Wake pulse K-Line dimulai")
        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.KLINE_INIT_STARTED,
            true,
            "Wake pulse baseline: BREAK LOW ${HondaEcuProfiles.baselineWake.lowMs} ms / HIGH " +
                "${HondaEcuProfiles.baselineWake.highMs} ms (force=$forceBreakPulse)"
        )

        session.beginInitialization()
        val wake = wire.wakePulse(
            d,
            HondaEcuProfiles.baselineWake.baudRate,
            HondaEcuProfiles.baselineWake.lowMs,
            HondaEcuProfiles.baselineWake.highMs
        )

        log(
            LogDirection.SYS,
            "",
            "Wake pulse host-side: BREAK LOW diminta ${wake.lowRequestedMs} ms (~${wake.lowMeasuredMs} ms), " +
                "HIGH diminta ${wake.highRequestedMs} ms (~${wake.highMeasuredMs} ms); ini waktu host, bukan hasil pengukuran bus."
        )
        session.markLineReady()
        return true
    }

    /**
     * One request/response transaction with the baseline wire behavior:
     * whole-frame write, one attempt, continuous read until the timeout, local
     * echo removal, then checksum validation. No byte pacing is inserted because
     * the confirmed-working build wrote the frame in a single USB transfer.
     */
    private suspend fun legacyTransceive(
        d: UsbTransport,
        command: ByteArray,
        timeoutMs: Int,
        sleepMs: Long = 0
    ): CommandOutcome = session.transact(
        request = command,
        config = KlineWireConfig(
            timeoutMs = timeoutMs,
            retries = 1,
            interByteMs = 0,
            echoWindowMs = 0,
            quietMs = 0,
            postWriteDelayMs = sleepMs
        ),
        expectedTransport = d
    )

    private suspend fun legacyQuery(
        d: UsbTransport,
        request: ByteArray,
        timeoutMs: Int,
        sleepMs: Long,
        status: String,
        label: String
    ): ByteArray? {
        log(LogDirection.SYS, hex(request), "TX $label")
        CommunicationSniffer.logEvent(
            CommEventCategory.PROTOCOL,
            CommDataStage.PROTOCOL_FRAME,
            rawBytes = request,
            status = status,
            description = label
        )
        return when (val outcome = legacyTransceive(d, request, timeoutMs, sleepMs)) {
            is CommandOutcome.Frame -> {
                log(LogDirection.RX, hex(outcome.bytes), "$label menerima frame valid")
                outcome.bytes
            }
            is CommandOutcome.Failure -> {
                log(LogDirection.ERR, hex(outcome.raw), "$label gagal: ${outcome.reason}")
                null
            }
        }
    }

    suspend fun sendCommand(
        command: ByteArray,
        timeoutMs: Int = 800,
        sleepMs: Long = 0
    ): ByteArray? = withContext(Dispatchers.IO) {
        commLock.withLock {
            val d = driver ?: return@withLock null
            if (!d.isOpen) return@withLock null
            when (val outcome = legacyTransceive(d, command, timeoutMs, sleepMs)) {
                is CommandOutcome.Frame -> {
                    markLinkAlive()
                    outcome.bytes
                }
                is CommandOutcome.Failure -> {
                    noteLinkFailure(outcome.reason, threshold = 1)
                    null
                }
            }
        }
    }

    private fun markLinkAlive() {
        if (driver == null || !_isConnected.value) return
        session.markAlive()
        consecutiveLinkFailures = 0
        _isEcuAlive.value = true
        _isKlineInitialized.value = true
        _ignitionState.value = IgnitionState.ON_DETECTED
    }

    private fun noteLinkFailure(reason: String, threshold: Int) {
        consecutiveLinkFailures++
        if (consecutiveLinkFailures >= threshold) {
            _isEcuAlive.value = false
            _isKlineInitialized.value = false
            // A failed read proves only that no valid reply arrived. It is not evidence
            // that the engine stopped, so do not report an ignition-OFF conclusion.
            _ignitionState.value = IgnitionState.LINK_LOST
            invalidateLiveTelemetry()
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.ECU_RESPONSE_RECEIVED,
                false,
                "Tidak ada respons ECU valid: $reason",
                reason
            )
        }
    }

    private fun invalidateLiveTelemetry() {
        _liveDataState.value = if (_liveTelemetry.value.lastUpdatedMs > 0L) {
            LiveDataState.STALE
        } else {
            LiveDataState.UNAVAILABLE
        }
        val previousStates = _liveTelemetry.value.parameterStates
        val staleStates = if (previousStates.isEmpty()) {
            emptyMap()
        } else {
            LiveParameterStates.stale(previousStates)
        }
        _liveTelemetry.value = _liveTelemetry.value.copy(
            isDataValid = false,
            lastReadAttemptMs = System.currentTimeMillis(),
            isRpmAvailable = false,
            isTpsAvailable = false,
            isTempAvailable = false,
            isIatAvailable = false,
            isMapAvailable = false,
            isBattAvailable = false,
            isInjectorAvailable = false,
            isIgnitionAvailable = false,
            isSpeedAvailable = false,
            isO2Available = false,
            parameterStates = staleStates
        )
    }

    private fun isCurrent(generation: Int): Boolean =
        generation == connectionGeneration && driver != null && _isConnected.value

    /**
     * Baseline identification order: wake pulse, PGM-FI tester present and the
     * baseline ID queries at 10400 bps, then the baseline 9600 bps fallback and
     * one repeated wake pulse when no valid frame arrived. The 0x1A query is
     * only used after those baseline queries produced no valid frame at all,
     * and a probe that merely proves the ECU answers never supplies an ID.
     */
    suspend fun identifyEcu(onPhase: ((ProtocolPhase) -> Unit)? = null): EcmModel? = withContext(Dispatchers.IO) {
        commLock.withLock {
            val d = driver ?: return@withLock null
            val generation = connectionGeneration

            _ignitionState.value = IgnitionState.CHECKING
            _isEcuIdentified.value = false
            _connectedModel.value = null
            _ecuIdentity.value = null
            _identityResolution.value = null
            _identificationError.value = null

            onPhase?.invoke(ProtocolPhase.FAST_INIT)
            session.beginIdentification()
            wakeupInternal(d, forceBreakPulse = false)
            if (!isCurrent(generation)) return@withLock null

            onPhase?.invoke(ProtocolPhase.SEARCHING_IDENTIFICATION)
            var result = baselineIdentifyAtBaud(d, FtdiConstants.BAUD_KLINE_DEFAULT)
            if (!isCurrent(generation)) return@withLock null

            if (!result.anyValidFrame) {
                log(LogDirection.SYS, "", "Tidak ada respons pada 10400 bps. Mencoba fallback baseline 9600 bps...")
                result = baselineIdentifyAtBaud(d, FtdiConstants.BAUD_STANDARD)
                if (!result.anyValidFrame) d.setBaudRate(FtdiConstants.BAUD_KLINE_DEFAULT)
                if (!isCurrent(generation)) return@withLock null
            }

            if (!result.anyValidFrame) {
                log(LogDirection.SYS, "", "Mengulang wake pulse baseline lalu query identifikasi pada 10400 bps...")
                d.setBaudRate(FtdiConstants.BAUD_KLINE_DEFAULT)
                wakeupInternal(d, forceBreakPulse = true)
                result = baselineIdentifyAtBaud(d, FtdiConstants.BAUD_KLINE_DEFAULT)
                if (!isCurrent(generation)) return@withLock null
            }

            if (result.identifier != null) {
                markLinkAlive()
                onPhase?.invoke(ProtocolPhase.ECU_RESPONDED)
                val extraction = result.canonicalization
                val frame = result.validFrame
                if (extraction == null || frame == null) {
                    _identificationError.value =
                        "Canonical ECM ID '${result.identifier}' tidak memiliki frame respons yang valid."
                    log(
                        LogDirection.ERR,
                        "",
                        "ID extract menghasilkan canonical tanpa frame; identitas tidak dipublikasikan."
                    )
                    onPhase?.invoke(ProtocolPhase.NO_RESPONSE)
                    return@withLock null
                }
                val identity = buildIdentity(extraction, frame, result.source, result.requestLabel)
                val model = publishIdentifiedModel(identity, result.requestLabel)
                if (!isCurrent(generation)) return@withLock null
                onPhase?.invoke(if (_isEcuIdentified.value) ProtocolPhase.IDENTIFIED else ProtocolPhase.ECU_RESPONDED)
                return@withLock model
            }

            if (!result.anyValidFrame) {
                onPhase?.invoke(ProtocolPhase.DIAGNOSTIC_FALLBACK)
                val idReply = readIdentifierFrame(d, withParameter = true)
                    ?: readIdentifierFrame(d, withParameter = false)
                if (!isCurrent(generation)) return@withLock null
                if (idReply != null) {
                    markLinkAlive()
                    CommunicationSniffer.setSessionStatus(
                        CommSessionStatus.ECU_RESPONSE_VALID,
                        "Frame identifikasi 0x1A checksum-valid diterima"
                    )
                    onPhase?.invoke(ProtocolPhase.ECU_RESPONDED)
                    val parsed = HondaIdentifierParser.parse(idReply)
                    if (parsed != null) {
                        val extraction = EcuIdCanonicalizer.canonicalizePayload(
                            payload = parsed.payload,
                            knownCanonicalIds = EcmDatabase.knownEcmIds()
                        )
                        log(
                            LogDirection.SYS,
                            "",
                            "ID extract query=0x1A raw=${extraction.rawId ?: "-"} " +
                                "canonical=${extraction.canonicalId ?: "-"} method=${extraction.method}"
                        )
                        val identity = EcuIdentity.fromIdentifier(
                            identifier = parsed.identifier,
                            source = "0x1A",
                            rawFrame = idReply,
                            payload = parsed.payload,
                            extraction = parsed.extraction,
                            canonicalizationResult = extraction,
                            extraNotes = listOf("Identification query: service 0x1A fallback")
                        )
                        val model = publishIdentifiedModel(identity, "service 0x1A")
                        if (!isCurrent(generation)) return@withLock null
                        onPhase?.invoke(if (_isEcuIdentified.value) ProtocolPhase.IDENTIFIED else ProtocolPhase.ECU_RESPONDED)
                        return@withLock model
                    }
                    CommunicationSniffer.recordParseFailure()
                    _identificationError.value =
                        "Frame 0x1A checksum-valid diterima, tetapi ID tidak dapat di-decode dengan aman."
                    log(
                        LogDirection.SYS,
                        hex(idReply),
                        "Frame 0x1A diterima, tetapi ID tidak dapat di-decode dengan aman."
                    )
                } else if (!probeEcuPresence(d)) {
                    if (!isCurrent(generation)) return@withLock null
                    onPhase?.invoke(ProtocolPhase.NO_RESPONSE)
                    _ignitionState.value = IgnitionState.LINK_LOST
                    CommunicationSniffer.updateTimelineStage(
                        DiagnosticStage.PROTOCOL_INIT_COMPLETED,
                        false,
                        "Tidak ada respons ECU valid",
                        "Wake pulse dan seluruh query baseline tidak menerima frame valid."
                    )
                    log(
                        LogDirection.ERR,
                        "",
                        "ECU tidak memberikan respons valid. Ini bukan bukti kontak OFF; periksa kontak ON, pin K-Line, dan transceiver adapter."
                    )
                    return@withLock null
                }
            }

            if (result.anyValidFrame) {
                _identificationError.value =
                    "Frame valid diterima, tetapi tidak ada ID ECU yang dapat diekstrak."
            }
            markLinkAlive()
            CommunicationSniffer.setSessionStatus(
                CommSessionStatus.ECU_RESPONSE_VALID,
                "ECU merespons, tetapi ID belum dapat diverifikasi"
            )
            onPhase?.invoke(ProtocolPhase.ECU_RESPONDED)
            val placeholder = EcmDatabase.createUnverifiedModel(
                "HONDA-KLINE",
                "ECM Belum Teridentifikasi (K-Line merespons)"
            )
            if (!isCurrent(generation)) return@withLock null
            _connectedModel.value = placeholder
            _isEcuIdentified.value = false
            session.markIdentified(verified = false)
            _connectionStep.value = ConnectionPipelineStep.PROTOCOL_INITIALIZED
            log(
                LogDirection.SYS,
                "",
                "ECU merespons di K-Line, tetapi ID tidak dapat dibaca. Model tidak diverifikasi."
            )
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.ECU_IDENTIFIED,
                false,
                "ECU aktif, ID tidak terbaca",
                "Query baseline tidak menghasilkan ID yang dapat diverifikasi."
            )
            return@withLock placeholder
        }
    }

    private data class BaselineIdResult(
        val alive: Boolean,
        val canonicalization: EcuIdCanonicalization?,
        val validFrame: ByteArray?,
        val source: String,
        val requestLabel: String
    ) {
        val identifier: String? get() = canonicalization?.canonicalId
        val anyValidFrame: Boolean get() = alive || validFrame != null
    }

    /**
     * Documented response layouts for the baseline identification queries.
     *
     * Only the 0x72/0x00 query has a captured frame:
     * `72 0B 72 00 00 01 03 7A 0F 02 <checksum>`. Its payload is the two-byte
     * selector echoed from the request (`00 00`) followed by the complete five-byte
     * ECM ID. The request's last parameter (`0x05`) names that field length. The
     * other queries keep no layout because no response capture proves their prefix
     * length; they use boundary candidates and exact database validation instead.
     */
    private fun responseLayoutFor(requestId: String): IdResponseLayout? = when (requestId) {
        "id.7200" -> IdResponseLayout(
            prefixBytes = 2,
            fieldBytes = 5,
            evidence = "RECONSTRUCTED_FROM_CAPTURED_RESPONSE",
            description = "72 0B 72 00 00 01 03 7A 0F 02: selector echo 00 00 + five-byte ECM ID"
        )
        else -> null
    }

    private fun canonicalizeReply(
        requestId: String,
        reply: ByteArray,
        knownEcmIds: Set<String>
    ): EcuIdCanonicalization? {
        val parsed = HondaIdentifierParser.parse(reply) ?: return null
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            payload = parsed.payload,
            layout = responseLayoutFor(requestId),
            knownCanonicalIds = knownEcmIds
        )
        log(
            LogDirection.SYS,
            "",
            "ID extract query=$requestId raw=${extraction.rawId ?: "-"} " +
                "canonical=${extraction.canonicalId ?: "-"} method=${extraction.method} " +
                "prefix=${extraction.protocolPrefixHex ?: "-"} trailing=${extraction.trailingHex ?: "-"}"
        )
        return extraction
    }

    private suspend fun baselineIdentifyAtBaud(d: UsbTransport, baudRate: Int): BaselineIdResult {
        // Identification always uses the family-neutral baseline catalog. A
        // family detected from a response header must not hide the probe frames
        // that are still being tried.
        val profile = HondaEcuProfiles.genericBaseline
        val testerReq = requireNotNull(profile.testerPresent)
        val id00F0Req = requireNotNull(profile.idTable00F0)
        val id7200Req = requireNotNull(profile.id7200)
        val id71Req = requireNotNull(profile.partNumber71)

        d.setBaudRate(baudRate)
        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.PROTOCOL_INIT_COMPLETED,
            true,
            "Query identifikasi baseline Honda pada $baudRate bps"
        )

        var alive = false
        var validFrame: ByteArray? = null
        val knownEcmIds = EcmDatabase.knownEcmIds()

        val tester = legacyQuery(
            d,
            testerReq.bytes,
            testerReq.timeoutMs,
            testerReq.sleepMs,
            testerReq.status,
            "${testerReq.label} @$baudRate bps"
        )
        if (tester != null) {
            alive = true
            validFrame = tester
            markLinkAlive()
        }

        val id00F0 = legacyQuery(
            d,
            id00F0Req.bytes,
            id00F0Req.timeoutMs,
            id00F0Req.sleepMs,
            id00F0Req.status,
            "${id00F0Req.label} @$baudRate bps"
        )
        if (id00F0 != null) {
            alive = true
            validFrame = id00F0
            markLinkAlive()
            val extraction = if (isNegativeResponse(id00F0)) {
                null
            } else {
                canonicalizeReply(id00F0Req.id, id00F0, knownEcmIds)
            }
            if (extraction?.canonicalId != null) {
                return BaselineIdResult(true, extraction, id00F0, "0x00/F0", id00F0Req.label)
            }
        }

        val id7200 = legacyQuery(
            d,
            id7200Req.bytes,
            id7200Req.timeoutMs,
            id7200Req.sleepMs,
            id7200Req.status,
            "${id7200Req.label} @$baudRate bps"
        )
        if (id7200 != null) {
            alive = true
            validFrame = id7200
            markLinkAlive()
            val extraction = if (isNegativeResponse(id7200)) {
                null
            } else {
                canonicalizeReply(id7200Req.id, id7200, knownEcmIds)
            }
            if (extraction?.canonicalId != null) {
                return BaselineIdResult(true, extraction, id7200, "0x72/0x00", id7200Req.label)
            }
        }

        val id71 = legacyQuery(
            d,
            id71Req.bytes,
            id71Req.timeoutMs,
            id71Req.sleepMs,
            id71Req.status,
            "${id71Req.label} @$baudRate bps"
        )
        if (id71 != null) {
            alive = true
            validFrame = id71
            markLinkAlive()
            val extraction = if (isNegativeResponse(id71)) {
                null
            } else {
                canonicalizeReply(id71Req.id, id71, knownEcmIds)
            }
            if (extraction?.canonicalId != null) {
                return BaselineIdResult(true, extraction, id71, "0x71/0x00", id71Req.label)
            }
        }

        return BaselineIdResult(alive, null, validFrame, "", "")
    }

    private fun isNegativeResponse(reply: ByteArray): Boolean =
        reply.size > 2 && (reply[2].toInt() and 0xFF) == NEGATIVE_RESPONSE_SERVICE

    /**
     * Builds the identity record from the canonical field and keeps the complete
     * raw payload. The old fixed five-byte slice at frame index 4 is gone: it read
     * `00 01 03 7A 0F` from `72 0B 72 00 00 01 03 7A 0F 02`, dropping the trailing
     * revision byte and keeping a selector byte.
     */
    private fun buildIdentity(
        extraction: EcuIdCanonicalization,
        frame: ByteArray,
        source: String,
        requestLabel: String
    ): EcuIdentity {
        val parsed = HondaIdentifierParser.parse(frame)
        val raw = extraction.rawId ?: parsed?.identifier ?: ""
        val notes = buildList {
            add("Identification query: $requestLabel")
            if (extraction.canonicalId == null) {
                add("No complete canonical ECM ID field was selected; raw payload is preserved.")
            }
            if (extraction.rawId != null && extraction.canonicalId != null &&
                extraction.rawId != extraction.canonicalId
            ) {
                add("Raw ID '${extraction.rawId}' and canonical ID '${extraction.canonicalId}' are kept separate.")
            }
        }
        return EcuIdentity.fromIdentifier(
            identifier = raw,
            source = source,
            rawFrame = frame,
            payload = parsed?.payload,
            extraction = parsed?.extraction ?: extraction.method,
            canonicalizationResult = extraction,
            extraNotes = notes
        )
    }

    private fun publishIdentifiedModel(identity: EcuIdentity, requestLabel: String): EcmModel {
        val resolution = EcmDatabase.resolveIdentity(identity)
        _ecuIdentity.value = identity
        _identityResolution.value = resolution

        val label = identity.canonicalEcuId ?: identity.ecuIdRaw ?: identity.ecmId ?: "HONDA-KLINE"
        val model = when (resolution.status) {
            IdentityMatchStatus.EXACT ->
                resolution.model ?: EcmDatabase.createUnverifiedModel(label)
            IdentityMatchStatus.PARTIAL_MATCH ->
                // A truncated identity is a diagnostic candidate, never an ECU model.
                EcmDatabase.createUnverifiedModel(label)
            IdentityMatchStatus.AMBIGUOUS ->
                EcmDatabase.createAmbiguousModel(identity, resolution.candidates)
            IdentityMatchStatus.NOT_FOUND ->
                EcmDatabase.createUnverifiedModel(label)
        }
        // ECU_IDENTIFIED is only valid after an exact database match.
        val identified = resolution.status == IdentityMatchStatus.EXACT

        _connectedModel.value = model
        _isEcuIdentified.value = identified
        session.markIdentified(verified = identified)
        _connectionStep.value = if (identified) {
            ConnectionPipelineStep.DIAGNOSTIC_READY
        } else {
            ConnectionPipelineStep.PROTOCOL_INITIALIZED
        }

        val matched = resolution.model
        val evidence = when (resolution.status) {
            IdentityMatchStatus.EXACT ->
                "${resolution.quality.name} ${resolution.method?.name ?: "EXACT"} " +
                    "confidence=${resolution.method?.confidence ?: 0}"
            IdentityMatchStatus.PARTIAL_MATCH ->
                "CANDIDATE ${resolution.method?.name ?: ""} " +
                    "(${resolution.candidates.firstOrNull()?.relation?.name ?: "BOUNDED"})"
            IdentityMatchStatus.AMBIGUOUS -> "AMBIGUOUS (${resolution.candidates.size} candidates)"
            IdentityMatchStatus.NOT_FOUND -> resolution.quality.name
        }
        val source = identity.responseSource
        val rawIdentifier = identity.ecuIdRaw ?: "-"
        val canonicalIdentifier = identity.canonicalEcuId ?: "-"

        _identificationError.value = when (resolution.status) {
            IdentityMatchStatus.EXACT -> null
            else -> resolution.explanation.ifBlank {
                "ID ECU tidak cocok persis dengan database; identitas ditandai belum terverifikasi."
            }
        }

        CommunicationSniffer.setSessionStatus(
            if (identified) CommSessionStatus.ECU_ID_VALID else CommSessionStatus.ECU_RESPONSE_VALID,
            "ID ECU raw=$rawIdentifier canonical=$canonicalIdentifier dari query $source: " +
                "status=${resolution.status} ($evidence)"
        )
        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.ECU_IDENTIFIED,
            identified,
            when {
                identified && matched != null ->
                    "ID cocok database: ${matched.bikeName} [${matched.partNumber}]"
                resolution.status == IdentityMatchStatus.PARTIAL_MATCH ->
                    "ID tidak lengkap; kandidat ${resolution.candidates.joinToString { it.model.id }} " +
                        "tidak dipilih sebagai model"
                resolution.status == IdentityMatchStatus.AMBIGUOUS ->
                    "ID $canonicalIdentifier ambigu (${resolution.candidates.size} kandidat); " +
                        "model tidak dipilih otomatis"
                else ->
                    "ID $canonicalIdentifier belum cocok persis dengan database; model belum terverifikasi"
            },
            when {
                identified && matched != null &&
                    (matched.isVerifiedDiagnostic || matched.isVerifiedRemap) -> null
                identified -> "Entri database ini belum diverifikasi dengan pengujian hardware."
                else -> resolution.explanation
            }
        )

        val pipelineLine = buildString {
            append("ID pipeline query=").append(requestLabel)
            append(" source=").append(source)
            append(" status=").append(resolution.status)
            append(" quality=").append(resolution.quality)
            append(" matcher=").append(resolution.method?.name ?: "-")
            append(" rawId=").append(rawIdentifier)
            append(" canonical=").append(canonicalIdentifier)
            append(" prefix=").append(identity.protocolPrefixHex ?: "-")
            append(" trailing=").append(identity.trailingBytesHex ?: "-")
            append(" method=").append(identity.canonicalizationMethod)
            append(" dbId=").append(matched?.id ?: "-")
            append(" part=").append(matched?.partNumber ?: "-")
            append(" confidence=").append(resolution.method?.confidence ?: 0)
            append(" reason=").append(resolution.explanation.ifBlank { "-" })
        }
        log(LogDirection.SYS, identity.rawResponseHex ?: "", pipelineLine)
        log(
            LogDirection.SYS,
            identity.rawResponseHex ?: "",
            when {
                resolution.status == IdentityMatchStatus.AMBIGUOUS ->
                    "ECU ID $rawIdentifier ambigu: ${resolution.candidates.joinToString { "${it.model.id}/${it.model.partNumber}" }} " +
                        "[$source; $evidence; raw retained]"
                resolution.status == IdentityMatchStatus.NOT_FOUND ->
                    "ECU merespons ID raw=$rawIdentifier canonical=$canonicalIdentifier ($source); " +
                        "belum terverifikasi di database. Payload lengkap disimpan. [$evidence]"
                resolution.status == IdentityMatchStatus.PARTIAL_MATCH ->
                    "ID ECU tidak lengkap: kandidat ${resolution.candidates.joinToString { "${it.model.id}/${it.model.partNumber}" }} " +
                        "[$source; $evidence]. Model tidak dipublikasikan."
                identified && matched != null &&
                    (matched.isVerifiedDiagnostic || matched.isVerifiedRemap) ->
                    "ID ECU cocok database: ${matched.bikeName} (${matched.partNumber}) [$source; $evidence]"
                else -> "ID ECU cocok entri database ${matched?.bikeName}; kompatibilitas belum terverifikasi. [$source; $evidence]"
            }
        )
        return model
    }

    /**
     * Bounded diagnostic fallback after fast-init failure. These are reference
     * request frames only; a valid reply proves the ECU is answering, but it is
     * never decoded as an identifier.
     */
    private suspend fun probeEcuPresence(d: UsbTransport): Boolean {
        val probes = listOf(
            "fallback: HDS tester-present FE 04 72 8C" to byteArrayOf(0xFE.toByte(), 0x04, 0x72, 0x8C.toByte()),
            "fallback: PGM-FI query 0x00/0xF0" to buildFrame(0x00, 0xF0),
            "fallback: live table 0x72/0x00" to buildFrame(0x72, 0x00)
        )

        for ((name, request) in probes) {
            if (driver !== d || !_isConnected.value) return false
            CommunicationSniffer.recordInitAttempt(name)
            log(LogDirection.SYS, hex(request), "Query diagnostik fallback: $name")
            CommunicationSniffer.logEvent(
                CommEventCategory.PROTOCOL,
                CommDataStage.PROTOCOL_FRAME,
                rawBytes = request,
                status = "FALLBACK_QUERY",
                description = "Query diagnostik fallback: $name"
            )

            when (val outcome = legacyTransceive(d, request, timeoutMs = 500)) {
                is CommandOutcome.Frame -> {
                    if (driver !== d || !_isConnected.value) return false
                    markLinkAlive()
                    CommunicationSniffer.setSessionStatus(
                        CommSessionStatus.ECU_RESPONSE_VALID,
                        "ECU merespons query diagnostik fallback: $name"
                    )
                    log(LogDirection.RX, hex(outcome.bytes), "Fallback $name menerima frame valid")
                    return true
                }
                is CommandOutcome.Failure -> {
                    log(LogDirection.ERR, hex(outcome.raw), "Fallback $name gagal: ${outcome.reason}")
                }
            }
        }
        return false
    }

    private suspend fun readIdentifierFrame(d: UsbTransport, withParameter: Boolean): ByteArray? {
        val request = if (withParameter) buildFrame(0x1A, 0x00) else buildFrame(0x1A)
        log(
            LogDirection.SYS,
            hex(request),
            "Query identifikasi service 0x1A ${if (withParameter) "parameter 0x00" else "tanpa parameter"}"
        )
        CommunicationSniffer.logEvent(
            CommEventCategory.PROTOCOL,
            CommDataStage.PROTOCOL_FRAME,
            rawBytes = request,
            status = "ID_QUERY",
            description = "Query ID service 0x1A ${if (withParameter) "parameter 0x00" else "tanpa parameter"}"
        )
        return when (val outcome = legacyTransceive(d, request, timeoutMs = 700)) {
            is CommandOutcome.Frame -> outcome.bytes
            is CommandOutcome.Failure -> {
                log(LogDirection.ERR, hex(outcome.raw), "Baca ID gagal: ${outcome.reason}")
                null
            }
        }
    }

    suspend fun readDtcs(): DtcReadResult = withContext(Dispatchers.IO) {
        commLock.withLock {
            readDtcsInternal()
        }
    }

    private suspend fun readDtcsInternal(): DtcReadResult {
        val d = driver
        if (d == null) {
            _dtcReadState.value = DtcReadState.NO_RESPONSE
            _dtcReadDetail.value = "Adapter USB tidak terbuka"
            return DtcReadResult.NoResponse("Adapter USB tidak terbuka")
        }

        val profile = activeProfile
        if (profile.dtcRead.isEmpty()) {
            val reason = "Tabel DTC ${profile.family.displayName} belum direkonstruksi; tidak ada query yang dikirim."
            _dtcReadState.value = DtcReadState.NO_RESPONSE
            _dtcReadDetail.value = reason
            log(LogDirection.ERR, "", reason)
            CommunicationSniffer.logEvent(
                CommEventCategory.PROTOCOL,
                CommDataStage.PARSED_RESPONSE,
                status = "DTC_UNRESOLVED",
                description = reason
            )
            return DtcReadResult.NoResponse(reason)
        }

        var lastReason = "Tidak ada respons query DTC baseline"
        for (req in profile.dtcRead) {
            val label = req.shortTag
            val reply = legacyQuery(
                d,
                req.bytes,
                req.timeoutMs,
                req.sleepMs,
                req.status,
                req.label
            )
            if (reply == null) {
                lastReason = "Query DTC $label tidak menerima frame valid"
                continue
            }
            if (reply.size < 5) {
                lastReason = "Frame DTC $label terlalu pendek (${reply.size} byte)"
                CommunicationSniffer.recordInvalidFrame()
                continue
            }
            val service = reply[2].toInt() and 0xFF
            if (service == NEGATIVE_RESPONSE_SERVICE) {
                lastReason = "ECU menolak query DTC $label (respons negatif 0x7F)"
                log(LogDirection.ERR, hex(reply), lastReason)
                CommunicationSniffer.logEvent(
                    CommEventCategory.PROTOCOL,
                    CommDataStage.PARSED_RESPONSE,
                    rawBytes = reply,
                    status = "NEGATIVE_RESPONSE",
                    description = lastReason
                )
                continue
            }

            markLinkAlive()
            // Baseline frame layout: [header, len, service, subfunction, data..., checksum].
            val dataStart = if (reply.size >= 6) 4 else 3
            val dataEnd = (reply.size - 1).coerceAtLeast(dataStart)
            val payload = if (dataEnd > dataStart) {
                reply.copyOfRange(dataStart, dataEnd)
            } else {
                ByteArray(0)
            }
            val dtcs = HondaDtcDatabase.parseDtcPacket(payload)
            _activeDtcs.value = dtcs
            dtcReadSucceededWithCodes = dtcs.isNotEmpty()
            _dtcReadState.value = if (dtcs.isEmpty()) DtcReadState.OK_EMPTY else DtcReadState.OK_CODES
            _dtcReadDetail.value = "Query baseline $label; ${dtcs.size} kode"
            CommunicationSniffer.setSessionStatus(
                CommSessionStatus.DTC_READ_CONFIRMED,
                "DTC read confirmed (query $label, ${dtcs.size} kode)"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.PROTOCOL,
                CommDataStage.PARSED_RESPONSE,
                rawBytes = reply,
                status = "DTC_READ_CONFIRMED",
                description = "DTC terbaca dari query $label: ${dtcs.size} kode, payload=${payload.size} byte"
            )
            log(
                LogDirection.RX,
                hex(reply),
                "DTC dibaca dari query $label: ${dtcs.size} kode"
            )
            return DtcReadResult.Success(dtcs, "baseline-$label")
        }

        noteLinkFailure("Baca DTC: $lastReason", threshold = 2)
        _dtcReadState.value = DtcReadState.NO_RESPONSE
        _dtcReadDetail.value = lastReason
        log(LogDirection.ERR, "", "Baca DTC gagal: $lastReason. Daftar DTC lama tidak diubah.")
        return DtcReadResult.NoResponse(lastReason)
    }

    suspend fun clearDtcs(): ClearDtcResult = withContext(Dispatchers.IO) {
        commLock.withLock {
            val d = driver ?: return@withLock ClearDtcResult(
                status = ClearDtcStatus.NO_ADAPTER,
                messageEn = "FAILED: USB diagnostic adapter is not open.",
                messageId = "FAILED: Adapter USB diagnostik belum terbuka.",
                remainingDtcs = _activeDtcs.value
            )

            val profile = activeProfile
            if (profile.dtcClear.isEmpty()) {
                val reason = "Perintah clear DTC ${profile.family.displayName} belum direkonstruksi; " +
                    "tidak ada perintah yang dikirim."
                log(LogDirection.ERR, "", reason)
                return@withLock ClearDtcResult(
                    status = ClearDtcStatus.UNSUPPORTED_FAMILY,
                    messageEn = "FAILED: Clear DTC frames for ${profile.family.displayName} are not reconstructed. No command was sent.",
                    messageId = "FAILED: Frame clear DTC untuk ${profile.family.displayName} belum direkonstruksi. Tidak ada perintah yang dikirim.",
                    remainingDtcs = _activeDtcs.value
                )
            }

            log(LogDirection.SYS, "", "Mengirim perintah Clear DTC baseline (service 0x60).")
            var ackFrame: ByteArray? = null
            var ackLabel = ""
            for (req in profile.dtcClear) {
                val label = req.shortTag
                val reply = legacyQuery(
                    d,
                    req.bytes,
                    req.timeoutMs,
                    req.sleepMs,
                    req.status,
                    req.label
                )
                if (reply == null || reply.size < 4) {
                    log(LogDirection.ERR, "", "Clear DTC $label tidak menghasilkan frame valid; mencoba kandidat berikutnya.")
                    continue
                }
                val header = reply[0].toInt() and 0xFF
                val service = reply[2].toInt() and 0xFF
                if (service == NEGATIVE_RESPONSE_SERVICE) {
                    log(LogDirection.ERR, hex(reply), "ECU menolak clear DTC $label (respons negatif 0x7F).")
                    CommunicationSniffer.logEvent(
                        CommEventCategory.PROTOCOL,
                        CommDataStage.PARSED_RESPONSE,
                        rawBytes = reply,
                        status = "NEGATIVE_RESPONSE",
                        description = "ECU menolak clear DTC $label (respons negatif 0x7F)"
                    )
                    continue
                }
                if (header !in profile.clearAckHeaders) {
                    log(LogDirection.ERR, hex(reply), "Respons clear $label bukan header ACK baseline (0x%02X).".format(header))
                    continue
                }
                ackFrame = reply
                ackLabel = label
                break
            }

            if (ackFrame == null) {
                noteLinkFailure("Clear DTC: tidak ada ACK valid", threshold = 2)
                log(LogDirection.ERR, "", "Perintah clear dikirim, ECU tidak mengonfirmasi pada seluruh kandidat baseline.")
                return@withLock ClearDtcResult(
                    status = ClearDtcStatus.REJECTED_OR_TIMEOUT,
                    messageEn = "FAILED: Clear command was sent, but the ECU did not confirm. DTC list unchanged.",
                    messageId = "FAILED: Perintah clear sudah dikirim, tetapi ECU tidak mengonfirmasi. Daftar DTC tidak diubah.",
                    remainingDtcs = _activeDtcs.value
                )
            }

            markLinkAlive()
            CommunicationSniffer.setSessionStatus(
                CommSessionStatus.ECU_RESPONSE_VALID,
                "ACK clear DTC diterima dari query $ackLabel"
            )
            log(LogDirection.RX, hex(ackFrame), "ACK clear DTC diterima ($ackLabel). Menunggu 250 ms lalu membaca ulang.")
            delay(250)

            return@withLock when (val reread = readDtcsInternal()) {
                is DtcReadResult.Success -> {
                    if (reread.dtcs.isEmpty()) {
                        CommunicationSniffer.setSessionStatus(
                            CommSessionStatus.OPERATION_CONFIRMED,
                            "Clear DTC dikonfirmasi dan pembacaan ulang tidak menemukan DTC"
                        )
                        ClearDtcResult(
                            status = ClearDtcStatus.SUCCESS_VERIFIED,
                            messageEn = "SUCCESS: ECU acknowledged clear and no DTC is readable anymore. This does not guarantee the MIL lamp is off; perform an ignition cycle and road test.",
                            messageId = "SUCCESS: ECU mengonfirmasi clear dan tidak ada DTC yang terbaca lagi. Ini tidak menjamin lampu MIL padam; lakukan siklus kontak dan uji jalan.",
                            remainingDtcs = emptyList()
                        )
                    } else {
                        val summary = reread.dtcs.joinToString(", ") { it.standardCode }
                        ClearDtcResult(
                            status = ClearDtcStatus.RE_DETECTED_ACTIVE_FAULT,
                            messageEn = "FAILED: Clear acknowledged, but the ECU re-reported active DTC(s): $summary.",
                            messageId = "FAILED: Clear dikonfirmasi, tetapi ECU kembali melaporkan DTC aktif: $summary.",
                            remainingDtcs = reread.dtcs
                        )
                    }
                }
                is DtcReadResult.NoResponse -> ClearDtcResult(
                    status = ClearDtcStatus.CLEARED_UNVERIFIED,
                    messageEn = "WARNING: Clear was acknowledged, but re-read failed (${reread.reason}). Result not verified.",
                    messageId = "PERINGATAN: Clear dikonfirmasi, tetapi pembacaan ulang gagal (${reread.reason}). Hasil belum terverifikasi.",
                    remainingDtcs = _activeDtcs.value
                )
                is DtcReadResult.Unrecognized -> ClearDtcResult(
                    status = ClearDtcStatus.CLEARED_UNVERIFIED,
                    messageEn = "WARNING: Clear was acknowledged, but the re-read payload format is unverified. Result not verified.",
                    messageId = "PERINGATAN: Clear dikonfirmasi, tetapi format pembacaan ulang tidak terverifikasi. Hasil belum dapat dipastikan.",
                    remainingDtcs = _activeDtcs.value
                )
            }
        }
    }

    suspend fun queryLiveData(): LiveTelemetry? = withContext(Dispatchers.IO) {
        commLock.withLock {
            val d = driver ?: run {
                _liveDataState.value = LiveDataState.UNAVAILABLE
                return@withLock null
            }
            val profile = activeProfile
            if (!profile.hasLiveDataCatalog) {
                invalidateLiveTelemetry()
                _liveDataState.value = LiveDataState.UNSUPPORTED_PROFILE
                log(
                    LogDirection.ERR,
                    "",
                    "Katalog live data ${profile.family.displayName} tidak tersedia (hak penggunaan belum jelas); tidak ada query yang dikirim."
                )
                return@withLock null
            }

            var response: ByteArray? = null
            var usedProfile: LiveTableProfile? = null

            // A table that answered last poll is retried first; three misses drop it.
            val cached = cachedLiveTable
            if (cached != null) {
                val cachedProfile = liveProfileFor(cached)
                if (cachedProfile == null) {
                    cachedLiveTable = null
                } else {
                    val reply = legacyQuery(
                        d,
                        cached,
                        300,
                        2,
                        "LIVE_DATA_QUERY",
                        "Live data tabel cache 0x${cachedProfile.tableId}"
                    )
                    if (reply != null && isUsableLiveFrame(reply, cachedProfile)) {
                        response = reply
                        usedProfile = cachedProfile
                        liveTableFailures = 0
                    } else {
                        liveTableFailures++
                        if (liveTableFailures >= 3) {
                            cachedLiveTable = null
                            liveTableFailures = 0
                        }
                    }
                }
            }

            // Registered tables are tried in the order the profile declares.
            if (response == null) {
                for (req in profile.liveDataTables) {
                    val command = req.bytes
                    if (cached != null && command.contentEquals(cached)) continue
                    val requestProfile = liveProfileFor(command) ?: continue
                    val reply = legacyQuery(
                        d,
                        command,
                        req.timeoutMs,
                        req.sleepMs,
                        req.status,
                        req.label
                    )
                    if (reply != null && isUsableLiveFrame(reply, requestProfile)) {
                        response = reply
                        usedProfile = requestProfile
                        cachedLiveTable = command
                        liveTableFailures = 0
                        break
                    }
                }
            }

            val frame = response
            val primaryProfile = usedProfile
            if (frame != null && primaryProfile != null) {
                markLinkAlive()
                session.beginMonitoring()
                pidFallbackCooldownUntilMs = 0L
                refreshExtensionTables(d, profile, primaryProfile.tableId)
                val telemetry = parseLiveFrame(frame, _connectedModel.value, primaryProfile)
                if (telemetry != null) {
                    _liveTelemetry.value = telemetry
                    _liveDataState.value = LiveDataState.ACTIVE
                    if (telemetry.isDataValid && telemetry.isRpmAvailable) {
                        CommunicationSniffer.setSessionStatus(
                            CommSessionStatus.LIVE_DATA_VALID,
                            "Live data parsed from ECU frame: RPM=${telemetry.engineRpm}"
                        )
                        CommunicationSniffer.logEvent(
                            CommEventCategory.PROTOCOL,
                            CommDataStage.PARSED_RESPONSE,
                            rawBytes = frame,
                            status = "LIVE_DATA_VALID",
                            description = "Live data tabel 0x${primaryProfile.tableId} parsed: RPM=${telemetry.engineRpm} TPS=${telemetry.tpsPercent}%"
                        )
                    }
                    return@withLock telemetry
                }
                invalidateLiveTelemetry()
                _liveDataState.value = LiveDataState.INVALID_PAYLOAD
                CommunicationSniffer.recordParseFailure()
                log(
                    LogDirection.ERR,
                    hex(frame),
                    "Frame live data valid checksum, tetapi field tidak masuk akal. Nilai tidak ditampilkan."
                )
                CommunicationSniffer.logEvent(
                    CommEventCategory.PROTOCOL,
                    CommDataStage.PARSED_RESPONSE,
                    rawBytes = frame,
                    status = "PARSE_FAILED",
                    description = "Frame live data valid checksum tetapi field tidak masuk akal; nilai tidak ditampilkan."
                )
                return@withLock null
            }

            log(LogDirection.ERR, "", "Tidak ada tabel live data yang dijawab. Mencoba fallback PID 0x21.")
            invalidateLiveTelemetry()

            val now = System.currentTimeMillis()
            if (now >= pidFallbackCooldownUntilMs) {
                val fallback = readPidFallback(d)
                if (fallback != null) {
                    _liveTelemetry.value = fallback
                    _liveDataState.value = LiveDataState.ACTIVE
                    return@withLock fallback
                }
                pidFallbackCooldownUntilMs = now + 10_000L
            }

            noteLinkFailure("Live data: tidak ada frame tabel baseline valid", threshold = LivePollPolicy.MAX_CONSECUTIVE_FAILURES)
            _liveDataState.value = if (_liveTelemetry.value.lastUpdatedMs > 0L) {
                LiveDataState.STALE
            } else {
                LiveDataState.WAITING_RESPONSE
            }
            null
        }
    }

    /**
     * Applies the supplied table layout to a de-framed response. The payload
     * starts at byte 4 (header, length, service, table echo); a response whose
     * table echo does not match the request is rejected instead of being decoded
     * with guessed offsets. Extension values only fill sensors the primary table
     * does not carry; primary values win.
     */
    private fun parseLiveFrame(
        frame: ByteArray,
        model: EcmModel?,
        primary: LiveTableProfile
    ): LiveTelemetry? {
        if (!KlinePacket.isValidHeader(frame[0])) return null
        val payload = extractLivePayload(frame, primary) ?: return null

        val parsed = LiveDataFrameParser.parse(payload, primary)

        // No single sensor gates the frame. At key-on/engine-off the ECU still
        // answers with TPS/ECT/IAT/battery even when RPM is absent or invalid.
        if (!parsed.dataValid && extensionAvailable.isEmpty()) {
            log(
                LogDirection.ERR,
                hex(frame),
                "Frame ${primary.id} tidak memuat satu pun parameter yang lolos pemeriksaan; nilai tidak ditampilkan."
            )
            return null
        }

        val values = mutableMapOf<LiveSensor, Double>()
        val states = mutableMapOf<LiveSensor, LiveParameterState>()

        // The primary table wins over a cached extension value for the same sensor.
        values.putAll(extensionValues)
        states.putAll(extensionStates)
        values.putAll(parsed.values)
        states.putAll(parsed.states)

        val rpm = values[LiveSensor.RPM]?.toInt() ?: 0
        val rpmOk = states[LiveSensor.RPM] == LiveParameterState.VALID
        val engineRunning = rpmOk && rpm > 0

        // Oxygen/trim values need a running engine. A value that arrived while the
        // engine was not proven spinning is reported by state, never as a zero.
        val effectiveStates = LiveParameterStates.withNotPublished(
            LiveParameterStates.withEngineRunning(
                states = states,
                engineRunning = engineRunning,
                engineRunningOnly = engineRunningOnlySensors
            )
        )
        val reported = LiveParameterStates.validSensors(effectiveStates)

        val tpsOk = LiveSensor.TPS_PERCENT in reported
        val tpsPercent = if (tpsOk) values[LiveSensor.TPS_PERCENT]?.toFloat() ?: 0.0f else 0.0f
        val tpsVoltage = if (LiveSensor.TPS_VOLTAGE in reported) {
            values[LiveSensor.TPS_VOLTAGE]?.toFloat() ?: 0.0f
        } else {
            0.0f
        }
        val tpsAdc = if (LiveSensor.TPS_ADC in reported) {
            values[LiveSensor.TPS_ADC]?.toInt() ?: 0
        } else {
            -1
        }
        val tempOk = LiveSensor.ECT in reported
        val tempC = values[LiveSensor.ECT]?.toInt() ?: 0
        val iatPlausible = LiveSensor.IAT in reported
        val iat = values[LiveSensor.IAT]?.toInt() ?: 0
        val mapOk = LiveSensor.MAP in reported
        val map = values[LiveSensor.MAP]?.toInt() ?: 0
        val battPlausible = LiveSensor.BATTERY in reported
        val batt = values[LiveSensor.BATTERY]?.toFloat() ?: 0.0f
        val advancePlausible = LiveSensor.IGNITION_ADVANCE in reported
        val advanceDeg = values[LiveSensor.IGNITION_ADVANCE]?.toFloat() ?: 0.0f
        val injectorOk = LiveSensor.INJECTOR in reported
        val injectorMs = values[LiveSensor.INJECTOR]?.toFloat() ?: 0.0f
        val speedOk = LiveSensor.SPEED in reported
        val speedKmh = values[LiveSensor.SPEED]?.toInt() ?: 0
        val o2Ok = LiveSensor.O2 in reported
        val o2Voltage = values[LiveSensor.O2]?.toFloat() ?: 0.0f
        val gear = gearLabel(reported, values)

        // A checksum-valid frame with at least one usable parameter is accepted;
        // each rejected field keeps its own non-VALID state instead of a zero.
        val dataValid = reported.isNotEmpty()

        if (parsed.rejected.isNotEmpty()) {
            log(
                LogDirection.ERR,
                hex(frame),
                "Plausibilitas sebagian gagal (${parsed.profileId}): RPM=$rpm ECT=$tempC IAT=$iat MAP=$map BATT=$batt ADV=$advanceDeg " +
                    "rejected=${parsed.rejected.entries.joinToString { "${it.key}=${it.value}" }}"
            )
        }

        val now = System.currentTimeMillis()
        return LiveTelemetry(
            timestamp = now,
            engineRpm = rpm,
            tpsPercent = tpsPercent,
            tpsVoltage = tpsVoltage,
            tpsAdc = tpsAdc,
            engineTempC = if (tempOk) tempC else 0,
            intakeAirTempC = if (iatPlausible) iat else 0,
            mapKpa = if (mapOk) map else 0,
            batteryVoltage = if (battPlausible) batt else 0.0f,
            o2Voltage = if (o2Ok) o2Voltage else 0.0f,
            injectorPulseMs = if (injectorOk) injectorMs else 0.0f,
            ignitionAdvanceDeg = if (advancePlausible) advanceDeg else 0.0f,
            vehicleSpeedKmh = if (speedOk) speedKmh else 0,
            gearPosition = gear,
            milActive = _activeDtcs.value.isNotEmpty(),
            tempSensorType = model?.tempSensorType ?: TempSensorType.ECT,
            isDataValid = dataValid,
            lastUpdatedMs = now,
            lastReadAttemptMs = now,
            isRpmAvailable = rpmOk,
            isTpsAvailable = tpsOk,
            isTempAvailable = tempOk,
            isIatAvailable = iatPlausible,
            isMapAvailable = mapOk,
            isBattAvailable = battPlausible,
            isInjectorAvailable = injectorOk,
            isIgnitionAvailable = advancePlausible,
            isSpeedAvailable = speedOk,
            isO2Available = o2Ok,
            activeTableHex = primary.tableId,
            parameterStates = effectiveStates
        )
    }

    /** Maps a request's table byte to a registered profile; null when none is registered. */
    private fun liveProfileFor(request: ByteArray): LiveTableProfile? {
        if (request.size < 4) return null
        return HondaLiveDataProfiles.forTable("%02X".format(request[3].toInt() and 0xFF))
    }

    /** Length, header and negative response are checked before any decoding. */
    private fun isUsableLiveFrame(frame: ByteArray, profile: LiveTableProfile): Boolean {
        if (frame.size < profile.minFrameSize) return false
        if (!KlinePacket.isValidHeader(frame[0])) return false
        return (frame[2].toInt() and 0xFF) != NEGATIVE_RESPONSE_SERVICE
    }

    /**
     * Payload is bytes 4..(size-2); the table echo must match the requested
     * table. A mismatch means the response layout is unknown, so it is not parsed.
     */
    private fun extractLivePayload(frame: ByteArray, profile: LiveTableProfile): ByteArray? {
        if (frame.size < profile.minFrameSize) {
            log(
                LogDirection.ERR,
                hex(frame),
                "Respons tabel 0x${profile.tableId} hanya ${frame.size} byte; minimum ${profile.minFrameSize}."
            )
            return null
        }
        if ((frame[2].toInt() and 0xFF) == NEGATIVE_RESPONSE_SERVICE) return null
        val echoedTable = "%02X".format(frame[3].toInt() and 0xFF)
        if (!echoedTable.equals(profile.tableId, ignoreCase = true)) {
            log(
                LogDirection.ERR,
                hex(frame),
                "Echo tabel 0x$echoedTable tidak cocok dengan permintaan 0x${profile.tableId}; frame tidak diparse."
            )
            return null
        }
        return frame.copyOfRange(profile.payloadStart, frame.size - 1)
    }

    /**
     * Polls one registered extension table per cycle in rotation. The primary
     * table is never re-queried here; a table that misses twice is skipped for 15 s.
     */
    private suspend fun refreshExtensionTables(
        d: UsbTransport,
        profile: HondaWireProfile,
        primaryTableId: String
    ) {
        val now = System.currentTimeMillis()
        if (now < nextExtensionPollMs) return
        val candidates = profile.liveExtensionTables.filter { request ->
            !request.shortTag.equals(primaryTableId, ignoreCase = true) &&
                (extensionTableSkipUntil[request.shortTag] ?: 0L) <= now
        }
        if (candidates.isEmpty()) return

        val request = candidates[extensionCursor % candidates.size]
        extensionCursor++
        nextExtensionPollMs = now + EXTENSION_POLL_INTERVAL_MS

        val extProfile = liveProfileFor(request.bytes) ?: return
        val reply = legacyQuery(d, request.bytes, request.timeoutMs, request.sleepMs, request.status, request.label)
        if (reply == null || !isUsableLiveFrame(reply, extProfile)) {
            val failures = (extensionTableFailures[request.shortTag] ?: 0) + 1
            extensionTableFailures[request.shortTag] = failures
            if (failures >= 2) {
                extensionTableSkipUntil[request.shortTag] = now + EXTENSION_SKIP_MS
                removeExtensionSensors(extProfile)
            }
            return
        }

        val payload = extractLivePayload(reply, extProfile) ?: return
        val parsed = LiveDataFrameParser.parse(payload, extProfile)
        // Replace this table's previous sensors instead of merging: a field that
        // failed this poll must not keep an old VALID state.
        val tableSensors = extProfile.fields.map { it.sensor }.toSet()
        extensionValues = extensionValues - tableSensors + parsed.values
        extensionAvailable = extensionAvailable - tableSensors + parsed.available
        extensionStates = extensionStates - tableSensors + parsed.states
        extensionTableFailures[request.shortTag] = 0
        extensionTableSkipUntil.remove(request.shortTag)
    }

    /** Withdraws values that came from an extension table which stopped answering. */
    private fun removeExtensionSensors(profile: LiveTableProfile) {
        val sensors = profile.fields.map { it.sensor }.toSet()
        extensionValues = extensionValues.filterKeys { it !in sensors }
        extensionAvailable = extensionAvailable - sensors
        extensionStates = extensionStates - sensors
    }

    /**
     * The gear-state byte only yields a neutral/on-gear indication. The raw gear
     * position byte is kept in the parsed map but not turned into a gear number
     * because the per-bit encoding was not recovered.
     */
    private fun gearLabel(available: Set<LiveSensor>, values: Map<LiveSensor, Double>): String {
        if (LiveSensor.GEAR_STATE !in available) return "-"
        return when (values[LiveSensor.GEAR_STATE]?.toInt()) {
            1 -> "N"
            0 -> "OnGear"
            else -> "-"
        }
    }

    private fun pidState(ok: Boolean): LiveParameterState =
        if (ok) LiveParameterState.VALID else LiveParameterState.NOT_RESPONDING

    private suspend fun readPidFallback(d: UsbTransport): LiveTelemetry? {
        val values = mutableMapOf<Int, Int>()
        var anyFrame = false

        for (pid in listOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0C)) {
            val outcome = legacyTransceive(d, buildFrame(0x21, pid), timeoutMs = 300)
            if (outcome is CommandOutcome.Frame) {
                val frame = outcome.bytes
                val echo = if (frame.size > 2) frame[2].toInt() and 0xFF else -1
                if (echo == 0x61 || echo == 0x21) {
                    CommunicationSniffer.recordParseFailure()
                    log(LogDirection.ERR, hex(frame), "PID 0x%02X: respons memakai echo service; data tidak diparse.".format(pid))
                    CommunicationSniffer.logEvent(
                        CommEventCategory.PROTOCOL,
                        CommDataStage.PARSED_RESPONSE,
                        rawBytes = frame,
                        status = "PARSE_FAILED",
                        description = "PID 0x%02X: respons memakai echo service; data tidak diparse.".format(pid)
                    )
                } else {
                    when (pid) {
                        0x01, 0x07 -> {
                            if (frame.size >= 5) {
                                values[pid] = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
                                anyFrame = true
                            } else {
                                CommunicationSniffer.recordParseFailure()
                            }
                        }
                        else -> {
                            if (frame.size >= 4) {
                                values[pid] = frame[2].toInt() and 0xFF
                                anyFrame = true
                            } else {
                                CommunicationSniffer.recordParseFailure()
                            }
                        }
                    }
                }
            }
            delay(15)
        }

        if (!anyFrame) {
            CommunicationSniffer.logEvent(
                CommEventCategory.PROTOCOL,
                CommDataStage.PARSED_RESPONSE,
                status = "LIVE_DATA_UNSUPPORTED",
                description = "Fallback PID 0x21 tidak menghasilkan nilai yang dapat diparse; ini bukan bukti sensor tersedia."
            )
            return null
        }
        markLinkAlive()

        val rpmRaw = values[0x01]
        val rpm = when {
            rpmRaw == null -> 0
            rpmRaw > 15000 -> rpmRaw * 50 / 6
            else -> rpmRaw
        }
        val rpmOk = rpmRaw != null && rpm in 0..15000
        val tpsOk = values.containsKey(0x02)
        val tpsPercent = if (tpsOk) (values.getValue(0x02) * 100.0f / 255.0f) else 0.0f
        val mapOk = values.containsKey(0x03)
        val iatRaw = values[0x04]
        val iat = if (iatRaw != null) iatRaw - 40 else 0
        val iatOk = iatRaw != null && iat in -40..100
        val ectRaw = values[0x05]
        val ect = if (ectRaw != null) ectRaw - 40 else 0
        val ectOk = ectRaw != null && ect in -40..150
        val battRaw = values[0x06]
        val batt = if (battRaw != null) battRaw * 0.0625f else 0.0f
        val battOk = batt in 5.0f..20.0f
        val injRaw = values[0x07]
        val injectorMs = if (injRaw != null) injRaw * 0.001f else 0.0f
        val injOk = injRaw != null && injectorMs in 0.0f..50.0f
        val ignRaw = values[0x08]
        val ignitionDeg = if (ignRaw != null) ignRaw - 64.0f else 0.0f
        val ignOk = ignRaw != null && ignitionDeg in -60.0f..70.0f
        val speedOk = values.containsKey(0x09)
        val speed = values[0x09] ?: 0
        val o2OkRaw = values.containsKey(0x0C)
        val o2Volts = values[0x0C]?.let { it * 4.882f / 1000.0f } ?: 0.0f
        val engineRunning = rpmOk && rpm > 0
        // The PID fallback cannot expose the raw ADC byte, so TPS_ADC stays
        // UNSUPPORTED here rather than reusing the percentage as a fake count.
        val o2Ok = o2OkRaw && engineRunning

        val states = buildMap {
            put(LiveSensor.RPM, pidState(rpmOk))
            put(LiveSensor.TPS_PERCENT, pidState(tpsOk))
            put(LiveSensor.TPS_VOLTAGE, LiveParameterState.UNSUPPORTED)
            put(LiveSensor.TPS_ADC, LiveParameterState.UNSUPPORTED)
            put(LiveSensor.MAP, pidState(mapOk))
            put(LiveSensor.IAT, pidState(iatOk))
            put(LiveSensor.ECT, pidState(ectOk))
            put(LiveSensor.BATTERY, pidState(battOk))
            put(LiveSensor.INJECTOR, pidState(injOk))
            put(LiveSensor.IGNITION_ADVANCE, pidState(ignOk))
            put(LiveSensor.SPEED, pidState(speedOk))
            put(LiveSensor.O2, when {
                !o2OkRaw -> LiveParameterState.NOT_RESPONDING
                !engineRunning -> LiveParameterState.NOT_AVAILABLE_IN_KOEO
                else -> LiveParameterState.VALID
            })
            // The fallback PID set carries O2 voltage only; AFR stays explicitly
            // unsupported instead of being derived on the host.
            put(LiveSensor.AFR, LiveParameterState.UNSUPPORTED)
        }
        val dataValid = states.containsValue(LiveParameterState.VALID)
        if (rpmOk) {
            CommunicationSniffer.setSessionStatus(
                CommSessionStatus.LIVE_DATA_VALID,
                "Fallback PID 0x21 parsed: RPM=$rpm"
            )
        }
        CommunicationSniffer.logEvent(
            CommEventCategory.PROTOCOL,
            CommDataStage.PARSED_RESPONSE,
            status = if (rpmOk) "LIVE_DATA_VALID" else "LIVE_DATA_PARTIAL",
            description = "Fallback PID parsed: RPM=${rpmOk} TPS=${tpsOk} MAP=${mapOk} IAT=${iatOk} ECT=${ectOk} BATT=${battOk} IGN=${ignOk}"
        )
        log(
            LogDirection.SYS,
            "",
            "Fallback PID: RPM=${rpmOk} TPS=${tpsOk} MAP=${mapOk} IAT=${iatOk} ECT=${ectOk} BATT=${battOk} IGN=${ignOk}"
        )

        val now = System.currentTimeMillis()
        return LiveTelemetry(
            timestamp = now,
            engineRpm = if (rpmOk) rpm else 0,
            tpsPercent = if (tpsOk) tpsPercent.coerceIn(0.0f, 100.0f) else 0.0f,
            tpsVoltage = 0.0f,
            tpsAdc = -1,
            engineTempC = if (ectOk) ect else 0,
            intakeAirTempC = if (iatOk) iat else 0,
            mapKpa = if (mapOk) values.getValue(0x03) else 0,
            batteryVoltage = if (battOk) batt else 0.0f,
            o2Voltage = if (o2Ok) o2Volts else 0.0f,
            injectorPulseMs = if (injOk) injectorMs else 0.0f,
            ignitionAdvanceDeg = if (ignOk) ignitionDeg else 0.0f,
            vehicleSpeedKmh = if (speedOk) speed else 0,
            gearPosition = "-",
            milActive = dtcReadSucceededWithCodes,
            tempSensorType = _connectedModel.value?.tempSensorType ?: TempSensorType.ECT,
            isDataValid = dataValid,
            lastUpdatedMs = now,
            lastReadAttemptMs = now,
            isRpmAvailable = rpmOk,
            isTpsAvailable = tpsOk,
            isTempAvailable = ectOk,
            isIatAvailable = iatOk,
            isMapAvailable = mapOk,
            isBattAvailable = battOk,
            isInjectorAvailable = injOk,
            isIgnitionAvailable = ignOk,
            isSpeedAvailable = speedOk,
            isO2Available = o2Ok,
            activeTableHex = "PID 0x21",
            parameterStates = states
        )
    }

    fun disconnect() {
        connectionGeneration++
        session.detach()
        try {
            driver?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing transport", e)
            CommunicationSniffer.recordUsbError()
        }
        driver = null
        _isAdapterOpen.value = false
        _isConnected.value = false
        _isKlineInitialized.value = false
        _isEcuIdentified.value = false
        _isEcuAlive.value = false
        _ignitionState.value = IgnitionState.UNKNOWN
        _connectedModel.value = null
        _ecuIdentity.value = null
        _identityResolution.value = null
        _identificationError.value = null
        _connectionStep.value = ConnectionPipelineStep.ADAPTER_DETECTED
        _activeDtcs.value = emptyList()
        _dtcReadState.value = DtcReadState.NOT_PERFORMED
        _dtcReadDetail.value = ""
        dtcReadSucceededWithCodes = false
        consecutiveLinkFailures = 0
        pidFallbackCooldownUntilMs = 0L
        cachedLiveTable = null
        liveTableFailures = 0
        nextExtensionPollMs = 0L
        extensionCursor = 0
        extensionTableFailures.clear()
        extensionTableSkipUntil.clear()
        extensionValues = emptyMap()
        extensionAvailable = emptySet()
        extensionStates = emptyMap()
        wire.reset()
        invalidateLiveTelemetry()
        _liveDataState.value = LiveDataState.UNAVAILABLE
        log(LogDirection.SYS, "", "Adapter diputus. Status ECU direset.")
        CommunicationSniffer.logEvent(
            CommEventCategory.SYSTEM,
            CommDataStage.STATE_CHANGE,
            status = "DISCONNECTED",
            description = "Adapter USB ditutup; status ECU direset. Riwayat log dan status tertinggi tetap disimpan."
        )
        // Timeline sengaja tidak di-reset agar bukti diagnostik terakhir tetap terlihat.
    }

    private companion object {
        const val NEGATIVE_RESPONSE_SERVICE = 0x7F
        const val EXTENSION_POLL_INTERVAL_MS = 400L
        const val EXTENSION_SKIP_MS = 15_000L
    }
}
