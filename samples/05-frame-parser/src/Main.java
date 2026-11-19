/**
 * SAMPEL 05 - Frame parser (pembaca frame)
 *
 * Tujuan: mengubah teks hex, misal "72 05 74 01 14", menjadi bagian-bagian
 * frame, lalu memvalidasinya. Juga mendeteksi respons negatif (service 0x7F).
 *
 * Layout frame (docs/KLINE_PROTOCOL.md):
 *   index 0 : header
 *   index 1 : declared length (JUMLAH TOTAL byte frame, termasuk checksum)
 *   index 2 : service
 *   index 3..n-2 : payload
 *   index n-1 : checksum
 *
 * Prinsip (docs/FRAME_PARSER.md): parser TIDAK memperbaiki frame. Jika tidak
 * valid, frame ditolak dengan pesan yang jelas, tidak pernah diterima diam-diam.
 */
public class Main {

    /** Hasil parsing satu frame. */
    static class ParsedFrame {
        boolean valid;          // true hanya jika semua pemeriksaan lolos
        String error;           // alasan penolakan (null jika valid)
        int header;
        int declaredLength;
        int service;
        int[] payload;
        int checksum;

        boolean isNegativeResponse() {
            return valid && service == 0x7F;
        }
    }

    /** Checksum tambah dua-komplemen (lihat sampel 03). */
    static int checksum(int[] bytes, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += bytes[i] & 0xFF;
        return (256 - (sum & 0xFF)) & 0xFF;
    }

    static String hex(int v) {
        return String.format("%02X", v & 0xFF);
    }

    static String hexList(int[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(hex(b[i]));
        }
        return sb.toString();
    }

    /**
     * Mengubah teks hex "72 05 74 01 14" menjadi array int.
     * Mengembalikan null jika ada token yang bukan 2 digit hex.
     */
    static int[] parseHex(String teks) {
        String bersih = teks.trim();
        if (bersih.isEmpty()) return null;
        String[] token = bersih.split("\\s+");
        int[] hasil = new int[token.length];
        for (int i = 0; i < token.length; i++) {
            if (!token[i].matches("[0-9A-Fa-f]{2}")) return null;
            hasil[i] = Integer.parseInt(token[i], 16);
        }
        return hasil;
    }

    /**
     * Memvalidasi dan memecah frame.
     * Urutan pemeriksaan: parse teks -> ukuran -> header -> length -> checksum.
     */
    static ParsedFrame parse(String teks) {
        ParsedFrame f = new ParsedFrame();

        // 1. Teks hex harus bisa dibaca.
        int[] bytes = parseHex(teks);
        if (bytes == null) {
            f.error = "Teks hex tidak valid";
            return f;
        }

        // 2. Ukuran minimal 4 byte (header, length, service, checksum).
        if (bytes.length < 4) {
            f.error = "Frame terlalu pendek: " + bytes.length + " byte (minimal 4)";
            return f;
        }

        // 3. Header baseline request/response adalah 0x72.
        f.header = bytes[0];
        if (f.header != 0x72) {
            f.error = "Header tidak dikenal: 0x" + hex(f.header);
            return f;
        }

        // 4. Declared length harus SAMA dengan jumlah byte yang diterima.
        f.declaredLength = bytes[1];
        if (f.declaredLength != bytes.length) {
            f.error = "Declared length 0x" + hex(f.declaredLength) + " (" + f.declaredLength
                    + ") tidak cocok dengan jumlah byte " + bytes.length;
            return f;
        }

        // 5. Checksum byte terakhir harus sama dengan hitungan semua byte sebelumnya.
        f.checksum = bytes[bytes.length - 1];
        int dihitung = checksum(bytes, bytes.length - 1);
        if (dihitung != f.checksum) {
            f.error = "Checksum tidak cocok: terbaca 0x" + hex(f.checksum)
                    + ", seharusnya 0x" + hex(dihitung);
            return f;
        }

        // 6. Semua lolos: pecah service dan payload.
        f.service = bytes[2];
        f.payload = new int[bytes.length - 4];
        System.arraycopy(bytes, 3, f.payload, 0, f.payload.length);
        f.valid = true;
        return f;
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

    /** Menjalankan satu kasus dan mencetak ringkasannya. */
    static ParsedFrame tampilkan(String teks) {
        ParsedFrame f = parse(teks);
        System.out.println("frame: " + teks);
        if (f.valid) {
            System.out.println("  VALID  header=0x" + hex(f.header) + " length=0x"
                    + hex(f.declaredLength) + " service=0x" + hex(f.service)
                    + " payload=[" + hexList(f.payload) + "] checksum=0x" + hex(f.checksum));
            if (f.isNegativeResponse()) {
                System.out.println("  -> RESPONS NEGATIF: service ditolak=0x"
                        + (f.payload.length > 0 ? hex(f.payload[0]) : "--")
                        + ", kode=" + (f.payload.length > 1 ? hex(f.payload[1]) : "--"));
            }
        } else {
            System.out.println("  DITOLAK: " + f.error);
        }
        return f;
    }

    public static void main(String[] args) {
        System.out.println("=== Sampel 05: Frame parser ===");

        // ------------------------------------------------------------------
        // KASUS VALID
        // ------------------------------------------------------------------
        System.out.println("-- Kasus valid --");
        ParsedFrame a = tampilkan("72 05 74 01 14");
        check("valid 74 01", a.valid && a.service == 0x74
                && a.payload.length == 1 && a.payload[0] == 0x01, "(service 0x74, payload [01])");

        ParsedFrame b = tampilkan("72 0A 74 01 02 01 01 07 01 03");
        check("valid payload DTC", b.valid && b.declaredLength == 0x0A
                && b.payload.length == 6, "([" + hexList(b.payload) + "])");
        check("payload DTC isi", b.valid && hexList(b.payload).equals("01 02 01 01 07 01"),
                "(01 02 01 01 07 01)");

        // ------------------------------------------------------------------
        // RESPONS NEGATIF: frame valid, tetapi BUKAN daftar DTC.
        // ------------------------------------------------------------------
        System.out.println("-- Kasus respons negatif --");
        ParsedFrame c = tampilkan("72 06 7F 74 12 83");
        check("negatif valid", c.valid && c.service == 0x7F, "(service 0x7F)");
        check("negatif service ditolak", c.isNegativeResponse()
                && c.payload.length >= 1 && c.payload[0] == 0x74, "(0x74)");

        // ------------------------------------------------------------------
        // KASUS DITOLAK: harus memberi pesan, tidak boleh lolos.
        // ------------------------------------------------------------------
        System.out.println("-- Kasus ditolak --");
        ParsedFrame d = tampilkan("72 05 74 01"); // terpotong
        check("terpotong ditolak", !d.valid && d.error != null
                && d.error.contains("tidak cocok"), "(" + d.error + ")");
        check("terpotong tidak punya service", !d.valid, "(service tidak dipakai)");

        ParsedFrame e = tampilkan("72 05 74 01 15"); // checksum salah (harus 14)
        check("checksum salah ditolak", !e.valid && e.error.contains("Checksum"),
                "(" + e.error + ")");


        // ------------------------------------------------------------------
        // KASUS LAIN: header salah, terlalu pendek, teks rusak.
        // ------------------------------------------------------------------
        System.out.println("-- Kasus lain --");
        ParsedFrame g = tampilkan("02 05 74 01 AE");
        check("header lain ditolak", !g.valid && g.error.contains("Header"), "(" + g.error + ")");

        ParsedFrame h = tampilkan("72 03 74");
        check("terlalu pendek ditolak", !h.valid && h.error.contains("pendek"), "(" + h.error + ")");

        ParsedFrame i = tampilkan("72 0Z 74 01 14");
        check("teks hex rusak ditolak", !i.valid && i.error.contains("hex"), "(" + i.error + ")");

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
