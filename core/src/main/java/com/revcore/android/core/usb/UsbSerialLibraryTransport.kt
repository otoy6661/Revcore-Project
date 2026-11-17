package com.revcore.android.core.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.revcore.android.core.ftdi.FtdiConstants
import com.revcore.android.core.protocol.CommDataStage
import com.revcore.android.core.protocol.CommEventCategory
import com.revcore.android.core.protocol.CommunicationSniffer
import com.revcore.android.core.protocol.DiagnosticStage
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbSerialLibraryTransport(
    private val usbManager: UsbManager,
    val device: UsbDevice,
    private val portIndex: Int = 0
) : UsbTransport {
    private val tag = "UsbSerialTransport"
    private var connection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialPort? = null
    private var detectedDriver: UsbSerialDriver? = null

    override var isOpen: Boolean = false
        private set
    override var currentBaudRate: Int = FtdiConstants.BAUD_KLINE_DEFAULT
        private set

    override val driverName: String
        get() = detectedDriver?.javaClass?.simpleName ?: "UsbSerialPort"

    override val chipTypeDescription: String
        get() {
            val vid = device.vendorId
            val pid = device.productId
            return when (vid) {
                FtdiConstants.VID_FTDI -> when (pid) {
                    FtdiConstants.PID_FT232R -> "FTDI FT232R K-Line"
                    FtdiConstants.PID_FT2232 -> "FTDI FT2232D Dual Channel"
                    24593 -> "FTDI FT4232H Quad Channel"
                    FtdiConstants.PID_FT232H -> "FTDI FT232H High-Speed"
                    else -> String.format("FTDI USB Serial (0x%04X)", pid)
                }
                1155, 5824, 9025 -> "CDC-ACM / Microcontroller K-Line"
                1659 -> if (pid == 8963) "Prolific PL2303 K-Line Cable" else String.format("Prolific USB Serial (0x%04X)", pid)
                FtdiConstants.VID_CP210X -> when (pid) {
                    60000 -> "Silicon Labs CP2102 Serial"
                    60016 -> "Silicon Labs CP2105 Dual Port"
                    60017 -> "Silicon Labs CP2108 Quad Port"
                    else -> String.format("Silicon Labs CP210x (0x%04X)", pid)
                }
                FtdiConstants.VID_CH340 -> when (pid) {
                    21795 -> "WCH CH341 Diagnostic Adapter"
                    FtdiConstants.PID_CH340 -> "WCH CH340 K-Line Adapter"
                    else -> String.format("WCH CH34x Serial (0x%04X)", pid)
                }
                else -> String.format("USB Serial Device (VID=0x%04X, PID=0x%04X)", vid, pid)
            }
        }

    override fun open(): Boolean {
        if (isOpen) return true

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_DEVICE_DETECTED,
            true,
            "Device: ${device.deviceName} [$chipTypeDescription]"
        )
        CommunicationSniffer.logEvent(
            CommEventCategory.USB,
            CommDataStage.BUS_EVENT,
            status = "OPEN_REQUEST",
            description = "USB open request: ${device.deviceName} VID=0x%04X PID=0x%04X interfaces=[%s]"
                .format(device.vendorId, device.productId, describeInterfaces())
        )

        if (!usbManager.hasPermission(device)) {
            Log.e(tag, "No USB permission for device: ${device.deviceName}")
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_PERMISSION_GRANTED,
                false,
                "Permission denied",
                "USB permission not granted by user for device ${device.deviceName}"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "USB_PERMISSION_MISSING",
                description = "Izin USB belum diberikan untuk ${device.deviceName}"
            )
            return false
        }

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_PERMISSION_GRANTED,
            true,
            "USB permission confirmed for ${device.deviceName}"
        )

        val prober = createDiagnosticProber()
        val driver = prober.probeDevice(device) ?: UsbSerialProber.getDefaultProber().probeDevice(device)

        if (driver == null) {
            Log.e(tag, "No compatible USB serial driver found for device: ${device.deviceName}")
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "Unsupported USB serial chipset",
                "VID=0x${Integer.toHexString(device.vendorId)}, PID=0x${Integer.toHexString(device.productId)}"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "UNSUPPORTED_SERIAL_CHIPSET",
                description = "Tidak ada driver USB serial yang cocok untuk ${device.deviceName}",
                errorDetails = "VID=0x%04X PID=0x%04X".format(device.vendorId, device.productId)
            )
            return false
        }
        this.detectedDriver = driver

        val ports = driver.ports
        if (ports.isEmpty() || portIndex >= ports.size) {
            Log.e(tag, "No serial ports available on driver: ${driver.javaClass.simpleName}")
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "NO_SERIAL_PORT",
                description = "Driver ${driver.javaClass.simpleName} tidak menyediakan port pada indeks $portIndex"
            )
            return false
        }

        val port = ports[portIndex]
        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e(tag, "Failed to open USB device connection: ${device.deviceName}")
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "UsbManager.openDevice failed",
                "Could not obtain UsbDeviceConnection"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "USB_OPEN_DEVICE_FAILED",
                description = "UsbManager.openDevice gagal untuk ${device.deviceName}"
            )
            return false
        }
        this.connection = conn

        return try {
            port.open(conn)
            this.serialPort = port
            port.setParameters(FtdiConstants.BAUD_KLINE_DEFAULT, 8, 1, 0)
            this.currentBaudRate = FtdiConstants.BAUD_KLINE_DEFAULT
            CommunicationSniffer.logEvent(
                CommEventCategory.USB,
                CommDataStage.CONTROL_TRANSFER,
                status = "SERIAL_INIT",
                description = "Serial init: ${FtdiConstants.BAUD_KLINE_DEFAULT} baud, 8 data bits, 1 stop bit, no parity (8N1) via $driverName"
            )

            try {
                port.dtr = true
                port.rts = true
                CommunicationSniffer.logEvent(
                    CommEventCategory.USB,
                    CommDataStage.CONTROL_TRANSFER,
                    status = "MODEM_CTRL",
                    description = "DTR=1 RTS=1 applied via $driverName"
                )
            } catch (e: Exception) {
                Log.w(tag, "Failed to set DTR/RTS", e)
                CommunicationSniffer.recordUsbError()
                CommunicationSniffer.logEvent(
                    CommEventCategory.ERROR,
                    CommDataStage.CONTROL_TRANSFER,
                    status = "MODEM_CTRL_FAILED",
                    description = "Gagal menerapkan DTR/RTS via $driverName",
                    errorDetails = e.toString()
                )
            }

            if (device.vendorId == FtdiConstants.VID_FTDI) {
                try {
                    conn.controlTransfer(64, 9, 2, portIndex + 1, null, 0, 300)
                    conn.controlTransfer(64, 9, 2, 0, null, 0, 300)
                    conn.controlTransfer(64, 2, 0, portIndex + 1, null, 0, 300)
                    conn.controlTransfer(64, 2, 0, 0, null, 0, 300)
                    Log.i(tag, "Configured FTDI SIO Low Latency (2ms) and Flow Control (None)")
                    CommunicationSniffer.logEvent(
                        CommEventCategory.USB,
                        CommDataStage.CONTROL_TRANSFER,
                        status = "FTDI_LATENCY",
                        description = "FTDI latency timer 2 ms, flow control disabled via $driverName"
                    )
                } catch (e: Exception) {
                    Log.w(tag, "Could not set custom FTDI latency timer", e)
                    CommunicationSniffer.recordUsbError()
                    CommunicationSniffer.logEvent(
                        CommEventCategory.ERROR,
                        CommDataStage.CONTROL_TRANSFER,
                        status = "FTDI_LATENCY_FAILED",
                        description = "Gagal mengatur latency timer FTDI",
                        errorDetails = e.toString()
                    )
                }
            }

            try {
                port.purgeHwBuffers(true, true)
                CommunicationSniffer.logEvent(
                    CommEventCategory.USB,
                    CommDataStage.CONTROL_TRANSFER,
                    status = "PURGE_OK",
                    description = "Hardware RX/TX buffers purged after serial init via $driverName"
                )
            } catch (e: Exception) {
                Log.w(tag, "purgeHwBuffers not supported", e)
                CommunicationSniffer.recordUsbError()
                CommunicationSniffer.logEvent(
                    CommEventCategory.ERROR,
                    CommDataStage.CONTROL_TRANSFER,
                    status = "PURGE_FAILED",
                    description = "purgeHwBuffers tidak didukung/gagal via $driverName",
                    errorDetails = e.toString()
                )
            }

            this.isOpen = true
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                true,
                "Port opened via $driverName ($chipTypeDescription)"
            )
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.FTDI_INIT_COMPLETED,
                true,
                "Configured 10400 8N1 via $driverName"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.SYSTEM,
                CommDataStage.STATE_CHANGE,
                description = "USB Serial Adapter Ready: $chipTypeDescription (10400 8N1)"
            )
            Log.i(tag, "USB Serial port successfully initialized: $chipTypeDescription")
            true
        } catch (e: Exception) {
            Log.e(tag, "Exception during USB Serial port configuration", e)
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_INTERFACE_OPENED,
                false,
                "Port initialization failed",
                e.message ?: e.javaClass.simpleName
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.BUS_EVENT,
                status = "PORT_INIT_FAILED",
                description = "Inisialisasi port serial gagal via ${driver.javaClass.simpleName}",
                errorDetails = e.toString()
            )
            close()
            false
        }
    }

    private fun describeInterfaces(): String {
        return try {
            val parts = mutableListOf<String>()
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                val endpoints = mutableListOf<String>()
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    endpoints.add("ep=0x%02X type=%d dir=%d".format(ep.address, ep.type, ep.direction))
                }
                parts.add(
                    "if$i class=${intf.interfaceClass} sub=${intf.interfaceSubclass} proto=${intf.interfaceProtocol}" +
                        if (endpoints.isEmpty()) "" else " [${endpoints.joinToString(", ")}]"
                )
            }
            if (parts.isEmpty()) "none" else parts.joinToString("; ")
        } catch (e: Exception) {
            "unavailable (${e.javaClass.simpleName})"
        }
    }

    override fun setBaudRate(baud: Int): Boolean {
        val port = serialPort ?: return false
        return try {
            port.setParameters(baud, 8, 1, 0)
            this.currentBaudRate = baud
            CommunicationSniffer.logEvent(
                CommEventCategory.USB,
                CommDataStage.CONTROL_TRANSFER,
                status = "BAUD_SET",
                description = "Baud rate changed to $baud bps, 8N1 via $driverName"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to set baud rate to $baud", e)
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.CONTROL_TRANSFER,
                status = "BAUD_SET_FAILED",
                description = "Gagal mengubah baud rate ke $baud bps via $driverName",
                errorDetails = e.toString()
            )
            false
        }
    }

    override fun setBreak(enable: Boolean): Boolean {
        val port = serialPort ?: return false
        return try {
            port.setBreak(enable)
            CommunicationSniffer.logEvent(
                CommEventCategory.KLINE,
                CommDataStage.BUS_EVENT,
                status = "OK",
                description = if (enable) "K-Line BREAK ASSERTED (LOW pulse) via $driverName" else "K-Line BREAK CLEARED (HIGH idle) via $driverName"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to set break condition ($enable)", e)
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.CONTROL_TRANSFER,
                status = "BREAK_FAILED",
                description = "Adapter gagal mengubah kondisi BREAK (enable=$enable) via $driverName",
                errorDetails = e.toString()
            )
            false
        }
    }

    override fun purgeRxTx(): Boolean {
        val port = serialPort ?: return false
        return try {
            port.purgeHwBuffers(true, true)
            CommunicationSniffer.logEvent(
                CommEventCategory.USB,
                CommDataStage.CONTROL_TRANSFER,
                description = "Purged hardware RX & TX buffers via $driverName"
            )
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to purge buffers", e)
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.CONTROL_TRANSFER,
                status = "PURGE_FAILED",
                description = "Gagal purge buffer RX/TX via $driverName",
                errorDetails = e.toString()
            )
            false
        }
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val port = serialPort ?: return false
        return try {
            port.dtr = dtr
            port.rts = rts
            CommunicationSniffer.logEvent(
                CommEventCategory.USB,
                CommDataStage.CONTROL_TRANSFER,
                status = "MODEM_CTRL",
                description = "DTR=${if (dtr) 1 else 0} RTS=${if (rts) 1 else 0} via $driverName"
            )
            true
        } catch (e: Exception) {
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.CONTROL_TRANSFER,
                status = "MODEM_CTRL_FAILED",
                description = "Gagal mengubah DTR/RTS via $driverName",
                errorDetails = e.toString()
            )
            false
        }
    }

    override suspend fun write(data: ByteArray, timeoutMs: Int): Int = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext -1

        CommunicationSniffer.updateTimelineStage(
            DiagnosticStage.USB_OUT_ATTEMPTED,
            true,
            "Transmitting ${data.size} bytes: " + data.joinToString(" ") { String.format("%02X", it) }
        )

        // Raw K-Line TX is logged once per command by KlineTransceiver. Logging
        // every paced byte here would duplicate the same payload many times.
        val startTime = System.currentTimeMillis()
        try {
            port.write(data, timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_OUT_COMPLETED,
                true,
                "Transmitted ${data.size} bytes in ${elapsed}ms"
            )
            data.size
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(tag, "USB Serial write error", e)
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_OUT_COMPLETED,
                false,
                "USB OUT error",
                e.message ?: "Write timeout or pipe error"
            )
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.RAW_USB_OUT,
                rawBytes = data,
                status = "FAILED",
                description = "USB Serial OUT failed: ${e.message}",
                errorDetails = e.toString(),
                durationMs = elapsed
            )
            -1
        }
    }

    override suspend fun read(bufferSize: Int, timeoutMs: Int): ByteArray = withContext(Dispatchers.IO) {
        val port = serialPort ?: return@withContext ByteArray(0)
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()
        try {
            val bytesRead = port.read(buffer, timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            if (bytesRead <= 0) {
                return@withContext ByteArray(0)
            }
            val result = buffer.copyOf(bytesRead)
            CommunicationSniffer.updateTimelineStage(
                DiagnosticStage.USB_IN_COMPLETED,
                true,
                "Received $bytesRead bytes in ${elapsed}ms via $driverName"
            )
            result
        } catch (e: Exception) {
            Log.e(tag, "USB Serial read error", e)
            CommunicationSniffer.recordUsbError()
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.RAW_USB_IN,
                status = "FAILED",
                description = "USB Serial IN failed via $driverName: ${e.message}",
                errorDetails = e.toString()
            )
            ByteArray(0)
        }
    }

    override fun close() {
        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing serial port", e)
        } finally {
            serialPort = null
            connection = null
            isOpen = false
            CommunicationSniffer.logEvent(
                CommEventCategory.SYSTEM,
                CommDataStage.STATE_CHANGE,
                description = "USB Serial port closed and resources released"
            )
        }
    }

    companion object {
        fun createDiagnosticProber(): UsbSerialProber {
            val customTable = ProbeTable()
            customTable.addProduct(FtdiConstants.VID_FTDI, FtdiConstants.PID_FT232R, FtdiSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_FTDI, FtdiConstants.PID_FT232H, FtdiSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_FTDI, FtdiConstants.PID_FT2232, FtdiSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_FTDI, 24593, FtdiSerialDriver::class.java)

            customTable.addProduct(FtdiConstants.VID_CH340, FtdiConstants.PID_CH340, Ch34xSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_CH340, 21795, Ch34xSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_CH340, 29986, Ch34xSerialDriver::class.java)

            customTable.addProduct(FtdiConstants.VID_CP210X, 60000, Cp21xxSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_CP210X, 60016, Cp21xxSerialDriver::class.java)
            customTable.addProduct(FtdiConstants.VID_CP210X, 60017, Cp21xxSerialDriver::class.java)

            customTable.addProduct(1659, 8963, ProlificSerialDriver::class.java)
            customTable.addProduct(1659, 9123, ProlificSerialDriver::class.java)

            return UsbSerialProber(customTable)
        }

        fun probeAllDevices(usbManager: UsbManager): List<UsbSerialDriver> {
            val prober = createDiagnosticProber()
            val list = prober.findAllDrivers(usbManager).toMutableList()
            val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            for (def in defaultDrivers) {
                if (list.none { it.device.deviceId == def.device.deviceId }) {
                    list.add(def)
                }
            }
            return list
        }
    }
}
