import java.util.ArrayList;
import java.util.List;

/**
 * Sampel 12: sesi diagnostik.
 *
 * Alur: connect -> identify -> read DTC -> clear DTC -> re-read (verifikasi).
 *
 * Tiga aturan yang tidak boleh dilanggar:
 *   1. SUCCESS_VERIFIED hanya boleh keluar setelah pembacaan ulang yang valid
 *      (OK_EMPTY). Tanpa bukti baca ulang, statusnya CLEARED_UNVERIFIED.
 *   2. NO_RESPONSE tidak boleh mengosongkan daftar DTC sebelumnya.
 *   3. Tanpa ACK yang valid dari ECU, status clear adalah REJECTED_OR_TIMEOUT.
 *
 * Status dan maknanya mengikuti docs/DTC_ENGINE.md. Clear TIDAK menjamin
 * lampu MIL padam; verifikasi hanya membuktikan ECU tidak lagi melaporkan
 * kode pada pembacaan ulang saat itu.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ============================================================
    // STATUS HASIL
    // ============================================================
    /** Status baca DTC. */
    enum ReadStatus { NOT_PERFORMED, OK_EMPTY, OK_CODES, NO_RESPONSE, UNRECOGNIZED }

    /** Status hapus DTC. */
    enum ClearStatus { SUCCESS_VERIFIED, CLEARED_UNVERIFIED, RE_DETECTED_ACTIVE_FAULT, REJECTED_OR_TIMEOUT }

    // ============================================================
    // UTILITAS FRAME
    // ============================================================
    static int checksum(byte[] body) {
        int s = 0;
        for (byte b : body) s += b & 0xFF;
        return (256 - (s & 0xFF)) & 0xFF;
    }

    static boolean isValidFrame(byte[] f) {
        if (f == null || f.length < 4) return false;
        byte[] body = java.util.Arrays.copyOf(f, f.length - 1);
        return checksum(body) == (f[f.length - 1] & 0xFF);
    }

    static byte[] hexToBytes(String hex) {
        String[] p = hex.trim().split("\\s+");
        byte[] out = new byte[p.length];
        for (int i = 0; i < p.length; i++) out[i] = (byte) Integer.parseInt(p[i], 16);
        return out;
    }

    static String hex(byte[] d) {
        if (d == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    // ============================================================
    // TRANSPORT SEDERHANA (hanya untuk sampel ini)
    // ============================================================
    interface Transport {
        void open();

        void close();

        /** Mengirim frame; respons berikutnya diambil lewat read(). */
        void write(byte[] request);

        /** null = tidak ada respons. */
        byte[] read(int timeoutMs);
    }

    /** ECU simulasi berurutan: respons diambil dari skenario yang ditentukan. */
    static final class ScriptedEcu implements Transport {
        final List<byte[]> responses;
        private int step = 0;
        private byte[] pending;

        ScriptedEcu(List<byte[]> responses) {
            this.responses = responses;
        }

        @Override
        public void open() { }

        @Override
        public void close() { }

        @Override
        public void write(byte[] request) {
            if (!isValidFrame(request)) { pending = null; step++; return; }
            pending = step < responses.size() ? responses.get(step) : null;
            step++;
        }

        @Override
        public byte[] read(int timeoutMs) {
            byte[] r = pending;
            pending = null;
            return r;
        }
    }

    // ============================================================
    // LOGIKA SESI
    // ============================================================
    static final String[] READ_QUERIES = { "72 05 74 01 14", "72 05 71 20 F8", "72 05 00 00 89" };
    static final String[] CLEAR_QUERIES = { "72 05 60 01 28", "72 05 74 02 13", "72 04 04 86" };

    /** Menganalisis payload respons baca DTC. */
    static class ReadResult {
        ReadStatus status;
        List<String> codes = new ArrayList<>();
    }

    /**
     * Parsing payload respons baca DTC.
     *
     * Frame: [hdr][len][svc][subfungsi][data...][ck]. Subfungsi dilewati
     * (indeks 3), jadi payload = byte indeks 4 sampai sebelum checksum.
     * Untuk frame katalog 72 0A 74 01 02 01 01 07 01 03, payload =
     * 02 01 01 07 01 -> count=2 lalu pasangan (01,01)=P0107 dan
     * (07,01)=P0117, sesuai docs/DTC_ENGINE.md.
     *
     * Respons negatif 0x7F bukan daftar DTC dan tidak boleh menjadi
     * "tidak ada DTC".
     */
    static ReadResult parseDtcResponse(byte[] frame) {
        ReadResult r = new ReadResult();
        if (!isValidFrame(frame)) {
            r.status = ReadStatus.NO_RESPONSE;
            return r;
        }
        int service = frame[2] & 0xFF;
        if (service == 0x7F) {              // respons negatif, bukan daftar DTC
            r.status = ReadStatus.UNRECOGNIZED;
            return r;
        }
        int from = Math.min(4, frame.length - 1);
        int to = frame.length - 1;           // sebelum checksum
        byte[] payload = java.util.Arrays.copyOfRange(frame, from, to);
        if (payload.length == 0) {
            r.status = ReadStatus.UNRECOGNIZED;
            return r;
        }
        int count = payload[0] & 0xFF;
        if (count == 0) {                    // count 0 -> "tidak ada DTC"
            r.status = ReadStatus.OK_EMPTY;
            return r;
        }
        if (count == 0x03 && payload.length == 1) {
            // Echo service 0x03 tanpa pasangan -> "tidak ada DTC" (docs/DTC_ENGINE.md).
            r.status = ReadStatus.OK_EMPTY;
            return r;
        }
        if (count > 10 || payload.length < 1 + count * 2) {
            r.status = ReadStatus.UNRECOGNIZED;
            return r;
        }
        for (int i = 0; i < count; i++) {
            int code = payload[1 + i * 2] & 0xFF;
            int sub = payload[2 + i * 2] & 0xFF;
            if (code == 0 || code > 99 || sub > 15) {
                r.status = ReadStatus.UNRECOGNIZED;
                r.codes.clear();
                return r;
            }
            r.codes.add(standardCode(code, sub));
        }
        r.status = ReadStatus.OK_CODES;
        return r;
    }

    /**
     * Pemetaan pasangan (kode, sub) ke kode standar.
     *
     * Hanya dua pasangan yang dipakai contoh ini dan keduanya tercatat di
     * examples/dtc/dtc_examples.json serta HondaDtcDatabase.kt:
     *   (0x01, 0x01) -> P0107   (MAP)
     *   (0x07, 0x01) -> P0117   (ECT/EOT)
     * Kode lain TIDAK ditebak: ditampilkan sebagai kode mentah
     * "DTC 0xXXYY" dengan penanda belum terverifikasi.
     */
    static String standardCode(int code, int sub) {
        if (code == 0x01 && sub == 0x01) return "P0107";
        if (code == 0x07 && sub == 0x01) return "P0117";
        return String.format("DTC 0x%02X%02X (belum terverifikasi)", code, sub);
    }

    /** Mengenali frame ACK hapus DTC: bukan 0x7F dan header-nya dikenal. */
    static boolean isClearAck(byte[] frame) {
        if (!isValidFrame(frame)) return false;
        int service = frame[2] & 0xFF;
        if (service == 0x7F) return false;
        int header = frame[0] & 0xFF;
        return header == 0x02 || header == 0x72 || header == 0x7E || header == 0x7C;
    }

    /** Keadaan satu sesi. */
    static final class Session {
        final Transport transport;
        /** Daftar DTC terakhir yang diketahui terbaca valid (cache tampilan). */
        ReadResult lastRead = new ReadResult();
        /** Hasil percobaan pembacaan terakhir, termasuk percobaan yang gagal. */
        ReadResult lastAttempt;
        /** Hasil baca DTC pertama (sebelum clear), disimpan terpisah untuk pemeriksaan. */
        ReadResult firstRead;
        ClearStatus clearStatus;
        final List<String> steps = new ArrayList<>();

        Session(Transport t) {
            this.transport = t;
            lastRead.status = ReadStatus.NOT_PERFORMED;
            lastAttempt = lastRead;
            clearStatus = null;
        }

        String identify() {
            transport.write(hexToBytes("72 05 71 00 18"));
            byte[] r = transport.read(500);
            String result = (r != null && isValidFrame(r) && (r[2] & 0xFF) == 0x71)
                    ? "OK identity" : "NO_RESPONSE";
            steps.add("identify -> " + result);
            return result;
        }

        ReadResult readDtcs(String label) {
            ReadResult result = new ReadResult();
            result.status = ReadStatus.NO_RESPONSE;
            for (String q : READ_QUERIES) {
                transport.write(hexToBytes(q));
                byte[] r = transport.read(500);
                if (r == null) continue;               // coba kandidat berikutnya
                result = parseDtcResponse(r);
                break;
            }
            lastAttempt = result;
            if (result.status == ReadStatus.NO_RESPONSE) {
                // Aturan 2: jangan kosongkan daftar lama.
                result.codes = new ArrayList<>(lastRead.codes);
            } else {
                lastRead = result;
            }
            steps.add(label + " -> " + result.status + " " + result.codes);
            return result;
        }

        ClearStatus clearDtcs() {
            boolean acked = false;
            for (String q : CLEAR_QUERIES) {
                transport.write(hexToBytes(q));
                byte[] r = transport.read(500);
                if (r != null && isClearAck(r)) {
                    acked = true;
                    break;
                }
            }
            if (!acked) {
                clearStatus = ClearStatus.REJECTED_OR_TIMEOUT;
                steps.add("clear -> REJECTED_OR_TIMEOUT");
                return clearStatus;
            }
            steps.add("clear -> ACK diterima");
            // Aturan 1: verifikasi hanya lewat pembacaan ulang.
            ReadResult verify = readDtcs("re-read");
            switch (verify.status) {
                case OK_EMPTY:
                    clearStatus = ClearStatus.SUCCESS_VERIFIED;
                    break;
                case OK_CODES:
                    clearStatus = ClearStatus.RE_DETECTED_ACTIVE_FAULT;
                    break;
                default: // NO_RESPONSE dan UNRECOGNIZED
                    clearStatus = ClearStatus.CLEARED_UNVERIFIED;
                    break;
            }
            steps.add("clear -> " + clearStatus);
            return clearStatus;
        }
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        System.out.println("== Sampel 12: sesi diagnostik ==");

        byte[] idResp = hexToBytes("72 06 71 00 01 02 03 11");
        byte[] dtcResp = hexToBytes("72 0A 74 01 02 01 01 07 01 03"); // P0107, P0117
        byte[] ackResp = hexToBytes("72 05 60 01 00 28");

        // Skenario A: satu DTC -> OK_CODES; ACK; baca ulang kosong -> SUCCESS_VERIFIED.
        List<byte[]> scenA = List.of(idResp, dtcResp, ackResp, emptyDtcFrame());
        // Skenario B: ACK, tetapi DTC muncul lagi -> RE_DETECTED_ACTIVE_FAULT.
        List<byte[]> scenB = List.of(idResp, dtcResp, ackResp, dtcResp);
        // Skenario C: ACK, tetapi baca ulang tidak ada respons -> CLEARED_UNVERIFIED.
        // Arrays.asList dipakai (bukan List.of) karena null = "tidak ada respons".
        List<byte[]> scenC = java.util.Arrays.asList(idResp, dtcResp, ackResp, null);

        Session a = runScenario("Skenario A: DTC hilang setelah clear", scenA);
        Session b = runScenario("Skenario B: DTC muncul lagi", scenB);
        Session c = runScenario("Skenario C: baca ulang tanpa respons", scenC);

        System.out.println("\n-- Pengujian --");
        check("A: baca awal = OK_CODES dengan P0107 dan P0117",
                a.firstRead != null && a.firstRead.status == ReadStatus.OK_CODES
                        && a.firstRead.codes.equals(List.of("P0107", "P0117")));
        check("A: clear = SUCCESS_VERIFIED", a.clearStatus == ClearStatus.SUCCESS_VERIFIED);
        check("B: clear = RE_DETECTED_ACTIVE_FAULT", b.clearStatus == ClearStatus.RE_DETECTED_ACTIVE_FAULT);
        check("C: clear = CLEARED_UNVERIFIED", c.clearStatus == ClearStatus.CLEARED_UNVERIFIED);
        check("C: NO_RESPONSE tidak mengosongkan daftar DTC (daftar lama tetap tertahan)",
                c.lastAttempt != null && c.lastAttempt.status == ReadStatus.NO_RESPONSE
                        && c.lastAttempt.codes.equals(List.of("P0107", "P0117"))
                        && c.lastRead.codes.equals(List.of("P0107", "P0117")));
        check("status baca awal sebelum sesi = NOT_PERFORMED",
                new Session(new ScriptedEcu(List.of())).lastRead.status == ReadStatus.NOT_PERFORMED);
        check("respons negatif 0x7F dibaca sebagai UNRECOGNIZED",
                parseDtcResponse(hexToBytes("72 06 7F 74 12 83")).status == ReadStatus.UNRECOGNIZED);
        check("respons 0x7F bukan OK_EMPTY",
                parseDtcResponse(hexToBytes("72 06 7F 74 12 83")).status != ReadStatus.OK_EMPTY);

        // Tanpa ACK sama sekali -> REJECTED_OR_TIMEOUT.
        List<byte[]> scenD = java.util.Arrays.asList(idResp, dtcResp, null, null, null);
        Session d = runScenario("Skenario D: tidak ada ACK clear", scenD);
        check("D: tanpa ACK -> REJECTED_OR_TIMEOUT", d.clearStatus == ClearStatus.REJECTED_OR_TIMEOUT);
        check("D: tidak pernah SUCCESS_VERIFIED tanpa ACK", d.clearStatus != ClearStatus.SUCCESS_VERIFIED);

        // ACK ada tetapi baca ulang tidak dapat diparse -> CLEARED_UNVERIFIED.
        List<byte[]> scenE = List.of(idResp, dtcResp, ackResp, hexToBytes("72 06 7F 74 12 83"));
        Session e = runScenario("Skenario E: baca ulang negatif", scenE);
        check("E: ACK + baca ulang 0x7F -> CLEARED_UNVERIFIED",
                e.clearStatus == ClearStatus.CLEARED_UNVERIFIED);

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK (sesi simulasi; MIL tidak dijamin padam)");
    }

    /** Membangun frame "tidak ada DTC" sintetis: 72 04 74 01 03 12. */
    static byte[] emptyDtcFrame() {
        // [hdr 72][len 04][svc 74][sub 01][echo+count 03][ck 12] -> payload "03" (echo, tanpa kode).
        return hexToBytes("72 04 74 01 03 12");
    }

    static Session runScenario(String title, List<byte[]> responses) {
        System.out.println("\n-- " + title + " --");
        Session s = new Session(new ScriptedEcu(responses));
        s.transport.open();
        s.identify();
        s.firstRead = s.readDtcs("read");
        s.clearDtcs();
        s.transport.close();
        for (String step : s.steps) System.out.println("  " + step);
        return s;
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
