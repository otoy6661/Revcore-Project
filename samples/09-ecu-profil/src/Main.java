import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sampel 09: model profil ECU (EcuProfile).
 *
 * Profil menyimpan, untuk satu "family" ECU: frame identifikasi, kandidat
 * frame baca DTC, kandidat frame hapus DTC, dan spesifikasi field live data.
 * Setiap frame yang disimpan WAJIB memiliki checksum valid; frame rusak
 * ditolak saat profil dimuat sehingga tidak pernah terkirim ke kendaraan.
 *
 * Family non-baseline sengaja memiliki katalog kosong: engine harus
 * melaporkan "tidak didukung", bukan meminjam frame family lain.
 *
 * Jalankan dari direktori sampel ini:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ============================================================
    // UTILITAS FRAME
    // ============================================================
    /** checksum = (256 - (jumlah byte sebelum checksum & 0xFF)) & 0xFF */
    static int checksum(byte[] frame) {
        int sum = 0;
        for (int i = 0; i < frame.length - 1; i++) sum += frame[i] & 0xFF;
        return (256 - (sum & 0xFF)) & 0xFF;
    }

    static byte[] hexToBytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }

    static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * True bila ukuran minimal 4, byte panjang cocok dengan ukuran frame,
     * dan checksum valid.
     *
     * Catatan: pada frame baseline repo ini (mis. 72 05 74 01 14) byte
     * panjang (0x05) = ukuran seluruh frame, bukan jumlah byte setelahnya.
     * Ini berlaku sama untuk frame permintaan maupun respons
     * (72 0A 74 01 02 01 01 07 01 03, 72 06 7F 74 12 83). Aturannya ketat
     * seperti `HondaFrameCodec.isWellFormed`: tidak ada toleransi "ukuran - 1".
     */
    static boolean isValidFrame(byte[] frame) {
        if (frame.length < 4) return false;
        int declared = frame[1] & 0xFF;
        return declared == frame.length
                && checksum(frame) == (frame[frame.length - 1] & 0xFF);
    }

    // ============================================================
    // MODEL
    // ============================================================
    /** Kesalahan profil yang terdeteksi saat dimuat. */
    static class ProfileException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ProfileException(String message) {
            super(message);
        }
    }

    /** Spesifikasi satu field live data. */
    record LiveField(String name, int offset, int length, double scale, double correction, String unit) {}

    /** Profil satu family ECU. Semua frame sudah divalidasi checksum-nya. */
    static final class EcuProfile {
        final String family;
        final List<byte[]> identityRequests;
        final List<byte[]> dtcReadRequests;
        final List<byte[]> dtcClearRequests;
        final List<LiveField> liveFields;

        EcuProfile(String family,
                   List<String> identityRequests,
                   List<String> dtcReadRequests,
                   List<String> dtcClearRequests,
                   List<LiveField> liveFields) {
            if (family == null || family.isBlank()) throw new ProfileException("family kosong");
            this.family = family;
            this.identityRequests = validated(family, "identityRequests", identityRequests);
            this.dtcReadRequests = validated(family, "dtcReadRequests", dtcReadRequests);
            this.dtcClearRequests = validated(family, "dtcClearRequests", dtcClearRequests);
            this.liveFields = List.copyOf(liveFields);
        }

        private static List<byte[]> validated(String family, String category, List<String> hexFrames) {
            List<byte[]> result = new ArrayList<>();
            for (String hex : hexFrames) {
                byte[] frame = hexToBytes(hex);
                if (!isValidFrame(frame)) {
                    throw new ProfileException("family '" + family + "' / " + category
                            + ": checksum frame rusak untuk '" + hex + "'");
                }
                result.add(frame);
            }
            return Collections.unmodifiableList(result);
        }

        boolean supportsDtc() {
            return !dtcReadRequests.isEmpty() && !dtcClearRequests.isEmpty();
        }

        @Override
        public String toString() {
            return "EcuProfile(" + family + ", id=" + identityRequests.size()
                    + ", read=" + dtcReadRequests.size() + ", clear=" + dtcClearRequests.size()
                    + ", live=" + liveFields.size() + ")";
        }
    }

    // ============================================================
    // KATALOG PROFIL (data sintetis)
    // ============================================================
    static final String FAMILY_BASELINE = "contoh-baseline";
    static final String FAMILY_LAIN = "contoh-family-lain";

    /** Family baseline: memakai frame yang sudah diverifikasi di dokumentasi repo. */
    static EcuProfile baselineProfile() {
        return new EcuProfile(
                FAMILY_BASELINE,
                List.of("72 05 71 00 18"),
                List.of("72 05 74 01 14", "72 05 71 20 F8", "72 05 00 00 89"),
                List.of("72 05 60 01 28", "72 05 74 02 13", "72 04 04 86"),
                List.of(new LiveField("CONTOH_MAP", 0, 1, 1.0, 0.0, "kPa"),
                        new LiveField("CONTOH_SUHU", 1, 1, 1.0, -40.0, "C")));
    }

    /** Family lain: katalog DTC KOSONG. Engine wajib melapor "tidak didukung". */
    static EcuProfile otherFamilyProfile() {
        return new EcuProfile(FAMILY_LAIN, List.of("72 05 71 00 18"), List.of(), List.of(), List.of());
    }

    static Map<String, EcuProfile> buildCatalog() {
        Map<String, EcuProfile> catalog = new LinkedHashMap<>();
        for (EcuProfile p : List.of(baselineProfile(), otherFamilyProfile())) {
            catalog.put(p.family.toLowerCase(Locale.ROOT), p);
        }
        return catalog;
    }

    // ============================================================
    // PROGRAM UTAMA
    // ============================================================
    static int pass = 0, fail = 0;

    public static void main(String[] args) {
        System.out.println("== Sampel 09: profil ECU ==");

        Map<String, EcuProfile> catalog = buildCatalog();
        System.out.println("\n-- Katalog profil --");
        for (EcuProfile p : catalog.values()) System.out.println("  " + p);

        EcuProfile p = lookup(catalog, FAMILY_BASELINE);
        System.out.println("\n-- Profil baseline --");
        System.out.println("  baca DTC : " + hexList(p.dtcReadRequests));
        System.out.println("  hapus DTC: " + hexList(p.dtcClearRequests));
        for (LiveField f : p.liveFields) {
            System.out.println("  field    : " + f.name() + " offset=" + f.offset()
                    + " length=" + f.length() + " scale=" + f.scale()
                    + " correction=" + f.correction() + " unit=" + f.unit());
        }

        System.out.println("\n-- Pengujian --");
        testBaselineFrames(p);
        testCorruptedFrameRejected();
        testLookup(catalog);
        testEmptyCatalog();
        testLiveDataDecode(p);

        System.out.println("\nRingkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1);
        }
        System.out.println("HASIL: OK");
    }

    /** Lookup by family, tidak peka huruf besar/kecil. */
    static EcuProfile lookup(Map<String, EcuProfile> catalog, String family) {
        EcuProfile p = catalog.get(family.toLowerCase(Locale.ROOT));
        if (p == null) throw new ProfileException("family tidak dikenal: " + family);
        return p;
    }

    static String hexList(List<byte[]> frames) {
        List<String> out = new ArrayList<>();
        for (byte[] f : frames) out.add(bytesToHex(f));
        return out.toString();
    }

    static void testBaselineFrames(EcuProfile p) {
        check("3 frame baca DTC", p.dtcReadRequests.size() == 3);
        check("baca #1 = 72 05 74 01 14", equalsHex(p.dtcReadRequests.get(0), "72 05 74 01 14"));
        check("baca #2 = 72 05 71 20 F8", equalsHex(p.dtcReadRequests.get(1), "72 05 71 20 F8"));
        check("baca #3 = 72 05 00 00 89", equalsHex(p.dtcReadRequests.get(2), "72 05 00 00 89"));
        check("3 frame hapus DTC", p.dtcClearRequests.size() == 3);
        check("hapus #1 = 72 05 60 01 28", equalsHex(p.dtcClearRequests.get(0), "72 05 60 01 28"));
        check("hapus #2 = 72 05 74 02 13", equalsHex(p.dtcClearRequests.get(1), "72 05 74 02 13"));
        check("hapus #3 = 72 04 04 86", equalsHex(p.dtcClearRequests.get(2), "72 04 04 86"));
        check("semua frame lolos validasi checksum", allValid(p.dtcReadRequests) && allValid(p.dtcClearRequests));
    }

    static void testCorruptedFrameRejected() {
        // Frame baca DTC sengaja dirusak: checksum 0x14 diubah menjadi 0x15.
        String corrupted = "72 05 74 01 15";
        // (Contoh lain: byte panjang salah, "72 06 74 01 14", juga ditolak.)
        check("checksum frame rusak terdeteksi cepat", !isValidFrame(hexToBytes(corrupted)));
        String msg = null;
        try {
            new EcuProfile("rusak", List.of("72 05 71 00 18"), List.of(corrupted), List.of(), List.of());
        } catch (ProfileException e) {
            msg = e.getMessage();
        }
        if (msg != null) System.out.println("   pesan  : " + msg);
        check("profil dengan frame rusak ditolak saat dimuat", msg != null);
    }

    static void testLookup(Map<String, EcuProfile> catalog) {
        check("lookup family baseline berhasil", lookup(catalog, FAMILY_BASELINE).family.equals(FAMILY_BASELINE));
        boolean thrown = false;
        try {
            lookup(catalog, "family-tidak-ada");
        } catch (ProfileException e) {
            thrown = true;
        }
        check("lookup family asing ditolak", thrown);
    }

    static void testEmptyCatalog() {
        EcuProfile other = lookup(buildCatalog(), FAMILY_LAIN);
        check("family lain punya katalog DTC kosong", !other.supportsDtc());
        check("family lain tidak meminjam frame baseline",
                other.dtcReadRequests.isEmpty() && other.dtcClearRequests.isEmpty());
    }

    static void testLiveDataDecode(EcuProfile p) {
        // Dua byte payload sintetis: 100 kPa, suhu 25 C (byte 65 dikurangi 40).
        byte[] payload = { (byte) 0x64, (byte) 0x41 };
        LiveField map = p.liveFields.get(0);
        LiveField suhu = p.liveFields.get(1);
        double mapValue = decode(payload, map);
        double suhuValue = decode(payload, suhu);
        check("decode MAP = 100 kPa", Math.abs(mapValue - 100.0) < 1e-9);
        check("decode SUHU = 25 C", Math.abs(suhuValue - 25.0) < 1e-9);
    }

    /** Konversi: nilai = byte * scale + correction. */
    static double decode(byte[] payload, LiveField f) {
        long raw = 0;
        for (int i = 0; i < f.length(); i++) raw = (raw << 8) | (payload[f.offset() + i] & 0xFF);
        return raw * f.scale() + f.correction();
    }

    static boolean allValid(List<byte[]> frames) {
        for (byte[] f : frames) if (!isValidFrame(f)) return false;
        return true;
    }

    static boolean equalsHex(byte[] frame, String expected) {
        return bytesToHex(frame).equals(expected);
    }

    static void check(String label, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + label);
    }
}
