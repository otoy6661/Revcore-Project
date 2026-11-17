package com.revcore.android.data.rom

data class CalibrationMap(
    val name: String,
    val rows: Int,
    val cols: Int,
    val data: Array<FloatArray>,
    val rpmHeaders: List<Int>,
    val tpsHeaders: List<Float>,
    val unit: String,
    val minVal: Float,
    val maxVal: Float,
    val isAvailable: Boolean = true
) {
    fun updateCell(row: Int, col: Int, value: Float) {
        if (row in 0 until rows && col in 0 until cols) {
            data[row][col] = value.coerceIn(minVal, maxVal)
        }
    }

    fun getCell(row: Int, col: Int): Float {
        return if (row in 0 until rows && col in 0 until cols) {
            data[row][col]
        } else {
            0f
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalibrationMap

        if (name != other.name) return false
        if (rows != other.rows) return false
        if (cols != other.cols) return false
        if (!data.contentDeepEquals(other.data)) return false
        if (rpmHeaders != other.rpmHeaders) return false
        if (tpsHeaders != other.tpsHeaders) return false
        if (unit != other.unit) return false
        if (minVal != other.minVal) return false
        if (maxVal != other.maxVal) return false
        if (isAvailable != other.isAvailable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + rows
        result = 31 * result + cols
        result = 31 * result + data.contentDeepHashCode()
        result = 31 * result + rpmHeaders.hashCode()
        result = 31 * result + tpsHeaders.hashCode()
        result = 31 * result + unit.hashCode()
        result = 31 * result + minVal.hashCode()
        result = 31 * result + maxVal.hashCode()
        result = 31 * result + isAvailable.hashCode()
        return result
    }
}
