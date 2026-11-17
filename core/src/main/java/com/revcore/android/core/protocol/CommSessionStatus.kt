package com.revcore.android.core.protocol

/**
 * Evidence levels for a communication session. A higher level is never inferred:
 * it is only recorded when the corresponding wire-level evidence was observed.
 */
enum class CommSessionStatus(val rank: Int) {
    NONE(0),
    USB_OPEN(10),
    INIT_ATTEMPTED(20),
    LOCAL_ECHO_ONLY(30),
    ECU_RESPONSE_VALID(40),
    ECU_ID_VALID(50),
    DTC_READ_CONFIRMED(60),
    LIVE_DATA_VALID(70),
    OPERATION_CONFIRMED(80);

    val isEcuEvidence: Boolean
        get() = rank >= ECU_RESPONSE_VALID.rank
}
