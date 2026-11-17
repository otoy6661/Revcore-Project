package com.revcore.android.core.protocol

enum class StageStatus {
    PASSED,
    FAILED,
    RUNNING,
    PENDING
}

data class TimelineStageItem(
    val stage: DiagnosticStage,
    val isSuccess: Boolean = false,
    val isFailed: Boolean = false,
    val isActive: Boolean = false,
    val timestamp: String = "",
    val lastRelevantEvent: String = "Pending",
    val errorMessage: String? = null
) {
    val name: String get() = stage.name.replace("_", " ")
    val status: StageStatus
        get() = when {
            isSuccess -> StageStatus.PASSED
            isFailed -> StageStatus.FAILED
            isActive -> StageStatus.RUNNING
            else -> StageStatus.PENDING
        }
    val stepNumber: Int get() = stage.ordinal + 1
}
