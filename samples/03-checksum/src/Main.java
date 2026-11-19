/**
 * SAMPEL 03 - Checksum tambah dua-komplemen (8 bit)
 *
 * Tujuan: menghitung byte checksum untuk frame K-Line Honda yang dipakai
 * RevCore. Frame:
 *   [header][length][service][params...][checksum]
 *
 * Rumus: checksum = (256 - (jumlah_semua_byte_sebelum_checksum & 0xFF)) & 0xFF
 *
 * Akibatnya, jika seluruh frame (termasuk checksum) dijumlahkan,
 * hasil 8-bit terendahnya selalu 0x00.
 *
 * Pemakaian:
 *   java -cp out Main                    -> jalankan demo + self-test
 *   java -cp out Main 72 05 74 01        -> cetak checksum untuk byte tersebut
 */
public class Main {

    /**
     * Menghitung checksum untuk array byte (int 0..255).
     * Byte yang dikirim adalah header, length, service, dan params.
     * Posisi checksum TIDAK ikut dijumlahkan.
     */
    static int hitungChecksum(int[] bytes) {
        int sum = 0;
        for (int b : bytes) {
            sum += b & 0xFF; // pastikan hanya 8 bit
        }
        // Ambil 8 bit terendah, lalu dua-komplemen terhadap 256.
        return (256 - (sum & 0xFF)) & 0xFF;
    }

    /** Menyalin byte Java (bertanda) menjadi int 0..255 agar aman dijumlahkan. */
    static int[] keIntArray(byte[] b) {
        int[] hasil = new int[b.length];
        for (int i = 0; i < b.length; i++) {
            hasil[i] = b[i] & 0xFF;
        }
        return hasil;
    }

    /** Mengubah int 0..255 menjadi teks hex dua digit, misal 0x0A -> "0A". */
    static String hex(int v) {
        return String.format("%02X", v & 0xFF);
    }

    /** Menggabung array int menjadi teks hex dengan spasi. */
    static String hexList(int[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(hex(bytes[i]));
        }
        return sb.toString();
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

    /**
     * Menjelaskan langkah hitung untuk satu kasus, lalu mencocokkan dengan
     * nilai yang diharapkan.
     */
    static void ujiKasus(String label, int[] bytes, int diharapkan) {
        int hasil = hitungChecksum(bytes);
        // Jumlah seluruh frame (bytes + checksum) harus 0x00 di 8 bit.
        int totalFrame = 0;
        for (int b : bytes) totalFrame += b & 0xFF;
        totalFrame = (totalFrame + hasil) & 0xFF;

        System.out.println("  " + label + " -> checksum 0x" + hex(hasil)
                + " (jumlah seluruh frame 8-bit = 0x" + hex(totalFrame) + ")");

        boolean ok = (hasil == diharapkan) && (totalFrame == 0x00);
        check("checksum " + label, ok, "(harap 0x" + hex(diharapkan) + ")");
    }

    public static void main(String[] args) {
        System.out.println("=== Sampel 03: Checksum tambah dua-komplemen ===");

        // ------------------------------------------------------------------
        // MODE CLI: jika ada argumen hex, hanya cetak checksum-nya.
        // Contoh: java -cp out Main 72 05 74 01  -> 14
        // ------------------------------------------------------------------
        if (args.length > 0) {
            int[] bytes = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                bytes[i] = Integer.parseInt(args[i], 16);
            }
            System.out.println(hex(hitungChecksum(bytes)));
            return;
        }

        // ------------------------------------------------------------------
        // MODE DEMO: menjelaskan langkah hitung satu frame.
        // ------------------------------------------------------------------
        int[] contoh = {0x72, 0x05, 0x71, 0x00};
        int sum = 0;
        for (int b : contoh) sum += b;
        System.out.println("Contoh frame tanpa checksum: " + hexList(contoh));
        System.out.println("  jumlah byte              = " + sum
                + " (0x" + hex(sum) + ")");
        System.out.println("  total & 0xFF             = 0x" + hex(sum & 0xFF));
        System.out.println("  256 - total              = " + (256 - (sum & 0xFF)));
        System.out.println("  checksum                 = 0x" + hex(hitungChecksum(contoh)));
        System.out.println("Frame lengkap: " + hexList(contoh) + " "
                + hex(hitungChecksum(contoh)));

        // ------------------------------------------------------------------
        // SELF-TEST: nilai acuan yang juga dipakai di dokumen RevCore.
        // ------------------------------------------------------------------
        System.out.println("-- Self-test --");
        ujiKasus("72 05 74 01", new int[] {0x72, 0x05, 0x74, 0x01}, 0x14);
        ujiKasus("72 05 71 00", new int[] {0x72, 0x05, 0x71, 0x00}, 0x18);
        ujiKasus("72 05 71 20", new int[] {0x72, 0x05, 0x71, 0x20}, 0xF8);
        ujiKasus("72 05 00 00", new int[] {0x72, 0x05, 0x00, 0x00}, 0x89);
        ujiKasus("72 05 60 01", new int[] {0x72, 0x05, 0x60, 0x01}, 0x28);
        ujiKasus("72 05 71 7A", new int[] {0x72, 0x05, 0x71, 0x7A}, 0x9E);

        // Kasus tepi: array kosong. Jumlah = 0, sehingga checksum = (256 - 0) & 0xFF = 0.
        check("array kosong", hitungChecksum(new int[0]) == 0x00, "(0x00)");

        // Kasus tepi: hanya byte 0x00 -> jumlah 0 -> checksum 0.
        check("byte 0x00", hitungChecksum(new int[] {0x00}) == 0x00, "(0x00)");

        // Kasus tepi: jumlah tepat 256 -> checksum 0.
        check("jumlah pas 256", hitungChecksum(new int[] {0x80, 0x80}) == 0x00, "(0x00)");

        // Konversi dari byte[] Java (bertanda) harus tetap benar.
        byte[] frame = {(byte) 0x72, (byte) 0x05, (byte) 0x71, (byte) 0x7A};
        check("konversi byte[]", hitungChecksum(keIntArray(frame)) == 0x9E, "(0x9E)");

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
