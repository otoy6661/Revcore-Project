/**
 * SAMPEL 02 - Dasar Java dan Kotlin
 *
 * Tujuan: mengenal variabel, kelas, method, loop, dan perhitungan bilangan
 * bulat memakai Java biasa. Konsep-konsep ini dipakai setiap hari saat
 * membaca source Kotlin RevCore (modul :core).
 *
 * Cara menjalankan:
 *   javac -d out src/Main.java
 *   java -cp out Main
 */
public class Main {

    // ------------------------------------------------------------------
    // 1. KELAS: "cetakan" yang menyimpan data (field) dan perilaku (method).
    // ------------------------------------------------------------------
    static class SensorReading {
        // Variabel anggota. final = nilainya tidak boleh diubah lagi.
        final String nama;
        final int nilaiMentah;

        // Konstruktor: dipanggil saat objek dibuat dengan "new".
        SensorReading(String nama, int nilaiMentah) {
            this.nama = nama;
            this.nilaiMentah = nilaiMentah;
        }

        // Method yang mengembalikan nilai int (bilangan bulat).
        // Contoh sederhana konversi ADC 0..255 menjadi persen (0..100).
        int kePersen() {
            return nilaiMentah * 100 / 255; // pembagian bulat: hasil dipotong
        }

        // Method tanpa nilai kembali (void).
        void cetak() {
            System.out.println("Sensor " + nama + ": mentah=" + nilaiMentah
                    + ", persen=" + kePersen() + "%");
        }
    }

    // ------------------------------------------------------------------
    // 2. Method bantu di kelas Main: menjumlahkan isi array int.
    // ------------------------------------------------------------------
    static int jumlah(int[] angka) {
        int total = 0;
        // for-each: "untuk setiap n di dalam angka".
        for (int n : angka) {
            total += n;
        }
        return total;
    }

    // Method untuk menghitung checksum sederhana (tambah dua-komplemen).
    // Rumus ini dipakai lagi di sampel 03.
    static int checksum(int[] bytes) {
        int sum = jumlah(bytes) & 0xFF;  // ambil 8 bit terendah
        return (256 - sum) & 0xFF;       // dua-komplemen
    }

    // Penghitung self-test global.
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

    public static void main(String[] args) {
        System.out.println("=== Sampel 02: Dasar Java dan Kotlin ===");

        // ----------------------------------------------------------------
        // 3. VARIABEL dan tipe data dasar.
        //    byte  : 8 bit  (-128..127)
        //    int   : 32 bit (bilangan bulat)
        //    double: bilangan pecahan
        // ----------------------------------------------------------------
        int suhuCelsius = 40;
        double tegangan = 2.5;
        byte header = (byte) 0x72; // 0x72 = 114, masih muat di byte
        String mode = "diagnostik";

        System.out.println("suhuCelsius = " + suhuCelsius);
        System.out.println("tegangan    = " + tegangan);
        System.out.println("header      = 0x" + String.format("%02X", header & 0xFF));
        System.out.println("mode        = " + mode);

        // ----------------------------------------------------------------
        // 4. Memakai kelas: membuat objek dan memanggil method-nya.
        // ----------------------------------------------------------------
        SensorReading tps = new SensorReading("TPS", 128);
        tps.cetak();

        // ----------------------------------------------------------------
        // 5. LOOP: mencetak tabel kecil 0..4 dengan perhitungan bulat.
        // ----------------------------------------------------------------
        System.out.println("-- konversi ADC ke volt (5.0V / 255) --");
        for (int adc = 0; adc <= 4; adc++) {
            // Hasil: 0, 19, 39, 58, 78 milivolt (dihitung sebagai int).
            int mv = adc * 5000 / 255;
            System.out.println("adc=" + adc + " -> " + mv + " mV");
        }

        // ----------------------------------------------------------------
        // 6. PERHITUNGAN BILANGAN BULAT: checksum frame sintetis.
        // ----------------------------------------------------------------
        int[] frameTanpaChecksum = {0x72, 0x05, 0x71, 0x7A};
        int cs = checksum(frameTanpaChecksum);
        System.out.println("checksum 72 05 71 7A = 0x" + String.format("%02X", cs));

        // ----------------------------------------------------------------
        // 7. SELF-TEST: memastikan hasil program sesuai harapan.
        // ----------------------------------------------------------------
        check("variabel-int", suhuCelsius == 40, "(40)");
        check("cast-byte", (header & 0xFF) == 0x72, "(0x72)");
        check("method-kePersen", tps.kePersen() == 50, "(128*100/255 = 50)");
        check("jumlah-array", jumlah(new int[] {1, 2, 3, 4}) == 10, "(10)");
        check("loop-adc-4", (4 * 5000 / 255) == 78, "(78 mV)");
        check("checksum", cs == 0x9E, "(0x9E)");

        // ----------------------------------------------------------------
        // 8. Ringkasan dan kode keluar (exit code).
        //    Jika ada FAIL, exit code = 1 supaya CI bisa mendeteksinya.
        // ----------------------------------------------------------------
        System.out.println("---");
        System.out.println("Ringkasan: PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.out.println("HASIL: GAGAL");
            System.exit(1); // exit code non-zero agar CI mendeteksi kegagalan
        }
        System.out.println("HASIL: OK");
    }
}
