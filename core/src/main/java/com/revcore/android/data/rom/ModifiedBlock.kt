package com.revcore.android.data.rom

data class ModifiedBlock(
    val offset: Int,
    val length: Int,
    val originalBytes: ByteArray,
    val modifiedBytes: ByteArray,
    val label: String
) {
    val name: String get() = label
    val startOffset: Int get() = offset
    val endOffset: Int get() = offset + length
    val diffCount: Int get() = length

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModifiedBlock

        if (offset != other.offset) return false
        if (length != other.length) return false
        if (!originalBytes.contentEquals(other.originalBytes)) return false
        if (!modifiedBytes.contentEquals(other.modifiedBytes)) return false
        if (label != other.label) return false

        return true
    }

    override fun hashCode(): Int {
        var result = offset
        result = 31 * result + length
        result = 31 * result + originalBytes.contentHashCode()
        result = 31 * result + modifiedBytes.contentHashCode()
        result = 31 * result + label.hashCode()
        return result
    }
}
