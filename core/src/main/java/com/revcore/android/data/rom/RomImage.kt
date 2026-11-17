package com.revcore.android.data.rom

/**
 * Read-only view over an ECU/ROM image for inspection and comparison.
 *
 * The class deliberately has no write, flash, or checksum-repair method. It reads
 * bytes and computes differences so a developer can analyse an image they own.
 * Offsets and checksum locations are supplied by the caller; this class does not
 * guess them from a model record.
 */
class RomImage(
    val bytes: ByteArray,
    val name: String = "image.bin"
) {
    val size: Int get() = bytes.size
    val sizeKb: Int get() = bytes.size / 1024

    fun word16(offset: Int, bigEndian: Boolean = true): Int? {
        if (offset < 0 || offset + 2 > bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        val second = bytes[offset + 1].toInt() and 0xFF
        return if (bigEndian) (first shl 8) or second else (second shl 8) or first
    }

    fun byteAt(offset: Int): Int? {
        if (offset !in bytes.indices) return null
        return bytes[offset].toInt() and 0xFF
    }

    /** Additive 16-bit checksum over [start, endExclusive). */
    fun additiveChecksum16(start: Int = 0, endExclusive: Int = bytes.size): Int? {
        if (!validRange(start, endExclusive)) return null
        var sum = 0
        for (i in start until endExclusive) {
            sum = (sum + (bytes[i].toInt() and 0xFF)) and 0xFFFF
        }
        return sum
    }

    /** CRC-16/Modbus-style checksum over [start, endExclusive). */
    fun crc16(start: Int = 0, endExclusive: Int = bytes.size): Int? {
        if (!validRange(start, endExclusive)) return null
        var crc = 0xFFFF
        for (i in start until endExclusive) {
            crc = crc xor (bytes[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }

    fun diff(other: RomImage): RomDiffResult {
        val compared = minOf(bytes.size, other.bytes.size)
        val blocks = mutableListOf<ModifiedBlock>()
        var blockStart = -1
        var different = 0

        for (i in 0 until compared) {
            if (bytes[i] != other.bytes[i]) {
                different++
                if (blockStart == -1) blockStart = i
            } else if (blockStart != -1) {
                blocks += block(blockStart, i, other)
                blockStart = -1
            }
        }
        if (blockStart != -1) {
            blocks += block(blockStart, compared, other)
        }

        val commonSize = compared
        val originalTail = if (bytes.size > commonSize) bytes.copyOfRange(commonSize, bytes.size) else null
        val modifiedTail = if (other.bytes.size > commonSize) other.bytes.copyOfRange(commonSize, other.bytes.size) else null
        if (originalTail != null || modifiedTail != null) {
            blocks += ModifiedBlock(
                offset = commonSize,
                length = maxOf(originalTail?.size ?: 0, modifiedTail?.size ?: 0),
                originalBytes = originalTail ?: ByteArray(modifiedTail?.size ?: 0),
                modifiedBytes = modifiedTail ?: ByteArray(originalTail?.size ?: 0),
                label = "Ukuran berbeda mulai 0x${commonSize.toString(16).uppercase()}"
            )
        }

        return RomDiffResult(
            totalBytesDifferent = different,
            modifiedBlocks = blocks,
            comparedLength = compared,
            sizeMismatch = bytes.size != other.bytes.size
        )
    }

    private fun block(start: Int, endExclusive: Int, other: RomImage): ModifiedBlock =
        ModifiedBlock(
            offset = start,
            length = endExclusive - start,
            originalBytes = bytes.copyOfRange(start, endExclusive),
            modifiedBytes = other.bytes.copyOfRange(start, endExclusive),
            label = "Blok 0x${start.toString(16).uppercase()}"
        )

    private fun validRange(start: Int, endExclusive: Int): Boolean =
        start >= 0 && endExclusive >= start && endExclusive <= bytes.size
}

data class RomDiffResult(
    val totalBytesDifferent: Int,
    val modifiedBlocks: List<ModifiedBlock>,
    val comparedLength: Int,
    val sizeMismatch: Boolean
)
