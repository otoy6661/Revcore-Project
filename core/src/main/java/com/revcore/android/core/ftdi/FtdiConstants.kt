package com.revcore.android.core.ftdi

object FtdiConstants {
    const val VID_FTDI = 0x0403
    const val PID_FT232R = 0x6001
    const val PID_FT2232 = 0x6010
    const val PID_FT232H = 0x6014
    const val PID_FTX = 0x6015

    const val VID_CH340 = 0x1A86
    const val PID_CH340 = 0x7523

    const val VID_CP210X = 0x10C4

    const val REQTYPE_HOST_TO_DEVICE = 0x40
    const val REQTYPE_DEVICE_TO_HOST = 0xC0

    const val SIO_RESET_REQUEST = 0
    const val SIO_SET_MODEM_CTRL_REQUEST = 1
    const val SIO_SET_FLOW_CTRL_REQUEST = 2
    const val SIO_SET_BAUD_RATE_REQUEST = 3
    const val SIO_SET_DATA_REQUEST = 4
    const val SIO_GET_MODEM_STATUS_REQUEST = 5
    const val SIO_SET_LATENCY_TIMER_REQUEST = 9
    const val SIO_GET_LATENCY_TIMER_REQUEST = 10

    const val SIO_RESET_SIO = 0
    const val SIO_RESET_PURGE_RX = 1
    const val SIO_RESET_PURGE_TX = 2

    const val SIO_DISABLE_FLOW_CTRL = 0
    const val SIO_SET_DTR_HIGH = 0x0101
    const val SIO_SET_DTR_LOW = 0x0100
    const val SIO_SET_RTS_HIGH = 0x0202
    const val SIO_SET_RTS_LOW = 0x0200

    const val SIO_DATA_8N1 = 8
    const val SIO_SET_BREAK_ON = 0x4000
    const val SIO_SET_BREAK_OFF = 0

    const val BAUD_KLINE_DEFAULT = 10400
    const val BAUD_STANDARD = 9600
    const val BAUD_KLINE_FAST = 38400
    const val BAUD_KLINE_REPROGRAM = 57600
    const val FTDI_CLOCK_FREQ = 3000000
}
