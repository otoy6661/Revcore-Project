package com.revcore.android.core.usb

interface UsbTransport {
    val isOpen: Boolean
    val currentBaudRate: Int
    val driverName: String
    val chipTypeDescription: String

    fun open(): Boolean
    fun close()
    fun setBaudRate(baud: Int): Boolean
    fun setBreak(enable: Boolean): Boolean
    fun purgeRxTx(): Boolean
    fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean

    suspend fun write(data: ByteArray, timeoutMs: Int = 1000): Int
    suspend fun read(bufferSize: Int = 256, timeoutMs: Int = 100): ByteArray
}
