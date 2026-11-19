# Profil ECU

## Tujuan

Memodelkan profil ECU (`EcuProfile`) sebagai satu tempat untuk semua konfigurasi yang dibutuhkan lapisan diagnostik, lalu memastikan setiap frame yang disimpan valid checksum-nya dan profil rusak ditolak saat dimuat.

## Yang dipelajari

- Isi sebuah profil ECU:
  - `family` — nama keluarga protokol/ECU.
  - `identityRequests` — frame untuk meminta identifikasi.
  - `dtcReadRequests` — kandidat frame baca DTC, dicoba berurutan.
  - `dtcClearRequests` — kandidat frame hapus DTC, dicoba berurutan.
  - `liveFields` — daftar field live data: `name`, `offset`, `length`, `scale`, `correction`, `unit`.
- Format frame K-Line: `[header][length][service][params...][checksum]`.
- Rumus checksum: `checksum = (256 - (jumlah byte header..params & 0xFF)) & 0xFF`.
- Aturan byte `length` pada sampel ini mengikuti frame baseline repo: nilai `length` sama dengan **ukuran seluruh frame**. Contoh: `72 05 74 01 14` adalah 5 byte dengan `length = 0x05`. Respons `72 0A 74 01 02 01 01 07 01 03` juga 10 byte dengan `length = 0x0A`. Validasi ketat seperti `HondaFrameCodec.isWellFormed`: frame dengan `length` berbeda dari ukuran array ditolak.
- Prinsip *fail fast*: validasi dilakukan sekali saat profil dimuat, bukan setiap kali frame dikirim.
- Konsep katalog kosong: family di luar baseline belum direkonstruksi, jadi daftar baca/hapus DTC-nya kosong dan engine harus melaporkan "tidak didukung".

## Prasyarat

- JDK 17 atau lebih baru (diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android, internet, atau library eksternal.

## Cara menjalankan

Jalankan dari **direktori sampel ini**:

```bash
cd samples/09-ecu-profil
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: tidak ada berkas — profil ditulis langsung di dalam `src/Main.java` sebagai data sintetis.

Output yang diharapkan:

- Katalog profil yang tersedia (baseline dan satu family lain).
- Daftar frame baca/hapus DTC dalam bentuk hex dan spesifikasi field live data.
- Baris `PASS`/`FAIL` per pengujian, lalu `Ringkasan: PASS=n FAIL=0` dan `HASIL: OK` (kode keluar 0).

Pengujian memeriksa:

1. Tiga frame baca DTC tepat `72 05 74 01 14`, `72 05 71 20 F8`, `72 05 00 00 89`.
2. Tiga frame hapus DTC tepat `72 05 60 01 28`, `72 05 74 02 13`, `72 04 04 86`.
3. Frame sengaja rusak (`72 05 74 01 15`) ditolak, dan `EcuProfile` yang memuatnya gagal dimuat.
4. Lookup family berhasil untuk nama dikenal dan ditolak untuk nama asing.
5. Family non-baseline memiliki katalog kosong dan tidak meminjam frame baseline.
6. Konversi live data `raw * scale + correction` pada payload sintetis.

## Batasan

- Profil di sini ditulis di kode, bukan dibaca dari JSON. Penggabungan dengan contoh 08 (parser JSON) adalah latihan.
- Hanya field live data satu byte atau multi-byte big-endian; decoding bertanda, bilangan pecahan, dan tabel lookup belum ada.
- Simbol DTC (P-code) tidak ditampilkan di sini; sampel 12 yang menanganinya.
- Family non-baseline adalah contoh konsep, bukan rekonstruksi protokol nyata.
- Tidak ada koneksi ke kendaraan.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan validasi bahwa `dtcReadRequests` dan `dtcClearRequests` tidak kosong hanya untuk family baseline, dan pastikan family lain ditandai "tidak didukung".
2. Muat profil dari `data/profile.json` memakai pendekatan parser sampel 08 (salin pendekatannya, bukan bergantung pada folder lain).
3. Tambahkan field `verified` (boolean) pada profil dan tolak profil yang belum diverifikasi bila diminta dalam mode ketat.
4. Tambahkan dukungan `length` 2 byte big-endian dengan pengujian nilai mentah `0x01 0x2C` = 300.
5. Buat pengujian bahwa frame dengan byte panjang salah (misalnya `72 06 74 01 14`) juga ditolak. Sekarang baru checksum yang diuji sebagai "rusak".
