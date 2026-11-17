package com.revcore.android.core.protocol

data class CommLogEntry(
    val timestamp: String,
    val direction: LogDirection,
    val hexString: String,
    val description: String
)
