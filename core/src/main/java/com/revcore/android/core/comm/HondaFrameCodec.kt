package com.revcore.android.core.comm

import com.revcore.android.core.protocol.HondaChecksum

/**
 * K-Line framing for Honda PGM-FI.
 *
 * Layout: [header][declared length][service][params...][checksum]
 * The declared length includes the checksum. The checksum is the additive
 * two's-complement value confirmed in the reference analysis image at 0x17a61ec and stored in the
 * final byte over every preceding byte (0x17a620c).
 */
object HondaFrameCodec {
    const val HEADER_BASELINE = 0x72
    const val HEADER_DIAG_RESPONSE = 0x02
    const val HEADER_KEIHIN_RESPONSE = 0x7E
    const val HEADER_SHINDENGEN_RESPONSE = 0x7C
    const val HEADER_KWP_REQUEST_PRIMARY = 0x7D
    const val HEADER_KWP_REQUEST_ALTERNATE = 0x7B
    const val HEADER_BROADCAST = 0xFE

    const val MIN_FRAME_LENGTH = 4
    const val MAX_FRAME_LENGTH = 0xFE
    const val NEGATIVE_RESPONSE_SERVICE = 0x7F

    val DEFAULT_ACCEPTED_HEADERS: Set<Int> = setOf(
        HEADER_BASELINE,
        HEADER_DIAG_RESPONSE,
        HEADER_KEIHIN_RESPONSE,
        HEADER_SHINDENGEN_RESPONSE,
        HEADER_BROADCAST,
        HEADER_KWP_REQUEST_PRIMARY,
        HEADER_KWP_REQUEST_ALTERNATE
    )

    data class EchoStripResult(val body: ByteArray, val removed: Boolean)

    fun checksum(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Byte =
        HondaChecksum.calculatePacketChecksum(bytes, offset, length)

    fun build(header: Int, service: Int, params: IntArray = IntArray(0)): ByteArray {
        val total = 4 + params.size
        require(total <= MAX_FRAME_LENGTH) { "K-Line frame exceeds $MAX_FRAME_LENGTH bytes" }
        val frame = ByteArray(total)
        frame[0] = header.toByte()
        frame[1] = total.toByte()
        frame[2] = service.toByte()
        params.forEachIndexed { index, value -> frame[3 + index] = value.toByte() }
        frame[total - 1] = checksum(frame, 0, total - 1)
        return frame
    }

    fun buildBaseline(service: Int, vararg params: Int): ByteArray =
        build(HEADER_BASELINE, service, params)

    fun declaredLength(frame: ByteArray): Int = if (frame.size > 1) frame[1].toInt() and 0xFF else 0

    fun isWellFormed(frame: ByteArray, acceptedHeaders: Set<Int> = DEFAULT_ACCEPTED_HEADERS): Boolean {
        if (frame.size < MIN_FRAME_LENGTH || frame.size > MAX_FRAME_LENGTH) return false
        if ((frame[0].toInt() and 0xFF) !in acceptedHeaders) return false
        if (declaredLength(frame) != frame.size) return false
        return checksum(frame, 0, frame.size - 1) == frame[frame.size - 1]
    }

    /** Service is byte 2; payload excludes header, length, service and checksum. */
    fun payload(frame: ByteArray): ByteArray {
        if (frame.size <= MIN_FRAME_LENGTH) return ByteArray(0)
        return frame.copyOfRange(3, frame.size - 1)
    }

    fun isNegativeResponse(frame: ByteArray): Boolean =
        frame.size > 2 && (frame[2].toInt() and 0xFF) == NEGATIVE_RESPONSE_SERVICE

    /**
     * Removes loopback echo only when it sits at the head of RX after leading
     * 0x00 padding. Leading 0xFF is preserved because the reference receiver
     * treats it as possible data.
     */
    fun stripLeadingEcho(body: ByteArray, sent: ByteArray): EchoStripResult {
        if (body.isEmpty() || sent.isEmpty()) return EchoStripResult(body, false)
        var start = 0
        while (start < body.size && body[start] == 0x00.toByte()) start++
        val trimmed = if (start > 0) body.copyOfRange(start, body.size) else body
        if (trimmed.size < sent.size) return EchoStripResult(trimmed, false)
        for (i in sent.indices) {
            if (trimmed[i] != sent[i]) return EchoStripResult(trimmed, false)
        }
        return EchoStripResult(trimmed.copyOfRange(sent.size, trimmed.size), true)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { String.format("%02X", it) }
}
