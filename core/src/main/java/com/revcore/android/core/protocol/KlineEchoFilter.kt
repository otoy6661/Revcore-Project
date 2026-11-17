package com.revcore.android.core.protocol

import java.io.ByteArrayOutputStream

class KlineEchoFilter {
    private val rxBuffer = ByteArrayOutputStream()

    val bufferSize: Int
        @Synchronized get() = rxBuffer.size()

    @Synchronized
    fun appendRx(data: ByteArray) {
        if (data.isNotEmpty()) {
            rxBuffer.write(data)
        }
    }

    @Synchronized
    fun suppressEcho(transmitted: ByteArray): Boolean {
        if (transmitted.isEmpty()) return false
        val current = rxBuffer.toByteArray()
        if (current.size < transmitted.size) return false

        val maxSearch = minOf(current.size - transmitted.size, 16)
        var matchOffset = -1

        for (offset in 0..maxSearch) {
            var match = true
            for (j in transmitted.indices) {
                if (current[offset + j] != transmitted[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                matchOffset = offset
                break
            }
        }

        if (matchOffset < 0) return false

        val echoEnd = matchOffset + transmitted.size
        val remainingLen = current.size - echoEnd
        rxBuffer.reset()
        if (remainingLen > 0) {
            rxBuffer.write(current, echoEnd, remainingLen)
        }

        CommunicationSniffer.logEvent(
            category = CommEventCategory.KLINE,
            stage = CommDataStage.KLINE_ECHO,
            rawBytes = transmitted,
            description = "K-Line Echo suppressed (${transmitted.size} bytes removed from RX stream, $remainingLen bytes remain)"
        )
        return true
    }

    @Synchronized
    fun extractPacket(isExpectingEcuResponse: Boolean = true): KlinePacket? {
        val bytes = rxBuffer.toByteArray()
        if (bytes.size < 4) return null

        var startIndex = -1
        for (i in bytes.indices) {
            val b = bytes[i]
            if (KlinePacket.isValidHeader(b)) {
                if (bytes.size - i >= 2) {
                    val expectedLen = bytes[i + 1].toInt() and 0xFF
                    if (expectedLen in 4..256) {
                        startIndex = i
                        break
                    }
                } else {
                    startIndex = i
                    break
                }
            }
        }

        if (startIndex == -1) {
            if (bytes.size > 256) {
                rxBuffer.reset()
            }
            return null
        }

        val available = bytes.size - startIndex
        if (available < 4) return null

        val expectedLength = bytes[startIndex + 1].toInt() and 0xFF
        if (expectedLength in 4..256) {
            if (available >= expectedLength) {
                val candidate = ByteArray(expectedLength)
                System.arraycopy(bytes, startIndex, candidate, 0, expectedLength)
                val packet = KlinePacket.parseFromBytes(candidate)
                if (packet != null) {
                    rxBuffer.reset()
                    val remaining = available - expectedLength
                    if (remaining > 0) {
                        rxBuffer.write(bytes, startIndex + expectedLength, remaining)
                    }
                    CommunicationSniffer.logEvent(
                        category = CommEventCategory.RX,
                        stage = CommDataStage.PROTOCOL_FRAME,
                        rawBytes = candidate,
                        description = String.format("Valid K-Line Packet Extracted: [Header=0x%02X, Cmd=0x%02X, Len=%d]", packet.header, packet.command, packet.length)
                    )
                    return packet
                }
                val trimmed = ByteArray(bytes.size - (startIndex + 1))
                System.arraycopy(bytes, startIndex + 1, trimmed, 0, trimmed.size)
                rxBuffer.reset()
                rxBuffer.write(trimmed)
            }
            return null
        }

        val trimmed = ByteArray(bytes.size - (startIndex + 1))
        System.arraycopy(bytes, startIndex + 1, trimmed, 0, trimmed.size)
        rxBuffer.reset()
        rxBuffer.write(trimmed)
        return null
    }

    @Synchronized
    fun clear() {
        rxBuffer.reset()
    }
}
