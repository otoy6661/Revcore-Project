package com.revcore.android.data.map

import kotlin.math.abs

/**
 * Generic, dependency-free MAP document for the synthetic `revcore-synthetic-map`
 * JSON format documented in `docs/MAP_FORMAT.md`.
 *
 * This is not an ECU binary format and it does not contain flashing logic. Values
 * are engineering units supplied by the document; the engine never invents an
 * offset, scale, or cell value.
 */
data class MapUnits(
    val rpm: String,
    val load: String,
    val value: String
)

data class MapDocument(
    val format: String,
    val version: Int,
    val id: String,
    val status: String,
    val units: MapUnits,
    val rpmAxis: List<Double>,
    val loadAxis: List<Double>,
    val cells: List<List<Double>>,
    val notes: String
) {
    val rows: Int get() = cells.size
    val columns: Int get() = cells.firstOrNull()?.size ?: 0

    fun validationIssues(): List<String> {
        val issues = mutableListOf<String>()
        if (format != FORMAT_NAME) issues += "format harus '$FORMAT_NAME'"
        if (version != FORMAT_VERSION) issues += "version harus $FORMAT_VERSION"
        if (!id.startsWith(PUBLIC_ID_PREFIX)) {
            issues += "id materi publik harus berawalan '$PUBLIC_ID_PREFIX'"
        }
        if (status !in ALLOWED_STATUSES) {
            issues += "status harus salah satu dari ${ALLOWED_STATUSES.joinToString()}"
        }
        if (units.rpm.isBlank()) issues += "units.rpm kosong"
        if (units.load.isBlank()) issues += "units.load kosong"
        if (units.value.isBlank()) issues += "units.value kosong"
        if (rpmAxis.size < 2) issues += "axes.rpm harus berisi minimal dua nilai"
        if (loadAxis.size < 2) issues += "axes.load harus berisi minimal dua nilai"
        if (!isStrictlyIncreasing(rpmAxis)) issues += "axes.rpm harus menaik ketat"
        if (!isStrictlyIncreasing(loadAxis)) issues += "axes.load harus menaik ketat"
        if (cells.size != rpmAxis.size) {
            issues += "jumlah baris cells (${cells.size}) harus sama dengan axes.rpm (${rpmAxis.size})"
        }
        cells.forEachIndexed { rowIndex, row ->
            if (row.size != loadAxis.size) {
                issues += "baris cells[$rowIndex] memiliki ${row.size} kolom, harus ${loadAxis.size}"
            }
            row.forEachIndexed { columnIndex, value ->
                if (!value.isFinite()) {
                    issues += "cells[$rowIndex][$columnIndex] bukan angka berhingga"
                }
            }
        }
        if (notes.isBlank()) issues += "notes wajib menjelaskan asal data dan batas bukti"
        return issues
    }

    /**
     * Bilinear sample in engineering units. Returns null outside the declared axes;
     * it never extrapolates. Exact axis values return the stored cell unchanged.
     */
    fun sampleBilinear(rpm: Double, load: Double): Double? {
        if (rpmAxis.size < 2 || loadAxis.size < 2) return null
        if (!rpm.isFinite() || !load.isFinite()) return null
        if (rpm < rpmAxis.first() || rpm > rpmAxis.last()) return null
        if (load < loadAxis.first() || load > loadAxis.last()) return null

        val rpmPosition = axisPosition(rpmAxis, rpm) ?: return null
        val loadPosition = axisPosition(loadAxis, load) ?: return null
        val (r0, r1, tr) = rpmPosition
        val (c0, c1, tc) = loadPosition

        val v00 = cells[r0][c0]
        val v01 = cells[r0][c1]
        val v10 = cells[r1][c0]
        val v11 = cells[r1][c1]
        val bottom = v00 + (v01 - v00) * tc
        val top = v10 + (v11 - v10) * tc
        return bottom + (top - bottom) * tr
    }

    /** Returns lower/upper indices and the fraction between them; exact hits use fraction 0. */
    private fun axisPosition(axis: List<Double>, value: Double): Triple<Int, Int, Double>? {
        val exact = axis.indexOfFirst { abs(it - value) <= AXIS_EPSILON }
        if (exact >= 0) return Triple(exact, exact, 0.0)
        for (i in 0 until axis.size - 1) {
            if (value > axis[i] && value < axis[i + 1]) {
                val fraction = (value - axis[i]) / (axis[i + 1] - axis[i])
                return Triple(i, i + 1, fraction)
            }
        }
        return null
    }

    companion object {
        const val FORMAT_NAME = "revcore-synthetic-map"
        const val FORMAT_VERSION = 1
        const val PUBLIC_ID_PREFIX = "SAMPLE-"
        val ALLOWED_STATUSES = setOf("REFERENCE", "RECONSTRUCTED", "VERIFIED", "UNVERIFIED")
        private const val AXIS_EPSILON = 1e-9

        private fun isStrictlyIncreasing(values: List<Double>): Boolean {
            if (values.any { !it.isFinite() }) return false
            for (i in 1 until values.size) {
                if (values[i] <= values[i - 1]) return false
            }
            return true
        }

        fun parse(text: String): MapParseResult {
            return try {
                val document = fromJson(MiniJson.parse(text))
                val issues = document.validationIssues()
                if (issues.isEmpty()) MapParseResult.Success(document)
                else MapParseResult.Failure(issues)
            } catch (e: Exception) {
                MapParseResult.Failure(listOf(e.message ?: "JSON MAP tidak dapat dibaca"))
            }
        }

        private fun fromJson(value: Any?): MapDocument {
            val root = value as? Map<*, *>
                ?: throw IllegalArgumentException("Akar JSON MAP harus berupa object")
            val units = root["units"] as? Map<*, *>
                ?: throw IllegalArgumentException("units wajib berupa object")
            val axes = root["axes"] as? Map<*, *>
                ?: throw IllegalArgumentException("axes wajib berupa object")
            return MapDocument(
                format = requiredString(root, "format"),
                version = requiredInt(root, "version"),
                id = requiredString(root, "id"),
                status = requiredString(root, "status"),
                units = MapUnits(
                    rpm = requiredString(units, "rpm"),
                    load = requiredString(units, "load"),
                    value = requiredString(units, "value")
                ),
                rpmAxis = requiredNumbers(axes["rpm"], "axes.rpm"),
                loadAxis = requiredNumbers(axes["load"], "axes.load"),
                cells = requiredMatrix(root["cells"], "cells"),
                notes = requiredString(root, "notes")
            )
        }

        private fun requiredString(map: Map<*, *>, key: String): String {
            val value = map[key] as? String
                ?: throw IllegalArgumentException("$key wajib berupa string")
            if (value.isBlank()) throw IllegalArgumentException("$key tidak boleh kosong")
            return value
        }

        private fun requiredInt(map: Map<*, *>, key: String): Int {
            val value = map[key] as? Double
                ?: throw IllegalArgumentException("$key wajib berupa angka")
            if (value % 1.0 != 0.0) throw IllegalArgumentException("$key harus bilangan bulat")
            return value.toInt()
        }

        private fun requiredNumbers(value: Any?, field: String): List<Double> {
            val list = value as? List<*>
                ?: throw IllegalArgumentException("$field wajib berupa array")
            return list.mapIndexed { index, item ->
                item as? Double
                    ?: throw IllegalArgumentException("$field[$index] wajib berupa angka")
            }
        }

        private fun requiredMatrix(value: Any?, field: String): List<List<Double>> {
            val rows = value as? List<*>
                ?: throw IllegalArgumentException("$field wajib berupa array baris")
            return rows.mapIndexed { rowIndex, row ->
                val columns = row as? List<*>
                    ?: throw IllegalArgumentException("$field[$rowIndex] wajib berupa array")
                columns.mapIndexed { columnIndex, item ->
                    item as? Double
                        ?: throw IllegalArgumentException(
                            "$field[$rowIndex][$columnIndex] wajib berupa angka"
                        )
                }
            }
        }
    }
}

sealed interface MapParseResult {
    data class Success(val document: MapDocument) : MapParseResult
    data class Failure(val issues: List<String>) : MapParseResult
}
