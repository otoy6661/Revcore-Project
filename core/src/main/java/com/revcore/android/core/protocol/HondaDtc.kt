package com.revcore.android.core.protocol

data class HondaDtc(
    val codeNumber: Int,
    val subCode: Int,
    val standardCode: String,
    val blinkCode: Int,
    val titleEn: String,
    val titleId: String,
    val symptomEn: String,
    val symptomId: String,
    val isKnown: Boolean = true
)
