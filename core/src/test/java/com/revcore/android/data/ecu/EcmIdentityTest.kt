package com.revcore.android.data.ecu

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EcmIdentityTest {

    private fun model(
        id: String,
        partNumber: String,
        ecmId: String
    ) = EcmModel(
        id = id,
        partNumber = partNumber,
        ecmId = ecmId,
        bikeName = "Test $id"
    )

    @Before
    fun setUp() {
        EcmDatabase.useModelsForTest(
            listOf(
                model("A", "30400-K1AA-N11", "01037A0F03"),
                model("B", "30400-K1AA-N01", "01037A0F01"),
                model("N02", "30400-K1AA-N02", "01037A0F02"),
                model("C", "38770-K25-901", "0102140A01"),
                model("D", "30400-K2V-N01", "0012345678"),
                model("E1", "30400-DUP-001", "0100CE0D01"),
                model("E2", "30400-DUP-002", "0100CE0D01"),
                model("E3", "30400-DUP-003", "0100CE0D01")
            )
        )
    }

    @After
    fun tearDown() {
        EcmDatabase.clearModelsForTest()
    }

    @Test
    fun exactEcmId_resolvesOneModel() {
        val identity = EcuIdentity.fromIdentifier("01037A0F03", "test")
        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals("A", resolution.model?.id)
        assertEquals(EcmDatabase.MatchMethod.ECM_ID_EXACT, resolution.method)
    }

    @Test
    fun fourByteId_isOnlyACandidateAndNeverAPublishedModel() {
        val identity = EcuIdentity.fromIdentifier("01037A0F", "test")
        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.AMBIGUOUS, resolution.status)
        assertEquals(3, resolution.candidates.size)
        assertNull(resolution.model)

        // With only one 01037A0F* record the same response is a unique partial match,
        // but the incomplete identity still must not select a model.
        EcmDatabase.useModelsForTest(listOf(model("A", "30400-K1AA-N11", "01037A0F03")))
        val unique = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.PARTIAL_MATCH, unique.status)
        assertNull(unique.model)
        assertEquals(1, unique.candidates.size)
        assertEquals("A", unique.candidates.single().model.id)
    }

    @Test
    fun duplicateExactEcmId_isAmbiguousAndChoosesNothing() {
        val resolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier("0100CE0D01", "test")
        )
        assertEquals(IdentityMatchStatus.AMBIGUOUS, resolution.status)
        assertEquals(3, resolution.candidates.size)
        assertNull(resolution.model)
    }

    @Test
    fun leadingZeroesAreNotSilentlyStrippedToForceExactMatch() {
        val exact = EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("0012345678", "test"))
        assertEquals(IdentityMatchStatus.EXACT, exact.status)
        assertEquals("D", exact.model?.id)

        val shortened = EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("12345678", "test"))
        assertEquals(IdentityMatchStatus.PARTIAL_MATCH, shortened.status)
        assertNull(shortened.model)
        assertEquals("D", shortened.candidates.single().model.id)
        assertTrue(shortened.explanation.contains("incomplete", ignoreCase = true))
    }

    @Test
    fun partNumberMatchesOnPartNumberFieldOnly() {
        val resolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier("30400-K1AA-N11", "test")
        )
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals("A", resolution.model?.id)
        assertEquals(EcmDatabase.MatchMethod.PART_NUMBER, resolution.method)
    }

    @Test
    fun unknownIdentity_isNotFoundWithoutPlaceholderModel() {
        val resolution = EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("K1A", "test"))
        assertEquals(IdentityMatchStatus.NOT_FOUND, resolution.status)
        assertNull(resolution.model)
        assertTrue(resolution.candidates.isEmpty())
    }

    @Test
    fun asciiPayloadKeepsEveryCharacterAndDoesNotBecomeEcmId() {
        val identity = EcuIdentity.fromIdentifier(
            identifier = "K1A",
            source = "0x1A",
            rawFrame = byteArrayOf(0x72, 0x07, 0x1A, 0x4B, 0x31, 0x41, 0x37),
            payload = byteArrayOf(0x4B, 0x31, 0x41),
            extraction = "payload-ascii"
        )
        assertEquals("K1A", identity.ecuIdRaw)
        assertEquals("K1A", identity.ecuIdNormalized)
        assertNull(identity.ecmId)
        assertNotNull(identity.rawResponseHex)
        assertEquals("4B3141", identity.payloadHex)
        assertEquals("K1A", identity.payloadAscii)
    }

    @Test
    fun multiFieldPayloadKeepsCompleteDocumentedTokens() {
        val identity = EcuIdentity.fromIdentifier(
            identifier = "PART:30400-K1AA-N11 ECM:01037A0F03",
            source = "0x1A"
        )
        assertEquals("30400-K1AA-N11", identity.partNumber)
        assertEquals("01037A0F03", identity.ecmId)
        assertTrue(identity.alternateIdentifiers.contains("30400-K1AA-N11"))
        assertTrue(identity.alternateIdentifiers.contains("01037A0F03"))

        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals("A", resolution.model?.id)
        assertEquals(IdentityMatchQuality.EXACT, resolution.quality)
    }

    @Test
    fun continuousHexIdentifierIsNeverSplitIntoTokens() {
        assertTrue(EcuIdentity.structuredTokens("01037A0F03").isEmpty())
        assertTrue(EcuIdentity.structuredTokens("000001037A0F02").isEmpty())
        assertEquals(
            listOf("30400-K1AA-N11", "01037A0F03"),
            EcuIdentity.structuredTokens("30400-K1AA-N11/01037A0F03")
        )
    }

    @Test
    fun matchQualityNeverUpgradesUnverifiedRecords() {
        val verified = EcmModel(
            id = "V",
            partNumber = "30400-K1AA-N11",
            ecmId = "01037A0F03",
            bikeName = "Verified",
            isVerifiedDiagnostic = true
        )
        EcmDatabase.useModelsForTest(listOf(verified))
        val verifiedResolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier("01037A0F03", "test")
        )
        assertEquals(IdentityMatchQuality.VERIFIED, verifiedResolution.quality)

        EcmDatabase.useModelsForTest(listOf(model("A", "30400-K1AA-N11", "01037A0F03")))
        assertEquals(
            IdentityMatchQuality.EXACT,
            EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("01037A0F03", "test")).quality
        )
        assertEquals(
            IdentityMatchQuality.CANDIDATE,
            EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("01037A0F", "test")).quality
        )

        EcmDatabase.useModelsForTest(
            listOf(model("E1", "30400-DUP-001", "0100CE0D01"), model("E2", "30400-DUP-002", "0100CE0D01"))
        )
        assertEquals(
            IdentityMatchQuality.AMBIGUOUS,
            EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("0100CE0D01", "test")).quality
        )
    }

    @Test
    fun regressionRawResponseCanonicalizesToTheDocumentedEcmId() {
        // Regression evidence from the communication report: the ECU replied with
        // payload 00 00 01 03 7A 0F 02. The selector echo is not part of the ECM ID,
        // and the trailing revision byte 02 must not be dropped.
        val identity = EcuIdentity.fromIdentifier("000001037A0F02", "legacy")
        assertEquals("000001037A0F02", identity.ecuIdRaw)
        assertEquals("01037A0F02", identity.canonicalEcuId)
        assertEquals("01037A0F02", identity.ecmId)
        assertNull(identity.partNumber)
        assertEquals("0000", identity.protocolPrefixHex)
        assertTrue(identity.alternateIdentifiers.isEmpty())

        val resolution = EcmDatabase.resolveIdentity(identity)
        assertEquals(IdentityMatchStatus.EXACT, resolution.status)
        assertEquals(IdentityMatchQuality.EXACT, resolution.quality)
        assertEquals("N02", resolution.model?.id)
        assertEquals("30400-K1AA-N02", resolution.model?.partNumber)
    }

    @Test
    fun readableButUnregisteredShapedIdIsUnknownNotUnsupported() {
        val resolution = EcmDatabase.resolveIdentity(
            EcuIdentity.fromIdentifier("0103FFFF99", "test")
        )
        assertEquals(IdentityMatchStatus.NOT_FOUND, resolution.status)
        assertEquals(IdentityMatchQuality.UNKNOWN, resolution.quality)
    }

    @Test
    fun unreadableIdentityIsUnsupported() {
        val resolution = EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("K1A", "test"))
        assertEquals(IdentityMatchQuality.UNSUPPORTED, resolution.quality)
    }
}
