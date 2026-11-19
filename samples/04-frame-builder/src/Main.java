/**
 * SAMPEL 04 - Frame builder (penyusun frame)
 *
 * Tujuan: menyusun frame K-Line lengkap dari header, service, dan params:
 *
 *   [header][length][service][params...][checksum]
 *
 * Aturan length (docs/KLINE_PROTOCOL.md): length = JUMLAH TOTAL BYTE FRAME,
 * termasuk checksum. Untuk 5 byte, length = 0x05.
 *
 * Penjelasan: pada `72 05 74 01 14`, byte 0x05 berarti "frame ini 5 byte",
 * bukan "ada 5 byte setelah length".
 *
 * Catatan penting: frame yang dibangun di sini adalah KANDIDAT request dari
 * dokumentasi docs/DTC_ENGINE.md, bukan hasil pengujian ECU fisik.
 */
public class Main {

    /**
     * Menghitung checksum tambah dua-komplemen (lihat sampel 03).
     * Input: int[] 0..255, output: int 0..255.
     */
    static int checksum(int[] bytes) {
        int sum = 0;
        for (int b : bytes) {
            sum += b & 0xFF; // hanya 8 bit
        }
        return (256 - (sum & 0xFF)) & 0xFF;
    }

    /** Format int 0..255 sebagai dua digit hex, misal 14 -> "14". */
    static String hex(int v) {
        return String.format("%02X", v & 0xFF);
    }

    /** Format seluruh array sebagai teks hex dipisah spasi. */
    static String hexList(int[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(hex(bytes[i]));
        }
        return sb.toString();
    }

    /**
     * Menyusun frame lengkap.
     *
     * @param header  byte header, misal 0x72
     * @param service byte service, misal 0x74
     * @param params  byte parameter setelah service (boleh kosong)
     * @return frame lengkap termasuk checksum
     */
    static int[] buildFrame(int header, int service, int... params) {
        // 1. length = jumlah total byte frame.
        //    header(1) + length(1) + service(1) + params(n) + checksum(1)
        int length = 1 + 1 + 1 + params.length + 1;

        // 2. Siapkan array hasil dengan ukuran yang sudah dihitung.
        int[] frame = new int[length];

        // 3. Isi header dan declared length.
        frame[0] = header & 0xFF;
        frame[1] = length;

        // 4. Isi service.
        frame[2] = service & 0xFF;

        // 5. Salin params satu per satu.
        for (int i = 0; i < params.length; i++) {
            frame[3 + i] = params[i] & 0xFF;
        }

        // 6. Hitung checksum dari semua byte SEBELUM posisi checksum.
        //    (header + length + service + params)
        int[] tanpaChecksum = new int[frame.length - 1];
        System.arraycopy(frame, 0, tanpaChecksum, 0, tanpaChecksum.length);
        frame[frame.length - 1] = checksum(tanpaChecksum);

        return frame;
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

    /** Membangun frame lalu membandingkan dengan teks acuan. */
    static void ujiFrame(String namaUji, int header, int service, int[] params, String acuan) {
        int[] frame = buildFrame(header, service, params);
        String hasil = hexList(frame);
        System.out.println("  build -> " + hasil);
        check(namaUji, hasil.equals(acuan), "(harap " + acuan + ")");

        // Pemeriksaan tambahan: jumlah seluruh frame harus 0x00 di 8 bit.
        int total = 0;
        for (int b : frame) total += b;
        check(namaUji + " jumlah=0x00", (total & 0xFF) == 0x00, "(0x" + hex(total) + ")");
    }

    public static void main(String[] args) {
        System.out.println("=== Sampel 04: Frame builder ===");

        // ------------------------------------------------------------------
        // DEMO: satu frame dibangun langkah demi langkah.
        // ------------------------------------------------------------------
        int[] demo = buildFrame(0x72, 0x74, 0x01);
        System.out.println("Demo buildFrame(0x72, 0x74, [0x01])");
        System.out.println("  header   : 0x" + hex(demo[0]));
        System.out.println("  length   : 0x" + hex(demo[1]) + " (total " + demo[1] + " byte)");
        System.out.println("  service  : 0x" + hex(demo[2]));
        System.out.println("  params   : " + hexList(new int[] {demo[3]}));
        System.out.println("  checksum : 0x" + hex(demo[demo.length - 1]));
        System.out.println("  frame    : " + hexList(demo));

        // ------------------------------------------------------------------
        // SELF-TEST: frame acuan dari dokumentasi repo (kandidat, bukan bukti).
        // ------------------------------------------------------------------
        System.out.println("-- Self-test (frame acuan dokumentasi) --");

        // Query baca DTC (docs/DTC_ENGINE.md bagian 2)
        ujiFrame("baca 74/01", 0x72, 0x74, new int[] {0x01}, "72 05 74 01 14");
        ujiFrame("baca 71/20", 0x72, 0x71, new int[] {0x20}, "72 05 71 20 F8");
        ujiFrame("baca 00/00", 0x72, 0x00, new int[] {0x00}, "72 05 00 00 89");

        // Query clear DTC (docs/DTC_ENGINE.md bagian 6)
        ujiFrame("clear 60/01", 0x72, 0x60, new int[] {0x01}, "72 05 60 01 28");
        ujiFrame("clear 74/02", 0x72, 0x74, new int[] {0x02}, "72 05 74 02 13");
        ujiFrame("clear 04", 0x72, 0x04, new int[] {}, "72 04 04 86");

        // Request live data tabel 7A (contoh sintetis di LiveDataProfileParserTest)
        ujiFrame("live 71/00", 0x72, 0x71, new int[] {0x00}, "72 05 71 00 18");
        ujiFrame("live 71/7A", 0x72, 0x71, new int[] {0x7A}, "72 05 71 7A 9E");

        // Pemeriksaan struktur: byte length harus sama dengan ukuran array.
        int[] f = buildFrame(0x72, 0x74, 0x01);
        check("length = ukuran frame", f[1] == f.length, "(length=" + f[1]
                + ", ukuran=" + f.length + ")");

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
