package com.revcore.android.core.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object CommunicationSniffer {
    private val idGen = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _events = MutableStateFlow<List<SnifferLogEvent>>(emptyList())
    val events: StateFlow<List<SnifferLogEvent>> = _events.asStateFlow()

    private val _stats = MutableStateFlow(SnifferStats())
    val stats: StateFlow<SnifferStats> = _stats.asStateFlow()

    private val _isCapturePaused = MutableStateFlow(false)
    val isCapturePaused: StateFlow<Boolean> = _isCapturePaused.asStateFlow()

    private val _timeline = MutableStateFlow<Map<DiagnosticStage, TimelineStageItem>>(initDefaultTimeline())
    val timeline: StateFlow<Map<DiagnosticStage, TimelineStageItem>> = _timeline.asStateFlow()

    private val _timelineStages = MutableStateFlow<List<TimelineStageItem>>(initDefaultTimeline().values.toList())
    val timelineStages: StateFlow<List<TimelineStageItem>> = _timelineStages.asStateFlow()

    private fun initDefaultTimeline(): Map<DiagnosticStage, TimelineStageItem> {
        return DiagnosticStage.entries.associateWith { stage ->
            TimelineStageItem(stage = stage, lastRelevantEvent = "Pending")
        }
    }

    fun setCapturePaused(paused: Boolean) {
        _isCapturePaused.value = paused
    }

    fun resetTimeline() {
        val def = initDefaultTimeline()
        _timeline.value = def
        _timelineStages.value = def.values.toList()
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        _stats.value = SnifferStats()
        resetTimeline()
    }

    fun updateTimelineStage(
        stage: DiagnosticStage,
        isSuccess: Boolean,
        lastRelevantEvent: String,
        errorMessage: String? = null
    ) {
        val current = _timeline.value.toMutableMap()
        val nowStr = timeFormat.format(Date())
        current[stage] = TimelineStageItem(
            stage = stage,
            isSuccess = isSuccess,
            isFailed = !isSuccess,
            isActive = false,
            timestamp = nowStr,
            lastRelevantEvent = lastRelevantEvent,
            errorMessage = errorMessage
        )
        _timeline.value = current
        _timelineStages.value = current.values.toList()
    }

    @Synchronized
    fun logEvent(
        category: CommEventCategory,
        stage: CommDataStage,
        rawBytes: ByteArray? = null,
        status: String = "OK",
        description: String = "",
        errorDetails: String? = null,
        durationMs: Long? = null
    ): SnifferLogEvent {
        val nowMs = System.currentTimeMillis()
        val timeStr = timeFormat.format(Date(nowMs))
        val hexStr = rawBytes?.joinToString(" ") { String.format("%02X", it) } ?: ""
        val asciiStr = rawBytes?.map { b ->
            val c = (b.toInt() and 0xFF).toChar()
            if (c in ' '..'~') c else '.'
        }?.joinToString("") ?: ""
        val byteCount = rawBytes?.size ?: 0

        val event = SnifferLogEvent(
            id = idGen.getAndIncrement(),
            timestamp = timeStr,
            timeEpochMs = nowMs,
            category = category,
            stage = stage,
            rawBytes = rawBytes,
            hexString = hexStr,
            asciiString = asciiStr,
            byteCount = byteCount,
            status = status,
            description = description,
            errorDetails = errorDetails,
            durationMs = durationMs
        )

        if (_isCapturePaused.value) {
            return event
        }

        val currentList = _events.value
        val newList = if (currentList.size >= 1000) {
            currentList.takeLast(999) + event
        } else {
            currentList + event
        }
        _events.value = newList

        val currentStats = _stats.value
        // Byte totals are counted once on the canonical K-Line events. USB-level
        // mirror events (read/write chunk traces) must not double-count them.
        val isTx = category == CommEventCategory.TX && stage == CommDataStage.RAW_USB_OUT
        val isRx = category == CommEventCategory.RX && stage == CommDataStage.RAW_USB_IN
        val normalizedStatus = status.uppercase(Locale.ROOT)
        val isErr = category == CommEventCategory.ERROR ||
            errorDetails != null ||
            normalizedStatus.contains("FAIL") ||
            normalizedStatus.contains("ERROR") ||
            normalizedStatus.contains("TIMEOUT") ||
            normalizedStatus.contains("REJECT") ||
            normalizedStatus == "LOCAL_ECHO_ONLY"

        _stats.value = currentStats.copy(
            totalEvents = currentStats.totalEvents + 1,
            totalTxBytes = currentStats.totalTxBytes + (if (isTx) byteCount else 0),
            totalRxBytes = currentStats.totalRxBytes + (if (isRx) byteCount else 0),
            errorCount = currentStats.errorCount + (if (isErr) 1 else 0)
        )

        return event
    }

    /**
     * Advances the highest observed evidence status. A lower observation is kept as
     * the last status but never downgrades the highest evidence level.
     */
    @Synchronized
    fun setSessionStatus(status: CommSessionStatus, description: String = "") {
        val current = _stats.value
        val highest = if (status.rank > current.highestSessionStatus.rank) {
            status
        } else {
            current.highestSessionStatus
        }
        _stats.value = current.copy(
            highestSessionStatus = highest,
            lastSessionStatus = status
        )
        if (description.isNotBlank()) {
            logEvent(
                category = CommEventCategory.SYSTEM,
                stage = CommDataStage.STATE_CHANGE,
                status = status.name,
                description = description
            )
        }
    }

    @Synchronized
    fun recordTxFrame() {
        updateStats { it.copy(txFrames = it.txFrames + 1) }
    }

    @Synchronized
    fun recordValidFrame() {
        updateStats { it.copy(rxFramesValid = it.rxFramesValid + 1) }
    }

    @Synchronized
    fun recordInvalidFrame() {
        updateStats { it.copy(rxFramesInvalid = it.rxFramesInvalid + 1) }
    }

    @Synchronized
    fun recordLocalEcho() {
        updateStats { it.copy(echoCount = it.echoCount + 1) }
    }

    @Synchronized
    fun recordTimeout() {
        updateStats { it.copy(timeoutCount = it.timeoutCount + 1) }
    }

    @Synchronized
    fun recordRetry() {
        updateStats { it.copy(retryCount = it.retryCount + 1) }
    }

    @Synchronized
    fun recordUsbError() {
        updateStats { it.copy(usbErrorCount = it.usbErrorCount + 1) }
    }

    @Synchronized
    fun recordParseFailure() {
        updateStats { it.copy(parseFailureCount = it.parseFailureCount + 1) }
    }

    @Synchronized
    fun recordInitAttempt(strategy: String) {
        updateStats {
            it.copy(
                initAttempts = it.initAttempts + 1,
                initStrategies = it.initStrategies + strategy
            )
        }
    }

    private fun updateStats(block: (SnifferStats) -> SnifferStats) {
        _stats.value = block(_stats.value)
    }

    @Synchronized
    fun clearLogs() {
        clear()
    }

    fun getLastFailure(): String? {
        val failed = _timeline.value.values.lastOrNull { it.isFailed } ?: return null
        return "${failed.stage}: ${failed.errorMessage ?: failed.lastRelevantEvent}"
    }

    fun exportAsText(ecuName: String = "ECU_UNKNOWN"): String {
        val sb = StringBuilder()
        sb.append("REVCORE_LOG_${ecuName}_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}\n")
        sb.append("REVCORE MOTORCYCLE ECU DIAGNOSTIC - PROTOCOL COMMUNICATION LOG\n")
        sb.append("ECU Target  : ").append(ecuName).append("\n")
        sb.append("Exported At : ").append(fullDateFormat.format(Date())).append("\n")
        val s = _stats.value
        sb.append("Statistics  : Total Events=").append(s.totalEvents)
            .append(" | TX=").append(s.totalTxBytes)
            .append(" B | RX=").append(s.totalRxBytes)
            .append(" B | Errors=").append(s.errorCount).append("\n")
        sb.append("Port Config : 10400/9600 8N1 (Virtual COM Port / PORTS COM & LPT mode, DTR=1, RTS=1, Latency=2ms)\n")
        sb.append("=========================================================================================================\n")
        sb.append("SESSION SUMMARY (evidence based, no status is inferred):\n")
        sb.append("  Highest evidence status : ").append(s.highestSessionStatus.name).append("\n")
        sb.append("  Last observed status    : ").append(s.lastSessionStatus.name).append("\n")
        sb.append("  TX frames               : ").append(s.txFrames).append("\n")
        sb.append("  RX frames valid         : ").append(s.rxFramesValid).append("\n")
        sb.append("  RX frames invalid       : ").append(s.rxFramesInvalid).append("\n")
        sb.append("  Local echo detected     : ").append(s.echoCount).append("\n")
        sb.append("  Timeouts                : ").append(s.timeoutCount).append("\n")
        sb.append("  Retries                 : ").append(s.retryCount).append("\n")
        sb.append("  USB errors              : ").append(s.usbErrorCount).append("\n")
        sb.append("  Parse failures          : ").append(s.parseFailureCount).append("\n")
        sb.append("  Init attempts           : ").append(s.initAttempts).append("\n")
        sb.append("  Init strategies tried   : ").append(
            if (s.initStrategies.isEmpty()) "-" else s.initStrategies.joinToString("; ")
        ).append("\n")
        sb.append("  Final connection status : ").append(s.highestSessionStatus.name).append("\n")
        sb.append("---------------------------------------------------------------------------------------------------------\n")
        sb.append("DIAGNOSTIC PIPELINE TIMELINE STAGES:\n")
        for ((stage, item) in _timeline.value) {
            val status = when {
                item.isSuccess -> "[PASSED]"
                item.isFailed -> "[FAILED]"
                else -> "[PENDING]"
            }
            val err = if (item.errorMessage != null) " (Error: ${item.errorMessage})" else ""
            sb.append(String.format("  %-8s %-28s : %s%s\n", status, stage.name, item.lastRelevantEvent, err))
        }
        sb.append("---------------------------------------------------------------------------------------------------------\n")
        sb.append("DLC & IGNITION TROUBLESHOOTING CHECKLIST:\n")
        sb.append("  1. Honda 4-pin DLC Connector: Pin 1 (+12V DC Battery), Pin 2 (GND Ground), Pin 3 (K-Line Data Serial).\n")
        sb.append("  2. Kunci Kontak (Ignition Switch) WAJIB dalam posisi ON agar ECM/ECU dialiri daya listrik.\n")
        sb.append("  3. Adapter USB FTDI FT232R / CH340 / CP2102 harus mendukung half-duplex K-Line transceiver (L9637D/MC33290).\n")
        sb.append("  4. Driver USB Serial Android disetel ke mode Virtual COM Port (PORTS COM & LPT) dengan buffer purge aktif.\n")
        sb.append("---------------------------------------------------------------------------------------------------------\n")
        sb.append(String.format("%-12s | %-8s | %-16s | %-4s | %-32s | %s\n", "TIME", "CATEGORY", "STAGE", "LEN", "HEX", "DESCRIPTION"))
        sb.append("---------------------------------------------------------------------------------------------------------\n")
        for (e in _events.value) {
            val hexTruncated = if (e.hexString.length > 32) e.hexString.take(29) + "..." else e.hexString
            val err = if (e.errorDetails != null) " [ERR: ${e.errorDetails}]" else ""
            sb.append(String.format(
                "%-12s | %-8s | %-16s | %-4d | %-32s | %s%s\n",
                e.timestamp, e.category.name, e.stage.name, e.byteCount, hexTruncated, e.description, err
            ))
        }
        sb.append("=========================================================================================================\n")
        return sb.toString()
    }

    fun exportAsCsv(): String {
        val sb = StringBuilder()
        sb.append("ID,Timestamp,EpochMs,Category,Stage,ByteCount,HexString,AsciiString,Status,DurationMs,Description,ErrorDetails\n")
        for (e in _events.value) {
            val desc = e.description.replace("\"", "\"\"")
            val err = (e.errorDetails ?: "").replace("\"", "\"\"")
            sb.append("${e.id},\"${e.timestamp}\",${e.timeEpochMs},${e.category.name},${e.stage.name},${e.byteCount},\"${e.hexString}\",\"${e.asciiString}\",\"${e.status}\",${e.durationMs ?: ""},\"$desc\",\"$err\"\n")
        }
        return sb.toString()
    }
}
