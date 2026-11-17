package com.revcore.android.core.protocol

data class SnifferLogEvent(
    val id: Long,
    val timestamp: String,
    val timeEpochMs: Long,
    val category: CommEventCategory,
    val stage: CommDataStage,
    val rawBytes: ByteArray?,
    val hexString: String,
    val asciiString: String,
    val byteCount: Int,
    val status: String,
    val description: String,
    val errorDetails: String? = null,
    val durationMs: Long? = null
) {
    val message: String get() = description
    val hexData: String get() = hexString
    val timestampMs: Long get() = timeEpochMs
    val direction: LogDirection
        get() = when (category) {
            CommEventCategory.TX -> LogDirection.TX
            CommEventCategory.RX -> LogDirection.RX
            CommEventCategory.ERROR -> LogDirection.ERR
            else -> LogDirection.SYS
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SnifferLogEvent

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (timeEpochMs != other.timeEpochMs) return false
        if (category != other.category) return false
        if (stage != other.stage) return false
        if (rawBytes != null) {
            if (other.rawBytes == null) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false
        } else if (other.rawBytes != null) return false
        if (hexString != other.hexString) return false
        if (asciiString != other.asciiString) return false
        if (byteCount != other.byteCount) return false
        if (status != other.status) return false
        if (description != other.description) return false
        if (errorDetails != other.errorDetails) return false
        if (durationMs != other.durationMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + timeEpochMs.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + stage.hashCode()
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + hexString.hashCode()
        result = 31 * result + asciiString.hashCode()
        result = 31 * result + byteCount
        result = 31 * result + status.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (errorDetails?.hashCode() ?: 0)
        result = 31 * result + (durationMs?.hashCode() ?: 0)
        return result
    }
}
