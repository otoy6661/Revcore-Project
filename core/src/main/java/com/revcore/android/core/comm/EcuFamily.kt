package com.revcore.android.core.comm

/**
 * Honda ECU families kept distinct on purpose. The reference protocol enum
 * contains separate variants even where the implementation is shared; merging
 * them here would hide which operations are actually evidenced per family.
 */
enum class HondaEcuFamily(
    val displayName: String,
    val referenceProtocolKind: String
) {
    KEIHIN_H99B_KLINE("Honda Keihin H99B K-Line", "HondaKeihinH99bKline"),
    KEIHIN_KWP_KLINE("Honda Keihin KWP K-Line", "HondaKeihinKwpKline"),
    SHINDENGEN_H99B_KLINE("Honda Shindengen H99B K-Line", "HondaShindengenH99bKline"),
    DENSO_H99B_KLINE("Honda Denso H99B K-Line", "HondaDensoH99bKline"),
    GENERIC_H99B_KLINE("Honda generic H99B K-Line", "HondaGenericH99bKline"),
    UNKNOWN("Honda K-Line (family belum terverifikasi)", "-");

    val isVerified: Boolean get() = this != UNKNOWN
}

/**
 * Family resolution evidence. Detection is not identification: a response
 * header selects a profile for routing, never a vehicle model claim.
 */
enum class FamilyEvidence(val label: String, val status: EvidenceStatus) {
    NONE("belum terdeteksi", EvidenceStatus.UNRESOLVED),
    RESPONSE_HEADER("header respons 0x7E/0x7C", EvidenceStatus.HYPOTHESIS),
    IDENTIFIER_DB("ID cocok database", EvidenceStatus.RECONSTRUCTED)
}

/**
 * The response-header mapping (0x7E Keihin, 0x7C Shindengen) follows the header
 * constants already carried by RevCore. It stays a hypothesis: the reference
 * binary was not observed emitting those headers to a real ECU.
 */
object EcuFamilyResolver {
    fun fromResponseHeader(header: Int): Pair<HondaEcuFamily, FamilyEvidence>? = when (header and 0xFF) {
        0x7E -> HondaEcuFamily.KEIHIN_H99B_KLINE to FamilyEvidence.RESPONSE_HEADER
        0x7C -> HondaEcuFamily.SHINDENGEN_H99B_KLINE to FamilyEvidence.RESPONSE_HEADER
        else -> null
    }
}
