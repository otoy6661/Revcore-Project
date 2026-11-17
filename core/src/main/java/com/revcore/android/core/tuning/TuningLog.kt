package com.revcore.android.core.tuning

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Categories are limited to tuning work. Live data, DTC and USB transport traffic have
 * their own log (CommunicationSniffer) and must not be mixed into this session log.
 */
enum class TuningLogCategory {
    SESSION,
    FILE,
    ECU_IDENTIFICATION,
    COMPATIBILITY,
    MAP_ANALYSIS,
    MAP_MODIFICATION,
    LIMITER,
    CHECKSUM,
    VALIDATION,
    BACKUP,
    ECU_WRITE,
    VERIFICATION,
    EXPORT_SAVE
}

enum class TuningLogStatus {
    INFO,
    RUNNING,
    SUCCESS,
    WARNING,
    ERROR,
    NOT_VERIFIED,
    COMPLETED,
    FAILED,
    INTERRUPTED;

    val isTerminal: Boolean
        get() = when (this) {
            INFO, RUNNING -> false
            else -> true
        }
}

data class TuningLogEvent(
    val id: Long,
    val timestamp: String,
    val epochMs: Long,
    val sessionId: String,
    val operationId: String,
    val category: TuningLogCategory,
    val stage: String,
    val status: TuningLogStatus,
    val message: String,
    val detail: String? = null,
    val errorCause: String? = null,
    val nextAction: String? = null
)

/**
 * Progress is only populated with values that were actually measured.
 * A null percentage means "not available", never zero-fill and never a timer estimate.
 */
data class TuningProgress(
    val operationId: String,
    val operationName: String,
    val stage: String,
    val overallPercent: Int? = null,
    val stagePercent: Int? = null,
    val completedUnits: Long? = null,
    val totalUnits: Long? = null,
    val unitLabel: String = "bytes",
    val status: TuningLogStatus = TuningLogStatus.RUNNING,
    val detail: String = "",
    val isTerminal: Boolean = false
) {
    val hasReliableOverallPercent: Boolean get() = overallPercent != null
}

object TuningProgressMath {
    /**
     * Progress from real completed units. Returns null when the total is unknown so the
     * UI can show "Progress unavailable" instead of a fabricated percentage.
     * A completed count at or above the total is reported as exactly 100.
     */
    fun percent(completed: Long, total: Long): Int? {
        if (total <= 0L) return null
        if (completed < 0L) return null
        if (completed >= total) return 100
        return (completed.toDouble() / total.toDouble() * 100.0).toInt().coerceIn(0, 99)
    }
}

/**
 * Process-wide tuning session log. It survives navigation because the store is a
 * singleton, and the "clear" action only moves the visible cutoff: the full session
 * history stays in memory and can still be exported.
 */
object TuningLogStore {
    private const val MAX_EVENTS = 2000
    private const val MAX_TRACKED_OPERATIONS = 50

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val eventIdGen = AtomicLong(1L)
    private val operationIdGen = AtomicLong(1L)

    private var sessionId: String = "TS-" + sessionFormat.format(Date())
    private var sessionStarted = false

    private val _events = MutableStateFlow<List<TuningLogEvent>>(emptyList())
    val events: StateFlow<List<TuningLogEvent>> = _events.asStateFlow()

    private val _visibleEvents = MutableStateFlow<List<TuningLogEvent>>(emptyList())
    val visibleEvents: StateFlow<List<TuningLogEvent>> = _visibleEvents.asStateFlow()

    private val _displayPaused = MutableStateFlow(false)
    val displayPaused: StateFlow<Boolean> = _displayPaused.asStateFlow()

    private val _latestProgress = MutableStateFlow<TuningProgress?>(null)
    val latestProgress: StateFlow<TuningProgress?> = _latestProgress.asStateFlow()

    private val terminalOperations = mutableSetOf<String>()
    private val progressByOperation = LinkedHashMap<String, TuningProgress>()
    private val operationCategories = mutableMapOf<String, TuningLogCategory>()

    /** When paused, only events up to this id stay visible. Work keeps running. */
    private var pausedUntilEventId = 0L
    /** Events at or below this id are hidden by the user's clear action but are retained. */
    private var visibleStartId = 0L

    @Synchronized
    fun getSessionId(): String = sessionId

    @Synchronized
    fun startSessionIfNeeded(message: String) {
        if (sessionStarted) return
        sessionStarted = true
        appendEvent(
            category = TuningLogCategory.SESSION,
            stage = "Session start",
            status = TuningLogStatus.INFO,
            message = message,
            operationId = ""
        )
    }

    @Synchronized
    fun beginOperation(
        operationName: String,
        category: TuningLogCategory,
        stage: String,
        message: String
    ): String {
        val operationId = "OP-" + operationIdGen.getAndIncrement().toString().padStart(3, '0')
        val progress = TuningProgress(
            operationId = operationId,
            operationName = operationName,
            stage = stage,
            status = TuningLogStatus.RUNNING
        )
        progressByOperation[operationId] = progress
        operationCategories[operationId] = category
        pruneOperationsLocked(operationId)
        _latestProgress.value = progress
        appendEvent(
            category = category,
            stage = stage,
            status = TuningLogStatus.RUNNING,
            message = message,
            operationId = operationId
        )
        return operationId
    }

    @Synchronized
    fun updateProgress(
        operationId: String,
        stage: String,
        overallPercent: Int? = null,
        stagePercent: Int? = null,
        completedUnits: Long? = null,
        totalUnits: Long? = null,
        unitLabel: String = "bytes",
        detail: String = ""
    ) {
        val current = progressByOperation[operationId] ?: return
        if (current.isTerminal) return
        val updated = current.copy(
            stage = stage,
            overallPercent = overallPercent,
            stagePercent = stagePercent,
            completedUnits = completedUnits,
            totalUnits = totalUnits,
            unitLabel = unitLabel,
            detail = detail
        )
        progressByOperation[operationId] = updated
        if (_latestProgress.value?.operationId == operationId) {
            _latestProgress.value = updated
        }
    }

    @Synchronized
    fun log(
        category: TuningLogCategory,
        stage: String,
        status: TuningLogStatus,
        message: String,
        operationId: String = "",
        detail: String? = null,
        errorCause: String? = null,
        nextAction: String? = null
    ): TuningLogEvent? {
        return appendEvent(
            category = category,
            stage = stage,
            status = status,
            message = message,
            operationId = operationId,
            detail = detail,
            errorCause = errorCause,
            nextAction = nextAction
        )
    }

    /**
     * Emits the single terminal event for an operation. A second terminal event for the
     * same operation is rejected so a late callback cannot rewrite a final result.
     */
    @Synchronized
    fun finishOperation(
        operationId: String,
        status: TuningLogStatus,
        stage: String,
        message: String,
        detail: String? = null,
        errorCause: String? = null,
        nextAction: String? = null,
        overallPercent: Int? = null,
        stagePercent: Int? = null,
        completedUnits: Long? = null,
        totalUnits: Long? = null,
        unitLabel: String = "bytes"
    ): TuningLogEvent? {
        if (!status.isTerminal) return null
        val event = appendEvent(
            category = operationCategories[operationId] ?: TuningLogCategory.SESSION,
            stage = stage,
            status = status,
            message = message,
            operationId = operationId,
            detail = detail,
            errorCause = errorCause,
            nextAction = nextAction
        ) ?: return null

        val current = progressByOperation[operationId]
        val updated = TuningProgress(
            operationId = operationId,
            operationName = current?.operationName ?: "Tuning operation",
            stage = stage,
            overallPercent = overallPercent ?: current?.overallPercent,
            stagePercent = stagePercent ?: current?.stagePercent,
            completedUnits = completedUnits ?: current?.completedUnits,
            totalUnits = totalUnits ?: current?.totalUnits,
            unitLabel = current?.unitLabel ?: unitLabel,
            status = status,
            detail = detail ?: "",
            isTerminal = true
        )
        progressByOperation[operationId] = updated
        if (_latestProgress.value?.operationId == operationId) {
            _latestProgress.value = updated
        }
        return event
    }

    @Synchronized
    fun setDisplayPaused(paused: Boolean) {
        if (paused == _displayPaused.value) return
        if (paused) {
            pausedUntilEventId = _events.value.lastOrNull()?.id ?: 0L
        }
        _displayPaused.value = paused
        rebuildVisibleLocked()
    }

    @Synchronized
    fun clearVisible() {
        visibleStartId = _events.value.lastOrNull()?.id ?: 0L
        rebuildVisibleLocked()
    }

    @Synchronized
    fun exportSessionText(): String {
        val sb = StringBuilder()
        sb.append("REVCORE TUNING SESSION LOG\n")
        sb.append("Session ID  : ").append(sessionId).append('\n')
        sb.append("Exported At : ").append(fullFormat.format(Date())).append('\n')
        sb.append("Scope       : Tuning operations only (session, file, ECU identification, compatibility,\n")
        sb.append("              map analysis/modification, limiter, checksum, validation, backup, ECU write,\n")
        sb.append("              verification, export). Live data and DTC traffic are excluded.\n")
        sb.append("Progress    : Percentages appear only when measured from real bytes or acknowledged blocks.\n")
        sb.append("              Operations without a measurable total are marked as unavailable/not verified.\n")
        sb.append("Events      : ").append(_events.value.size).append('\n')
        sb.append("================================================================================\n")
        for (event in _events.value) {
            sb.append("[").append(event.timestamp).append("] ")
                .append(event.category.name).append(" | ").append(event.stage)
                .append(" | ").append(event.status.name).append('\n')
            sb.append("  ").append(event.message).append('\n')
            if (!event.detail.isNullOrBlank()) {
                sb.append("  Detail: ").append(event.detail).append('\n')
            }
            if (!event.errorCause.isNullOrBlank()) {
                sb.append("  Cause: ").append(event.errorCause).append('\n')
            }
            if (!event.nextAction.isNullOrBlank()) {
                sb.append("  Next action: ").append(event.nextAction).append('\n')
            }
        }
        sb.append("================================================================================\n")
        sb.append("OPERATION PROGRESS (last ").append(progressByOperation.size).append(" operations):\n")
        for (progress in progressByOperation.values) {
            sb.append("  ").append(progress.operationId).append(" | ").append(progress.operationName)
                .append(" | stage=").append(progress.stage)
                .append(" | status=").append(progress.status.name)
                .append(" | overall=")
                .append(progress.overallPercent?.let { "$it%" } ?: "unavailable")
            if (progress.completedUnits != null) {
                sb.append(" | units=").append(progress.completedUnits)
                if (progress.totalUnits != null && progress.totalUnits > 0) {
                    sb.append("/").append(progress.totalUnits)
                }
                sb.append(" ").append(progress.unitLabel)
            }
            sb.append('\n')
            if (progress.detail.isNotBlank()) {
                sb.append("      ").append(progress.detail).append('\n')
            }
        }
        return sb.toString()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Synchronized
    fun resetSessionForTests() {
        _events.value = emptyList()
        _visibleEvents.value = emptyList()
        _latestProgress.value = null
        _displayPaused.value = false
        pausedUntilEventId = 0L
        visibleStartId = 0L
        terminalOperations.clear()
        progressByOperation.clear()
        operationCategories.clear()
        sessionId = "TS-" + sessionFormat.format(Date())
        sessionStarted = false
        eventIdGen.set(1L)
        operationIdGen.set(1L)
    }

    private fun appendEvent(
        category: TuningLogCategory,
        stage: String,
        status: TuningLogStatus,
        message: String,
        operationId: String,
        detail: String? = null,
        errorCause: String? = null,
        nextAction: String? = null
    ): TuningLogEvent? {
        if (operationId.isNotEmpty() && status.isTerminal) {
            if (!terminalOperations.add(operationId)) {
                return null
            }
        }
        val nowMs = System.currentTimeMillis()
        val event = TuningLogEvent(
            id = eventIdGen.getAndIncrement(),
            timestamp = timeFormat.format(Date(nowMs)),
            epochMs = nowMs,
            sessionId = sessionId,
            operationId = operationId,
            category = category,
            stage = stage,
            status = status,
            message = message,
            detail = detail,
            errorCause = errorCause,
            nextAction = nextAction
        )
        val current = _events.value
        _events.value = if (current.size >= MAX_EVENTS) {
            current.takeLast(MAX_EVENTS - 1) + event
        } else {
            current + event
        }
        rebuildVisibleLocked()
        return event
    }

    private fun rebuildVisibleLocked() {
        val all = _events.value
        val pausedBoundary = if (_displayPaused.value) pausedUntilEventId else Long.MAX_VALUE
        _visibleEvents.value = all.filter { it.id > visibleStartId && it.id <= pausedBoundary }
    }

    private fun pruneOperationsLocked(protectedId: String) {
        while (progressByOperation.size > MAX_TRACKED_OPERATIONS) {
            val eldest = progressByOperation.keys.firstOrNull() ?: return
            if (eldest == protectedId) return
            progressByOperation.remove(eldest)
            operationCategories.remove(eldest)
        }
    }
}
