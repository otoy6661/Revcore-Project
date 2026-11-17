package com.revcore.android.core.protocol

sealed class DtcReadResult {
    data class Success(val dtcs: List<HondaDtc>, val format: String) : DtcReadResult()
    data class NoResponse(val reason: String) : DtcReadResult()
    data class Unrecognized(val rawHex: String, val reason: String) : DtcReadResult()
}

enum class DtcReadState {
    NOT_PERFORMED,
    OK_EMPTY,
    OK_CODES,
    NO_RESPONSE,
    UNRECOGNIZED
}

data class DtcParse(
    val dtcs: List<HondaDtc>,
    val format: String
)
