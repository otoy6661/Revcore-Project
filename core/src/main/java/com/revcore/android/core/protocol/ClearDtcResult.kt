package com.revcore.android.core.protocol

enum class ClearDtcStatus {
    SUCCESS_VERIFIED,
    CLEARED_UNVERIFIED,
    RE_DETECTED_ACTIVE_FAULT,
    REJECTED_OR_TIMEOUT,
    UNSUPPORTED_FAMILY,
    NO_ADAPTER
}

data class ClearDtcResult(
    val status: ClearDtcStatus,
    val messageEn: String,
    val messageId: String,
    val remainingDtcs: List<HondaDtc> = emptyList()
)
