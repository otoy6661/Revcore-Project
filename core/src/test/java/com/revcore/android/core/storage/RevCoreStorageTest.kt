package com.revcore.android.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevCoreStorageTest {

    @Test
    fun sanitizesVehicleNamesForFileSystemUse() {
        assertEquals("Honda_Vario", RevCoreStorage.sanitizeName("Honda Vario (160)"))
        assertEquals("ECU", RevCoreStorage.sanitizeName("   "))
        assertEquals("A_B_C", RevCoreStorage.sanitizeName("A/B:C"))
    }

    @Test
    fun buildsBackupNameWithIdentityAndDateSuffix() {
        val name = RevCoreStorage.buildBackupFileName("Honda Beat", "1234567890")
        assertTrue(name.startsWith("Honda_Beat_1234567890_Backup_"))
        assertTrue(name.endsWith(".bin"))
    }

    @Test
    fun buildsMapAndExportNamesWithExpectedExtensions() {
        assertTrue(RevCoreStorage.buildMapFileName("Vario 160").endsWith(".map"))
        assertTrue(RevCoreStorage.buildExportFileName("log.txt").startsWith("log_Export_"))
        assertTrue(RevCoreStorage.buildExportFileName("log.txt").endsWith(".txt"))
    }
}
