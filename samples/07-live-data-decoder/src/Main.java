/**
 * SAMPEL 07 - Live data decoder
 *
 * Tujuan: mengubah payload biner menjadi nilai sensor memakai tabel field
 * generik: offset byte, panjang, urutan byte (big/little endian), skala, dan bias.
 *
 * Layout dan rumus diambil dari source test yang dipublikasikan:
 *   core/src/test/java/com/revcore/android/core/comm/LiveDataProfileParserTest.kt
 * (object SampleLiveData.profile). Data itu SINTETIS, bukan layout ECU Honda.
 *
 * Payload : 0B B8 80 40 50 40 00 64 F0 3C 5A C8
 * index   : 0  1 | 2  3  4  5 | 6  7 | 8  9 | 10 | 11
 * isi     : RPM  | TPS DEG ECT IAT | MAP | BAT SPD| ECT alternatif | INJ
 * (byte 10 = 0x5A hanya dicadangkan untuk ECT bila byte 4 bernilai 0xFF)
 *
 * Catatan locale (Java 19+): String.format memakai locale sistem, sehingga
 * desimal bisa menjadi koma. Sampel ini memakai Locale.ROOT agar selalu titik.
 */
import java.util.Locale;

public class Main {

    /** Definisi satu field sensor dalam tabel profil. */
    static class Field {
        final String nama;
        final int offset;         // posisi byte awal (-1 = tidak dipublikasikan)
        final int panjang;        // jumlah byte (1 atau 2)
        final boolean bigEndian;  // true = byte pertama bit paling tinggi
        final boolean bertanda;   // true = nilai 16-bit dibaca signed
        final double skala;       // pengali
        final double bias;        // penambah, misal -40 untuk suhu
        final double min;         // batas bawah nilai masuk akal
        final double max;         // batas atas nilai masuk akal
        final String unit;
        final int offsetAlternatif; // -1 jika tidak ada

        Field(String nama, int offset, int panjang, boolean bigEndian, boolean bertanda,
              double skala, double bias, double min, double max, String unit, int alt) {
            this.nama = nama;
            this.offset = offset;
            this.panjang = panjang;
            this.bigEndian = bigEndian;
            this.bertanda = bertanda;
            this.skala = skala;
            this.bias = bias;
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.offsetAlternatif = alt;
        }
    }

    /** Field unsigned big-endian 1 byte (paling banyak dipakai di tabel). */
    static Field f8(String nama, int offset, double skala, double bias,
                    double min, double max, String unit, int alt) {
        return new Field(nama, offset, 1, true, false, skala, bias, min, max, unit, alt);
    }

    /** Tabel field, urutan dan rumus sama dengan SampleLiveData.profile. */
    static final Field[] FIELDS = {
        new Field("RPM",          0, 2, true, false, 1.0,          0.0,    0.0, 20000.0, "rpm",   -1),
        f8("TPS_ADC",             2,    1.0,            0.0,    0.0,  255.0, "count", -1),
        f8("TPS_PERCENT",         2,    100.0 / 255.0,  0.0,    0.0,  100.0, "%",     -1),
        f8("TPS_VOLTAGE",         2,    5.0 / 255.0,    0.0,    0.0,    5.0, "V",     -1),
        f8("TPS_DEGREES",         3,    1.0,            0.0,    0.0,  255.0, "deg",   -1),
        f8("ECT",                 4,    1.0,          -40.0,  -40.0,  150.0, "C",     10),
        f8("IAT",                 5,    1.0,          -40.0,  -40.0,  100.0, "C",     -1),
        new Field("MAP",          6, 2, true, false, 1.0,          0.0,    0.0,  5000.0, "kPa",   -1),
        f8("BATTERY",             8,    0.05,           0.0,    5.0,   20.0, "V",     -1),
        f8("SPEED",               9,    1.0,            0.0,    0.0,  300.0, "km/h",  -1),
        f8("INJECTOR",           11,    0.01,           0.0,    0.0,   50.0, "ms",    -1),
        f8("AFR",                -1,    1.0,            0.0,    0.0,  100.0, "ratio", -1)
    };

    /** minPayload dari SampleLiveData.profile. */
    static final int MIN_PAYLOAD = 12;

    /** Membaca nilai mentah unsigned sesuai urutan byte (payload = int 0..255). */
    static int bacaMentah(int[] p, int offset, int panjang, boolean bigEndian) {
        int v = 0;
        if (bigEndian) {
            for (int i = 0; i < panjang; i++) v = (v << 8) | p[offset + i];
        } else {
            for (int i = panjang - 1; i >= 0; i--) v = (v << 8) | p[offset + i];
        }
        return v;
    }

    /** Hasil decode: state (VALID/UNSUPPORTED/NOT_RESPONDING/INVALID_RESPONSE), raw, nilai. */
    static class Hasil {
        String state;
        int raw;
        double nilai;
        boolean adaRaw;
    }

    static Hasil decode(Field f, int[] p) {
        Hasil h = new Hasil();
        // 1. Tidak dipublikasikan oleh tabel -> UNSUPPORTED (bukan 0).
        if (f.offset < 0) { h.state = "UNSUPPORTED"; return h; }
        // 2. Byte belum ada di payload -> NOT_RESPONDING.
        if (f.offset + f.panjang > p.length) { h.state = "NOT_RESPONDING"; return h; }
        int posisi = f.offset;
        // 3. 0xFF berarti "tidak dikirim" hanya untuk field yang punya offset alternatif.
        if (p[posisi] == 0xFF && f.offsetAlternatif >= 0) {
            if (f.offsetAlternatif + f.panjang > p.length || p[f.offsetAlternatif] == 0xFF) {
                h.state = "INVALID_RESPONSE";
                return h;
            }
            posisi = f.offsetAlternatif;
        }
        h.raw = bacaMentah(p, posisi, f.panjang, f.bigEndian);
        h.adaRaw = true;
        // 4. Field signed: bit tertinggi 16-bit menandakan bilangan negatif.
        long mentah = h.raw;
        if (f.bertanda && f.panjang == 2 && h.raw >= 0x8000) mentah -= 0x10000;
        // 5. Skala dan bias, lalu 6. tolak nilai di luar rentang (tidak di-clamp).
        h.nilai = mentah * f.skala + f.bias;
        if (h.nilai < f.min || h.nilai > f.max) { h.state = "INVALID_RESPONSE"; return h; }
        h.state = "VALID";
        return h;
    }

    // Penghitung self-test.
    static int pass = 0;
    static int fail = 0;

    static void check(String namaUji, boolean kondisi, String detail) {
        if (kondisi) { pass++; System.out.println("PASS " + namaUji + " " + detail); }
        else { fail++; System.out.println("FAIL " + namaUji + " " + detail); }
    }

    static boolean near(double a, double b) { return Math.abs(a - b) < 0.001; }

    static String hexList(int[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(String.format(Locale.ROOT, " %02X", b[i] & 0xFF));
            else sb.append(String.format(Locale.ROOT, "%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    /** Cari field berdasarkan nama. */
    static Field field(String nama) {
        for (Field f : FIELDS) if (f.nama.equals(nama)) return f;
        throw new IllegalArgumentException("field tidak ada: " + nama);
    }

    /** Decode per nama field, misal val("ECT", payload). */
    static Hasil val(String nama, int[] p) { return decode(field(nama), p); }

    public static void main(String[] args) {
        System.out.println("=== Sampel 07: Live data decoder ===");

        // Payload sintetis dari LiveDataProfileParserTest.kt (SampleLiveData.payload).
        int[] payload = {0x0B, 0xB8, 0x80, 0x40, 0x50, 0x40, 0x00, 0x64,
                         0xF0, 0x3C, 0x5A, 0xC8};

        // DEMO: jelaskan satu field (ECT) langkah demi langkah.
        Field ect = field("ECT");
        System.out.println("Demo ECT: byte index 4 = 0x" + hexList(new int[] {payload[4]})
                + " (" + payload[4] + "), bias " + ect.bias + " -> "
                + (payload[4] + (int) ect.bias) + " " + ect.unit);

        // TABEL: field | raw | value | unit | state (payload = 0B B8 ... C8)
        System.out.printf(Locale.ROOT, "%-12s %6s %12s %-8s %s%n",
                "FIELD", "RAW", "VALUE", "UNIT", "STATE");
        for (Field f : FIELDS) {
            Hasil h = decode(f, payload);
            String nilai = teks(h);
            System.out.printf(Locale.ROOT, "%-12s %6s %12s %-8s %s%n",
                    f.nama,
                    h.adaRaw ? String.valueOf(h.raw) : "--",
                    nilai,
                    f.offset < 0 ? "-" : f.unit,
                    h.state);
        }

        // SELF-TEST: nilai harus sama dengan assert di LiveDataProfileParserTest.
        System.out.println("-- Self-test (acuan test Kotlin) --");
        check("RPM", val("RPM", payload).nilai == 3000.0, "(3000)");
        check("TPS_ADC", val("TPS_ADC", payload).nilai == 128.0, "(128)");
        check("TPS_PERCENT", near(val("TPS_PERCENT", payload).nilai, 128 * 100.0 / 255.0),
                "(128*100/255)");
        check("TPS_VOLTAGE", near(val("TPS_VOLTAGE", payload).nilai, 128 * 5.0 / 255.0),
                "(128*5/255)");
        check("TPS_DEGREES", val("TPS_DEGREES", payload).nilai == 64.0, "(64)");
        check("ECT", val("ECT", payload).nilai == 40.0, "(40)");
        check("IAT", val("IAT", payload).nilai == 24.0, "(24)");
        check("MAP", val("MAP", payload).nilai == 100.0, "(100)");
        check("BATTERY", near(val("BATTERY", payload).nilai, 12.0), "(12)");
        check("SPEED", val("SPEED", payload).nilai == 60.0, "(60)");
        check("INJECTOR", near(val("INJECTOR", payload).nilai, 2.0), "(2)");
        check("semua valid kecuali AFR", semuaValid(tanpaAfr(), payload), "(11 VALID)");

        // ------------------------------------------------------------------
        // BIG-ENDIAN: 0B B8 = 3000; jika salah urutan menjadi 0xB80B = 47115.
        // -+
        int be = bacaMentah(payload, 0, 2, true);
        int le = bacaMentah(payload, 0, 2, false);
        check("big-endian RPM", be == 3000 && le == 0xB80B, "(3000 / 47115)");

        // UJI TEPI (1): AFR dan payload pendek.
        check("AFR UNSUPPORTED", val("AFR", payload).state.equals("UNSUPPORTED"), "(UNSUPPORTED)");

        int[] pendek = {0x0B, 0xB8, 0x80, 0x40}; // hanya 4 byte
        check("pendek: RPM tetap", val("RPM", pendek).nilai == 3000.0, "(3000)");
        check("pendek: MAP NOT_RESPONDING", val("MAP", pendek).state.equals("NOT_RESPONDING"),
                "(bukan 0)");

        // UJI TEPI (2): 0xFF, nilai mustahil, dan 0 yang sah.
        int[] alt = payload.clone();
        alt[4] = 0xFF; alt[10] = 90; // ECT utama 0xFF, alternatif 0x5A -> 50 C
        check("ECT offset alternatif", val("ECT", alt).nilai == 50.0, "(50)");

        int[] hilang = payload.clone(); hilang[4] = 0xFF; hilang[10] = 0xFF;
        check("ECT hilang", val("ECT", hilang).state.equals("INVALID_RESPONSE"), "(bukan 0)");

        int[] mustahil = payload.clone();
        mustahil[4] = 0xFE; // 254 - 40 = 214 C, di atas max 150
        check("ECT mustahil", val("ECT", mustahil).state.equals("INVALID_RESPONSE"),
                "(ditolak, tidak di-clamp)");

        int[] tpsFF = payload.clone(); tpsFF[2] = 0xFF; // TPS tanpa offset alternatif: 0xFF tetap nilai sah
        check("TPS 0xFF tetap 255", val("TPS_ADC", tpsFF).nilai == 255.0, "(255)");
        check("TPS 0xFF -> 100%", near(val("TPS_PERCENT", tpsFF).nilai, 100.0), "(100%)");

        int[] nol = new int[MIN_PAYLOAD]; // semua 0x00: mesin mati, bukan "hilang"
        check("nol tetap VALID", val("RPM", nol).state.equals("VALID") && val("RPM", nol).nilai == 0.0,
                "(0 rpm VALID)");

        // LITTLE-ENDIAN dan SIGNED (test littleEndianAndSignedFields): payload 34 12 FE 0C.
        // RPM little-endian = 0x1234 = 4660; signed big-endian = 0xFE0C = -500, x 0.5 = -250.
        int[] enc = {0x34, 0x12, 0xFE, 0x0C};
        Field rpmLe = new Field("RPM_LE", 0, 2, false, false, 1.0, 0.0, 0, 20000, "rpm", -1);
        Field adv = new Field("IGN_ADV", 2, 2, true, true, 0.5, 0.0, -300, 300, "deg", -1);
        check("little-endian 0x1234", decode(rpmLe, enc).nilai == 0x1234, "(4660)");
        check("signed 0xFE0C", near(decode(adv, enc).nilai, -250.0), "(-500 * 0.5)");

        // Ringkasan dan exit code.
        System.out.println("---");
        System.out.println("Ringkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1); // exit code non-zero agar CI mendeteksi kegagalan
        }
        System.out.println("HASIL: OK");
    }

    /** Target "semua field valid kecuali AFR": salin tabel tanpa AFR. */
    static Field[] tanpaAfr() {
        Field[] hasil = new Field[FIELDS.length - 1];
        for (int i = 0; i < hasil.length; i++) hasil[i] = FIELDS[i];
        return hasil;
    }

    /** Format angka: bulat bila tidak ada pecahan, tiga desimal bila ada. */
    static String teks(Hasil h) {
        if (!h.state.equals("VALID")) return h.state;
        if (h.nilai == Math.rint(h.nilai)) return String.valueOf((long) h.nilai);
        return String.format(Locale.ROOT, "%.3f", h.nilai);
    }

    /** true jika setiap field di daftar menghasilkan state VALID. */
    static boolean semuaValid(Field[] daftar, int[] payload) {
        for (Field f : daftar) if (!decode(f, payload).state.equals("VALID")) return false;
        return true;
    }
}
