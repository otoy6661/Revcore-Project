/**
 * SAMPEL 06 - DTC parser
 *
 * Tujuan: mengubah payload DTC menjadi daftar kode yang bisa dibaca.
 *
 * Aturan dari docs/DTC_ENGINE.md:
 *   3.1 Count-prefixed : [count][code][sub]...[code][sub]
 *       - count 1..10
 *       - payload minimal 1 + count*2 byte
 *       - setiap code harus 1..99 dan bukan byte header protokol
 *   3.2 Pair stream    : [code][sub][code][sub]... (dipakai jika 3.1 tidak valid)
 *       - byte 0x00, 0xFF, dan header protokol dilewati
 *       - subcode di luar 0..15 dianggap 0
 *
 * Prinsip: jika payload tidak dapat diparse, hasilnya ERROR (null),
 * BUKAN daftar kosong yang terlihat seperti "tidak ada DTC".
 */
public class Main {

    /** Byte header protokol yang tidak boleh dianggap kode (docs/DTC_ENGINE.md). */
    static final int[] HEADER_BYTES = {0x02, 0x72, 0x7E, 0x7C, 0x7D, 0x7B, 0xFE};

    /** Satu entri DTC hasil parsing. */
    static class Dtc {
        final int code;
        final int sub;
        final String standard;   // misal "P0107" atau "DTC 0A05"
        final String deskripsi;
        final boolean known;

        Dtc(int code, int sub, String standard, String deskripsi, boolean known) {
            this.code = code;
            this.sub = sub;
            this.standard = standard;
            this.deskripsi = deskripsi;
            this.known = known;
        }

        @Override
        public String toString() {
            return standard + " (code=" + code + ", sub=" + sub + ")";
        }
    }

    /** Tabel 15 kode yang terdokumentasi di docs/DTC_ENGINE.md bagian 4. */
    static final int[][] TABEL = {
        // Pasangan {code, sub}. Kode standar ada di TABEL_STANDAR dengan urutan sama.
        {1, 1}, {1, 2}, {7, 0}, {7, 1}, {7, 2}, {8, 1}, {8, 2}, {9, 1},
        {9, 2}, {11, 1}, {12, 1}, {21, 1}, {29, 1}, {33, 1}, {54, 1}
    };
    static final String[] TABEL_STANDAR = {
        "P0107", "P0108", "P0115", "P0117", "P0118", "P0122", "P0123", "P0112",
        "P0113", "P0500", "P0201", "P0135", "P0505", "P0605", "P1601"
    };
    static final String[] TABEL_JUDUL = {
        "MAP Sensor Voltage Low", "MAP Sensor Voltage High",
        "ECT/EOT Circuit Malfunction", "ECT/EOT Voltage Low", "ECT/EOT Voltage High",
        "TP Sensor Voltage Low", "TP Sensor Voltage High",
        "IAT Sensor Voltage Low", "IAT Sensor Voltage High",
        "Vehicle Speed Sensor", "Fuel Injector Circuit", "O2 Sensor Circuit",
        "IACV Circuit", "ECM Internal Memory", "Bank Angle Sensor"
    };

    static boolean isHeaderByte(int b) {
        for (int h : HEADER_BYTES) if (h == b) return true;
        return false;
    }

    static String hex2(int v) {
        return String.format("%02X", v & 0xFF);
    }

    /**
     * Membuat Dtc dari code/sub. Kode yang tidak ada di tabel TIDAK dikarang
     * menjadi P-code; yang dikembalikan "DTC XXYY".
     */
    static Dtc decode(int code, int sub) {
        for (int i = 0; i < TABEL.length; i++) {
            if (TABEL[i][0] == code && TABEL[i][1] == sub) {
                return new Dtc(code, sub, TABEL_STANDAR[i], TABEL_JUDUL[i], true);
            }
        }
        String raw = hex2(code) + hex2(sub);
        return new Dtc(code, sub, "DTC " + raw, "Kode ECM 0x" + raw + " (belum terverifikasi)", false);
    }

    /** Menambah ke daftar hanya jika pasangan (code, sub) belum ada. */
    static void tambahUnik(java.util.List<Dtc> list, Dtc d) {
        for (Dtc x : list) {
            if (x.code == d.code && x.sub == d.sub) return;
        }
        list.add(d);
    }

    /**
     * Parser utama. Mengembalikan null jika payload tidak dapat diparse.
     * Mengembalikan daftar kosong hanya jika payload valid tetapi memang kosong
     * secara aturan (di sampel ini tidak ada kasus seperti itu).
     */
    static java.util.List<Dtc> parseDtcPayload(int[] payload) {
        // 1. Payload kosong = gagal (bukan "tidak ada DTC").
        if (payload == null || payload.length == 0) {
            return null;
        }

        // 2. Respons negatif (service 0x7F di depan) bukan daftar DTC.
        if (payload[0] == 0x7F) {
            return null;
        }

        // 3. Coba count-prefixed.
        java.util.List<Dtc> hasil = cobaCountPrefixed(payload);
        if (hasil != null) {
            return hasil;
        }

        // 4. Jika gagal, coba pair stream.
        hasil = cobaPairStream(payload);
        return hasil.isEmpty() ? null : hasil;
    }

    /** Langkah 3.1: [count][code][sub]... Kembalikan null jika tidak valid. */
    static java.util.List<Dtc> cobaCountPrefixed(int[] p) {
        int count = p[0];
        if (count < 1 || count > 10) return null;                 // count 1..10
        if (p.length < 1 + count * 2) return null;                // minimal 1+count*2 byte

        java.util.List<Dtc> list = new java.util.ArrayList<>();
        for (int c = 0; c < count; c++) {
            int code = p[1 + c * 2];
            int sub = p[2 + c * 2];
            if (code < 1 || code > 99 || isHeaderByte(code)) return null; // batal semua
            tambahUnik(list, decode(code, sub));
        }
        return list.isEmpty() ? null : list;
    }

    /** Langkah 3.2: pindai pasangan, lewati byte kosong/header, koreksi subcode. */
    static java.util.List<Dtc> cobaPairStream(int[] p) {
        java.util.List<Dtc> list = new java.util.ArrayList<>();
        int i = 0;
        while (i < p.length) {
            int code = p[i];
            // Lewati 0x00, 0xFF, header protokol, dan kode di luar 1..99.
            if (code == 0x00 || code == 0xFF || isHeaderByte(code) || code < 1 || code > 99) {
                i++;
                continue;
            }
            int rawSub = (i + 1 < p.length) ? p[i + 1] & 0xFF : 0;
            int sub = (rawSub <= 15) ? rawSub : 0; // subcode di luar 0..15 -> 0
            tambahUnik(list, decode(code, sub));
            i += 2;
        }
        return list;
    }

    // Penghitung self-test.
    static int pass = 0;
    static int fail = 0;

    static void check(String namaUji, boolean kondisi, String detail) {
        if (kondisi) {
            pass++;
            System.out.println("PASS " + namaUji + " " + detail);
        } else {
            fail++;
            System.out.println("FAIL " + namaUji + " " + detail);
        }
    }

    static String hexList(int[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(hex2(b[i]));
        }
        return sb.toString();
    }

    /** Format daftar DTC menjadi "P0107, P0117". */
    static String ringkas(java.util.List<Dtc> list) {
        if (list == null) return "ERROR (payload tidak dapat diparse)";
        StringBuilder sb = new StringBuilder();
        for (Dtc d : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.standard);
        }
        return sb.toString();
    }

    static java.util.List<Dtc> uji(String judul, int[] payload) {
        System.out.println(judul + " payload: " + hexList(payload));
        java.util.List<Dtc> hasil = parseDtcPayload(payload);
        System.out.println("  hasil: " + ringkas(hasil));
        return hasil;
    }

    public static void main(String[] args) {
        System.out.println("=== Sampel 06: DTC parser ===");

        // ------------------------------------------------------------------
        // KASUS 1: contoh dokumentasi docs/DTC_ENGINE.md bagian 3.1.
        // Payload count-prefixed: count=02, lalu (01,01) dan (07,01).
        // ------------------------------------------------------------------
        java.util.List<Dtc> a = uji("count-prefixed", new int[] {0x02, 0x01, 0x01, 0x07, 0x01});
        check("count-prefixed", a != null && a.size() == 2
                && a.get(0).standard.equals("P0107") && a.get(1).standard.equals("P0117"),
                "(P0107, P0117)");
        check("count-prefixed diketahui", a != null && a.get(0).known && a.get(1).known, "(known)");
        check("deskripsi P0107", a != null
                && a.get(0).deskripsi.equals("MAP Sensor Voltage Low"), "(MAP Sensor Voltage Low)");

        // ------------------------------------------------------------------
        // KASUS 2: pair stream (byte pertama > 10, jadi bukan count-prefix).
        // Terdapat 0x00 dan 0xFF yang harus dilewati, dan subcode 0x20 (>15) -> 0.
        // ------------------------------------------------------------------
        java.util.List<Dtc> b = uji("pair stream", new int[] {0x00, 0x07, 0x01, 0xFF, 0x08, 0x01, 0x21, 0x20});
        check("pair stream", b != null && ringkas(b).equals("P0117, P0122, DTC 2100"),
                "(P0117, P0122, DTC 2100)");
        check("pair stream subcode >15 jadi 0", b != null
                && b.get(2).sub == 0, "(sub=0)");
        check("pair stream tidak mengarang P-code", b != null && !b.get(2).known, "(unknown)");

        // ------------------------------------------------------------------
        // KASUS 3: kode tidak dikenal tetap ditampilkan sebagai DTC XXYY.
        // ------------------------------------------------------------------
        java.util.List<Dtc> c = uji("kode tak dikenal", new int[] {0x01, 0x05, 0x0F});
        check("kode tak dikenal", c != null && c.get(0).standard.equals("DTC 050F"),
                "(DTC 050F)");
        check("deskripsi tak dikenal", c != null
                && c.get(0).deskripsi.equals("Kode ECM 0x050F (belum terverifikasi)"), "(0x050F)");

        // ------------------------------------------------------------------
        // KASUS 4 dan 5: payload yang HARUS ditolak.
        // ------------------------------------------------------------------
        java.util.List<Dtc> d = uji("respons negatif", new int[] {0x7F, 0x74, 0x12});
        check("respons negatif ditolak", d == null, "(null/ERROR)");

        java.util.List<Dtc> e = uji("payload kosong", new int[0]);
        check("payload kosong ditolak", e == null, "(null/ERROR, bukan OK_EMPTY)");

        java.util.List<Dtc> f = uji("count > panjang", new int[] {0x05, 0x01, 0x01});
        // count=5 butuh 11 byte; 3 byte -> count-prefix gagal. Pair stream:
        // 0x05 (code 5, sub 1) lalu 0x01 (code 1, sub 0). Diterima sebagai pair stream.
        check("count gagal, pair stream dipakai", f != null && f.size() == 2,
                "(2 kode)");

        java.util.List<Dtc> g = uji("hanya byte lewatan", new int[] {0x00, 0xFF, 0x72});
        check("tanpa kode ditolak", g == null, "(null/ERROR)");

        // ------------------------------------------------------------------
        // KASUS 6: duplikat dibuang.
        // ------------------------------------------------------------------
        java.util.List<Dtc> h = uji("duplikat", new int[] {0x02, 0x01, 0x01, 0x01, 0x01});
        check("duplikat dibuang", h != null && h.size() == 1, "(1 kode)");

        // ------------------------------------------------------------------
        // KASUS 7: tabel dikenal lengkap.
        // ------------------------------------------------------------------
        int[][] semua = {{1, 1}, {1, 2}, {7, 0}, {7, 1}, {7, 2}, {8, 1}, {8, 2},
                {9, 1}, {9, 2}, {11, 1}, {12, 1}, {21, 1}, {29, 1}, {33, 1}, {54, 1}};
        boolean semuaDikenal = true;
        for (int[] k : semua) semuaDikenal &= decode(k[0], k[1]).known;
        check("15 kode tabel dikenal", semuaDikenal, "(15 known)");

        // ------------------------------------------------------------------
        // Ringkasan dan exit code.
        // ------------------------------------------------------------------
        System.out.println("---");
        System.out.println("Ringkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1); // exit code non-zero agar CI mendeteksi kegagalan
        }
        System.out.println("HASIL: OK");
    }
}
