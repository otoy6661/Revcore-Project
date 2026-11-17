package com.revcore.android.core.protocol

data class SnifferStats(
    val totalEvents: Int = 0,
    val totalTxBytes: Int = 0,
    val totalRxBytes: Int = 0,
    val errorCount: Int = 0,
    val txFrames: Int = 0,
    val rxFramesValid: Int = 0,
    val rxFramesInvalid: Int = 0,
    val echoCount: Int = 0,
    val timeoutCount: Int = 0,
    val retryCount: Int = 0,
    val usbErrorCount: Int = 0,
    val parseFailureCount: Int = 0,
    val initAttempts: Int = 0,
    val initStrategies: Set<String> = emptySet(),
    val highestSessionStatus: CommSessionStatus = CommSessionStatus.NONE,
    val lastSessionStatus: CommSessionStatus = CommSessionStatus.NONE
) {
    val txPackets: Int get() = txFrames
    val rxPackets: Int get() = rxFramesValid + rxFramesInvalid
    val echoStrippedCount: Int get() = echoCount
}
