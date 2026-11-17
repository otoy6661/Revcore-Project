package com.revcore.android.core.comm

/**
 * Provenance status for every reconstructed communication element.
 *
 * Nothing in this layer may be promoted above the highest status its evidence
 * actually supports. Values below are carried into the UI/export inventory so a
 * user can distinguish binary-proven behaviour from a hypothesis.
 */
enum class EvidenceStatus(val label: String) {
    BINARY_EVIDENCE("binary evidence"),
    XREF_MAPPED("xref mapped"),
    RECONSTRUCTED("reconstructed"),
    HYPOTHESIS("hypothesis"),
    UNRESOLVED("unresolved")
}

data class CommEvidenceEntry(
    val id: String,
    val title: String,
    val status: EvidenceStatus,
    val binaryAddress: String?,
    val detail: String
)

/**
 * Registry of what the reference analysis pack (AArch64 image, SHA-256 prefix
 * `1acc5e50`) actually proves, what was reconstructed from the restored RevCore
 * baseline, and what remains open. Addresses are file-relative virtual
 * addresses inside the AArch64 `.text`/`.rodata` image.
 */
object CommEvidence {
    const val REFERENCE_LIBRARY = "reference analysis image (AArch64)"
    const val REFERENCE_SHA256_PREFIX = "1acc5e50"

    val entries: List<CommEvidenceEntry> = listOf(
        CommEvidenceEntry(
            id = "checksum.additive8",
            title = "Honda additive 8-bit two's-complement checksum",
            status = EvidenceStatus.BINARY_EVIDENCE,
            binaryAddress = "0x17a61ec",
            detail = "Routine sums every preceding byte and negates the low 8 bits (neg w0, w8). " +
                "Frame finalizer 0x17a620c writes that value into the last byte over len-1 bytes."
        ),
        CommEvidenceEntry(
            id = "frame.lengthLimit",
            title = "K-Line frame body limit 254 bytes (0xFE)",
            status = EvidenceStatus.BINARY_EVIDENCE,
            binaryAddress = "~0x17a620c",
            detail = "Error string 'K-line frame requires header and data' sits beside the 0xFE length cap; " +
                "a second guard rejects frames over 2048 bytes."
        ),
        CommEvidenceEntry(
            id = "frame.headerSelection",
            title = "Request header selection 0x7D / 0x7B",
            status = EvidenceStatus.BINARY_EVIDENCE,
            binaryAddress = "0x17a5e04",
            detail = "Request helper picks 0x7D when the argument flag is non-zero, else 0x7B, then emits " +
                "the caller bytes followed by 0x01 0x03. A reply must be 9 bytes with at least 2 payload bytes."
        ),
        CommEvidenceEntry(
            id = "init.baudFallback",
            title = "Fast-init retry falls back to 10400 bps",
            status = EvidenceStatus.BINARY_EVIDENCE,
            binaryAddress = "0x17a5a30",
            detail = "On 'PGMFI fast init failed; continuing identification:' the wrapper compares a state " +
                "byte to 2 and selects 0x28A0 (10400) before re-probing."
        ),
        CommEvidenceEntry(
            id = "init.wake",
            title = "Host BREAK wake pulse LOW 70 ms / HIGH 130 ms",
            status = EvidenceStatus.RECONSTRUCTED,
            binaryAddress = null,
            detail = "Preserved from the restored RevCore baseline build. Host-side timing only; the binary " +
                "does not expose a literal 70/130 ms constant pair."
        ),
        CommEvidenceEntry(
            id = "h99b.sessionProbe",
            title = "Generic H99B KWP session probe",
            status = EvidenceStatus.XREF_MAPPED,
            binaryAddress = "0x178d944",
            detail = "The generic H99B implementation probes KWP sessions in stages 0..3, logs a " +
                "session probe message, requires reply length >= 4 and first payload byte 0x07. " +
                "Exact session request bytes remain unresolved."
        ),
        CommEvidenceEntry(
            id = "h99b.keihinKwp",
            title = "Dedicated Keihin KWP-over-K-Line module",
            status = EvidenceStatus.XREF_MAPPED,
            binaryAddress = "0x178ba68",
            detail = "A dedicated Keihin KWP-over-K-Line module exists in the reference image. Frame bytes " +
                "and session keys are not yet reconstructed."
        ),
        CommEvidenceEntry(
            id = "h99b.shindengen",
            title = "Shindengen H99B implementation",
            status = EvidenceStatus.HYPOTHESIS,
            binaryAddress = null,
            detail = "The protocol enum contains HondaShindengenH99bKline, but no Shindengen-specific source " +
                "module was found; it appears to share the generic H99B engine. Kept as a distinct family " +
                "profile; no Shindengen request frames are invented."
        ),
        CommEvidenceEntry(
            id = "ftdi.androidTransfer",
            title = "FTDI Android bulk transfer/claim behaviour",
            status = EvidenceStatus.BINARY_EVIDENCE,
            binaryAddress = "0x17703b8 / 0x17930d8",
            detail = "JNI bulkTransfer signature (UsbEndpoint, byte[], int, int) and claimInterface discovery " +
                "of bulk IN/OUT endpoints; timeouts converted us -> ms and clamped to Int.MAX_VALUE."
        ),
        CommEvidenceEntry(
            id = "baseline.requestCatalog",
            title = "Restored baseline PGM-FI request catalog (0x72 header)",
            status = EvidenceStatus.RECONSTRUCTED,
            binaryAddress = null,
            detail = "Frames such as FE 04 72 8C and 72 05 71 00 18 come from the restored RevCore baseline " +
                "build, not from literal bytes in the reference image. They remain family-neutral probes."
        ),
        CommEvidenceEntry(
            id = "shindengen.frames",
            title = "Shindengen-specific identification / live-data / DTC frames",
            status = EvidenceStatus.UNRESOLVED,
            binaryAddress = null,
            detail = "No Shindengen-specific frames are evidenced. The profile is intentionally empty rather " +
                "than reusing Keihin frames, so no unverified operation is transmitted."
        ),
        CommEvidenceEntry(
            id = "keihin.frames",
            title = "Keihin-specific frame bytes from the reference binary",
            status = EvidenceStatus.UNRESOLVED,
            binaryAddress = null,
            detail = "No literal Honda frames were found in the reference image. Keihin H99B/KWP modules are " +
                "located, but their exact request bytes and keys are still open."
        )
    )

    fun byId(id: String): CommEvidenceEntry? = entries.firstOrNull { it.id == id }

    fun highestStatus(): EvidenceStatus = entries
        .map { it.status }
        .minByOrNull { it.ordinal }
        ?: EvidenceStatus.UNRESOLVED
}
