# Contoh Progresif RevCore

Direktori ini berisi proyek contoh yang **terpisah dari source produksi**. Tidak ada
contoh yang mengubah `:core`, identitas RevCore, package name, atau mekanisme keamanan.
Setiap contoh memiliki README, perintah menjalankan, input/output, dan batasnya sendiri.

## Indeks

| Langkah | Direktori | Fokus | Cara jalan | Status |
|---:|---|---|---|---|
| 01 | [01-hello-android](01-hello-android/) | Activity, manifest, APK | `gradle assembleDebug` | Build APK terverifikasi |
| 02 | [02-java-dan-kotlin-dasar](02-java-dan-kotlin-dasar/) | Bahasa, class, method | `javac` + `java` | Logika terverifikasi |
| 03 | [03-checksum](03-checksum/) | Checksum frame | `javac` + `java` | Logika terverifikasi |
| 04 | [04-frame-builder](04-frame-builder/) | Menyusun frame | `javac` + `java` | Logika terverifikasi |
| 05 | [05-frame-parser](05-frame-parser/) | Membaca dan memvalidasi frame | `javac` + `java` | Logika terverifikasi |
| 06 | [06-dtc-parser](06-dtc-parser/) | Payload DTC | `javac` + `java` | Logika terverifikasi |
| 07 | [07-live-data-decoder](07-live-data-decoder/) | Decode sensor | `javac` + `java` | Logika terverifikasi |
| 08 | [08-json-mini-parser](08-json-mini-parser/) | Konfigurasi JSON | `javac` + `java` | Logika terverifikasi |
| 09 | [09-ecu-profil](09-ecu-profil/) | Profil ECU | `javac` + `java` | Logika terverifikasi |
| 10 | [10-ecu-simulasi](10-ecu-simulasi/) | ECU tiruan | `javac` + `java` | Logika terverifikasi |
| 11 | [11-abstraksi-transport](11-abstraksi-transport/) | Transport interface | `javac` + `java` | Logika terverifikasi |
| 12 | [12-sesi-diagnostik](12-sesi-diagnostik/) | Siklus baca/clear DTC | `javac` + `java` | Logika terverifikasi |
| 13 | [13-database-kendaraan](13-database-kendaraan/) | Database sintetis | `javac` + `java` | Logika terverifikasi |

Panduan tanpa kode yang dapat dijalankan di environment ini:

- [14 — Integrasi `:core`](../education/02-membangun-apk.md) — langkah memakai AAR di
  aplikasi sendiri; sebagian `REFERENCE`, belum diuji sebagai proyek aplikasi.
- [15 — Uji adapter nyata](../education/05-katalog-kegagalan-dan-pemulihan.md) — perlu
  perangkat keras; **belum dieksekusi**.
- [16 — Aplikasi diagnostik milikmu](../education/07-kontributor-dan-kemandirian.md) —
  panduan identitas, atribusi, dan batas distribusi.

## Cara menjalankan contoh Java

Setiap contoh 02–13 adalah satu berkas `src/Main.java` tanpa package:

```bash
cd samples/03-checksum
javac -d out src/Main.java
java -cp out Main
```

Sebagian contoh membaca berkas data di direktorinya sendiri. Jalankan dari direktori
contoh, bukan dari root repository.

## Status bukti

Semua contoh 02–13 memakai data **Synthetic**. Program membuktikan logika perangkat
lunak: checksum, parsing, state, dan skenario simulasi. Program **tidak** membuktikan
kompatibilitas ECU, timing hardware, atau perilaku kendaraan.

Contoh 01 menghasilkan APK nyata, tetapi APK itu hanya menghitung tombol. Ia tidak
berkomunikasi dengan ECU.

## Aturan kontribusi contoh

1. Jangan menyalin source produksi lalu mengubahnya menjadi contoh; tulis contoh
   terpisah dan sederhana.
2. Jangan menambah dependency eksternal untuk contoh dasar.
3. Setiap contoh harus dapat dijalankan tanpa Android SDK, kecuali contoh 01.
4. Jangan menambahkan data kendaraan nyata, firmware, MAP, atau kredensial.
5. Sertakan self-test yang gagal dengan exit code bukan nol bila logika salah.
