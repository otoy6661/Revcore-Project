package com.revcore.android.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomImageTest {

    @Test
    fun readsBigAndLittleEndianWords() {
        val image = RomImage(byteArrayOf(0x12, 0x34))
        assertEquals(0x1234, image.word16(0, bigEndian = true))
        assertEquals(0x3412, image.word16(0, bigEndian = false))
        assertNull(image.word16(1))
    }

    @Test
    fun computesAdditiveAndCrcChecksums() {
        val image = RomImage(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertEquals(10, image.additiveChecksum16())
        assertEquals(image.crc16(), RomImage(byteArrayOf(0x01, 0x02, 0x03, 0x04)).crc16())
        assertNull(image.additiveChecksum16(2, 1))
    }

    @Test
    fun mergesConsecutiveDifferencesIntoBlocks() {
        val original = RomImage(byteArrayOf(0, 1, 2, 3, 4, 5))
        val modified = RomImage(byteArrayOf(0, 9, 9, 3, 8, 5))
        val result = original.diff(modified)

        assertEquals(3, result.totalBytesDifferent)
        assertEquals(2, result.modifiedBlocks.size)
        assertEquals(1, result.modifiedBlocks[0].offset)
        assertEquals(2, result.modifiedBlocks[0].length)
        assertEquals(4, result.modifiedBlocks[1].offset)
        assertFalse(result.sizeMismatch)
    }

    @Test
    fun reportsSizeMismatchWithoutHidingTail() {
        val original = RomImage(byteArrayOf(1, 2, 3))
        val modified = RomImage(byteArrayOf(1, 2, 3, 4, 5))
        val result = original.diff(modified)

        assertTrue(result.sizeMismatch)
        assertEquals(3, result.comparedLength)
        assertEquals(1, result.modifiedBlocks.size)
        assertEquals(3, result.modifiedBlocks.single().offset)
        assertEquals(2, result.modifiedBlocks.single().length)
    }

    @Test
    fun identicalImagesHaveNoDifferences() {
        val result = RomImage(byteArrayOf(1, 2, 3)).diff(RomImage(byteArrayOf(1, 2, 3)))
        assertEquals(0, result.totalBytesDifferent)
        assertTrue(result.modifiedBlocks.isEmpty())
    }
}
