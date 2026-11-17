package com.revcore.android.core.protocol

/**
 * Strict decoder for the Honda identification response.
 *
 * The frame layout is [header][declared length][service][payload][checksum].
 * The complete payload is preserved: this parser never removes leading or trailing
 * identity bytes. When the payload is printable ASCII it is used verbatim; otherwise
 * it is represented as hexadecimal. A candidate is rejected when framing, declared
 * length or checksum is invalid, when the response is negative, or when the payload
 * is empty or entirely 0x00/0xFF.
 *
 * The old implementation hardcoded four bytes at RESP[3..6] and required at least
 * eight frame bytes. That both truncated longer identifiers and rejected valid short
 * responses, so it was replaced.
 */
internal object HondaIdentifierParser {
    private const val NEGATIVE_RESPONSE_SERVICE = 0x7F
    private const val MIN_FRAME_SIZE = 5 // header, length, service, >=1 payload, checksum

    data class IdentifierPayload(
        val frameHex: String,
        val payloadHex: String,
        val payloadAscii: String?,
        val identifier: String,
        val extraction: String,
        val payload: ByteArray
    )

    fun parse(frame: ByteArray): IdentifierPayload? {
        if (frame.size < MIN_FRAME_SIZE) return null
        if (!KlinePacket.isValidHeader(frame[0])) return null

        val declaredLength = frame[1].toInt() and 0xFF
        if (declaredLength != frame.size) return null
        if (!HondaChecksum.validatePacket(frame)) return null

        val service = frame[2].toInt() and 0xFF
        if (service == NEGATIVE_RESPONSE_SERVICE) return null

        val payload = frame.copyOfRange(3, frame.size - 1)
        if (payload.isEmpty()) return null
        if (payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }) return null

        val printable = payload.all { (it.toInt() and 0xFF) in 0x20..0x7E }
        val ascii = if (printable) payload.toString(Charsets.US_ASCII) else null
        val identifier = ascii ?: payload.joinToString("") { String.format("%02X", it) }

        return IdentifierPayload(
            frameHex = frame.joinToString("") { String.format("%02X", it) },
            payloadHex = payload.joinToString("") { String.format("%02X", it) },
            payloadAscii = ascii,
            identifier = identifier,
            extraction = if (printable) "payload-ascii" else "payload-hex",
            payload = payload
        )
    }

    /** Complete identifier text; never a truncated slice. */
    fun extract(frame: ByteArray): String? = parse(frame)?.identifier
}
