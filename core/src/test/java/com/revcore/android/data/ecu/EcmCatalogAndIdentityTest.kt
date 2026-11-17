package com.revcore.android.data.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcmCatalogAndIdentityTest {
    private fun model(
        id: String = "TEST-1",
        part: String = "38770-TEST-001",
        ecmId: String = "0109990F01",
        name: String = "Vario 150",
        country: String = "Indonesia",
        cc: Double = 150.0
    ) = EcmModel(
        id = id,
        partNumber = part,
        ecmId = ecmId,
        bikeName = name,
        country = country,
        displacementCc = cc
    )

    @Test
    fun activeCatalogRequiresIndonesiaAndDisplacementEvidence() {
        assertTrue(model().isActiveCatalogEligible)
        assertFalse(model(country = "Thailand").isActiveCatalogEligible)
        assertFalse(model(cc = 500.0).isActiveCatalogEligible)
        assertFalse(model(country = "", cc = 0.0).isActiveCatalogEligible)
        assertFalse(model(name = "CBR 500 R", cc = 150.0).isActiveCatalogEligible)
    }

    @Test
    fun fallbackRecordsWithoutRegionAreNotInActiveCatalog() {
        assertTrue(EcmDatabase.activeCatalogModels().isEmpty())
    }

    @Test
    fun idAndPartNumberMatchesCarryExplicitConfidence() {
        val byId = EcmDatabase.identify("SAMPLE-ECM-001")
        assertNotNull(byId)
        assertEquals(EcmDatabase.MatchMethod.DB_ID, byId!!.method)
        assertEquals(100, byId.method.confidence)

        val byPart = EcmDatabase.identify("00000-SAMPLE-001")
        assertNotNull(byPart)
        assertEquals(EcmDatabase.MatchMethod.PART_NUMBER, byPart!!.method)

        val unknown = EcmDatabase.identify("NOT-A-REAL-ECU")
        assertNull(unknown)
    }

    @Test
    fun ecmIdTruncationIsNeverAnIdentification() {
        // A strict prefix is a bounded diagnostic candidate only; it must not
        // publish a model or satisfy the identification step.
        val truncated = EcmDatabase.identify("0103790F")
        assertNull(truncated)
        assertNull(EcmDatabase.findByEcmId("0103790F"))

        val resolution = EcmDatabase.resolveIdentity(EcuIdentity.fromIdentifier("0103790F", "test"))
        assertTrue(
            resolution.status == IdentityMatchStatus.PARTIAL_MATCH ||
                resolution.status == IdentityMatchStatus.NOT_FOUND ||
                resolution.status == IdentityMatchStatus.AMBIGUOUS
        )
        assertNull(resolution.model)

        // Shorter than the 8-character core, or a differing tail, is not a match.
        assertNull(EcmDatabase.identify("010379"))
        assertNull(EcmDatabase.identify("0103790F09"))
    }
}
