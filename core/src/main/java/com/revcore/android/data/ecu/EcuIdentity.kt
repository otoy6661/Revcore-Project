package com.revcore.android.data.ecu

import java.util.Locale

/**
 * Evidence of how an identifier string was obtained from a wire frame.
 *
 * [payloadHex] is the complete payload (every byte between the service byte and
 * the checksum). Nothing is removed from it. [legacyCandidate] is the value the
 * old baseline heuristic produced; it is kept only for comparison and traceability.
 */
data class IdentifierEvidence(
    val frameHex: String? = null,
    val payloadHex: String? = null,
    val payloadAscii: String? = null,
    val extraction: String = "unknown",
    val legacyCandidate: String? = null,
    val notes: List<String> = emptyList()
)

/**
 * Separate ECU identity fields captured from the wire.
 *
 * The raw response and full payload are kept verbatim. [ecuIdRaw] is exactly the
 * identifier string taken from the frame; [ecuIdNormalized] only removes separators
 * and case, which are formatting differences. [canonicalEcuId] is the complete
 * database-shaped ECM ID field selected by [EcuIdCanonicalizer]; it is a different
 * value from [ecuIdRaw] and neither replaces the other. [protocolPrefixHex] and
 * [trailingBytesHex] retain the bytes outside that field. [ecmId] mirrors the
 * canonical ID when it has a documented shape; nothing is inferred from a substring.
 */
data class EcuIdentity(
    val rawResponseHex: String? = null,
    val payloadHex: String? = null,
    val payloadAscii: String? = null,
    val ecuIdRaw: String? = null,
    val ecuIdNormalized: String? = null,
    val canonicalEcuId: String? = null,
    val canonicalCandidates: List<String> = emptyList(),
    val protocolPrefixHex: String? = null,
    val trailingBytesHex: String? = null,
    val canonicalizationMethod: String = "unknown",
    val ecmId: String? = null,
    val partNumber: String? = null,
    val hardwareNumber: String? = null,
    val softwareId: String? = null,
    val responseSource: String = "unknown",
    val extraction: String = "unknown",
    val legacyCandidate: String? = null,
    /**
     * Complete tokens found in a multi-field payload, for example a part number and
     * an ECM ID sent in the same ASCII response. Tokens are kept verbatim; they are
     * never sliced out of a continuous identifier.
     */
    val alternateIdentifiers: List<String> = emptyList(),
    val normalizationNotes: List<String> = emptyList()
) {
    companion object {
        private val ECM_ID_SHAPE = Regex("^(?:[0-9A-F]{10}|[0-9A-F]{12})$")
        private val PART_NUMBER_SHAPE = Regex("^\\d{5}-[A-Z0-9]{3,5}-[A-Z0-9]{2,4}$")

        /** Separators observed between identity fields; a hyphen stays inside part numbers. */
        private val FIELD_SEPARATOR = Regex("[^0-9A-Z-]+")

        /**
         * Extracts only complete tokens with a documented identity shape from a
         * multi-field payload. A single continuous string (for example a hex ID) is
         * never split, so this cannot turn an arbitrary byte sequence into an ID.
         */
        fun structuredTokens(value: String?): List<String> {
            val upper = value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return emptyList()
            val tokens = upper.split(FIELD_SEPARATOR).filter { it.isNotEmpty() }
            if (tokens.size <= 1) return emptyList()
            return tokens.filter { ECM_ID_SHAPE.matches(it) || PART_NUMBER_SHAPE.matches(it) }
                .distinct()
        }

        /**
         * Builds an identity record without discarding any wire data.
         *
         * [identifier] is the complete decoded identifier (ASCII when the payload is
         * printable, otherwise hexadecimal). [payload] is the untouched payload
         * bytes. [legacyCandidate] is an additionally retained heuristic value.
         */
        fun fromIdentifier(
            identifier: String?,
            source: String,
            rawFrame: ByteArray? = null,
            payload: ByteArray? = null,
            extraction: String = "unknown",
            legacyCandidate: String? = null,
            canonicalizationResult: EcuIdCanonicalization? = null,
            knownCanonicalIds: Set<String> = emptySet(),
            extraNotes: List<String> = emptyList()
        ): EcuIdentity {
            val raw = identifier?.trim()?.takeIf { it.isNotEmpty() }
            val normalized = raw?.let { EcmIdentityMatcher.normalize(it) }
            val upper = raw?.uppercase(Locale.ROOT)
            val legacyUpper = legacyCandidate?.trim()?.uppercase(Locale.ROOT)
            val structured = structuredTokens(raw)
            val structuredEcmId = structured.firstOrNull { ECM_ID_SHAPE.matches(it) }
            val structuredPartNumber = structured.firstOrNull { PART_NUMBER_SHAPE.matches(it) }
            val canonicalization = canonicalizationResult
                ?: EcuIdCanonicalizer.canonicalizeRawString(raw, knownCanonicalIds)
            val canonical = canonicalization.canonicalId?.takeIf { ECM_ID_SHAPE.matches(it) }

            // The canonical field is the primary identity. The legacy baseline
            // candidate is only a secondary documented shape and never overrides it.
            val ecmId = canonical
                ?: structuredEcmId
                ?: legacyUpper?.takeIf { ECM_ID_SHAPE.matches(it) }
                ?: upper?.takeIf { ECM_ID_SHAPE.matches(it) }
            val partNumber = upper?.takeIf { PART_NUMBER_SHAPE.matches(it) }
                ?: structuredPartNumber
                ?: legacyUpper?.takeIf { PART_NUMBER_SHAPE.matches(it) }

            val notes = buildList {
                if (raw != null && normalized != null && raw != normalized) {
                    add("Case and separators normalized only; every alphanumeric character retained")
                }
                if (raw != null && normalized != null && raw.length != normalized.length) {
                    add("Length changed only by removed separators (${raw.length} -> ${normalized.length})")
                }
                if (structured.isNotEmpty()) {
                    add("Multi-field payload: ${structured.size} complete documented token(s) kept verbatim")
                }
                if (canonical != null && canonical != normalized) {
                    add(
                        "Canonical ECM ID '$canonical' selected from the complete payload; " +
                            "raw identifier '$raw' retained unchanged"
                    )
                }
                addAll(canonicalization.notes)
                if (ecmId != null) add("Identifier has the documented 10/12-hex ECM ID shape")
                if (partNumber != null) add("Identifier has the documented part-number shape")
                if (ecmId == null && partNumber == null) {
                    add("Identifier shape is not a documented ECM ID or part number; kept as ECU ID")
                }
                if (legacyCandidate != null && legacyCandidate != raw) {
                    add("Legacy baseline candidate '$legacyCandidate' retained for traceability; full payload preserved")
                }
                addAll(extraNotes)
            }

            val canonicalCandidates = (canonicalization.canonicalCandidates + listOfNotNull(canonical))
                .map { EcmIdentityMatcher.normalize(it) }
                .filter { it.isNotEmpty() }
                .distinct()

            return EcuIdentity(
                rawResponseHex = rawFrame?.joinToString("") { String.format("%02X", it) },
                payloadHex = payload?.joinToString("") { String.format("%02X", it) },
                payloadAscii = payload?.takeIf { bytes -> bytes.all { it in 0x20..0x7E } }
                    ?.toString(Charsets.US_ASCII),
                ecuIdRaw = raw,
                ecuIdNormalized = normalized,
                canonicalEcuId = canonical,
                canonicalCandidates = canonicalCandidates,
                protocolPrefixHex = canonicalization.protocolPrefixHex,
                trailingBytesHex = canonicalization.trailingHex,
                canonicalizationMethod = canonicalization.method,
                ecmId = ecmId,
                partNumber = partNumber,
                responseSource = source,
                extraction = extraction,
                legacyCandidate = legacyCandidate,
                alternateIdentifiers = structured.filter { it != raw }.distinct(),
                normalizationNotes = notes
            )
        }
    }
}

enum class IdentityMatchStatus {
    /** The identity equals exactly one database field after formatting normalization. */
    EXACT,

    /**
     * Unique but incomplete identity: only the first or last bytes of a database
     * ECM ID were received. Never treated as exact.
     */
    PARTIAL_MATCH,

    /** Several database records share the identity; no model is chosen automatically. */
    AMBIGUOUS,

    /** No database record matches; the ECU stays unidentified. */
    NOT_FOUND
}

/**
 * User-facing match confidence. It never upgrades evidence: [VERIFIED] is used only
 * when the matched database record itself carries hardware verification, and
 * [CANDIDATE] for a bounded incomplete identifier.
 */
enum class IdentityMatchQuality {
    VERIFIED,
    EXACT,
    CANDIDATE,
    AMBIGUOUS,
    UNKNOWN,
    UNSUPPORTED
}

/** Which database field produced a candidate. */
enum class IdentityField { DB_ID, PART_NUMBER, ECM_ID }

/** How the query relates to the database value. */
enum class IdentityRelation { EXACT, PREFIX, SUFFIX }

data class IdentityCandidate(
    val model: EcmModel,
    val identityField: IdentityField,
    val relation: IdentityRelation,
    val matchedValue: String
) {
    val method: EcmDatabase.MatchMethod
        get() = when {
            identityField == IdentityField.DB_ID -> EcmDatabase.MatchMethod.DB_ID
            identityField == IdentityField.PART_NUMBER -> EcmDatabase.MatchMethod.PART_NUMBER
            relation == IdentityRelation.EXACT -> EcmDatabase.MatchMethod.ECM_ID_EXACT
            else -> EcmDatabase.MatchMethod.ECM_ID_PREFIX
        }
}

data class IdentityResolution(
    val status: IdentityMatchStatus,
    val identity: EcuIdentity,
    val model: EcmModel? = null,
    val candidates: List<IdentityCandidate> = emptyList(),
    val method: EcmDatabase.MatchMethod? = null,
    val quality: IdentityMatchQuality = IdentityMatchQuality.UNKNOWN,
    val explanation: String = ""
) {
    /** True when a single model may be shown, even if its revision bytes are incomplete. */
    val hasSingleModel: Boolean get() = model != null
}
