package com.revcore.android.data.ecu

import com.revcore.android.core.comm.HondaFrameCodec
import com.revcore.android.core.protocol.HondaIdentifierParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EcuIdCanonicalizerTest {

    private fun model(id: String, part: String, ecmId: String) = EcmModel(
        id = id,
        partNumber = part,
        ecmId = ecmId,
        bikeName = "Test $id"
    )

    @Before
    fun setUp() {
        EcmDatabase.useModelsForTest(
            listOf(
                model("K1AA-N01", "30400-K1AA-N01", "01037A0F01"),
                model("K1AA-N02", "30400-K1AA-N02", "01037A0F02"),
                model("K1AA-N03", "30400-K1AA-N03", "01037A0F03"),
                model("K2F-N01", "38770-K2F-N01", "0104080F01"),
                model("MKA-D71", "38770-MKA-D71", "0801DF0101"),
                model("HR6-MO2", "38770-HR6-MO2", "0D02FC0401"),
                model("UNKNOWN-PART", "?????-???-???", "05003E0402"),
                model("MGM-G11", "38770-MGM-G11", "9999999999"),
                model("K97F-T", "30400-K97F", "010102990D01")
            )
        )
    }

    @After
    fun tearDown() {
        EcmDatabase.clearModelsForTest()
    }

    @Test
    fun capturedBeatFrameUsesSelectorPrefixAndCompleteField() {
        val payload = byteArrayOf(0x00, 0x00, 0x01, 0x03, 0x7A, 0x0F, 0x02)
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            payload = payload,
            layout = IdResponseLayout(2, 5, "test", "selector + ECM ID"),
            knownCanonicalIds = EcmDatabase.knownEcmIds()
        )
        assertEquals("000001037A0F02", extraction.rawId)
        assertEquals("01037A0F02", extraction.canonicalId)
        assertEquals("0000", extraction.protocolPrefixHex)
        assertNull(extraction.trailingHex)
        assertEquals("protocol-layout", extraction.method)
    }

    @Test
    fun capturedBeatFrameWithoutLayoutStillSelectsTrailingCompleteField() {
        val payload = byteArrayOf(0x00, 0x00, 0x01, 0x03, 0x7A, 0x0F, 0x02)
        val extraction = EcuIdCanonicalizer.canonicalizePayload(payload)
        assertEquals("000001037A0F02", extraction.rawId)
        assertEquals("01037A0F02", extraction.canonicalId)
        assertEquals("0000", extraction.protocolPrefixHex)
        assertEquals("trailing-field", extraction.method)
    }

    @Test
    fun trailingBytesAreNeverDroppedOrShiftedIntoTheId() {
        val payload = byteArrayOf(0x00, 0x00, 0x01, 0x03, 0x7A, 0x0F, 0x02, 0xAA.toByte())
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            payload = payload,
            layout = IdResponseLayout(2, 5, "test", "selector + ECM ID + trailing status")
        )
        assertEquals("01037A0F02", extraction.canonicalId)
        assertEquals("0000", extraction.protocolPrefixHex)
        assertEquals("AA", extraction.trailingHex)
    }

    @Test
    fun rawStringVariantsCanonicalizeToN01N02N03() {
        val expected = mapOf(
            "000001037A0F01" to "01037A0F01",
            "000001037A0F02" to "01037A0F02",
            "000001037A0F03" to "01037A0F03"
        )
        for ((raw, canonical) in expected) {
            val extraction = EcuIdCanonicalizer.canonicalizeRawString(
                raw,
                EcmDatabase.knownEcmIds()
            )
            assertEquals(canonical, extraction.canonicalId)
            assertEquals(raw, extraction.rawId)
            assertEquals("0000", extraction.protocolPrefixHex)
        }
    }

    @Test
    fun unknownButWellFormedIdStaysUnknownWithoutFalseMatch() {
        val extraction = EcuIdCanonicalizer.canonicalizeRawString("000001037A0F99")
        assertEquals("01037A0F99", extraction.canonicalId)

        val identity = EcuIdentity.fromIdentifier(
            identifier = "000001037A0F99",
            source = "test",
            canonicalizationResult = extraction
        )
        assertEquals("000001037A0F99", identity.ecuIdRaw)
        assertEquals("01037A0F99", identity.canonicalEcuId)
        assertEquals("01037A0F99", identity.ecmId)

        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.NOT_FOUND, resolution.status)
        assertEquals(IdentityMatchQuality.UNKNOWN, resolution.quality)
        assertNull(resolution.model)
        assertTrue(resolution.candidates.isEmpty())
    }

    @Test
    fun truncatedPayloadDoesNotBecomeACanonicalId() {
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            byteArrayOf(0x00, 0x00, 0x01, 0x03)
        )
        assertNull(extraction.canonicalId)
        assertEquals("unparsed", extraction.method)
    }

    @Test
    fun truncatedSixByteCandidateDoesNotMatchWithoutExactDatabaseEntry() {
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            byteArrayOf(0x00, 0x00, 0x01, 0x03, 0x7A, 0x0F)
        )
        assertEquals("000001037A0F", extraction.canonicalId)

        val resolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier(
                identifier = extraction.rawId,
                source = "test",
                canonicalizationResult = extraction
            )
        )
        assertEquals(IdentityMatchStatus.NOT_FOUND, resolution.status)
        assertNull(resolution.model)
    }

    @Test
    fun malformedHexAndMissingPayloadAreRejected() {
        val odd = EcuIdCanonicalizer.canonicalizeRawString("000001037A0F0")
        assertNull(odd.canonicalId)
        assertEquals("malformed-hex", odd.method)

        val nonHex = EcuIdCanonicalizer.canonicalizeRawString("01037A0F0Z")
        assertNull(nonHex.canonicalId)
        assertEquals("not-hex", nonHex.method)

        val empty = EcuIdCanonicalizer.canonicalizePayload(ByteArray(0))
        assertNull(empty.canonicalId)
        assertEquals("empty-payload", empty.method)
    }

    @Test
    fun asciiTextIsPreservedButNotForcedIntoAHexId() {
        val extraction = EcuIdCanonicalizer.canonicalizePayload("K1A".toByteArray(Charsets.US_ASCII))
        assertEquals("K1A", extraction.rawId)
        assertEquals("K1A", extraction.rawAscii)
        assertNull(extraction.canonicalId)
    }

    @Test
    fun asciiHexPayloadIsCanonicalized() {
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            "01037A0F02".toByteArray(Charsets.US_ASCII)
        )
        assertEquals("01037A0F02", extraction.rawId)
        assertEquals("01037A0F02", extraction.canonicalId)
        assertEquals("ascii-whole-payload", extraction.method)
    }

    @Test
    fun sixByteDatabaseFormatIsSupported() {
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            byteArrayOf(0x01, 0x01, 0x02, 0x99.toByte(), 0x0D, 0x01)
        )
        assertEquals("010102990D01", extraction.canonicalId)
        assertEquals("whole-payload", extraction.method)

        val resolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier(
                identifier = extraction.rawId,
                source = "test",
                canonicalizationResult = extraction
            )
        )
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals("K97F-T", resolution.model?.id)
    }

    @Test
    fun capturedFrameFromCodecProducesN02() {
        val frame = HondaFrameCodec.buildBaseline(0x72, 0x00, 0x00, 0x01, 0x03, 0x7A, 0x0F, 0x02)
        val parsed = HondaIdentifierParser.parse(frame)
        assertNotNull(parsed)
        val extraction = EcuIdCanonicalizer.canonicalizePayload(
            payload = parsed!!.payload,
            layout = IdResponseLayout(2, 5, "test", "captured frame"),
            knownCanonicalIds = EcmDatabase.knownEcmIds()
        )
        assertEquals("01037A0F02", extraction.canonicalId)

        val identity = EcuIdentity.fromIdentifier(
            identifier = parsed.identifier,
            source = "0x72/0x00",
            rawFrame = frame,
            payload = parsed.payload,
            canonicalizationResult = extraction
        )
        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals("K1AA-N02", resolution.model?.id)
        assertEquals("30400-K1AA-N02", resolution.model?.partNumber)
    }

    @Test
    fun everyRepresentedDatabaseIdFormatMatchesExactly() {
        val ids = listOf(
            "0104080F01" to "K2F-N01",
            "0801DF0101" to "MKA-D71",
            "0D02FC0401" to "HR6-MO2",
            "05003E0402" to "UNKNOWN-PART",
            "9999999999" to "MGM-G11",
            "010102990D01" to "K97F-T"
        )
        for ((ecmId, expectedId) in ids) {
            val identity = EcuIdentity.fromIdentifier(ecmId, "format-regression")
            val resolution = EcmDatabase.resolveIdentity(identity)
            assertEquals("$ecmId should match", IdentityMatchStatus.EXACT, resolution.status)
            assertEquals(expectedId, resolution.model?.id)
        }
    }

    @Test
    fun prefixedPayloadsForEveryFormatMatchExactly() {
        val ids = listOf(
            "0104080F01",
            "0801DF0101",
            "0D02FC0401",
            "05003E0402",
            "9999999999"
        )
        for (ecmId in ids) {
            val idBytes = ecmId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val payload = byteArrayOf(0x00, 0x00) + idBytes
            val extraction = EcuIdCanonicalizer.canonicalizePayload(
                payload = payload,
                knownCanonicalIds = EcmDatabase.knownEcmIds()
            )
            assertEquals(ecmId, extraction.canonicalId)
            val resolution = EcmDatabase.resolveIdentity(
                EcuIdentity.fromIdentifier(
                    identifier = extraction.rawId,
                    source = "format-regression",
                    canonicalizationResult = extraction
                )
            )
            assertEquals(IdentityMatchStatus.EXACT, resolution.status)
            assertEquals(ecmId, resolution.model?.ecmId)
        }
    }
}
