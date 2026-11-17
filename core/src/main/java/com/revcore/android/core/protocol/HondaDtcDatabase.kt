package com.revcore.android.core.protocol

object HondaDtcDatabase {
    val KNOWN_DTCS: List<HondaDtc> = listOf(
        HondaDtc(1, 1, "P0107", 1, "MAP Sensor Voltage Low", "Tegangan Sensor MAP Rendah (Hubung Singkat ke Ground)", "Poor engine acceleration, stalling, erratic idle.", "Akselerasi mesin lambat, mudah mati, langsam tidak stabil."),
        HondaDtc(1, 2, "P0108", 1, "MAP Sensor Voltage High", "Tegangan Sensor MAP Tinggi (Sirkuit Terbuka / Sensor Lepas)", "Rich fuel mixture, black exhaust smoke, poor fuel economy.", "Campuran bahan bakar terlalu kaya, asap knalpot hitam, boros bensin."),
        HondaDtc(7, 1, "P0117", 7, "ECT / EOT Sensor Voltage Low", "Tegangan Sensor ECT/EOT Rendah (Suhu Terbaca Terlalu Tinggi / Short)", "Hard start when engine is cold, radiator fan runs continuously.", "Sulit starter saat mesin dingin, kipas radiator menyala terus menerus."),
        HondaDtc(7, 2, "P0118", 7, "ECT / EOT Sensor Voltage High (Sensor Disconnected)", "Tegangan Sensor ECT/EOT Tinggi (Sirkuit Terbuka / Sensor Dilepas)", "Rough idling, high fuel consumption, engine hesitation.", "Langsam kasar, konsumsi bahan bakar meningkat, tarikan tersendat."),
        HondaDtc(7, 0, "P0115", 7, "ECT / EOT Sensor Circuit Malfunction", "Sirkuit Sensor Suhu Mesin ECT/EOT Bermasalah", "Engine hesitation, abnormal idle temperature compensation.", "Tarikan tersendat, kompensasi suhu langsam tidak normal."),
        HondaDtc(8, 1, "P0122", 8, "TP Sensor Voltage Low", "Tegangan Sensor TP Rendah (Hubung Singkat ke Ground)", "Poor throttle response, engine stalls when opening gas throttle.", "Respon gas lambat, mesin mati saat bukaan gas mendadak."),
        HondaDtc(8, 2, "P0123", 8, "TP Sensor Voltage High", "Tegangan Sensor TP Tinggi (Sirkuit Terbuka / 5V Short)", "Engine revs uncontrollably, erratic idle speed.", "Putaran mesin meraung sendiri, putaran stasioner tidak normal."),
        HondaDtc(9, 1, "P0112", 9, "IAT Sensor Voltage Low", "Tegangan Sensor Suhu Udara Masuk (IAT) Rendah", "Engine runs slightly rough during weather temperature changes.", "Performa mesin sedikit menurun saat perubahan suhu lingkungan."),
        HondaDtc(9, 2, "P0113", 9, "IAT Sensor Voltage High", "Tegangan Sensor Suhu Udara Masuk (IAT) Tinggi (Sirkuit Terbuka)", "Slightly increased fuel consumption, hesitation on rapid throttle.", "Konsumsi bensin bertambah, sedikit jeda saat akselerasi cepat."),
        HondaDtc(11, 1, "P0500", 11, "Vehicle Speed (VS) Sensor Circuit Malfunction", "Sirkuit Sensor Kecepatan Kendaraan (VS) Bermasalah", "Speedometer inoperative, ISS idling stop system disabled.", "Speedometer tidak berfungsi, sistem idling stop ISS mati."),
        HondaDtc(12, 1, "P0201", 12, "Fuel Injector Circuit Malfunction", "Sirkuit Injektor Bahan Bakar Bermasalah", "Engine will not start, no fuel injection spray.", "Mesin tidak dapat hidup sama sekali, tidak ada semprotan injektor."),
        HondaDtc(21, 1, "P0135", 21, "O2 Oxygen Sensor Circuit Malfunction", "Sirkuit Sensor Oksigen (O2) Bermasalah", "Poor exhaust emissions, sluggish fuel trim feedback.", "Emisi gas buang memburuk, koreksi bahan bakar lambat."),
        HondaDtc(29, 1, "P0505", 29, "IACV Idle Air Control Valve Circuit Malfunction", "Sirkuit IACV (Katup Kontrol Udara Langsam) Bermasalah", "Engine stalls on deceleration, unable to hold idle rpm.", "Mesin mati saat deselerasi atau lepas gas, langsam tidak bisa stabil."),
        HondaDtc(33, 1, "P0605", 33, "ECM Internal Memory (EEPROM / Flash Checksum) Error", "Kesalahan Memori Internal ECM (EEPROM / Checksum Flash)", "ECM operating in emergency failsafe mode or fails to boot correctly.", "ECM beroperasi dalam mode darurat failsafe atau gagal boot normal."),
        HondaDtc(54, 1, "P1601", 54, "Bank Angle Sensor (BAS / Rollover) Circuit Malfunction", "Sensor Kemiringan / Jatuh (Bank Angle Sensor) Aktif / Bermasalah", "Ignition cut and fuel cut triggered, engine will not run.", "Pengapian dan suplai bensin diputus, mesin tidak mau menyala.")
    )

    fun findKnown(code: Int, subCode: Int): HondaDtc? =
        KNOWN_DTCS.firstOrNull { it.codeNumber == code && it.subCode == subCode }

    private fun unknownDtc(code: Int, subCode: Int): HondaDtc = HondaDtc(
        codeNumber = code,
        subCode = subCode,
        standardCode = String.format("DTC %02X%02X", code, subCode),
        blinkCode = code,
        titleEn = String.format("Unverified ECM code 0x%02X%02X", code, subCode),
        titleId = String.format("Kode ECM 0x%02X%02X (belum terverifikasi)", code, subCode),
        symptomEn = "No verified description in the RevCore database. Consult the Honda service manual.",
        symptomId = "Tidak ada arti terverifikasi di database RevCore. Rujuk buku servis resmi Honda.",
        isKnown = false
    )

    private fun decode(code: Int, subCode: Int): HondaDtc =
        findKnown(code, subCode) ?: unknownDtc(code, subCode)

    /**
     * Strictly parses a checksum-validated DTC response frame.
     *
     * Supported layouts (evidence from the reference implementation):
     * - content = [service echo 0x03][pairs...]
     * - content = [table echo 0x00][pairs...]
     * - content = [count][code, sub, ...]
     * - content = [code, sub, ...] (no echo)
     *
     * Each pair is decoded as (high byte, low byte); a pair that matches the
     * RevCore database is described, otherwise the raw bytes are shown without
     * inventing a standard P-code. Returns null when the structure cannot be
     * interpreted with confidence.
     */
    fun parseDtcResponse(frame: ByteArray): DtcParse? {
        if (frame.size < 4) return null
        if ((frame[1].toInt() and 0xFF) != frame.size) return null
        if (!HondaChecksum.validatePacket(frame)) return null

        var data = frame.copyOfRange(2, frame.size - 1)
        if (data.isEmpty()) return DtcParse(emptyList(), "empty-payload")

        var format = "plain-pairs"
        val first = data[0].toInt() and 0xFF
        if (first == 0x03 || first == 0x00) {
            if (data.size == 1) return DtcParse(emptyList(), "echo-only")
            data = data.copyOfRange(1, data.size)
            format = "echo+pairs"
        }

        if (data.isEmpty()) return DtcParse(emptyList(), format)

        // Optional count prefix: count in 0..20 followed by exactly count pairs.
        val count = data[0].toInt() and 0xFF
        if (count in 0..20 && data.size == 1 + count * 2) {
            if (count == 0) return DtcParse(emptyList(), "count-prefixed")
            val parsed = ArrayList<HondaDtc>(count)
            for (i in 0 until count) {
                val hi = data[1 + i * 2].toInt() and 0xFF
                val lo = data[2 + i * 2].toInt() and 0xFF
                if ((hi shl 8) or lo == 0x0000) continue
                parsed.add(decode(hi, lo))
            }
            return DtcParse(parsed.distinctBy { it.codeNumber to it.subCode }, "count-prefixed")
        }

        if (data.size % 2 != 0) return null
        if (data.size > 64) return null

        val parsed = ArrayList<HondaDtc>()
        var i = 0
        while (i + 1 < data.size) {
            val hi = data[i].toInt() and 0xFF
            val lo = data[i + 1].toInt() and 0xFF
            val raw = (hi shl 8) or lo
            if (raw != 0x0000 && raw != 0xFFFF) {
                parsed.add(decode(hi, lo))
            }
            i += 2
        }
        return DtcParse(parsed.distinctBy { it.codeNumber to it.subCode }, format)
    }

    // Service IDs and status bytes that must never be interpreted as blink codes.
    private val PROTOCOL_HEADER_BYTES = setOf(
        0x02, 0x71, 0x72, 0x74, 0x60, 0x04, 0xC1, 0xC4, 0xB0, 0x80, 0x7F, 0x00, 0xFF
    )

    /**
     * Baseline DTC payload parser. Accepts a count-prefixed list or a direct
     * [code, subCode] pair stream and only keeps blink codes in 1..99.
     */
    fun parseDtcPacket(payload: ByteArray): List<HondaDtc> {
        val list = mutableListOf<HondaDtc>()
        if (payload.isEmpty()) return list

        val firstByte = payload[0].toInt() and 0xFF
        if (firstByte in 1..10 && payload.size >= 1 + firstByte * 2) {
            var validCountPrefixed = true
            val tempList = mutableListOf<HondaDtc>()
            for (c in 0 until firstByte) {
                val code = payload[1 + c * 2].toInt() and 0xFF
                val subCode = payload[2 + c * 2].toInt() and 0xFF
                if (code in 1..99 && code !in PROTOCOL_HEADER_BYTES) {
                    tempList.add(decode(code, subCode))
                } else {
                    validCountPrefixed = false
                    break
                }
            }
            if (validCountPrefixed && tempList.isNotEmpty()) {
                return tempList.distinctBy { it.codeNumber to it.subCode }
            }
        }

        var i = 0
        while (i < payload.size) {
            val code = payload[i].toInt() and 0xFF
            if (code == 0 || code == 0xFF || code in PROTOCOL_HEADER_BYTES || code !in 1..99) {
                i++
                continue
            }
            val rawSub = if (i + 1 < payload.size) payload[i + 1].toInt() and 0xFF else 0
            val subCode = if (rawSub in 0..15) rawSub else 0
            list.add(decode(code, subCode))
            i += 2
        }

        return list.distinctBy { it.codeNumber to it.subCode }
    }
}
