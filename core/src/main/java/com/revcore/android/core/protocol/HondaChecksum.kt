package com.revcore.android.core.protocol

object HondaChecksum {
    fun calculatePacketChecksum(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Byte {
        var sum = 0
        val end = offset + length
        for (i in offset until end) {
            sum += bytes[i].toInt() and 0xFF
        }
        val cs = (256 - (sum and 0xFF)) and 0xFF
        return cs.toByte()
    }

    fun validatePacket(packetBytes: ByteArray): Boolean {
        if (packetBytes.size < 4) return false
        val computed = calculatePacketChecksum(packetBytes, 0, packetBytes.size - 1)
        val expected = packetBytes.last()
        return computed == expected
    }

    fun calculateKeihin16BitChecksum(romData: ByteArray, startAddr: Int, endAddr: Int): Int {
        var sum = 0
        val maxLen = minOf(endAddr, romData.size - 1)
        var i = startAddr
        while (i < maxLen) {
            val high = romData[i].toInt() and 0xFF
            val low = romData[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low
            sum = (sum + word) and 0xFFFF
            i += 2
        }
        return sum
    }

    fun calculateInverted8BitChecksum(romData: ByteArray, startAddr: Int, endAddr: Int): Byte {
        var sum = 0
        val maxLen = minOf(endAddr, romData.size)
        for (i in startAddr until maxLen) {
            sum = ((romData[i].toInt() and 0xFF) + sum) and 0xFF
        }
        val inverted = sum xor 0xFF
        return inverted.toByte()
    }
}
