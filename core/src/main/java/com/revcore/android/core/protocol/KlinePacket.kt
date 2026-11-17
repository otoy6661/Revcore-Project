package com.revcore.android.core.protocol

import java.util.Arrays

data class KlinePacket(
    val header: Byte,
    val command: Byte,
    val payload: ByteArray
) {
    val length: Int
        get() = payload.size + 4

    val checksum: Byte
        get() {
            val raw = toByteArrayWithoutChecksum()
            return HondaChecksum.calculatePacketChecksum(raw, 0, raw.size)
        }

    fun toByteArrayWithoutChecksum(): ByteArray {
        val len = length
        val bytes = ByteArray(len - 1)
        bytes[0] = header
        bytes[1] = len.toByte()
        bytes[2] = command
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, bytes, 3, payload.size)
        }
        return bytes
    }

    fun toByteArray(): ByteArray {
        val len = length
        val bytes = ByteArray(len)
        bytes[0] = header
        bytes[1] = len.toByte()
        bytes[2] = command
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, bytes, 3, payload.size)
        }
        bytes[len - 1] = HondaChecksum.calculatePacketChecksum(bytes, 0, len - 1)
        return bytes
    }

    fun toHexString(): String {
        return toByteArray().joinToString(" ") { String.format("%02X", it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KlinePacket) return false

        if (header != other.header) return false
        if (command != other.command) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.toInt()
        result = 31 * result + command.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val HEADER_ECU_REQUEST: Byte = 0x72
        const val HEADER_ECU_RESPONSE: Byte = 0x72
        const val HEADER_ECU_RESPONSE_DIAG: Byte = 0x02
        const val HEADER_ECU_RESPONSE_KEIHIN: Byte = 0x7E
        const val HEADER_ECU_RESPONSE_SHINDENGEN: Byte = 0x7C
        const val HEADER_BROADCAST: Byte = 0xFE.toByte()
        const val HEADER_FLASH_KEIHIN: Byte = 0x7D
        const val HEADER_FLASH_SHINDENGEN: Byte = 0x7B

        const val CMD_QUERY_ID: Byte = 0x71
        const val CMD_READ_DATA: Byte = 0x00
        const val CMD_CLEAR_DTC: Byte = 0x72
        const val CMD_ACTUATOR_TEST: Byte = 0x01
        const val CMD_SECURITY_ACCESS: Byte = 0x27
        const val CMD_READ_MEMORY: Byte = 0x3C
        const val CMD_WRITE_MEMORY: Byte = 0x3D

        fun isValidHeader(b: Byte): Boolean {
            val ub = b.toInt() and 0xFF
            return ub == 0x72 || ub == 0x02 || ub == 0x7E || ub == 0x7C || ub == 0xFE || ub == 0x7D || ub == 0x7B
        }

        fun parseFromBytes(bytes: ByteArray): KlinePacket? {
            if (bytes.size < 4 || !HondaChecksum.validatePacket(bytes)) {
                return null
            }
            val header = bytes[0]
            val totalLength = bytes[1].toInt() and 0xFF
            if (totalLength != bytes.size) {
                return null
            }
            val command = bytes[2]
            val payloadSize = totalLength - 4
            val payload = if (payloadSize > 0) {
                ByteArray(payloadSize).also {
                    System.arraycopy(bytes, 3, it, 0, payloadSize)
                }
            } else {
                ByteArray(0)
            }
            return KlinePacket(header, command, payload)
        }
    }
}
