package com.revcore.android.data.ecu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EcmIdentityMatcherTest {

    @Test
    fun normalizeStripsSeparatorsAndCase() {
        assertEquals("K2FN01", EcmIdentityMatcher.normalize(" k2f-n01 "))
        assertEquals("38770K2FN01", EcmIdentityMatcher.normalize("38770-K2F-N01"))
    }

    @Test
    fun canonicalEcmIdNeverStripsLeadingZeroes() {
        // No evidence established that "00" is padding, so it is retained.
        assertEquals("000104080F01", EcmIdentityMatcher.canonicalEcmId("000104080F01"))
        assertEquals("00000104080F", EcmIdentityMatcher.canonicalEcmId("00000104080F"))
        assertEquals("0104080F01", EcmIdentityMatcher.canonicalEcmId("0104080F01"))
    }

    @Test
    fun leadingZeroDifferenceIsOnlyABoundedPartialRelation() {
        assertEquals(
            IdentityRelation.SUFFIX,
            EcmIdentityMatcher.relation("0104080F01", "000104080F01")
        )
    }

    @Test
    fun exactEcmIdMatches() {
        assertTrue(EcmIdentityMatcher.ecmIdMatches("K2FN01", "k2f-n01"))
    }

    @Test
    fun eightCharResponseMatchesTenCharDatabaseId() {
        // ECU responses often carry 8 characters while the reference ID has 10.
        assertTrue(EcmIdentityMatcher.ecmIdMatches("0104080F", "0104080F01"))
    }

    @Test
    fun rejectsLooseSubstringMatch() {
        assertFalse(EcmIdentityMatcher.ecmIdMatches("0104080", "0104080F01"))
        assertFalse(EcmIdentityMatcher.ecmIdMatches("0104080F", "0104080F01AB"))
    }

    @Test
    fun rejectsEmptyValues() {
        assertFalse(EcmIdentityMatcher.ecmIdMatches("", "K2FN01"))
        assertFalse(EcmIdentityMatcher.ecmIdMatches("K2FN01", ""))
    }

    @Test
    fun partNumberRequiresExactNormalizedMatch() {
        assertTrue(EcmIdentityMatcher.partNumberMatches("38770-k2f-n01", "38770K2FN01"))
        assertFalse(EcmIdentityMatcher.partNumberMatches("38770K2F", "38770K2FN01"))
        assertFalse(EcmIdentityMatcher.partNumberMatches("K2FN01", "38770K2FN01"))
    }

    @Test
    fun unverifiedModelDefaultsDoNotFabricateCalibration() {
        val model = EcmModel(id = "K2FN01", partNumber = "38770K2FN01")
        assertEquals(0, model.stockRevLimit)
        assertEquals(0, model.maxSafeRevLimit)
        assertEquals(0, model.recommendedRevLimit)
        assertEquals(0, model.revLimiterOffset)
        assertEquals(0, model.fuelMapOffset)
        assertEquals(0, model.ignitionMapOffset)
        assertFalse(model.isVerifiedDiagnostic)
        assertFalse(model.isVerifiedRemap)
        assertFalse(model.hasRecommendedRevLimit)
    }
}
