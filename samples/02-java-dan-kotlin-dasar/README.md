# Sampel 02 - Dasar Java dan Kotlin

## Tujuan

Memahami konsep bahasa pemrograman dasar (variabel, kelas, method, loop, dan
perhitungan bilangan bulat) lewat Java biasa sebelum membaca source Kotlin
RevCore di modul `:core`.

## Yang dipelajari

- Tipe data dasar: `byte`, `int`, `double`, `String`.
- Membuat kelas sederhana (`SensorReading`) dengan field, konstruktor, dan method.
- Method dengan nilai kembali (`int`) dan tanpa nilai kembali (`void`).
- Loop `for` biasa dan `for-each`, serta pembagian bilangan bulat.
- Menghitung checksum tambah dua-komplemen sebagai pemanasan untuk sampel 03.
- Pola self-test PASS/FAIL dan exit code non-zero saat gagal.

## Prasyarat

- JDK (Temurin 21 atau lebih baru) sudah terpasang dan `javac` ada di PATH.
- Tidak butuh Android Studio, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/02-java-dan-kotlin-dasar
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Tidak ada input dari pengguna. Program langsung menjalankan demo lalu self-test.
Contoh potongan output:

```text
adc=4 -> 78 mV
checksum 72 05 71 7A = 0x9E
PASS checksum (0x9E)
---
Ringkasan: PASS=6 FAIL=0
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Perbandingan Java vs Kotlin

Java dan Kotlin sangat mirip secara konsep. Contoh di bawah hanya ilustrasi
(cuplikan Kotlin **tidak** dikompilasi oleh sampel ini), supaya kamu siap
membaca source Kotlin RevCore.

Java:

```java
class Sensor {
    final String nama;
    final int nilai;
    Sensor(String nama, int nilai) { this.nama = nama; this.nilai = nilai; }
    int kePersen() { return nilai * 100 / 255; }
}
```

Kotlin (ilustrasi):

```kotlin
class Sensor(val nama: String, val nilai: Int) {
    fun kePersen(): Int = nilai * 100 / 255
}
```

Perbedaan yang sering terlihat di RevCore:

| Hal | Java | Kotlin |
|---|---|---|
| Deklarasi field tetap | `final int x;` | `val x: Int` |
| Field bisa diubah | `int x;` | `var x: Int` |
| Tipe data | tulis di depan | tulis setelah `:` |
| Method | `int f()` | `fun f(): Int` |
| Null-safety | tidak dicek compiler | `String?` vs `String` |

## Batasan

- Program ini hanya demo bahasa, bukan bagian dari library RevCore.
- Tidak berkomunikasi dengan kendaraan, tidak membaca port serial apa pun.
- Angka checksum yang dihitung hanya dipakai sebagai latihan; penjelasan
  lengkap ada di sampel 03.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Ubah `SensorReading` agar punya method `keVolt()` dengan rumus `nilai * 5000 / 255` milivolt, lalu tambahkan self-test-nya.
2. Ubah loop ADC menjadi 0..255 dan cetak nilainya; cek bahwa `adc=255` menghasilkan `5000` mV.
3. Tulis ulang kelas `SensorReading` dalam Kotlin (file `.kt` terpisah, tidak perlu dikompilasi) dan bandingkan dengan versi Java.
4. Tambahkan self-test yang sengaja salah (misal `1 == 2`) untuk melihat exit code menjadi `1`.
