package com.revcore.android.core.comm

import com.revcore.android.core.protocol.CommandOutcome
import com.revcore.android.core.protocol.CommDataStage
import com.revcore.android.core.protocol.CommEventCategory
import com.revcore.android.core.protocol.CommSessionStatus
import com.revcore.android.core.protocol.CommunicationSniffer
import com.revcore.android.core.protocol.DiagnosticStage
import com.revcore.android.core.protocol.LogDirection
import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream

/**
 * Wire-level transaction configuration. Defaults mirror the restored baseline:
 * one whole-frame write, no pacing, no echo window, read until timeout.
 */
data class KlineWireConfig(
    val timeoutMs: Int = 700,
    val retries: Int = 1,
    val interByteMs: Int = 0,
    val echoWindowMs: Long = 0,
    val quietMs: Long = 0,
    val postWriteDelayMs: Long = 0,
    val acceptedHeaders: Set<Int> = HondaFrameCodec.DEFAULT_ACCEPTED_HEADERS,
    val recentTxWindowMs: Long = 1500,
    val maxRxBuffer: Int = 1024,
    val keepRxBuffer: Int = 512
)

data class WakePulseOutcome(
    val breakAccepted: Boolean,
    val lowRequestedMs: Long,
    val lowMeasuredMs: Long,
    val highRequestedMs: Long,
    val highMeasuredMs: Long
)

/**
 * K-Line wire engine: purge, transmit, echo suppression, frame extraction and
 * checksum validation over an already opened [UsbTransport].
 *
 * A candidate frame is accepted only when it is checksum-valid, its declared
 * length matches, and it is not byte-identical to a frame this adapter sent
 * inside [KlineWireConfig.recentTxWindowMs]. The window spans retries so a
 * delayed local echo from an earlier attempt cannot be promoted to a response.
 */
class KlineWireEngine(
    private val logger: (LogDirection, String, String) -> Unit = { _, _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class SentFrame(val bytes: ByteArray, val sentAtMs: Long)

    private class FrameScan {
        val rejected = mutableListOf<String>()
        var ownEchoSeen = false
        var checksumFailures = 0
        var incomplete: String? = null
    }

    private val recentTxFrames = ArrayDeque<SentFrame>()

    fun reset() {
        recentTxFrames.clear()
    }

    suspend fun wakePulse(
        transport: UsbTransport,
        baudRate: Int,
        lowMs: Long,
        highMs: Long
    ): WakePulseOutcome {
        transport.setBaudRate(baudRate)
        transport.purgeRxTx()
        reset()

        val breakAccepted = transport.setBreak(true)
        if (!breakAccepted) {
            CommunicationSniffer.recordUsbError()
            logger(LogDirection.ERR, "", "Adapter menolak kondisi BREAK; pulsa wake tidak dapat dikirim.")
        }
        val lowStart = clock()
        delay(lowMs)
        val lowMeasured = clock() - lowStart

        transport.setBreak(false)
        val highStart = clock()
        delay(highMs)
        val highMeasured = clock() - highStart
        transport.purgeRxTx()

        return WakePulseOutcome(
            breakAccepted = breakAccepted,
            lowRequestedMs = lowMs,
            lowMeasuredMs = lowMeasured,
            highRequestedMs = highMs,
            highMeasuredMs = highMeasured
        )
    }

    suspend fun transceive(
        transport: UsbTransport,
        request: ByteArray,
        config: KlineWireConfig = KlineWireConfig()
    ): CommandOutcome {
        val attempts = config.retries.coerceAtLeast(1)
        var lastRaw = ByteArray(0)
        var lastReason = "Tidak ada respons"

        for (attempt in 0 until attempts) {
            currentCoroutineContext().ensureActive()

            if (!transport.purgeRxTx()) {
                CommunicationSniffer.recordUsbError()
                logger(LogDirection.ERR, "", "Adapter gagal purge buffer RX/TX sebelum TX")
            }
            drainStaleInput(transport)

            val requestHex = HondaFrameCodec.hex(request)
            logger(LogDirection.TX, requestHex, "TX K-Line ${request.size} byte (percobaan ${attempt + 1}/$attempts)")
            CommunicationSniffer.logEvent(
                CommEventCategory.TX,
                CommDataStage.RAW_USB_OUT,
                rawBytes = request,
                status = "TX_ATTEMPT",
                description = "K-Line TX ${request.size} byte (percobaan ${attempt + 1}/$attempts, inter-byte ${config.interByteMs} ms)"
            )
            registerOwnTx(request)

            if (!write(transport, request, config.interByteMs)) {
                lastReason = "Gagal menulis ke endpoint USB"
                CommunicationSniffer.recordUsbError()
                logger(LogDirection.ERR, requestHex, lastReason)
                CommunicationSniffer.logEvent(
                    CommEventCategory.ERROR,
                    CommDataStage.RAW_USB_OUT,
                    rawBytes = request,
                    status = "FAILED",
                    description = lastReason,
                    errorDetails = lastReason
                )
                if (attempt < attempts - 1) {
                    CommunicationSniffer.recordRetry()
                    delay(50)
                }
                continue
            }
            CommunicationSniffer.recordTxFrame()

            if (config.postWriteDelayMs > 0) delay(config.postWriteDelayMs)

            val buffer = ByteArrayOutputStream()
            readForWindow(buffer, transport, config.echoWindowMs, config.quietMs)
            val rawRx = buffer.toByteArray()
            if (rawRx.isNotEmpty()) {
                CommunicationSniffer.logEvent(
                    CommEventCategory.RX,
                    CommDataStage.RAW_USB_IN,
                    rawBytes = rawRx,
                    status = "RX_RAW",
                    description = "RX mentah sebelum filter echo (${rawRx.size} byte)"
                )
            }

            val scan = FrameScan()
            val stripped = HondaFrameCodec.stripLeadingEcho(rawRx, request)
            var body = stripped.body
            scan.ownEchoSeen = stripped.removed
            if (rawRx.isNotEmpty()) {
                CommunicationSniffer.logEvent(
                    CommEventCategory.KLINE,
                    CommDataStage.KLINE_ECHO,
                    rawBytes = body,
                    status = if (stripped.removed) "ECHO_FILTERED" else "POST_FILTER",
                    description = "RX setelah filter echo (${body.size} byte); echo_lokal=${stripped.removed}"
                )
            }

            var frame = extractFrame(body, scan, config)
            val deadline = clock() + config.timeoutMs

            while (frame == null && clock() < deadline) {
                currentCoroutineContext().ensureActive()
                val chunk = transport.read(256, 40)
                if (chunk.isNotEmpty()) {
                    body = body + chunk
                    if (body.size > config.maxRxBuffer) {
                        val dropCount = body.size - config.keepRxBuffer
                        val dropped = body.copyOfRange(0, dropCount)
                        CommunicationSniffer.logEvent(
                            CommEventCategory.RX,
                            CommDataStage.RAW_USB_IN,
                            rawBytes = dropped,
                            status = "DISCARDED",
                            description = "Buffer RX melebihi ${config.maxRxBuffer} byte; membuang $dropCount byte terlama SEBELUM parsing."
                        )
                        body = body.copyOfRange(dropCount, body.size)
                    }
                    CommunicationSniffer.logEvent(
                        CommEventCategory.RX,
                        CommDataStage.RAW_USB_IN,
                        rawBytes = chunk,
                        status = "RX_APPEND",
                        description = "RX fragment ${chunk.size} byte; total buffer ${body.size} byte"
                    )
                    frame = extractFrame(body, scan, config)
                }
            }

            lastRaw = body
            if (frame != null) {
                CommunicationSniffer.recordValidFrame()
                val service = if (frame.size > 2) frame[2].toInt() and 0xFF else -1
                val negative = service == HondaFrameCodec.NEGATIVE_RESPONSE_SERVICE
                val frameStatus = if (negative) "NEGATIVE_RESPONSE" else "VALID_FRAME"
                val header = frame[0].toInt() and 0xFF
                logger(LogDirection.RX, HondaFrameCodec.hex(frame), "RX frame valid (${frame.size} byte)")
                CommunicationSniffer.setSessionStatus(
                    CommSessionStatus.ECU_RESPONSE_VALID,
                    "Frame ECU checksum-valid diterima (${frame.size} byte, status=$frameStatus)"
                )
                CommunicationSniffer.updateTimelineStage(
                    DiagnosticStage.ECU_RESPONSE_RECEIVED,
                    true,
                    "Frame ECU valid: ${HondaFrameCodec.hex(frame)}"
                )
                CommunicationSniffer.logEvent(
                    CommEventCategory.RX,
                    CommDataStage.PARSED_RESPONSE,
                    rawBytes = frame,
                    status = frameStatus,
                    description = "K-Line frame valid: header=0x%02X declaredLen=%d actualLen=%d checksum=OK service=0x%02X"
                        .format(header, HondaFrameCodec.declaredLength(frame), frame.size, service)
                )
                if (scan.rejected.isNotEmpty()) {
                    logger(
                        LogDirection.ERR,
                        "",
                        "Kandidat sebelum frame valid ditolak: ${scan.rejected.take(3).joinToString(" | ")}"
                    )
                }
                return CommandOutcome.Frame(frame)
            }

            val bodyIsOnlyEcho = onlyOwnEchoReceived(body)
            val failureStatus = when {
                body.isEmpty() && scan.ownEchoSeen -> "LOCAL_ECHO_ONLY"
                body.isEmpty() -> "TIMEOUT"
                bodyIsOnlyEcho -> "LOCAL_ECHO_ONLY"
                scan.checksumFailures > 0 -> "CHECKSUM_FAILED"
                scan.incomplete != null -> "INCOMPLETE"
                else -> "NO_VALID_FRAME"
            }
            lastReason = when (failureStatus) {
                "LOCAL_ECHO_ONLY" -> "Hanya echo TX sendiri yang diterima; ECU tidak merespons"
                "TIMEOUT" -> "Tidak ada byte diterima dalam ${config.timeoutMs} ms"
                "CHECKSUM_FAILED" -> "Frame kandidat ditolak: checksum tidak cocok"
                "INCOMPLETE" -> "Frame K-Line belum lengkap (${scan.incomplete})"
                else -> "Tidak ada frame K-Line valid pada RX"
            }

            when (failureStatus) {
                "LOCAL_ECHO_ONLY" -> {
                    if (!stripped.removed) CommunicationSniffer.recordLocalEcho()
                    CommunicationSniffer.setSessionStatus(CommSessionStatus.LOCAL_ECHO_ONLY, lastReason)
                }
                "TIMEOUT" -> CommunicationSniffer.recordTimeout()
                "CHECKSUM_FAILED", "INCOMPLETE" -> CommunicationSniffer.recordInvalidFrame()
            }

            logger(LogDirection.ERR, HondaFrameCodec.hex(body), lastReason)
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.RAW_USB_IN,
                rawBytes = body,
                status = failureStatus,
                description = lastReason,
                errorDetails = lastReason
            )
            if (attempt < attempts - 1) {
                CommunicationSniffer.recordRetry()
                delay(60)
            }
        }

        return CommandOutcome.Failure(lastReason, lastRaw)
    }

    private suspend fun write(d: UsbTransport, data: ByteArray, interByteMs: Int): Boolean {
        if (interByteMs <= 0) {
            return d.write(data, 500) >= 0
        }
        for (i in data.indices) {
            if (d.write(byteArrayOf(data[i]), 250) < 0) return false
            if (i < data.lastIndex) delay(interByteMs.toLong())
        }
        return true
    }

    private suspend fun drainStaleInput(d: UsbTransport) {
        val stale = ByteArrayOutputStream()
        for (i in 0 until 8) {
            val chunk = d.read(512, 5)
            if (chunk.isEmpty()) break
            stale.write(chunk)
        }
        if (stale.size() > 0) {
            val bytes = stale.toByteArray()
            logger(LogDirection.RX, HondaFrameCodec.hex(bytes), "Byte RX lama dibuang sebelum TX (${bytes.size} byte)")
            CommunicationSniffer.logEvent(
                CommEventCategory.RX,
                CommDataStage.RAW_USB_IN,
                rawBytes = bytes,
                status = "DISCARDED",
                description = "Membuang ${bytes.size} byte RX lama sebelum TX."
            )
        }
    }

    private suspend fun readForWindow(
        buffer: ByteArrayOutputStream,
        d: UsbTransport,
        windowMs: Long,
        quietMs: Long
    ) {
        if (windowMs <= 0) return
        val start = clock()
        var lastByteAt = start
        while (clock() - start < windowMs) {
            currentCoroutineContext().ensureActive()
            val chunk = d.read(256, 20)
            val now = clock()
            if (chunk.isNotEmpty()) {
                buffer.write(chunk)
                lastByteAt = now
            } else if (buffer.size() > 0 && now - lastByteAt >= quietMs) {
                break
            }
        }
    }

    private fun extractFrame(bytes: ByteArray, scan: FrameScan, config: KlineWireConfig): ByteArray? {
        var i = 0
        while (i < bytes.size) {
            val header = bytes[i].toInt() and 0xFF
            if (header !in config.acceptedHeaders) {
                i++
                continue
            }
            if (i + 2 > bytes.size) {
                scan.incomplete = "${bytes.size - i} byte, header belum lengkap"
                i++
                continue
            }
            val declaredLength = bytes[i + 1].toInt() and 0xFF
            if (declaredLength < HondaFrameCodec.MIN_FRAME_LENGTH) {
                i++
                continue
            }
            if (i + declaredLength > bytes.size) {
                scan.incomplete = "${bytes.size - i} dari $declaredLength byte"
                i++
                continue
            }

            val candidate = bytes.copyOfRange(i, i + declaredLength)
            if (isOwnEcho(candidate)) {
                scan.ownEchoSeen = true
                if (scan.rejected.size < 16) {
                    scan.rejected.add("echo TX sendiri: ${HondaFrameCodec.hex(candidate).take(60)}")
                }
                i += declaredLength
                continue
            }

            if (HondaFrameCodec.isWellFormed(candidate, config.acceptedHeaders)) {
                return candidate
            }

            scan.checksumFailures++
            if (scan.rejected.size < 16) {
                scan.rejected.add(
                    "0x%02X len=%d checksum gagal: %s".format(
                        candidate[0].toInt() and 0xFF,
                        candidate.size,
                        HondaFrameCodec.hex(candidate).take(60)
                    )
                )
            }
            i++
        }
        return null
    }

    private fun registerOwnTx(frame: ByteArray) {
        val now = clock()
        recentTxFrames.addLast(SentFrame(frame.copyOf(), now))
        while (recentTxFrames.size > MAX_RECENT_TX_FRAMES) recentTxFrames.removeFirst()
        pruneRecentTx(now)
    }

    private fun pruneRecentTx(now: Long) {
        while (recentTxFrames.isNotEmpty() && now - recentTxFrames.first().sentAtMs > MAX_RECENT_TX_WINDOW_MS) {
            recentTxFrames.removeFirst()
        }
    }

    private fun isOwnEcho(candidate: ByteArray): Boolean {
        pruneRecentTx(clock())
        return recentTxFrames.any { it.bytes.contentEquals(candidate) }
    }

    private fun onlyOwnEchoReceived(body: ByteArray): Boolean {
        pruneRecentTx(clock())
        var i = 0
        var sawEcho = false
        while (i < body.size) {
            val value = body[i].toInt() and 0xFF
            if (value == 0x00 || value == 0xFF) {
                i++
                continue
            }
            val remaining = body.copyOfRange(i, body.size)
            val full = recentTxFrames.firstOrNull {
                remaining.size >= it.bytes.size &&
                    remaining.copyOfRange(0, it.bytes.size).contentEquals(it.bytes)
            }
            if (full != null) {
                i += full.bytes.size
                sawEcho = true
                continue
            }
            val partial = recentTxFrames.any {
                remaining.size < it.bytes.size &&
                    it.bytes.copyOfRange(0, remaining.size).contentEquals(remaining)
            }
            if (partial) {
                sawEcho = true
                i = body.size
            } else {
                return false
            }
        }
        return sawEcho
    }

    private companion object {
        const val MAX_RECENT_TX_WINDOW_MS = 1500L
        const val MAX_RECENT_TX_FRAMES = 32
    }
}
