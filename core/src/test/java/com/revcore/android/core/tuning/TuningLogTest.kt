package com.revcore.android.core.tuning

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TuningLogTest {

    @Before
    fun setUp() {
        TuningLogStore.resetSessionForTests()
    }

    @After
    fun tearDown() {
        TuningLogStore.resetSessionForTests()
    }

    @Test
    fun percentRequiresARealTotal() {
        assertNull(TuningProgressMath.percent(0, 0))
        assertNull(TuningProgressMath.percent(-1, 10))
        assertEquals(50, TuningProgressMath.percent(50, 100))
        assertEquals(100, TuningProgressMath.percent(120, 100))
    }

    @Test
    fun terminalEventCannotBeRewritten() {
        TuningLogStore.startSessionIfNeeded("mulai")
        val operation = TuningLogStore.beginOperation(
            operationName = "uji",
            category = TuningLogCategory.VALIDATION,
            stage = "mulai",
            message = "operasi dimulai"
        )
        val first = TuningLogStore.finishOperation(
            operationId = operation,
            status = TuningLogStatus.SUCCESS,
            stage = "selesai",
            message = "berhasil",
            overallPercent = 100
        )
        val second = TuningLogStore.finishOperation(
            operationId = operation,
            status = TuningLogStatus.FAILED,
            stage = "selesai",
            message = "gagal"
        )

        assertEquals(TuningLogStatus.SUCCESS, first?.status)
        assertNull(second)
        assertEquals(100, TuningLogStore.latestProgress.value?.overallPercent)
        assertTrue(TuningLogStore.exportSessionText().contains("OPERATION PROGRESS"))
    }
}
