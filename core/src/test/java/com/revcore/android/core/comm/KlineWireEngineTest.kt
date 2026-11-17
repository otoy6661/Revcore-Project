package com.revcore.android.core.comm

import com.revcore.android.core.protocol.CommandOutcome
import com.revcore.android.core.protocol.CommunicationSniffer
import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class KlineWireEngineTest {

    @Before
    fun resetSniffer() {
        CommunicationSniffer.clear()
    }

    private fun newEngine(scheduler: TestCoroutineScheduler) = KlineWireEngine(
        logger = { _, _, _ -> },
        clock = { scheduler.currentTime }
    )

    private fun hondaFrame(service: Int, vararg params: Int): ByteArray =
        HondaFrameCodec.buildBaseline(service, *params)

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

    private suspend fun transceive(
        fake: FakeTransport,
        request: ByteArray,
        scheduler: TestCoroutineScheduler,
        timeoutMs: Int = 300,
        retries: Int = 1
    ): CommandOutcome = newEngine(scheduler).transceive(
        fake,
        request,
        KlineWireConfig(
            timeoutMs = timeoutMs,
            retries = retries,
            interByteMs = 2,
            echoWindowMs = 50,
            quietMs = 0
        )
    )

    @Test
    fun validResponse_isAccepted() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 10, response)))

        val outcome = transceive(fake, request, testScheduler)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
        assertArrayEquals(request, fake.written.toByteArray())
    }

    @Test
    fun echoOnly_isRejected() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, request)))

        val outcome = transceive(fake, request, testScheduler)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("echo", ignoreCase = true))
        assertEquals("LOCAL_ECHO_ONLY", CommunicationSniffer.events.value.last().status)
    }

    @Test
    fun echoPrefixedResponse_keepsRealResponse() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val response = hondaFrame(0x71, 0x01)
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, request + response)))

        val outcome = transceive(fake, request, testScheduler)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun corruptedChecksum_isRejected() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val corrupted = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        corrupted[corrupted.size - 1] = (corrupted.last().toInt() + 1).toByte()
        val fake = FakeTransport({ testScheduler.currentTime }, listOf(FakeTransport.Plan(0, 0, corrupted)))

        val outcome = transceive(fake, request, testScheduler)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("checksum", ignoreCase = true))
    }

    @Test
    fun fragmentedResponse_isAssembled() = runTest {
        val request = hondaFrame(0x1A, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport(
            clock = { testScheduler.currentTime },
            plans = listOf(
                FakeTransport.Plan(0, 0, response.copyOfRange(0, 3)),
                FakeTransport.Plan(0, 10, response.copyOfRange(3, response.size))
            )
        )

        val outcome = transceive(fake, request, testScheduler)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun delayedEchoFromFirstAttempt_isSkippedOnRetry() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val response = hondaFrame(0x1A, 0x4B, 0x31, 0x41)
        val fake = FakeTransport(
            clock = { testScheduler.currentTime },
            plans = listOf(
                FakeTransport.Plan(afterWrite = 0, delayMs = 800, bytes = request),
                FakeTransport.Plan(afterWrite = 1, delayMs = 200, bytes = response)
            )
        )

        val outcome = transceive(fake, request, testScheduler, timeoutMs = 500, retries = 2)

        assertTrue(outcome is CommandOutcome.Frame)
        assertArrayEquals(response, (outcome as CommandOutcome.Frame).bytes)
    }

    @Test
    fun noBytes_isReportedAsTimeout() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime })

        val outcome = transceive(fake, request, testScheduler, timeoutMs = 200)

        assertTrue(outcome is CommandOutcome.Failure)
        assertTrue((outcome as CommandOutcome.Failure).reason.contains("Tidak ada byte diterima"))
        assertTrue(testScheduler.currentTime >= 200)
    }

    @Test
    fun retryCount_isReportedForTimeout() = runTest {
        val request = hondaFrame(0x71, 0x00)
        val fake = FakeTransport({ testScheduler.currentTime })

        transceive(fake, request, testScheduler, timeoutMs = 100, retries = 2)

        assertEquals(1, CommunicationSniffer.stats.value.retryCount)
        assertEquals(2, CommunicationSniffer.stats.value.timeoutCount)
    }
}
