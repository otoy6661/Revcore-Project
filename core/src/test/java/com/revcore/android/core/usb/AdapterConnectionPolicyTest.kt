package com.revcore.android.core.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterConnectionPolicyTest {

    @Test
    fun supportedVendors_matchTransportProbeTable() {
        assertTrue(AdapterConnectionPolicy.isSupportedVendor(0x0403)) // FTDI
        assertTrue(AdapterConnectionPolicy.isSupportedVendor(0x1A86)) // CH340/CH341
        assertTrue(AdapterConnectionPolicy.isSupportedVendor(0x10C4)) // CP210x
        assertTrue(AdapterConnectionPolicy.isSupportedVendor(0x067B)) // Prolific
    }

    @Test
    fun unsupportedDevice_isIgnoredInsteadOfPrompting() {
        assertEquals(
            AdapterConnectAction.IGNORE_UNSUPPORTED,
            AdapterConnectionPolicy.decide(
                supported = false,
                hasPermission = false,
                permissionDenied = false,
                alreadyConnected = false,
                autoConnectPaused = false
            )
        )
    }

    @Test
    fun attachWithPermission_opensAdapterWithoutConnectButton() {
        assertEquals(
            AdapterConnectAction.OPEN_ADAPTER,
            AdapterConnectionPolicy.decide(
                supported = true,
                hasPermission = true,
                permissionDenied = false,
                alreadyConnected = false,
                autoConnectPaused = false
            )
        )
    }

    @Test
    fun attachWithoutPermission_requestsPermission() {
        assertEquals(
            AdapterConnectAction.REQUEST_PERMISSION,
            AdapterConnectionPolicy.decide(
                supported = true,
                hasPermission = false,
                permissionDenied = false,
                alreadyConnected = false,
                autoConnectPaused = false
            )
        )
    }

    @Test
    fun permissionDenial_isNotRetriedAutomatically() {
        assertEquals(
            AdapterConnectAction.WAIT_FOR_USER,
            AdapterConnectionPolicy.decide(
                supported = true,
                hasPermission = false,
                permissionDenied = true,
                alreadyConnected = false,
                autoConnectPaused = false
            )
        )
    }

    @Test
    fun manualDisconnect_pausesAutoConnectEvenWithPermission() {
        assertEquals(
            AdapterConnectAction.WAIT_FOR_USER,
            AdapterConnectionPolicy.decide(
                supported = true,
                hasPermission = true,
                permissionDenied = false,
                alreadyConnected = false,
                autoConnectPaused = true
            )
        )
    }

    @Test
    fun alreadyConnected_doesNotOpenSecondConnection() {
        assertEquals(
            AdapterConnectAction.NO_OP_ALREADY_CONNECTED,
            AdapterConnectionPolicy.decide(
                supported = true,
                hasPermission = true,
                permissionDenied = false,
                alreadyConnected = true,
                autoConnectPaused = false
            )
        )
    }

    @Test
    fun detachOfSelectedAdapter_tearsDownConnection() {
        assertTrue(AdapterConnectionPolicy.shouldTeardown(100, 100, selectedStillAttached = false))
    }

    @Test
    fun detachOfOtherAdapter_keepsSelectedConnection() {
        assertFalse(AdapterConnectionPolicy.shouldTeardown(200, 100, selectedStillAttached = true))
    }

    @Test
    fun staleSelection_tearsDownEvenWithoutMatchingDetachEvent() {
        assertTrue(AdapterConnectionPolicy.shouldTeardown(null, 100, selectedStillAttached = false))
    }

    @Test
    fun noSelectedAdapter_hasNothingToTearDown() {
        assertFalse(AdapterConnectionPolicy.shouldTeardown(100, null, selectedStillAttached = false))
    }

    @Test
    fun resumeReconnectsOnlyWithPermissionAndNoPauseOrDenial() {
        assertTrue(
            AdapterConnectionPolicy.shouldAutoConnectOnResume(
                hasPermission = true,
                permissionDenied = false,
                autoConnectPaused = false,
                alreadyConnected = false
            )
        )
        assertFalse(
            AdapterConnectionPolicy.shouldAutoConnectOnResume(
                hasPermission = false,
                permissionDenied = false,
                autoConnectPaused = false,
                alreadyConnected = false
            )
        )
        assertFalse(
            AdapterConnectionPolicy.shouldAutoConnectOnResume(
                hasPermission = true,
                permissionDenied = false,
                autoConnectPaused = true,
                alreadyConnected = false
            )
        )
        assertFalse(
            AdapterConnectionPolicy.shouldAutoConnectOnResume(
                hasPermission = true,
                permissionDenied = true,
                autoConnectPaused = false,
                alreadyConnected = false
            )
        )
        assertFalse(
            AdapterConnectionPolicy.shouldAutoConnectOnResume(
                hasPermission = true,
                permissionDenied = false,
                autoConnectPaused = false,
                alreadyConnected = true
            )
        )
    }
}
