package com.revcore.android.core.protocol

enum class ProtocolPhase {
    FAST_INIT,
    DIAGNOSTIC_FALLBACK,
    SEARCHING_IDENTIFICATION,
    ECU_RESPONDED,
    IDENTIFIED,
    NO_RESPONSE
}
