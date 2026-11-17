package com.revcore.android.core.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightGateTest {

    @Test
    fun secondCaller_isRejectedWhileFirstIsRunning() {
        val gate = SingleFlightGate()
        assertTrue(gate.tryEnter())
        assertTrue(gate.isRunning)
        assertFalse(gate.tryEnter())
    }

    @Test
    fun exit_allowsNextIdentificationAttempt() {
        val gate = SingleFlightGate()
        assertTrue(gate.tryEnter())
        gate.exit()
        assertFalse(gate.isRunning)
        assertTrue(gate.tryEnter())
    }

    @Test
    fun reset_releasesGateAfterCancelledWork() {
        val gate = SingleFlightGate()
        assertTrue(gate.tryEnter())
        gate.reset()
        assertTrue(gate.tryEnter())
    }
}
