package com.revcore.android.data.ecu

import java.util.Locale

/**
 * Layout of the identifier field inside a vendor identification response.
 *
 * [prefixBytes] are response bytes that belong to the protocol exchange (for
 * example the selector echoed from the request) and are preserved as evidence.
 * [fieldBytes] is the complete ECM ID field length. Nothing outside these
 * boundaries is treated as identity data.
 *
 * A layout is only supplied where the corresponding request/response pair is
 * documented. When no layout is known the canonicalizer falls back to complete
 * field candidates anchored at the payload boundaries.
 */
data class IdResponseLayout(
    val prefixBytes: Int,
    val fieldBytes: Int,
    val evidence: String,
    val description: String
)

/** One complete ECM ID field candidate found inside a response payload. */
data class EcuIdFieldCandidate(
    val value: String,
    val startByte: Int,
    val byteLength: Int,
    val origin: String
)

/**
 * Result of canonicalizing one identification response.
 *
 * [rawId] is the complete decoded payload (hex for binary data, ASCII when the
 * whole payload is printable). [canonicalId] is the database-shaped field only.
 * [protocolPrefixHex] and [trailingHex] keep the bytes outside the selected
 * field so a caller can log or display them; they are never silently discarded.
 */
data class EcuIdCanonicalization(
    val rawId: String?,
    val rawAscii: String?,
    val canonicalId: String?,
    val candidates: List<EcuIdFieldCandidate> = emptyList(),
    val protocolPrefixHex: String? = null,
    val trailingHex: String? = null,
    val method: String = "none",
    val notes: List<String> = emptyList()
) {
    val canonicalCandidates: List<String>
        get() = (listOfNotNull(canonicalId) + candidates.map { it.value }).distinct()
}

/**
 * Selects the canonical ECM ID field from an identification response.
 *
 * Canonical database IDs in this project are 5 or 6 bytes (10 or 12 hexadecimal
 * characters). The 489-record catalog contains 488 five-byte IDs, so five bytes is
 * the reference field length. Extraction never strips an arbitrary number of
 * leading bytes and never drops trailing bytes: it selects a complete field inside
 * documented boundaries and reports everything around it.
 *
 * When a known database ID set is supplied, a complete boundary candidate that
 * matches it exactly is preferred. This is still an exact full-field match; it is
 * not substring matching. If no candidate is known, the trailing reference field
 * is used because observed baseline identification responses are
 * `[echoed selector][complete ECM ID]`.
 */
object EcuIdCanonicalizer {
    /** Dominant ECM ID field size in the reference database (488 of 489 records). */
    const val REFERENCE_FIELD_BYTES = 5

    /** Complete ECM ID field sizes represented by the reference database. */
    val SUPPORTED_FIELD_BYTES: List<Int> = listOf(5, 6)

    private val ID_10 = Regex("^[0-9A-F]{10}$")
    private val ID_12 = Regex("^[0-9A-F]{12}$")
    private val ASCII_ID_TOKEN = Regex("(?<![0-9A-F])(?:[0-9A-F]{10}|[0-9A-F]{12})(?![0-9A-F])")

    fun isCanonicalId(value: String?): Boolean {
        val normalized = value?.trim()?.uppercase(Locale.ROOT) ?: return false
        return ID_10.matches(normalized) || ID_12.matches(normalized)
    }

    fun canonicalIdByteLength(value: String?): Int? =
        value?.trim()?.uppercase(Locale.ROOT)?.let { normalized ->
            when {
                ID_10.matches(normalized) -> 5
                ID_12.matches(normalized) -> 6
                else -> null
            }
        }

    /**
     * Canonicalizes an identifier string that is not accompanied by its frame.
     * The original string stays available as [EcuIdCanonicalization.rawId].
     *
     * [knownCanonicalIds] lets a caller disambiguate between complete boundary
     * candidates using the reference catalog. Without it the dominant five-byte
     * trailing field is used.
     */
    fun canonicalizeRawString(
        raw: String?,
        knownCanonicalIds: Set<String> = emptySet()
    ): EcuIdCanonicalization {
        val original = raw?.trim()?.takeIf { it.isNotEmpty() }
            ?: return EcuIdCanonicalization(
                rawId = null,
                rawAscii = null,
                canonicalId = null,
                method = "empty"
            )

        val normalized = original.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) {
            return EcuIdCanonicalization(
                rawId = original,
                rawAscii = null,
                canonicalId = null,
                method = "empty",
                notes = listOf("Identifier contained no alphanumeric characters.")
            )
        }
        if (!normalized.all { it in '0'..'9' || it in 'A'..'F' }) {
            return EcuIdCanonicalization(
                rawId = original,
                rawAscii = null,
                canonicalId = null,
                method = "not-hex",
                notes = listOf("Raw identifier is not hexadecimal; no ECM ID field was inferred.")
            )
        }
        if (normalized.length % 2 != 0) {
            return EcuIdCanonicalization(
                rawId = original,
                rawAscii = null,
                canonicalId = null,
                method = "malformed-hex",
                notes = listOf("Hex identifier has an odd number of characters; it cannot be a complete byte field.")
            )
        }
        if (isCanonicalId(normalized)) {
            return EcuIdCanonicalization(
                rawId = original,
                rawAscii = null,
                canonicalId = normalized,
                candidates = listOf(
                    EcuIdFieldCandidate(normalized, 0, normalized.length / 2, "whole-identifier")
                ),
                method = "whole-identifier",
                notes = listOf("Identifier already has a complete database ECM ID shape.")
            )
        }

        if (normalized.length > 10) {
            val candidates = linkedSetOf<EcuIdFieldCandidate>()
            for (lengthChars in listOf(12, 10)) {
                if (normalized.length <= lengthChars) continue
                val leading = normalized.take(lengthChars)
                val trailing = normalized.takeLast(lengthChars)
                if (!leading.startsWith("00")) {
                    candidates += EcuIdFieldCandidate(
                        leading,
                        0,
                        lengthChars / 2,
                        "leading-reference-field"
                    )
                }
                if (!trailing.startsWith("00")) {
                    candidates += EcuIdFieldCandidate(
                        trailing,
                        (normalized.length - lengthChars) / 2,
                        lengthChars / 2,
                        "trailing-reference-field"
                    )
                }
            }

            val known = knownCanonicalIds.map { it.uppercase(Locale.ROOT) }.toSet()
            val knownMatch = candidates.singleOrNull { it.value in known }
            val selected = knownMatch
                ?: candidates.firstOrNull { it.byteLength == REFERENCE_FIELD_BYTES && it.origin == "trailing-reference-field" }
                ?: candidates.firstOrNull()

            if (selected != null) {
                val prefixChars = selected.startByte * 2
                return EcuIdCanonicalization(
                    rawId = original,
                    rawAscii = null,
                    canonicalId = selected.value,
                    candidates = candidates.toList(),
                    protocolPrefixHex = normalized.take(prefixChars).takeIf { it.isNotEmpty() },
                    trailingHex = normalized.drop(prefixChars + selected.value.length).takeIf { it.isNotEmpty() },
                    method = selected.origin,
                    notes = listOf(
                        "Longer hexadecimal identifier: selected the complete ${selected.byteLength}-byte " +
                            "${selected.origin}. Bytes outside that field are retained as evidence."
                    )
                )
            }
        }

        return EcuIdCanonicalization(
            rawId = original,
            rawAscii = null,
            canonicalId = null,
            method = "unparsed",
            notes = listOf(
                "No complete database-shaped ECM ID field could be identified without discarding significant bytes."
            )
        )
    }

    /**
     * Canonicalizes a checksum-validated response payload.
     *
     * [knownCanonicalIds] contains the normalized ECM IDs of the reference catalog.
     * It is used only to choose between complete boundary-aligned candidates; the
     * caller still has to perform the exact database lookup.
     */
    fun canonicalizePayload(
        payload: ByteArray,
        layout: IdResponseLayout? = null,
        knownCanonicalIds: Set<String> = emptySet()
    ): EcuIdCanonicalization {
        if (payload.isEmpty()) {
            return EcuIdCanonicalization(
                rawId = null,
                rawAscii = null,
                canonicalId = null,
                method = "empty-payload",
                notes = listOf("Identification response carried no payload bytes.")
            )
        }

        val rawHex = payload.joinToString("") { String.format("%02X", it) }
        val printable = payload.all { (it.toInt() and 0xFF) in 0x20..0x7E }
        if (printable) {
            return canonicalizeAsciiPayload(payload.toString(Charsets.US_ASCII))
        }

        if (payload.all { it == 0x00.toByte() || it == 0xFF.toByte() }) {
            return EcuIdCanonicalization(
                rawId = rawHex,
                rawAscii = null,
                canonicalId = null,
                method = "padding-only",
                notes = listOf("Payload consists only of 0x00/0xFF bytes; it carries no identifier.")
            )
        }

        val candidates = linkedMapOf<String, EcuIdFieldCandidate>()
        fun add(start: Int, length: Int, origin: String) {
            if (start < 0 || length <= 0 || start + length > payload.size) return
            val value = payload.copyOfRange(start, start + length)
                .joinToString("") { String.format("%02X", it) }
            if (!isCanonicalId(value)) return
            val key = "$start:$length"
            candidates.putIfAbsent(key, EcuIdFieldCandidate(value, start, length, origin))
        }

        if (layout != null &&
            layout.prefixBytes >= 0 &&
            layout.fieldBytes in SUPPORTED_FIELD_BYTES &&
            payload.size >= layout.prefixBytes + layout.fieldBytes
        ) {
            add(layout.prefixBytes, layout.fieldBytes, "protocol-layout")
        }

        if (payload.size in SUPPORTED_FIELD_BYTES) {
            add(0, payload.size, "whole-payload")
        }

        // Complete fields anchored at either boundary of the protocol payload.
        // Ordered longest-first for the non-reference size so a six-byte format is
        // considered before the five-byte fallback.
        val boundaryLengths = listOf(REFERENCE_FIELD_BYTES) +
            SUPPORTED_FIELD_BYTES.filter { it != REFERENCE_FIELD_BYTES }
        for (length in boundaryLengths) {
            if (payload.size > length) {
                add(payload.size - length, length, "trailing-field")
                add(0, length, "leading-field")
            }
        }

        val all = candidates.values.toList()
        val known = knownCanonicalIds.map { it.uppercase(Locale.ROOT) }.toSet()
        val knownMatches = all.filter { it.value in known }
        val selected = knownMatches.singleOrNull()
            ?: all.firstOrNull { it.origin == "protocol-layout" }
            ?: all.firstOrNull()
            ?: return EcuIdCanonicalization(
                rawId = rawHex,
                rawAscii = null,
                canonicalId = null,
                method = "unparsed",
                notes = listOf(
                    "Binary payload has no complete ${SUPPORTED_FIELD_BYTES.joinToString("/")}-byte " +
                        "database-shaped field at a protocol boundary."
                )
            )

        val prefixHex = if (selected.startByte > 0) {
            payload.copyOfRange(0, selected.startByte)
                .joinToString("") { String.format("%02X", it) }
        } else {
            null
        }
        val end = selected.startByte + selected.byteLength
        val trailingHex = if (end < payload.size) {
            payload.copyOfRange(end, payload.size)
                .joinToString("") { String.format("%02X", it) }
        } else {
            null
        }

        val notes = buildList {
            add(
                "Selected a complete ${selected.byteLength}-byte field at payload byte ${selected.startByte} " +
                    "(${selected.origin})."
            )
            if (prefixHex != null) {
                add("${selected.startByte} leading protocol byte(s) retained as evidence: $prefixHex.")
            }
            if (trailingHex != null) {
                add("${payload.size - end} trailing byte(s) retained and not treated as identity: $trailingHex.")
            }
            if (knownMatches.size > 1) {
                add("Several complete boundary candidates exist in the reference database; the exact lookup must decide.")
            }
            if (layout != null) {
                add("Response layout evidence ${layout.evidence}: ${layout.description}")
            }
        }

        return EcuIdCanonicalization(
            rawId = rawHex,
            rawAscii = null,
            canonicalId = selected.value,
            candidates = all,
            protocolPrefixHex = prefixHex,
            trailingHex = trailingHex,
            method = selected.origin,
            notes = notes
        )
    }

    private fun canonicalizeAsciiPayload(text: String): EcuIdCanonicalization {
        val trimmed = text.trim()
        val normalized = trimmed.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (isCanonicalId(normalized)) {
            return EcuIdCanonicalization(
                rawId = trimmed,
                rawAscii = trimmed,
                canonicalId = normalized,
                candidates = listOf(
                    EcuIdFieldCandidate(normalized, 0, normalized.length / 2, "ascii-whole-payload")
                ),
                method = "ascii-whole-payload",
                notes = listOf("Printable ASCII payload is itself a complete database ECM ID.")
            )
        }

        val tokens = ASCII_ID_TOKEN.findAll(trimmed.uppercase(Locale.ROOT))
            .map { it.value }
            .distinct()
            .toList()
        return when {
            tokens.size == 1 -> EcuIdCanonicalization(
                rawId = trimmed,
                rawAscii = trimmed,
                canonicalId = tokens.single(),
                candidates = listOf(
                    EcuIdFieldCandidate(tokens.single(), 0, tokens.single().length / 2, "ascii-token")
                ),
                method = "ascii-token",
                notes = listOf("ASCII payload contained exactly one complete database-shaped ECM ID token.")
            )
            tokens.size > 1 -> EcuIdCanonicalization(
                rawId = trimmed,
                rawAscii = trimmed,
                canonicalId = null,
                method = "ascii-ambiguous",
                notes = listOf("ASCII payload contained several database-shaped ID tokens; no field was chosen.")
            )
            else -> EcuIdCanonicalization(
                rawId = trimmed,
                rawAscii = trimmed,
                canonicalId = null,
                method = "ascii-text",
                notes = listOf(
                    "Printable ASCII payload is not a complete ECM ID field; it is kept as the raw identifier."
                )
            )
        }
    }
}
