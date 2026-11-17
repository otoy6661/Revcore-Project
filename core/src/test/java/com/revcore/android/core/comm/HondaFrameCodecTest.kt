package com.revcore.android.core.comm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HondaFrameCodecTest {

    @Test
    fun buildBaseline_matchesKnownFrameAndChecksum() {
        val frame = HondaFrameCodec.buildBaseline(0x71, 0x00)
        assertArrayEquals(byteArrayOf(0x72, 0x05, 0x71, 0x00, 0x18), frame)
        assertTrue(HondaFrameCodec.isWellFormed(frame))
    }

    @Test
    fun builtFrames_passTheirOwnValidation() {
        val requests = listOf(
            HondaFrameCodec.buildBaseline(0x1A, 0x00),
            HondaFrameCodec.buildBaseline(0x71, 0x00, 0x18),
            HondaFrameCodec.build(0x7D, 0x10, intArrayOf(0x01, 0x03)),
            HondaFrameCodec.build(0x7B, 0x10, intArrayOf(0x01, 0x03))
        )
        for (frame in requests) {
            assertEquals(frame.size, HondaFrameCodec.declaredLength(frame))
            assertTrue(HondaFrameCodec.isWellFormed(frame))
        }
    }

    @Test
    fun corruptedChecksum_isRejected() {
        val frame = HondaFrameCodec.buildBaseline(0x1A, 0x4B, 0x31, 0x41)
        frame[frame.size - 1] = (frame.last().toInt() + 1).toByte()
        assertFalse(HondaFrameCodec.isWellFormed(frame))
    }

    @Test
    fun mismatchedDeclaredLength_isRejected() {
        val frame = HondaFrameCodec.buildBaseline(0x1A, 0x00)
        frame[1] = 0x06
        assertFalse(HondaFrameCodec.isWellFormed(frame))
    }

    @Test
    fun unknownHeader_isRejectedUnlessAllowed() {
        val frame = HondaFrameCodec.build(0x55, 0x1A, intArrayOf(0x00))
        assertFalse(HondaFrameCodec.isWellFormed(frame))
        assertTrue(HondaFrameCodec.isWellFormed(frame, setOf(0x55)))
    }

    @Test
    fun frameLimit_is254Bytes() {
        assertEquals(254, HondaFrameCodec.MAX_FRAME_LENGTH)
        val max = HondaFrameCodec.buildBaseline(0x71, *IntArray(250))
        assertEquals(254, max.size)
        assertTrue(HondaFrameCodec.isWellFormed(max))

        var rejected = false
        try {
            HondaFrameCodec.buildBaseline(0x71, *IntArray(251))
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("frame over 254 bytes must be rejected", rejected)
    }

    @Test
    fun payload_excludesHeaderLengthServiceAndChecksum() {
        val frame = HondaFrameCodec.buildBaseline(0x71, 0x01, 0x02, 0x03)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), HondaFrameCodec.payload(frame))
    }

    @Test
    fun negativeResponse_isDetected() {
        assertTrue(HondaFrameCodec.isNegativeResponse(HondaFrameCodec.buildBaseline(0x7F, 0x1A)))
        assertFalse(HondaFrameCodec.isNegativeResponse(HondaFrameCodec.buildBaseline(0x1A, 0x00)))
    }

    @Test
    fun headEcho_isStripped_onlyAtHead() {
        val sent = HondaFrameCodec.buildBaseline(0x71, 0x00)
        val response = HondaFrameCodec.buildBaseline(0x71, 0x01)

        val head = HondaFrameCodec.stripLeadingEcho(sent + response, sent)
        assertTrue(head.removed)
        assertArrayEquals(response, head.body)

        val embedded = HondaFrameCodec.stripLeadingEcho(response + sent, sent)
        assertFalse(embedded.removed)
        assertArrayEquals(response + sent, embedded.body)
    }

    @Test
    fun leadingZeroPaddingIsSkippedBeforeEcho() {
        val sent = HondaFrameCodec.buildBaseline(0x71, 0x00)
        val response = HondaFrameCodec.buildBaseline(0x71, 0x01)
        val result = HondaFrameCodec.stripLeadingEcho(byteArrayOf(0x00, 0x00) + sent + response, sent)
        assertTrue(result.removed)
        assertArrayEquals(response, result.body)
    }
}
