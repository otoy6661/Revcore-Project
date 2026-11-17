package com.revcore.android.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapDocumentTest {

    private val validJson = """
        {
          "format": "revcore-synthetic-map",
          "version": 1,
          "id": "SAMPLE-MAP-001",
          "status": "REFERENCE",
          "units": {"rpm": "rpm", "load": "%", "value": "ms"},
          "axes": {"rpm": [1000, 2000, 3000], "load": [0, 50, 100]},
          "cells": [
            [1.0, 1.1, 1.2],
            [1.2, 1.3, 1.4],
            [1.4, 1.5, 1.6]
          ],
          "notes": "Contoh sintetis untuk pengujian."
        }
    """.trimIndent()

    @Test
    fun parsesValidDocument() {
        val result = MapDocument.parse(validJson)
        assertTrue(result is MapParseResult.Success)
        val document = (result as MapParseResult.Success).document
        assertEquals("SAMPLE-MAP-001", document.id)
        assertEquals(3, document.rows)
        assertEquals(3, document.columns)
        assertEquals(1.3, document.sampleBilinear(2000.0, 50.0)!!, 1e-9)
    }

    @Test
    fun interpolatesBilinearInsideAxes() {
        val document = (MapDocument.parse(validJson) as MapParseResult.Success).document
        assertEquals(1.15, document.sampleBilinear(1500.0, 25.0)!!, 1e-9)
    }

    @Test
    fun refusesToExtrapolateOutsideAxes() {
        val document = (MapDocument.parse(validJson) as MapParseResult.Success).document
        assertNull(document.sampleBilinear(500.0, 25.0))
        assertNull(document.sampleBilinear(1500.0, 120.0))
    }

    @Test
    fun rejectsNonPublicId() {
        val result = MapDocument.parse(validJson.replace("SAMPLE-MAP-001", "REAL-MAP-001"))
        assertTrue(result is MapParseResult.Failure)
        assertTrue((result as MapParseResult.Failure).issues.any { it.contains("SAMPLE-") })
    }

    @Test
    fun rejectsNonIncreasingAxis() {
        val result = MapDocument.parse(validJson.replace("[1000, 2000, 3000]", "[1000, 1000, 3000]"))
        assertTrue(result is MapParseResult.Failure)
        assertTrue((result as MapParseResult.Failure).issues.any { it.contains("menaik") })
    }

    @Test
    fun rejectsCellShapeMismatch() {
        val result = MapDocument.parse(validJson.replace("[1.4, 1.5, 1.6]", "[1.4, 1.5]"))
        assertTrue(result is MapParseResult.Failure)
        assertTrue((result as MapParseResult.Failure).issues.any { it.contains("kolom") })
    }

    @Test
    fun rejectsMalformedJson() {
        val result = MapDocument.parse("""{"format": "revcore-synthetic-map", """)
        assertTrue(result is MapParseResult.Failure)
    }

    @Test
    fun parsesRepositoryExample() {
        val example = findExample()
        val result = MapDocument.parse(example.readText())
        assertTrue((result as MapParseResult.Success).document.validationIssues().isEmpty())
        assertEquals(1.34, result.document.sampleBilinear(3500.0, 45.0)!!, 1e-9)
    }

    private fun findExample(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "examples/maps/synthetic_map.json")
            if (candidate.exists()) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("examples/maps/synthetic_map.json tidak ditemukan")
    }
}
