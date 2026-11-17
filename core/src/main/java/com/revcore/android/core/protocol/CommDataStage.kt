package com.revcore.android.core.protocol

enum class CommDataStage {
    BUS_EVENT,
    CONTROL_TRANSFER,
    FTDI_PAYLOAD,
    KLINE_ECHO,
    PARSED_RESPONSE,
    PROTOCOL_FRAME,
    RAW_USB_IN,
    RAW_USB_OUT,
    STATE_CHANGE
}
