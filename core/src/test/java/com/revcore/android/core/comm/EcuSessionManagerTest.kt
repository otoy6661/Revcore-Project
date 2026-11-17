package com.revcore.android.core.comm

import com.revcore.android.core.protocol.CommandOutcome
import com.revcore.android.core.protocol.CommunicationSniffer
import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class EcuSessionManagerTest {

    @Before
    fun resetSniffer() {
        CommunicationSniffer.clear()
    }

    private class ScriptedTransport(private val response: ByteArray?) : UsbTransport {
        override val isOpen = true
        override val currentBaudRate = 10400
        override val driverName = "ScriptedTransport"
        override val chipTypeDescription = "ScriptedTransport"

        var writes = 0
        private var delivered = false
        val written = ByteArrayOutputStream()

        override fun open() = true
        override fun close() = Unit
        override fun setBaudRate(baud: Int) = true
        override fun setBreak(enable: Boolean) = true
        override fun purgeRxTx() = true
        override fun setDtrRts(dtr: Boolean, rts: Boolean) = true

        override suspend fun write(data: ByteArray, timeoutMs: Int): Int {
            writes++
            written.write(data)
            return data.size
        }

        override suspend fun read(bufferSize: Int, timeoutMs: Int): ByteArray {
            // A scripted ECU response is only observable after a request was
            // written, so the pre-TX stale-input drain cannot consume it.
            if (writes > 0 && !delivered && response != null) {
                delivered = true
                return response
            }
            delay(timeoutMs.toLong())
            return ByteArray(0)
        }
    }

    private fun manager() = EcuSessionManager(KlineWireEngine(logger = { _, _, _ -> }))

    private fun config(timeoutMs: Int = 100) =
        KlineWireConfig(timeoutMs = timeoutMs, retries = 1, interByteMs = 0, echoWindowMs = 0, quietMs = 0)

    @Test
    fun lifecycle_movesFromAdapterOpenToReady() = runTest {
        val transport = ScriptedTransport(HondaFrameCodec.build(0x72, 0x1A, intArrayOf(0x4B)))
        val session = manager()

        assertEquals(CommLifecycle.DISCONNECTED, session.lifecycle.value)
        session.attach(transport)
        assertEquals(CommLifecycle.ADAPTER_OPEN, session.lifecycle.value)

        session.beginIdentification()
        assertEquals(CommLifecycle.IDENTIFYING, session.lifecycle.value)

        val outcome = session.transact(HondaFrameCodec.buildBaseline(0x1A), config())
        assertTrue(outcome is CommandOutcome.Frame)
        assertEquals(CommLifecycle.ECU_READY, session.lifecycle.value)

        session.markIdentified(verified = true)
        assertEquals(FamilyEvidence.IDENTIFIER_DB, session.familyEvidence.value)

        session.beginMonitoring()
        assertEquals(CommLifecycle.MONITORING, session.lifecycle.value)
    }

    @Test
    fun keihinHeader_resolvesFamilyAsHypothesis() = runTest {
        val response = HondaFrameCodec.build(0x7E, 0x71, intArrayOf(0x01))
        val transport = ScriptedTransport(response)
        val session = manager()
        session.attach(transport)

        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config())

        assertEquals(HondaEcuFamily.KEIHIN_H99B_KLINE, session.family.value)
        assertEquals(FamilyEvidence.RESPONSE_HEADER, session.familyEvidence.value)
        assertEquals(EvidenceStatus.HYPOTHESIS, session.familyEvidence.value.status)
    }

    @Test
    fun shindengenHeader_resolvesDistinctFamily() = runTest {
        val response = HondaFrameCodec.build(0x7C, 0x71, intArrayOf(0x01))
        val transport = ScriptedTransport(response)
        val session = manager()
        session.attach(transport)

        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config())

        assertEquals(HondaEcuFamily.SHINDENGEN_H99B_KLINE, session.family.value)
    }

    @Test
    fun baselineHeader_doesNotClaimAFamily() = runTest {
        val response = HondaFrameCodec.build(0x72, 0x71, intArrayOf(0x01))
        val transport = ScriptedTransport(response)
        val session = manager()
        session.attach(transport)

        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config())

        assertEquals(HondaEcuFamily.UNKNOWN, session.family.value)
        assertEquals(FamilyEvidence.NONE, session.familyEvidence.value)
    }

    @Test
    fun repeatedFailures_raiseSuspectedLossOnlyWhileOperational() = runTest {
        val session = manager()
        val transport = ScriptedTransport(null)
        session.attach(transport)
        session.markAlive()
        session.beginMonitoring()
        assertEquals(CommLifecycle.MONITORING, session.lifecycle.value)

        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config(30))
        assertEquals(CommLifecycle.MONITORING, session.lifecycle.value)

        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config(30))
        assertEquals(CommLifecycle.SUSPECTED_LOSS, session.lifecycle.value)

        session.markAlive()
        assertEquals(CommLifecycle.ECU_READY, session.lifecycle.value)
    }

    @Test
    fun closedSession_refusesTransactionWithoutWriting() = runTest {
        val transport = ScriptedTransport(HondaFrameCodec.build(0x72, 0x71, intArrayOf(0x01)))
        val session = manager()

        val outcome = session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config())

        assertTrue(outcome is CommandOutcome.Failure)
        assertEquals(0, transport.writes)
    }

    @Test
    fun expectedTransportMismatch_isRefused() = runTest {
        val attached = ScriptedTransport(HondaFrameCodec.build(0x72, 0x71, intArrayOf(0x01)))
        val other = ScriptedTransport(null)
        val session = manager()
        session.attach(attached)

        val outcome = session.transact(
            HondaFrameCodec.buildBaseline(0x71, 0x00),
            config(),
            expectedTransport = other
        )

        assertTrue(outcome is CommandOutcome.Failure)
        assertEquals(0, attached.writes)
        assertEquals(0, other.writes)
    }

    @Test
    fun detach_resetsFamilyAndLifecycle() = runTest {
        val response = HondaFrameCodec.build(0x7E, 0x71, intArrayOf(0x01))
        val transport = ScriptedTransport(response)
        val session = manager()
        session.attach(transport)
        session.transact(HondaFrameCodec.buildBaseline(0x71, 0x00), config())

        session.detach()

        assertEquals(CommLifecycle.DISCONNECTED, session.lifecycle.value)
        assertEquals(HondaEcuFamily.UNKNOWN, session.family.value)
        assertFalse(session.snapshot().lifecycle.isOperational)
    }
}
