package com.revcore.android.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineProtocolTest {

    private fun frame(vararg bytes: Int): ByteArray =
        ByteArray(bytes.size) { bytes[it].toByte() }

    @Test
    fun `baseline identification requests have valid Honda checksums`() {
        val requests = listOf(
            frame(0xFE, 0x04, 0x72, 0x8C),
            frame(0x72, 0x05, 0x00, 0xF0, 0x99),
            frame(0x72, 0x07, 0x72, 0x00, 0x00, 0x05, 0x10),
            frame(0x72, 0x05, 0x71, 0x00, 0x18)
        )
        requests.forEach { assertTrue(HondaChecksum.validatePacket(it)) }
    }

    @Test
    fun `baseline DTC and clear requests have valid Honda checksums`() {
        val requests = listOf(
            frame(0x72, 0x05, 0x74, 0x01, 0x14),
            frame(0x72, 0x05, 0x71, 0x20, 0xF8),
            frame(0x72, 0x05, 0x00, 0x00, 0x89),
            frame(0x72, 0x05, 0x60, 0x01, 0x28),
            frame(0x72, 0x05, 0x74, 0x02, 0x13),
            frame(0x72, 0x04, 0x04, 0x86)
        )
        requests.forEach { assertTrue(HondaChecksum.validatePacket(it)) }
    }

    @Test
    fun `synthetic live data request has a valid Honda checksum`() {
        val request = frame(0x72, 0x05, 0x71, 0x7A, 0x9E)
        assertTrue(HondaChecksum.validatePacket(request))
    }

    @Test
    fun `count prefixed DTC payload maps known Honda codes`() {
        val dtcs = HondaDtcDatabase.parseDtcPacket(frame(0x02, 0x01, 0x01, 0x08, 0x01))
        assertEquals(2, dtcs.size)
        assertEquals("P0107", dtcs[0].standardCode)
        assertEquals("P0122", dtcs[1].standardCode)
    }

    @Test
    fun `direct DTC payload filters protocol bytes and invalid sub codes`() {
        val dtcs = HondaDtcDatabase.parseDtcPacket(frame(0x71, 0x72, 0x74, 0x01, 0x01, 0x00, 0xFF, 0x08, 0x02))
        assertEquals(2, dtcs.size)
        assertEquals("P0107", dtcs[0].standardCode)
        assertEquals("P0123", dtcs[1].standardCode)
    }

    @Test
    fun `empty DTC payload reports no codes`() {
        assertEquals(0, HondaDtcDatabase.parseDtcPacket(ByteArray(0)).size)
    }
}
