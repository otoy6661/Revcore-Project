package com.revcore.android.core.comm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuProfilesTest {

    @Test
    fun familiesRemainDistinct() {
        assertNotSame(HondaEcuFamily.KEIHIN_H99B_KLINE, HondaEcuFamily.SHINDENGEN_H99B_KLINE)
        assertNotSame(HondaEcuProfiles.keihinH99b, HondaEcuProfiles.shindengenH99b)
        assertNotSame(HondaEcuProfiles.keihinKwp, HondaEcuProfiles.keihinH99b)
        assertEquals(
            HondaEcuFamily.SHINDENGEN_H99B_KLINE,
            HondaEcuProfiles.shindengenH99b.family
        )
    }

    @Test
    fun genericBaseline_carriesTheRestoredCatalog() {
        val profile = HondaEcuProfiles.genericBaseline
        assertEquals(4, profile.identification.size)
        assertEquals(3, profile.dtcRead.size)
        assertEquals(3, profile.dtcClear.size)
        assertEquals(0, profile.liveDataTables.size)
        assertEquals(EvidenceStatus.RECONSTRUCTED, profile.identificationEvidence)

        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0x04, 0x72, 0x8C.toByte()),
            profile.testerPresent!!.bytes
        )
        assertArrayEquals(
            byteArrayOf(0x72, 0x05, 0x71, 0x00, 0x18),
            profile.partNumber71!!.bytes
        )
    }

    @Test
    fun canonicalFramesHaveValidChecksums() {
        val profile = HondaEcuProfiles.genericBaseline
        val canonical = profile.identification + profile.dtcRead + profile.dtcClear
        for (request in canonical) {
            assertTrue(
                "frame ${request.id} must carry a valid Honda checksum",
                HondaFrameCodec.isWellFormed(request.bytes)
            )
        }
    }

    /**
     * The built-in live-data catalogue is intentionally empty: its previous
     * frames and constants were withheld because their usage rights are
     * unresolved. No live-data request may be transmitted from the default
     * profile until a developer supplies an owned table.
     */
    @Test
    fun liveDataCatalogIsEmptyUntilRightsAreResolved() {
        val profile = HondaEcuProfiles.genericBaseline
        assertTrue(profile.liveDataTables.isEmpty())
        assertTrue(profile.liveExtensionTables.isEmpty())
        assertFalse(profile.hasLiveDataCatalog)
        assertNull(profile.liveProbe)
        assertTrue(HondaLiveDataProfiles.tables.isEmpty())
        assertNull(HondaLiveDataProfiles.forTable("7A"))
        assertNull(HondaLiveDataProfiles.baseline)
    }

    @Test
    fun shindengenProfile_doesNotBorrowKeihinOrBaselineFrames() {
        val shindengen = HondaEcuProfiles.shindengenH99b
        assertTrue(shindengen.identification.isEmpty())
        assertTrue(shindengen.dtcRead.isEmpty())
        assertTrue(shindengen.dtcClear.isEmpty())
        assertTrue(shindengen.liveDataTables.isEmpty())
        assertEquals(EvidenceStatus.UNRESOLVED, shindengen.identificationEvidence)
        assertNull(shindengen.testerPresent)
    }

    @Test
    fun keihinProfiles_doNotInventFrames() {
        for (profile in listOf(HondaEcuProfiles.keihinH99b, HondaEcuProfiles.keihinKwp)) {
            assertFalse(profile.hasIdentificationCatalog)
            assertFalse(profile.hasDtcCatalog)
            assertFalse(profile.hasLiveDataCatalog)
            assertEquals(EvidenceStatus.UNRESOLVED, profile.identificationEvidence)
        }
    }

    @Test
    fun familyRouting_preservesProfilesAndFallsBackToBaselineOnlyWhenUnknown() {
        assertSame(
            HondaEcuProfiles.keihinH99b,
            HondaEcuProfiles.forFamily(HondaEcuFamily.KEIHIN_H99B_KLINE)
        )
        assertSame(
            HondaEcuProfiles.shindengenH99b,
            HondaEcuProfiles.forFamily(HondaEcuFamily.SHINDENGEN_H99B_KLINE)
        )
        assertSame(
            HondaEcuProfiles.genericBaseline,
            HondaEcuProfiles.forFamily(HondaEcuFamily.UNKNOWN)
        )
        assertSame(
            HondaEcuProfiles.genericBaseline,
            HondaEcuProfiles.forFamily(HondaEcuFamily.GENERIC_H99B_KLINE)
        )
    }

    @Test
    fun requestShortTags_matchBaselineLogLabels() {
        val profile = HondaEcuProfiles.genericBaseline
        assertEquals("74/01", profile.dtcRead[0].shortTag)
        assertEquals("60/01", profile.dtcClear[0].shortTag)
        assertEquals("74/02", profile.dtcClear[1].shortTag)
    }

    @Test
    fun evidenceInventory_preservesAllStatusesIncludingUnresolved() {
        val statuses = CommEvidence.entries.map { it.status }.toSet()
        assertTrue(statuses.contains(EvidenceStatus.BINARY_EVIDENCE))
        assertTrue(statuses.contains(EvidenceStatus.XREF_MAPPED))
        assertTrue(statuses.contains(EvidenceStatus.RECONSTRUCTED))
        assertTrue(statuses.contains(EvidenceStatus.HYPOTHESIS))
        assertTrue(statuses.contains(EvidenceStatus.UNRESOLVED))
        assertEquals(EvidenceStatus.BINARY_EVIDENCE, CommEvidence.highestStatus())
        assertEquals(
            EvidenceStatus.UNRESOLVED,
            CommEvidence.byId("shindengen.frames")?.status
        )
    }
}
