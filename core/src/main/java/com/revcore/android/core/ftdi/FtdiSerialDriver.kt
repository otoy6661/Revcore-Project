package com.revcore.android.core.ftdi

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import com.revcore.android.core.protocol.CommDataStage
import com.revcore.android.core.protocol.CommEventCategory
import com.revcore.android.core.protocol.CommunicationSniffer
import com.revcore.android.core.protocol.DiagnosticStage
import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FtdiSerialDriver(
    private val usbManager: UsbManager,
    val device: UsbDevice
) : UsbTransport {
    private val tag = "FtdiSerialDriver"
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    override var isOpen: Boolean = false
        private set
    override var currentBaudRate: Int = FtdiConstants.BAUD_KLINE_DEFAULT
        private set
    override val driverName: String = "NativeFtdiDriver"
    override val chipTypeDescription: String = "FTDI FT232R (Direct SIO 2ms Latency)"

    override fun open(): Boolean {
        if (isOpen) return true

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_DEVICE_DETECTED,
            true,
            "Device: ${device.deviceName} (VID=0x${Integer.toHexString(device.vendorId)}, PID=0x${Integer.toHexString(device.productId)})"
        )

        if (!usbManager.hasPermission(device)) {
            Log.e(tag, "No USB permission for device: ${device.deviceName}")
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_PERMISSION_GRANTED,
                false,
                "Permission denied",
                "USB permission not granted by user for device ${device.deviceName}"
            )
            return false
        }

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_PERMISSION_GRANTED,
            true,
            "USB permission confirmed for ${device.deviceName}"
        )

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e(tag, "Failed to open USB device: ${device.deviceName}")
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "Open failed",
                "UsbManager.openDevice returned null"
            )
            return false
        }
        this.connection = conn

        if (device.interfaceCount == 0) {
            Log.e(tag, "No USB interfaces found")
            conn.close()
            this.connection = null
            return false
        }

        val iface = device.getInterface(0)
        if (!conn.claimInterface(iface, true)) {
            Log.e(tag, "Failed to claim USB interface 0")
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "Claim failed",
                "Could not claim USB interface 0"
            )
            conn.close()
            this.connection = null
            return false
        }
        this.usbInterface = iface

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == 2) { // UsbConstants.USB_ENDPOINT_XFER_BULK
                if (ep.direction == 128) { // UsbConstants.USB_DIR_IN
                    this.inEndpoint = ep
                } else {
                    this.outEndpoint = ep
                }
            }
        }

        if (this.inEndpoint == null || this.outEndpoint == null) {
            Log.e(tag, "Could not locate both IN and OUT bulk endpoints")
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "Endpoints missing",
                "Bulk endpoints not found (IN=${this.inEndpoint != null}, OUT=${this.outEndpoint != null})"
            )
            close()
            return false
        }

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_INTERFACE_OPENED,
            true,
            "Interface 0 claimed (EP IN=0x${Integer.toHexString(inEndpoint?.address ?: 0)}, OUT=0x${Integer.toHexString(outEndpoint?.address ?: 0)})"
        )

        initFtdiChip()
        this.isOpen = true
        Log.i(tag, "FTDI device initialized and ready: ${device.deviceName}")
        return true
    }

    private fun initFtdiChip() {
        val conn = connection ?: return
        conn.controlTransfer(64, 0, 0, 0, null, 0, 500)
        CommunicationSniffer.logEvent(
            CommEventCategory.FTDI,
            CommDataStage.CONTROL_TRANSFER,
            description = "FTDI Reset SIO (REQ=0x00, VAL=0x00)"
        )

        conn.controlTransfer(64, 2, 0, 0, null, 0, 500)
        CommunicationSniffer.logEvent(
            CommEventCategory.FTDI,
            CommDataStage.CONTROL_TRANSFER,
            description = "FTDI Flow Control Disabled (None)"
        )

        conn.controlTransfer(64, 9, 2, 0, null, 0, 500)
        CommunicationSniffer.logEvent(
            CommEventCategory.FTDI,
            CommDataStage.CONTROL_TRANSFER,
            description = "FTDI Latency Timer set to 2 ms"
        )

        setLineParameters(8, 1, 0)
        setBaudRate(FtdiConstants.BAUD_KLINE_DEFAULT)
        setDtrRts(true, true)
        purgeRxTx()

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.FTDI_INIT_COMPLETED,
            true,
            "FTDI configured: 10400 8N1, Latency=2ms, Flow=None"
        )
    }

    override fun setBaudRate(baud: Int): Boolean {
        val conn = connection ?: return false
        val (value, index) = calculateBaudDivisor(baud)
        val result = conn.controlTransfer(64, 3, value, index, null, 0, 500)
        if (result >= 0) {
            this.currentBaudRate = baud
            Log.d(tag, "Baud rate set to $baud (value=0x${Integer.toHexString(value)}, index=0x${Integer.toHexString(index)})")
            CommunicationSniffer.logEvent(
                CommEventCategory.FTDI,
                CommDataStage.CONTROL_TRANSFER,
                description = "FTDI Baud Rate set to $baud bps (Divisor=0x${Integer.toHexString(value)}, Index=0x${Integer.toHexString(index)})"
            )
            return true
        }
        CommunicationSniffer.logEvent(
            CommEventCategory.ERROR,
            CommDataStage.CONTROL_TRANSFER,
            status = "FAILED",
            description = "Failed to set FTDI Baud Rate to $baud bps",
            errorDetails = "Control transfer returned $result"
        )
        return false
    }

    private fun calculateBaudDivisor(baud: Int): Pair<Int, Int> {
        val divisor8 = ((FtdiConstants.FTDI_CLOCK_FREQ * 8) + (baud / 2)) / baud
        val divisor = divisor8 / 8
        val fraction = divisor8 % 8
        val fracCode = when (fraction) {
            1 -> 4
            2 -> 2
            3 -> 6
            4 -> 1
            5 -> 5
            6 -> 3
            7 -> 7
            else -> 0
        }
        val value = (divisor and 0x3FFF) or (fracCode shl 14)
        val index = (fracCode shr 2) and 1
        return Pair(value, index)
    }

    fun setLineParameters(dataBits: Int, stopBits: Int, parity: Int): Boolean {
        val conn = connection ?: return false
        val config = (dataBits and 0x0F) or ((parity and 7) shl 8)
        val stopCode = if (stopBits != 2) 0 else 2
        val res = conn.controlTransfer(64, 4, config or (stopCode shl 11), 0, null, 0, 500)
        val success = res >= 0
        CommunicationSniffer.logEvent(
            CommEventCategory.FTDI,
            CommDataStage.CONTROL_TRANSFER,
            status = if (success) "OK" else "FAILED",
            description = "FTDI Line Parameters: $dataBits data bits, $stopBits stop bit, parity=$parity"
        )
        return success
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val value = (if (dtr) FtdiConstants.SIO_SET_DTR_HIGH else FtdiConstants.SIO_SET_DTR_LOW) or
                (if (rts) FtdiConstants.SIO_SET_RTS_HIGH else FtdiConstants.SIO_SET_RTS_LOW)
        val res = conn.controlTransfer(64, 1, value, 0, null, 0, 500)
        return res >= 0
    }

    override fun setBreak(enable: Boolean): Boolean {
        val conn = connection ?: return false
        val config = (if (enable) FtdiConstants.SIO_SET_BREAK_ON else 0) or 8
        val res = conn.controlTransfer(64, 4, config, 0, null, 0, 500)
        val success = res >= 0
        CommunicationSniffer.logEvent(
            CommEventCategory.KLINE,
            CommDataStage.BUS_EVENT,
            status = if (success) "OK" else "FAILED",
            description = if (enable) "K-Line BREAK condition ASSERTED (Bus pulled LOW)" else "K-Line BREAK condition CLEARED (Bus idling HIGH)"
        )
        return success
    }

    override fun purgeRxTx(): Boolean {
        val conn = connection ?: return false
        val r1 = conn.controlTransfer(64, 0, 1, 0, null, 0, 500)
        val r2 = conn.controlTransfer(64, 0, 2, 0, null, 0, 500)
        val success = r1 >= 0 && r2 >= 0
        CommunicationSniffer.logEvent(
            CommEventCategory.FTDI,
            CommDataStage.CONTROL_TRANSFER,
            status = if (success) "OK" else "FAILED",
            description = "FTDI Purge RX & TX Buffers"
        )
        return success
    }

    override suspend fun write(data: ByteArray, timeoutMs: Int): Int = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext -1
        val outEp = outEndpoint ?: return@withContext -1

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_OUT_ATTEMPTED,
            true,
            "Attempting to transmit ${data.size} bytes: " + data.joinToString(" ") { String.format("%02X", it) }
        )

        CommunicationSniffer.logEvent(
            CommEventCategory.USB,
            CommDataStage.RAW_USB_OUT,
            rawBytes = data,
            description = "Raw USB Bulk OUT: ${data.size} bytes to EP 0x${Integer.toHexString(outEp.address)}"
        )

        val startTime = System.currentTimeMillis()
        val bytesWritten = conn.bulkTransfer(outEp, data, data.size, timeoutMs)
        val elapsed = System.currentTimeMillis() - startTime

        if (bytesWritten >= 0) {
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_OUT_COMPLETED,
                true,
                "Transmitted $bytesWritten bytes in ${elapsed}ms via FTDI EP 0x${Integer.toHexString(outEp.address)}"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.TX,
                CommDataStage.RAW_USB_OUT,
                rawBytes = data,
                status = "OK",
                description = "Successfully transmitted $bytesWritten bytes",
                durationMs = elapsed
            )
        } else {
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_OUT_COMPLETED,
                false,
                "FTDI bulkTransfer OUT failed",
                "Error code $bytesWritten"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.RAW_USB_OUT,
                status = "FAILED",
                description = "bulkTransfer OUT error ($bytesWritten)",
                durationMs = elapsed
            )
        }
        bytesWritten
    }

    override suspend fun read(bufferSize: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext ByteArray(0)
        val inEp = inEndpoint ?: return@withContext ByteArray(0)

        val rawBuffer = ByteArray(bufferSize + 2)
        val startTime = System.currentTimeMillis()
        val bytesRead = conn.bulkTransfer(inEp, rawBuffer, rawBuffer.size, timeoutMs)
        val elapsed = System.currentTimeMillis() - startTime

        if (bytesRead <= 0) {
            return@withContext ByteArray(0)
        }

        val rawBytes = rawBuffer.copyOf(bytesRead)
        CommunicationSniffer.logEvent(
            CommEventCategory.USB,
            CommDataStage.RAW_USB_IN,
            rawBytes = rawBytes,
            status = "OK",
            description = "Received $bytesRead raw bytes in ${elapsed}ms",
            durationMs = elapsed
        )

        if (bytesRead >= 2) {
            val statusByte0 = rawBuffer[0].toInt() and 0xFF
            val statusByte1 = rawBuffer[1].toInt() and 0xFF
            val cts = if ((statusByte0 and 0x10) != 0) 1 else 0
            val dsr = if ((statusByte0 and 0x20) != 0) 1 else 0
            val ri = if ((statusByte0 and 0x40) != 0) 1 else 0
            val dcd = if ((statusByte0 and 0x80) != 0) 1 else 0
            val oe = if ((statusByte1 and 0x02) != 0) 1 else 0
            val pe = if ((statusByte1 and 0x04) != 0) 1 else 0
            val fe = if ((statusByte1 and 0x08) != 0) 1 else 0
            val bi = if ((statusByte1 and 0x10) != 0) 1 else 0

            val statusDesc = String.format(
                "FTDI Status [0x%02X, 0x%02X] (Modem: CTS=%d DSR=%d RI=%d DCD=%d | Line: OE=%d PE=%d FE=%d BI=%d)",
                statusByte0, statusByte1, cts, dsr, ri, dcd, oe, pe, fe, bi
            )

            val isLineErr = (oe != 0 || pe != 0 || fe != 0)
            CommunicationSniffer.logEvent(
                category = if (isLineErr) CommEventCategory.ERROR else CommEventCategory.FTDI,
                stage = CommDataStage.CONTROL_TRANSFER,
                rawBytes = byteArrayOf(rawBuffer[0], rawBuffer[1]),
                status = if (isLineErr) "LINE_ERROR" else "OK",
                description = statusDesc,
                errorDetails = if (fe != 0) "Framing Error on K-Line" else if (oe != 0) "Overrun Error" else null
            )
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.FTDI_STATUS_PROCESSED,
                true,
                statusDesc
            )

            val payloadLen = bytesRead - 2
            if (payloadLen > 0) {
                val payload = ByteArray(payloadLen)
                System.arraycopy(rawBuffer, 2, payload, 0, payloadLen)
                CommunicationSniffer.updateTimelineStage(
                    DiagnosticStage.USB_IN_COMPLETED,
                    true,
                    "Payload received: ${payload.size} bytes"
                )
                CommunicationSniffer.logEvent(
                    CommEventCategory.RX,
                    CommDataStage.FTDI_PAYLOAD,
                    rawBytes = payload,
                    description = "FTDI Payload: ${payload.size} bytes (${payload.joinToString(" ") { String.format("%02X", it) }})"
                )
                return@withContext payload
            }
        }
        ByteArray(0)
    }

    override fun close() {
        try {
            connection?.let { conn ->
                usbInterface?.let { iface ->
                    conn.releaseInterface(iface)
                }
                conn.close()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error closing connection", e)
        } finally {
            connection = null
            usbInterface = null
            inEndpoint = null
            outEndpoint = null
            isOpen = false
            CommunicationSniffer.logEvent(
                CommEventCategory.SYSTEM,
                CommDataStage.STATE_CHANGE,
                description = "FTDI Device connection closed and USB resources released"
            )
        }
    }
}
