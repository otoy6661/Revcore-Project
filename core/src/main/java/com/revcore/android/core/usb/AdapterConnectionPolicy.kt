package com.revcore.android.core.usb

enum class AdapterConnectAction {
    IGNORE_UNSUPPORTED,
    REQUEST_PERMISSION,
    WAIT_FOR_USER,
    OPEN_ADAPTER,
    NO_OP_ALREADY_CONNECTED
}

/**
 * Pure decision logic for USB OTG auto-connect. Kept free of Android types so the
 * attach/permission/detach/reconnect flow can be unit tested without hardware.
 */
object AdapterConnectionPolicy {
    val SUPPORTED_VENDOR_IDS: Set<Int> = setOf(0x0403, 0x1A86, 0x10C4, 0x067B)

    fun isSupportedVendor(vendorId: Int): Boolean = vendorId in SUPPORTED_VENDOR_IDS

    fun decide(
        supported: Boolean,
        hasPermission: Boolean,
        permissionDenied: Boolean,
        alreadyConnected: Boolean,
        autoConnectPaused: Boolean
    ): AdapterConnectAction = when {
        !supported -> AdapterConnectAction.IGNORE_UNSUPPORTED
        alreadyConnected -> AdapterConnectAction.NO_OP_ALREADY_CONNECTED
        autoConnectPaused || permissionDenied -> AdapterConnectAction.WAIT_FOR_USER
        hasPermission -> AdapterConnectAction.OPEN_ADAPTER
        else -> AdapterConnectAction.REQUEST_PERMISSION
    }

    fun shouldTeardown(
        detachedDeviceId: Int?,
        selectedDeviceId: Int?,
        selectedStillAttached: Boolean
    ): Boolean {
        if (selectedDeviceId == null) return false
        if (detachedDeviceId == selectedDeviceId) return true
        return !selectedStillAttached
    }

    fun shouldAutoConnectOnResume(
        hasPermission: Boolean,
        permissionDenied: Boolean,
        autoConnectPaused: Boolean,
        alreadyConnected: Boolean
    ): Boolean = !autoConnectPaused && !alreadyConnected && hasPermission && !permissionDenied
}
