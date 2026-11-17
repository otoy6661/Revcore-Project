package com.revcore.android.core.protocol.legacy

import com.revcore.android.core.protocol.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.revcore.android.core.ftdi.FtdiConstants
import com.revcore.android.core.ftdi.FtdiSerialDriver
import com.revcore.android.core.usb.UsbSerialLibraryTransport
import com.revcore.android.core.usb.UsbTransport
import com.revcore.android.data.ecu.EcmDatabase
import com.revcore.android.data.ecu.EcmModel
import com.revcore.android.data.ecu.EcuIdCanonicalization
import com.revcore.android.data.ecu.EcuIdCanonicalizer
import com.revcore.android.data.ecu.IdResponseLayout
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

/**
 * Rollback copy of the pre-replacement communication engine. It is kept
 * intentionally: switching [com.revcore.android.core.protocol.HondaProtocolEngine]
 * back to this implementation restores the exact restored-baseline behaviour.
 */
class LegacyHondaProtocolEngine(private val usbManager: UsbManager) {
    private val tag = "LegacyHondaProtocolEngine"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val commLock = Mutex()

    private val link = KlineTransceiver(
        logger = { direction, hexText, description -> log(direction, hexText, description) }
    )

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

    private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
    val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()
    val telemetry: StateFlow<LiveTelemetry> get() = liveTelemetry

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

    private fun log(direction: LogDirection, hex: String, description: String) {
        val timeStr = timeFormat.format(Date())
        val entry = CommLogEntry(timeStr, direction, hex, description)
        val current = _commLogs.value
        _commLogs.value = if (current.size >= 500) current.takeLast(499) + entry else current + entry
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format("%02X", it) }

    private fun buildFrame(service: Int, vararg params: Int): ByteArray {
        val frameLen = 4 + params.size
        require(frameLen <= 0xFF) { "K-Line frame too long" }
        val frame = ByteArray(frameLen)
        frame[0] = 0x72
        frame[1] = frameLen.toByte()
        frame[2] = service.toByte()
        params.forEachIndexed { index, value -> frame[3 + index] = value.toByte() }
        frame[frameLen - 1] = HondaChecksum.calculatePacketChecksum(frame, 0, frameLen - 1)
        return frame
    }

    private fun buildRawFrame(body: ByteArray): ByteArray {
        val frame = ByteArray(body.size + 1)
        System.arraycopy(body, 0, frame, 0, body.size)
        frame[frame.size - 1] = HondaChecksum.calculatePacketChecksum(frame, 0, body.size)
        return frame
    }

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
        _isAdapterOpen.value = true
        _isConnected.value = true
        _isKlineInitialized.value = false
        _isEcuIdentified.value = false
        _isEcuAlive.value = false
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
            "Wake pulse baseline: BREAK LOW 70 ms / HIGH 130 ms (force=$forceBreakPulse)"
        )

        d.setBaudRate(FtdiConstants.BAUD_KLINE_DEFAULT)
        d.purgeRxTx()
        link.reset()

        val lowStart = System.currentTimeMillis()
        if (!d.setBreak(true)) {
            CommunicationSniffer.recordUsbError()
            log(LogDirection.ERR, "", "Adapter menolak kondisi BREAK; pulsa wake tidak dapat dikirim.")
        }
        delay(WAKE_LOW_MS)
        val lowMeasured = System.currentTimeMillis() - lowStart
        d.setBreak(false)
        val highStart = System.currentTimeMillis()
        delay(WAKE_HIGH_MS)
        val highMeasured = System.currentTimeMillis() - highStart
        d.purgeRxTx()

        log(
            LogDirection.SYS,
            "",
            "Wake pulse host-side: BREAK LOW diminta ${WAKE_LOW_MS} ms (~${lowMeasured} ms), " +
                "HIGH diminta ${WAKE_HIGH_MS} ms (~${highMeasured} ms); ini waktu host, bukan hasil pengukuran bus."
        )
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
    ): CommandOutcome = link.transceive(
        d,
        command,
        timeoutMs,
        retries = 1,
        interByteMs = 0,
        echoWindowMs = 0,
        quietMs = 0,
        postWriteDelayMs = sleepMs
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
            isO2Available = false
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

            onPhase?.invoke(ProtocolPhase.FAST_INIT)
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
                val model = publishIdentifiedModel(result.identifier, result.source)
                if (!isCurrent(generation)) return@withLock null
                onPhase?.invoke(ProtocolPhase.IDENTIFIED)
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
                    val identifier = canonicalizeReply("0x1A", idReply)?.canonicalId
                        ?: HondaIdentifierParser.extract(idReply)
                    if (identifier != null) {
                        val model = publishIdentifiedModel(identifier, "0x1A")
                        if (!isCurrent(generation)) return@withLock null
                        onPhase?.invoke(ProtocolPhase.IDENTIFIED)
                        return@withLock model
                    }
                    CommunicationSniffer.recordParseFailure()
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
        val identifier: String?,
        val validFrame: ByteArray?,
        val source: String
    ) {
        val anyValidFrame: Boolean get() = alive || validFrame != null
    }

    /**
     * Same canonical extraction as the active engine. The captured 0x72 response is
     * `72 0B 72 00 00 01 03 7A 0F 02`, i.e. two selector bytes followed by the
     * complete five-byte ECM ID.
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

    private fun canonicalizeReply(requestId: String, reply: ByteArray): EcuIdCanonicalization? {
        val parsed = HondaIdentifierParser.parse(reply) ?: return null
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            payload = parsed.payload,
            layout = responseLayoutFor(requestId),
            knownCanonicalIds = EcmDatabase.knownEcmIds()
        )
        log(
            LogDirection.SYS,
            "",
            "ID extract query=$requestId raw=${extraction.rawId ?: "-"} " +
                "canonical=${extraction.canonicalId ?: "-"} method=${extraction.method}"
        )
        return extraction
    }

    private suspend fun baselineIdentifyAtBaud(d: UsbTransport, baudRate: Int): BaselineIdResult {
        d.setBaudRate(baudRate)
        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.PROTOCOL_INIT_COMPLETED,
            true,
            "Query identifikasi baseline Honda pada $baudRate bps"
        )

        var alive = false
        var validFrame: ByteArray? = null

        val tester = legacyQuery(
            d,
            TESTER_PRESENT_REQUEST,
            600,
            10,
            "TESTER_PRESENT",
            "Tester present baseline FE 04 72 8C @$baudRate bps"
        )
        if (tester != null) {
            alive = true
            validFrame = tester
            markLinkAlive()
        }

        val id00F0 = legacyQuery(
            d,
            ID_REQUEST_TABLE_00F0,
            800,
            10,
            "ID_QUERY",
            "Query ID baseline 72 05 00 F0 99 @$baudRate bps"
        )
        if (id00F0 != null) {
            alive = true
            validFrame = id00F0
            markLinkAlive()
            val identifier = if (isNegativeResponse(id00F0)) null else canonicalizeReply("id.00F0", id00F0)?.canonicalId
            if (identifier != null) return BaselineIdResult(true, identifier, id00F0, "0x00/F0")
        }

        val id7200 = legacyQuery(
            d,
            ID_REQUEST_72_00,
            800,
            10,
            "ID_QUERY",
            "Query ID baseline 72 07 72 00 00 05 10 @$baudRate bps"
        )
        if (id7200 != null) {
            alive = true
            validFrame = id7200
            markLinkAlive()
            val identifier = if (isNegativeResponse(id7200)) null else canonicalizeReply("id.7200", id7200)?.canonicalId
            if (identifier != null) return BaselineIdResult(true, identifier, id7200, "0x72/0x00")
        }

        val id71 = legacyQuery(
            d,
            ID_REQUEST_PART_NUMBER,
            800,
            10,
            "ID_QUERY",
            "Query ID baseline 72 05 71 00 18 @$baudRate bps"
        )
        if (id71 != null) {
            alive = true
            validFrame = id71
            markLinkAlive()
            val identifier = if (isNegativeResponse(id71)) null else canonicalizeReply("id.7100", id71)?.canonicalId
            if (identifier != null) return BaselineIdResult(true, identifier, id71, "0x71/0x00")
        }

        return BaselineIdResult(alive, null, validFrame, "")
    }

    private fun isNegativeResponse(reply: ByteArray): Boolean =
        reply.size > 2 && (reply[2].toInt() and 0xFF) == NEGATIVE_RESPONSE_SERVICE

    private fun publishIdentifiedModel(identifier: String, source: String): EcmModel {
        val exact = EcmDatabase.findExactModel(identifier)
        val model = exact ?: EcmDatabase.createUnverifiedModel(identifier)
        _connectedModel.value = model
        _isEcuIdentified.value = exact != null
        _connectionStep.value = if (exact != null) {
            ConnectionPipelineStep.DIAGNOSTIC_READY
        } else {
            ConnectionPipelineStep.PROTOCOL_INITIALIZED
        }
        CommunicationSniffer.setSessionStatus(
            CommSessionStatus.ECU_ID_VALID,
            "ID ECU valid dari query $source: $identifier"
        )
        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.ECU_IDENTIFIED,
            exact != null,
            when {
                exact == null -> "ID ECM $identifier belum terdaftar; model ditandai belum terverifikasi"
                exact.isVerifiedDiagnostic || exact.isVerifiedRemap ->
                    "ID cocok database: ${model.bikeName} [${model.partNumber}]"
                else -> "ID cocok entri database ${model.bikeName}; kompatibilitas belum terverifikasi"
            },
            when {
                exact == null -> "ID tidak cocok persis dengan database RevCore."
                exact.isVerifiedDiagnostic || exact.isVerifiedRemap -> null
                else -> "Entri database ini belum diverifikasi dengan pengujian hardware."
            }
        )
        log(
            LogDirection.SYS,
            "",
            when {
                exact == null -> "ECU merespons ID $identifier ($source); belum terverifikasi di database."
                exact.isVerifiedDiagnostic || exact.isVerifiedRemap ->
                    "ID ECU cocok database: ${model.bikeName} (${model.partNumber}) [$source]"
                else -> "ID ECU cocok entri database ${model.bikeName}; kompatibilitas belum terverifikasi. [$source]"
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

        var lastReason = "Tidak ada respons query DTC baseline"
        for ((request, label) in DTC_READ_REQUESTS) {
            val reply = legacyQuery(
                d,
                request,
                700,
                0,
                "DTC_QUERY",
                "Query DTC baseline $label"
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

            log(LogDirection.SYS, "", "Mengirim perintah Clear DTC baseline (service 0x60).")
            var ackFrame: ByteArray? = null
            var ackLabel = ""
            for ((request, label) in CLEAR_DTC_REQUESTS) {
                val reply = legacyQuery(
                    d,
                    request,
                    800,
                    0,
                    "CLEAR_DTC_QUERY",
                    "Clear DTC baseline $label (read-only verification follows; no calibration write)"
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
                if (header !in CLEAR_ACK_HEADERS) {
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

    /**
     * The legacy engine no longer transmits live-data frames. Its baseline
     * table catalogue was withheld because the usage rights of the request
     * frames and scaling constants are unresolved. Use the core engine with a
     * [com.revcore.android.core.comm.LiveTableProfile] that you own instead.
     */
    suspend fun queryLiveData(): LiveTelemetry? = withContext(Dispatchers.IO) {
        commLock.withLock {
            log(
                LogDirection.ERR,
                "",
                "Legacy engine: katalog live data baseline tidak dipublikasikan; tidak ada query yang dikirim."
            )
            invalidateLiveTelemetry()
            null
        }
    }

    /**
     * Baseline live-data layout for a 0x71/0xC1/0x72 table response:
     * [RPM hi, RPM lo, TPS, ECT, IAT, MAP, battery, ignition] followed by the
     * checksum. Fields the table does not carry stay marked unavailable instead
     * of being filled with estimated values.
     */
    private fun parseLiveFrame(frame: ByteArray, model: EcmModel?, usedTable: String): LiveTelemetry? {
        if (!KlinePacket.isValidHeader(frame[0])) return null
        if (frame.size < 8) return null
        val service = frame[2].toInt() and 0xFF
        if (service == NEGATIVE_RESPONSE_SERVICE) {
            log(LogDirection.ERR, hex(frame), "Frame live data adalah respons negatif service 0x7F; data tidak diparse.")
            return null
        }

        val dataStart = if (frame.size >= 10 && service in LIVE_DATA_SERVICES) 4 else 3
        val dataEnd = (frame.size - 1).coerceAtLeast(dataStart)
        if (dataEnd - dataStart < 4) return null
        val p = frame.copyOfRange(dataStart, dataEnd)

        fun u8(index: Int): Int = p[index].toInt() and 0xFF

        val rawRpm = (u8(0) shl 8) or u8(1)
        val rpmOk = rawRpm in 0..18000
        val rpm = if (rpmOk) rawRpm else 0

        val tpsRaw = u8(2)
        val tpsPercent = (tpsRaw * 100.0f / 255.0f).coerceIn(0.0f, 100.0f)
        val tpsVoltage = (tpsRaw * 5.0f / 255.0f).coerceIn(0.0f, 5.0f)

        val tempC = u8(3) - 40
        val tempOk = tempC in -40..150

        val iatOk = p.size > 4
        val iat = if (iatOk) u8(4) - 40 else 0
        val iatPlausible = iatOk && iat in -40..100

        val mapOk = p.size > 5
        val map = if (mapOk) u8(5) else 0

        val battOk = p.size > 6
        val batt = if (battOk) u8(6) * 0.1f else 0.0f
        val battPlausible = battOk && batt in 5.0f..20.0f

        val advanceOk = p.size > 7
        val advanceDeg = if (advanceOk) u8(7) * 0.5f else 0.0f
        val advancePlausible = advanceOk && advanceDeg in -60.0f..70.0f

        // A checksum-valid frame with a plausible RPM is usable; an individual
        // implausible field stays hidden through its own availability flag.
        val dataValid = rpmOk

        if (!dataValid || !tempOk || !iatPlausible || !battPlausible || !advancePlausible) {
            log(
                LogDirection.ERR,
                hex(frame),
                "Plausibilitas sebagian gagal: RPM=$rpm ECT=$tempC IAT=$iat MAP=$map BATT=$batt ADV=$advanceDeg"
            )
        }

        val now = System.currentTimeMillis()
        return LiveTelemetry(
            timestamp = now,
            engineRpm = if (rpmOk) rpm else 0,
            tpsPercent = tpsPercent,
            tpsVoltage = tpsVoltage,
            engineTempC = if (tempOk) tempC else 0,
            intakeAirTempC = if (iatPlausible) iat else 0,
            mapKpa = if (mapOk) map else 0,
            batteryVoltage = if (battPlausible) batt else 0.0f,
            o2Voltage = 0.0f,
            injectorPulseMs = 0.0f,
            ignitionAdvanceDeg = if (advancePlausible) advanceDeg else 0.0f,
            vehicleSpeedKmh = 0,
            gearPosition = "-",
            milActive = _activeDtcs.value.isNotEmpty(),
            tempSensorType = model?.tempSensorType ?: TempSensorType.ECT,
            isDataValid = dataValid,
            lastUpdatedMs = now,
            lastReadAttemptMs = now,
            isRpmAvailable = rpmOk,
            isTpsAvailable = true,
            isTempAvailable = tempOk,
            isIatAvailable = iatPlausible,
            isMapAvailable = mapOk,
            isBattAvailable = battPlausible,
            isInjectorAvailable = false,
            isIgnitionAvailable = advancePlausible,
            isSpeedAvailable = false,
            isO2Available = false,
            activeTableHex = usedTable
        )
    }

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
        val o2Ok = values.containsKey(0x0C)
        val o2Volts = values[0x0C]?.let { it * 4.882f / 1000.0f } ?: 0.0f

        val dataValid = anyFrame
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
            engineTempC = if (ectOk) ect else 0,
            intakeAirTempC = if (iatOk) iat else 0,
            mapKpa = if (mapOk) values.getValue(0x03) else 0,
            batteryVoltage = if (battOk) batt else 0.0f,
            o2Voltage = o2Volts,
            injectorPulseMs = if (injOk) injectorMs else 0.0f,
            ignitionAdvanceDeg = if (ignOk) ignitionDeg else 0.0f,
            vehicleSpeedKmh = speed,
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
            activeTableHex = "PID 0x21"
        )
    }

    fun disconnect() {
        connectionGeneration++
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
        _connectionStep.value = ConnectionPipelineStep.ADAPTER_DETECTED
        _activeDtcs.value = emptyList()
        _dtcReadState.value = DtcReadState.NOT_PERFORMED
        _dtcReadDetail.value = ""
        dtcReadSucceededWithCodes = false
        consecutiveLinkFailures = 0
        pidFallbackCooldownUntilMs = 0L
        link.reset()
        invalidateLiveTelemetry()
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
        const val WAKE_LOW_MS = 70L
        const val WAKE_HIGH_MS = 130L
        const val NEGATIVE_RESPONSE_SERVICE = 0x7F

        val TESTER_PRESENT_REQUEST = byteArrayOf(0xFE.toByte(), 0x04, 0x72, 0x8C.toByte())
        val ID_REQUEST_TABLE_00F0 = byteArrayOf(0x72, 0x05, 0x00, 0xF0.toByte(), 0x99.toByte())
        val ID_REQUEST_72_00 = byteArrayOf(0x72, 0x07, 0x72, 0x00, 0x00, 0x05, 0x10)
        val ID_REQUEST_PART_NUMBER = byteArrayOf(0x72, 0x05, 0x71, 0x00, 0x18)

        val DTC_READ_REQUESTS = listOf(
            byteArrayOf(0x72, 0x05, 0x74, 0x01, 0x14) to "74/01",
            byteArrayOf(0x72, 0x05, 0x71, 0x20, 0xF8.toByte()) to "71/20",
            byteArrayOf(0x72, 0x05, 0x00, 0x00, 0x89.toByte()) to "00/00"
        )

        val CLEAR_DTC_REQUESTS = listOf(
            byteArrayOf(0x72, 0x05, 0x60, 0x01, 0x28) to "60/01",
            byteArrayOf(0x72, 0x05, 0x74, 0x02, 0x13) to "74/02",
            byteArrayOf(0x72, 0x04, 0x04, 0x86.toByte()) to "04"
        )

        val LIVE_DATA_SERVICES = setOf(0x71, 0xC1, 0x72)
        val CLEAR_ACK_HEADERS = setOf(0x02, 0x72, 0x7E, 0x7C)
    }
}
