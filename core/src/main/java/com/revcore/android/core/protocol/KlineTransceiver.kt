package com.revcore.android.core.protocol

import com.revcore.android.core.usb.UsbTransport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream

sealed class CommandOutcome {
    data class Frame(val bytes: ByteArray) : CommandOutcome()
    data class Failure(val reason: String, val raw: ByteArray) : CommandOutcome()
}

/**
 * Transaction runner for the Honda K-Line link.
 *
 * A received candidate is accepted only when it is checksum-valid and is not
 * byte-identical to a frame this adapter transmitted within
 * [RECENT_TX_WINDOW_MS]. The window spans retries so a delayed local echo from
 * an earlier attempt is never promoted to an ECU response. Exact identity with
 * our own TX is the only rejection criterion; header/length similarity alone
 * never discards a frame.
 */
internal class KlineTransceiver(
    private val logger: (LogDirection, String, String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class SentFrame(val bytes: ByteArray, val sentAtMs: Long)

    private class FrameScan {
        val rejected = mutableListOf<String>()
        var ownEchoSeen = false
        var checksumFailures = 0
        var incomplete: String? = null
    }

    private data class EchoStripResult(val body: ByteArray, val removed: Boolean)

    private val recentTxFrames = ArrayDeque<SentFrame>()

    fun reset() {
        recentTxFrames.clear()
    }

    suspend fun transceive(
        d: UsbTransport,
        command: ByteArray,
        timeoutMs: Int,
        retries: Int,
        interByteMs: Int,
        echoWindowMs: Long = 80,
        quietMs: Long = 35,
        postWriteDelayMs: Long = 0
    ): CommandOutcome {
        val attempts = retries.coerceAtLeast(1)
        var lastRaw = ByteArray(0)
        var lastReason = "Tidak ada respons"

        for (attempt in 0 until attempts) {
            currentCoroutineContext().ensureActive()
            // Baseline purges the hardware buffers before every request so a
            // stale byte can never be promoted to a reply for this command.
            if (!d.purgeRxTx()) {
                CommunicationSniffer.recordUsbError()
                logger(LogDirection.ERR, "", "Adapter gagal purge buffer RX/TX sebelum TX")
            }
            drainStaleInput(d)

            val commandHex = hex(command)
            logger(LogDirection.TX, commandHex, "TX K-Line ${command.size} byte (percobaan ${attempt + 1}/$attempts)")
            CommunicationSniffer.logEvent(
                CommEventCategory.TX,
                CommDataStage.RAW_USB_OUT,
                rawBytes = command,
                status = "TX_ATTEMPT",
                description = "K-Line TX ${command.size} byte (percobaan ${attempt + 1}/$attempts, inter-byte ${interByteMs} ms)"
            )
            registerOwnTx(command)

            if (!writeSlow(d, command, interByteMs)) {
                lastReason = "Gagal menulis ke endpoint USB"
                CommunicationSniffer.recordUsbError()
                logger(LogDirection.ERR, commandHex, lastReason)
                CommunicationSniffer.logEvent(
                    CommEventCategory.ERROR,
                    CommDataStage.RAW_USB_OUT,
                    rawBytes = command,
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

            if (postWriteDelayMs > 0) delay(postWriteDelayMs)

            val buffer = ByteArrayOutputStream()
            readForWindow(buffer, d, echoWindowMs, quietMs)
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
            val stripped = stripEcho(rawRx, command)
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

            var frame = extractFrame(body, scan)
            val deadline = clock() + timeoutMs

            while (frame == null && clock() < deadline) {
                currentCoroutineContext().ensureActive()
                val chunk = d.read(256, 40)
                if (chunk.isNotEmpty()) {
                    body = body + chunk
                    if (body.size > MAX_RX_BUFFER) {
                        val dropCount = body.size - KEEP_RX_BUFFER
                        val dropped = body.copyOfRange(0, dropCount)
                        // Log the exact bytes and the reason before they leave the buffer.
                        CommunicationSniffer.logEvent(
                            CommEventCategory.RX,
                            CommDataStage.RAW_USB_IN,
                            rawBytes = dropped,
                            status = "DISCARDED",
                            description = "Buffer RX melebihi $MAX_RX_BUFFER byte; membuang $dropCount byte terlama SEBELUM parsing. Alasan: batas buffer."
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
                    frame = extractFrame(body, scan)
                }
            }

            lastRaw = body
            if (frame != null) {
                CommunicationSniffer.recordValidFrame()
                val service = if (frame.size > 2) frame[2].toInt() and 0xFF else -1
                val negative = service == NEGATIVE_RESPONSE_SERVICE
                val frameStatus = if (negative) "NEGATIVE_RESPONSE" else "VALID_FRAME"
                val header = frame[0].toInt() and 0xFF
                val declaredLength = frame[1].toInt() and 0xFF
                logger(LogDirection.RX, hex(frame), "RX frame valid (${frame.size} byte)")
                CommunicationSniffer.setSessionStatus(
                    CommSessionStatus.ECU_RESPONSE_VALID,
                    "Frame ECU checksum-valid diterima (${frame.size} byte, status=$frameStatus)"
                )
                CommunicationSniffer.updateTimelineStage(
                    DiagnosticStage.ECU_RESPONSE_RECEIVED,
                    true,
                    "Frame ECU valid: ${hex(frame)}"
                )
                CommunicationSniffer.logEvent(
                    CommEventCategory.RX,
                    CommDataStage.PARSED_RESPONSE,
                    rawBytes = frame,
                    status = frameStatus,
                    description = "K-Line frame valid: header=0x%02X declaredLen=%d actualLen=%d checksum=OK service=0x%02X bytes=%s"
                        .format(header, declaredLength, frame.size, service, hex(frame))
                )
                if (scan.rejected.isNotEmpty()) {
                    logger(
                        LogDirection.ERR,
                        "",
                        "Kandidat sebelum frame valid ditolak: ${scan.rejected.take(3).joinToString(" | ")}"
                    )
                    CommunicationSniffer.logEvent(
                        CommEventCategory.KLINE,
                        CommDataStage.PROTOCOL_FRAME,
                        status = "CANDIDATES_REJECTED",
                        description = "Kandidat frame ditolak sebelum frame valid: ${scan.rejected.take(3).joinToString(" | ")}"
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
                "TIMEOUT" -> "Tidak ada byte diterima dalam ${timeoutMs} ms"
                "CHECKSUM_FAILED" -> "Frame kandidat ditolak: checksum tidak cocok"
                "INCOMPLETE" -> "Frame K-Line belum lengkap (${scan.incomplete})"
                else -> "Tidak ada frame K-Line valid pada RX: ${hex(body)}"
            }

            when (failureStatus) {
                "LOCAL_ECHO_ONLY" -> {
                    if (!stripped.removed) CommunicationSniffer.recordLocalEcho()
                    CommunicationSniffer.setSessionStatus(CommSessionStatus.LOCAL_ECHO_ONLY, lastReason)
                }
                "TIMEOUT" -> CommunicationSniffer.recordTimeout()
                "CHECKSUM_FAILED", "INCOMPLETE" -> CommunicationSniffer.recordInvalidFrame()
            }

            logger(LogDirection.ERR, hex(body), lastReason)
            CommunicationSniffer.logEvent(
                CommEventCategory.ERROR,
                CommDataStage.RAW_USB_IN,
                rawBytes = body,
                status = failureStatus,
                description = lastReason,
                errorDetails = lastReason
            )
            if (scan.rejected.isNotEmpty()) {
                logger(LogDirection.ERR, "", "Kandidat frame ditolak: ${scan.rejected.take(3).joinToString(" | ")}")
            }
            if (attempt < attempts - 1) {
                CommunicationSniffer.recordRetry()
                delay(60)
            }
        }

        return CommandOutcome.Failure(lastReason, lastRaw)
    }

    private suspend fun writeSlow(d: UsbTransport, data: ByteArray, interByteMs: Int): Boolean {
        if (interByteMs <= 0) {
            return d.write(data, 500) >= 0
        }
        var gapSum = 0L
        var gapCount = 0
        for (i in data.indices) {
            if (d.write(byteArrayOf(data[i]), 250) < 0) return false
            if (i < data.lastIndex) {
                val before = clock()
                delay(interByteMs.toLong())
                val after = clock()
                gapSum += (after - before)
                gapCount++
            }
        }
        if (gapCount > 0) {
            val average = gapSum / gapCount
            logger(
                LogDirection.SYS,
                "",
                "TX pacing host-side: diminta ${interByteMs} ms/byte, terukur rata-rata ${average} ms " +
                    "(waktu host, bukan durasi fisik di bus)"
            )
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
            logger(LogDirection.RX, hex(bytes), "Byte RX lama dibuang sebelum TX (${bytes.size} byte)")
            CommunicationSniffer.logEvent(
                CommEventCategory.RX,
                CommDataStage.RAW_USB_IN,
                rawBytes = bytes,
                status = "DISCARDED",
                description = "Membuang ${bytes.size} byte RX lama sebelum TX. Alasan: mencegah data sisa terbaca sebagai respons perintah baru."
            )
        }
    }

    private suspend fun readForWindow(
        buffer: ByteArrayOutputStream,
        d: UsbTransport,
        windowMs: Long,
        quietMs: Long
    ) {
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

    /**
     * Removes a loopback echo only when it sits at the head of the RX stream
     * (after leading 0x00 padding). An embedded byte sequence that merely
     * resembles the request is never cut out of a larger response.
     *
     * Leading 0xFF is not stripped: the reference receiver only skips 0x00 and
     * treats 0xFF as a possible data byte.
     */
    private fun stripEcho(body: ByteArray, sent: ByteArray): EchoStripResult {
        if (body.isEmpty() || sent.isEmpty()) return EchoStripResult(body, false)

        var start = 0
        while (start < body.size && body[start] == 0x00.toByte()) start++
        val trimmed = if (start > 0) body.copyOfRange(start, body.size) else body
        if (trimmed.size < sent.size) return EchoStripResult(trimmed, false)

        for (i in sent.indices) {
            if (trimmed[i] != sent[i]) return EchoStripResult(trimmed, false)
        }

        val result = trimmed.copyOfRange(sent.size, trimmed.size)
        CommunicationSniffer.recordLocalEcho()
        CommunicationSniffer.logEvent(
            CommEventCategory.KLINE,
            CommDataStage.KLINE_ECHO,
            rawBytes = sent,
            status = "ECHO_REMOVED",
            description = "Echo lokal dihapus: ${sent.size} byte di awal RX cocok 100% dengan TX sendiri"
        )
        return EchoStripResult(result, true)
    }

    private fun extractFrame(bytes: ByteArray, scan: FrameScan): ByteArray? {
        var i = 0
        while (i < bytes.size) {
            if (!KlinePacket.isValidHeader(bytes[i])) {
                i++
                continue
            }
            if (i + 2 > bytes.size) {
                scan.incomplete = "${bytes.size - i} byte, header belum lengkap"
                i++
                continue
            }
            val declaredLength = bytes[i + 1].toInt() and 0xFF
            if (declaredLength < 4) {
                i++
                continue
            }
            if (i + declaredLength > bytes.size) {
                // Do not abandon the whole buffer: a complete valid frame may
                // follow this incomplete candidate.
                scan.incomplete = "${bytes.size - i} dari $declaredLength byte"
                i++
                continue
            }

            val candidate = bytes.copyOfRange(i, i + declaredLength)
            if (isOwnEcho(candidate)) {
                scan.ownEchoSeen = true
                if (scan.rejected.size < 16) {
                    scan.rejected.add("echo TX sendiri: ${hex(candidate).take(60)}")
                }
                i += declaredLength
                continue
            }

            if (HondaChecksum.validatePacket(candidate)) {
                return candidate
            }

            scan.checksumFailures++
            if (scan.rejected.size < 16) {
                scan.rejected.add(
                    "0x%02X len=%d checksum gagal: %s".format(
                        candidate[0].toInt() and 0xFF,
                        candidate.size,
                        hex(candidate).take(60)
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
        while (recentTxFrames.isNotEmpty() && now - recentTxFrames.first().sentAtMs > RECENT_TX_WINDOW_MS) {
            recentTxFrames.removeFirst()
        }
    }

    private fun isOwnEcho(candidate: ByteArray): Boolean {
        pruneRecentTx(clock())
        return recentTxFrames.any { it.bytes.contentEquals(candidate) }
    }

    /**
     * True when the RX buffer holds only frames this adapter already
     * transmitted (plus idle padding), including a truncated echo prefix.
     */
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

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { String.format("%02X", it) }

    private companion object {
        const val RECENT_TX_WINDOW_MS = 1500L
        const val MAX_RECENT_TX_FRAMES = 32
        const val MAX_RX_BUFFER = 1024
        const val KEEP_RX_BUFFER = 512
        const val NEGATIVE_RESPONSE_SERVICE = 0x7F
    }
}
