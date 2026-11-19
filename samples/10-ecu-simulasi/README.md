# ECU Simulasi

## Tujuan

Membuat ECU **simulasi di memori** yang bisa menerima frame permintaan, memeriksa checksum, dan menjawab sesuai service yang diminta — sehingga logika diagnostik bisa dilatih tanpa perangkat keras.

## Yang dipelajari

- Alur penerimaan frame: cek checksum → baca service dan parameter → pilih rute → susun respons.
- Rumus checksum: `checksum = (256 - (jumlah byte header..params & 0xFF)) & 0xFF`.
- Respons sintetis yang dibangun program dan checksum-nya dihitung otomatis:
  - Identifikasi `0x71/0x00` → `72 06 71 00 01 02 03 11`
  - Hapus DTC `0x60/0x01` → `72 05 60 01 00 28` (ACK)
- Respons tetap baca DTC `0x74/0x01` → `72 0A 74 01 02 01 01 07 01 03` (payload `02 01 01 07 01` = P0107, P0117).
- Respons negatif: `0x7F` + service yang ditolak + kode alasan `0x12` (misalnya `72 05 7F 74 12 84`).
- Bahwa frame dengan checksum salah tidak ditanggapi sama sekali.

## Prasyarat

- JDK 17 atau lebih baru (diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android, kabel, adapter USB, atau library eksternal.

## Cara menjalankan

Jalankan dari **direktori sampel ini**:

```bash
cd samples/10-ecu-simulasi
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: tidak ada berkas — permintaan ditulis di dalam `src/Main.java`.

Output yang diharapkan:

- Baris `permintaan <hex> -> <respons hex>` untuk setiap kiriman (termasuk yang tidak ditanggapi).
- Log **TX/RX** berisi setiap frame yang dikirim ke simulator (`TX`) dan jawabannya (`RX`).
- Baris `PASS`/`FAIL` per pengujian, `Ringkasan: PASS=n FAIL=0`, dan baris terakhir `HASIL: OK (simulasi perangkat lunak, bukan uji kendaraan)` dengan kode keluar 0.

Pengujian memeriksa: rute identifikasi mengembalikan service `0x71`; rute baca DTC mengembalikan `0x74` dengan payload tepat; rute hapus DTC mengembalikan ACK `0x60`; frame checksum rusak `72 05 74 01 15` ditolak tanpa respons; service tak dikenal mendapat `0x7F` dengan service asal dan kode `0x12`; semua respons sintetis memiliki checksum valid.

## Batasan

- Ini **simulator**, bukan ECU. Tidak ada kendaraan, ECU fisik, adapter, atau antarmuka optocoupler yang diuji.
- Logika simulasi membuktikan **logika perangkat lunak** saja: parsing frame, perhitungan checksum, dan pemilihan rute. Hasil ini **tidak** membuktikan kompatibilitas dengan kendaraan mana pun.
- Simulator selalu memberi jawaban yang sama. Ia tidak mensimulasikan waktu, antrean byte, echo, timeout, atau kondisi kabel.
- Data identifikasi `01 02 03` dan daftar DTC adalah data buatan, bukan identitas kendaraan.
- Aturan byte panjang: `length` sama dengan ukuran seluruh frame (sesuai frame baseline repo).

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan rute `0x71/0x7A` (live data) yang mengembalikan beberapa byte sintetis, lalu hitung checksum-nya dengan `buildFrame`.
2. Tambahkan mode `afterClear` (DTC hilang setelah clear) dan `alwaysFail` (DTC tetap), lalu bandingkan responsnya.
3. Buat rute baca DTC yang mengembalikan payload tanpa kode, misalnya `03` atau `00`, dan pastikan itu berarti "kosong", bukan "gagal".
4. Tambahkan simulator yang mengembalikan nada diam (null) untuk `0x74/0x01` agar kasus tanpa respons bisa dilatih.
5. Cetak log dengan penghitung sesi (`[1] TX`, `[1] RX`) untuk memudahkan pembacaan rangkaian panjang.
