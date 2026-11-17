package com.revcore.android.core.comm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePollPolicyTest {
    @Test
    fun healthyTransactionDelayTracksMeasuredDuration() {
        assertEquals(150L, LivePollPolicy.nextDelayMs(20, 0))
        assertEquals(300L, LivePollPolicy.nextDelayMs(250, 0))
        assertEquals(1_000L, LivePollPolicy.nextDelayMs(5_000, 0))
    }

    @Test
    fun failuresBackOffExponentiallyAndStopIsBounded() {
        assertEquals(300L, LivePollPolicy.nextDelayMs(0, 1))
        assertEquals(600L, LivePollPolicy.nextDelayMs(0, 2))
        assertEquals(1_200L, LivePollPolicy.nextDelayMs(0, 3))
        assertEquals(2_400L, LivePollPolicy.nextDelayMs(0, 4))
        assertFalse(LivePollPolicy.shouldStop(3))
        assertTrue(LivePollPolicy.shouldStop(4))
    }
}
