package com.revcore.android.core.protocol

import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class KlineTransceiverTest {

    @Before
    fun resetSniffer() {
        CommunicationSniffer.clear()
    }

    private fun newTransceiver(scheduler: TestCoroutineScheduler) = KlineTransceiver(
        logger = { _, _, _ -> },
        clock = { scheduler.currentTime }
    )

    private fun hondaFrame(service: Int, vararg params: Int): ByteArray {
        val length = 4 + params.size
        val frame = ByteArray(length)
        frame[0] = 0x72
        frame[1] = length.toByte()
        frame[2] = service.toByte()
        params.forEachIndexed { index, value -> frame[3 + index] = value.toByte() }
        frame[length - 1] = HondaChecksum.calculatePacketChecksum(frame, 0, length - 1)
        return frame
    }

    private class FakeTransport(
        private val clock: () -> Long,
        plans: List<Plan> = emptyList()
    ) : UsbTransport {
        data class Plan(val afterWrite: Int, val delayMs: Long, val bytes: ByteArray)

        private val plans = plans.toMutableList()
        private val writeTimes = mutableListOf<Long>()
        val written = ByteArrayOutputStream()

        override val isOpen = true
        override val currentBaudRate = 10400
        override val driverName = "FakeTransport"
        override val chipTypeDescription = "FakeTransport"

        override fun open() = true
        override fun close() = Unit
        override fun setBaudRate(baud: Int) = true
        override fun setBreak(enable: Boolean) = true
        override fun purgeRxTx() = true
        override fun setDtrRts(dtr: Boolean, rts: Boolean) = true

        override suspend fun write(data: ByteArray, timeoutMs: Int): Int {
            writeTimes.add(clock())
            written.write(data)
            return data.size
        }

        override suspend fun read(bufferSize: Int, timeoutMs: Int): ByteArray {
            ready()?.let { return it }
            val nextAvailableAt = plans
                .filter { it.afterWrite < writeTimes.size }
                .minOfOrNull { writeTimes[it.afterWrite] + it.delayMs }
            val waitMs = if (nextAvailableAt == null) {
                timeoutMs.toLong()
            } else {
                (nextAvailableAt - clock()).coerceIn(0L, timeoutMs.toLong())
            }
            if (waitMs > 0) delay(waitMs)
            return ready() ?: ByteArray(0)
        }

        private fun ready(): ByteArray? {
            val plan = plans.firstOrNull {
                it.afterWrite < writeTimes.size && clock() >= writeTimes[it.afterWrite] + it.delayMs
            } ?: return null
            plans.remove(plan)
            return plan.bytes
        }
    }

    @Test
    fun fullEcho_isRejectedAndReportedAsEchoOnly() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, request)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("echo", ignoreCase = true))
        assertEquals("LOCAL_ECHO_ONLY", CommunicationSniffer.events.value.last().status)
        assertTrue(CommunicationSniffer.stats.value.echoCount >= 1)
    }

    @Test
    fun partialEcho_isNotAcceptedAsResponse() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val partial = request.copyOfRange(0, 3)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, partial)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("echo", ignoreCase = true))
    }

    @Test
    fun delayedEchoFromPreviousRetry_isSkippedAndRealResponseAccepted() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport(
            clock = { testScheduler.currentTime },
            plans = listOf(
                FakeTransport.Plan(afterWrite = 0, delayMs = 800, bytes = request),
                FakeTransport.Plan(afterWrite = 1, delayMs = 200, bytes = response)
            )
        )

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 500, retries = 2, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun validEcuResponse_isAccepted() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 10, response)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun fullEchoFollowedByRealResponse_keepsTheRealResponse() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val response = hondaFrame(0x71, 0x01)
        val combined = request + response
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, combined)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun badChecksum_isRejectedWithChecksumReason() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val corrupted = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        corrupted[corrupted.size - 1] = (corrupted.last().toInt() + 1).toByte()
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, corrupted)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Failure)
        val reason = (outcome as CommandOutcome.Failure).reason
        assertTrue(reason.contains("checksum", ignoreCase = true))
        assertFalse(reason.contains("echo", ignoreCase = true))
        assertNotNull(CommunicationSniffer.events.value.lastOrNull { it.status == "CHECKSUM_FAILED" })
        assertTrue(CommunicationSniffer.stats.value.rxFramesInvalid >= 1)
    }

    @Test
    fun fragmentedResponse_isAssembledAndAccepted() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport(
            clock = { testScheduler.currentTime },
            plans = listOf(
                FakeTransport.Plan(0, 0, response.copyOfRange(0, 3)),
                FakeTransport.Plan(0, 10, response.copyOfRange(3, response.size))
            )
        )

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
        assertEquals(1, CommunicationSniffer.stats.value.rxFramesValid)
    }

    @Test
    fun noiseBeforeValidFrame_isSkippedAndFrameAccepted() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val noise = byteArrayOf(0x00, 0xFF.toByte(), 0x99.toByte())
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, noise + response)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun incompleteCandidateBeforeValidFrame_doesNotAbortScan() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val truncated = byteArrayOf(0x72, 0x40)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, truncated + response)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun negativeResponse_isValidFrameButMarkedNegative() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val negative = hondaFrame(0x7F, 0x1A)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, negative)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(negative, (outcome as CommandOutcome.Frame).bytes)
        assertEquals(
            "NEGATIVE_RESPONSE",
            CommunicationSniffer.events.value.lastOrNull { it.status == "NEGATIVE_RESPONSE" }?.status
        )
    }

    @Test
    fun rawTxAndRxEvents_areLoggedInProtocolOrder() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 10, response)))

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Frame)
        val events = CommunicationSniffer.events.value
        val tx = events.firstOrNull { it.status == "TX_ATTEMPT" }
        val rx = events.firstOrNull { it.status == "RX_RAW" }
        assertNotNull(tx)
        assertNotNull(rx)
        assertTrue(tx!!.id < rx!!.id)
        assertArrayEquals(request, tx.rawBytes)
        assertArrayEquals(response, rx.rawBytes)
        assertEquals(request.size, CommunicationSniffer.stats.value.totalTxBytes)
        assertEquals(response.size, CommunicationSniffer.stats.value.totalRxBytes)
    }

    @Test
    fun timeoutRetry_incrementsRetryCounter() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime })

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 100, retries = 2, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Failure)
        assertEquals(1, CommunicationSniffer.stats.value.retryCount)
        assertEquals(2, CommunicationSniffer.stats.value.timeoutCount)
    }

    @Test
    fun exportAsText_containsEvidenceSessionSummary() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 10, response)))

        newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 300, retries = 1, interByteMs = 2, echoWindowMs = 50)

        val text = CommunicationSniffer.exportAsText("TEST_ECU")
        assertTrue(text.contains("SESSION SUMMARY"))
        assertTrue(text.contains("Highest evidence status"))
        assertTrue(text.contains("RX frames valid"))
        assertTrue(text.contains("Init strategies tried"))
    }

    @Test
    fun noBytes_isReportedAsTimeout() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime })

        val outcome = newTransceiver(testScheduler)
            .transceive(fake, request, timeoutMs = 200, retries = 1, interByteMs = 2, echoWindowMs = 50)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("Tidak ada byte diterima"))
        assertTrue(testScheduler.currentTime >= 200)
    }
}
