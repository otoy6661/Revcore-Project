package com.revcore.android.core.usb

/**
 * Adapter detection and ECU identification are deliberately separate states.
 * The adapter can be open while no ECU has answered.
 */
enum class UsbAutoConnectState {
    IDLE,
    WAITING_FOR_DEVICE,
    DEVICE_DETECTED,
    WAITING_PERMISSION,
    OPENING_ADAPTER,
    KLINE_INITIALIZING,
    SEARCHING_ECU,
    ECU_RESPONDED_UNVERIFIED,
    ECU_IDENTIFIED,
    FAILED,
    PAUSED
}
