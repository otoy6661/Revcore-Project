package com.revcore.android.core.protocol

import com.revcore.android.core.comm.HondaFrameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HondaIdentifierParserTest {

    @Test
    fun asciiPayloadIsPreservedVerbatim() {
        val frame = HondaFrameCodec.buildBaseline(0x1A, 0x4B, 0x31, 0x41) // "K1A"
        val parsed = HondaIdentifierParser.parse(frame)
        assertNotNull(parsed)
        assertEquals("K1A", parsed!!.identifier)
        assertEquals("4B3141", parsed.payloadHex)
        assertEquals("K1A", parsed.payloadAscii)
        assertEquals("payload-ascii", parsed.extraction)
        assertEquals(7, frame.size) // the previous parser rejected frames shorter than 8 bytes
    }

    @Test
    fun fiveByteBinaryPayloadIsNotTruncated() {
        val frame = HondaFrameCodec.buildBaseline(0x1A, 0x01, 0x03, 0x7A, 0x0F, 0x03)
        val parsed = HondaIdentifierParser.parse(frame)
        assertNotNull(parsed)
        assertEquals("01037A0F03", parsed!!.identifier)
        assertEquals("payload-hex", parsed.extraction)
    }

    @Test
    fun longerPayloadKeepsEveryByte() {
        val frame = HondaFrameCodec.buildBaseline(
            0x1A, 0x01, 0x03, 0x7A, 0x0F, 0x03, 0x01, 0x02
        )
        val parsed = HondaIdentifierParser.parse(frame)
        assertNotNull(parsed)
        assertEquals("01037A0F030102", parsed!!.identifier)
    }

    @Test
    fun corruptChecksumIsRejected() {
        val frame = HondaFrameCodec.buildBaseline(0x1A, 0x4B, 0x31, 0x41)
        frame[frame.size - 1] = (frame.last().toInt() + 1).toByte()
        assertNull(HondaIdentifierParser.parse(frame))
        assertNull(HondaIdentifierParser.extract(frame))
    }

    @Test
    fun negativeResponseIsRejected() {
        val frame = HondaFrameCodec.buildBaseline(0x7F, 0x1A, 0x12)
        assertNull(HondaIdentifierParser.parse(frame))
    }

    @Test
    fun allZeroOrAllFfPayloadIsRejected() {
        assertNull(HondaIdentifierParser.parse(HondaFrameCodec.buildBaseline(0x1A, 0x00, 0x00, 0x00)))
        assertNull(
            HondaIdentifierParser.parse(
                HondaFrameCodec.buildBaseline(0x1A, 0xFF, 0xFF, 0xFF)
            )
        )
    }

    @Test
    fun emptyPayloadIsRejected() {
        assertNull(HondaIdentifierParser.parse(HondaFrameCodec.buildBaseline(0x1A)))
    }
}
