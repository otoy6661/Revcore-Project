package com.revcore.android.data.ecu

enum class ChecksumType {
    KEIHIN_16BIT_ADD,
    KEIHIN_8BIT_INVERTED,
    SHINDENGEN_ADDITIVE,
    SHINDENGEN_CRC16
}
