# Sampel 04 - Frame builder

## Tujuan

Menyusun frame K-Line lengkap dari header, service, dan parameter, termasuk
menghitung `length` dan `checksum`, lalu membandingkannya dengan frame
kandidat yang tercatat di dokumentasi RevCore.

## Yang dipelajari

- Susunan frame: `[header][length][service][params...][checksum]`.
- Arti `length`: **jumlah total byte frame** (termasuk checksum), sesuai
  `docs/KLINE_PROTOCOL.md`. Frame 5 byte memakai `length = 0x05`.
- Cara menghitung checksum dari semua byte sebelum posisi checksum (sampel 03).
- Menyalin isi array dengan `System.arraycopy`.
- Varargs (`int... params`) karena jumlah parameter bisa berubah, termasuk 0.

## Prasyarat

- JDK (Temurin 21 atau lebih baru) terpasang, `javac` dan `java` ada di PATH.
- Sudah membaca sampel 03 (checksum).
- Tidak butuh Android, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/04-frame-builder
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Tidak ada input. Program membangun delapan frame dan mencetak hasilnya:

| Header | Service | Params | Frame hasil | Sumber di dokumentasi |
|---:|---:|---|---|---|
| 0x72 | 0x74 | `01` | `72 05 74 01 14` | Query baca DTC `74/01` |
| 0x72 | 0x71 | `20` | `72 05 71 20 F8` | Query baca DTC `71/20` |
| 0x72 | 0x00 | `00` | `72 05 00 00 89` | Query baca DTC `00/00` |
| 0x72 | 0x60 | `01` | `72 05 60 01 28` | Query clear `60/01` |
| 0x72 | 0x74 | `02` | `72 05 74 02 13` | Query clear `74/02` |
| 0x72 | 0x04 | (kosong) | `72 04 04 86` | Query clear `04` |
| 0x72 | 0x71 | `00` | `72 05 71 00 18` | Request live data tabel sintetis |
| 0x72 | 0x71 | `7A` | `72 05 71 7A 9E` | Request live data tabel sintetis |

Setiap frame juga dicek jumlah seluruh byte-nya = `0x00` di 8 bit.

Contoh potongan output:

```text
  build -> 72 05 74 01 14
PASS baca 74/01 (harap 72 05 74 01 14)
PASS baca 74/01 jumlah=0x00 (0x00)
---
Ringkasan: PASS=17 FAIL=0
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Status bukti frame

Frame di atas adalah **kandidat request yang terdokumentasi** di
`docs/DTC_ENGINE.md` dan `docs/KLINE_PROTOCOL.md`. Statusnya
**Source-analyzed**: diambil dari analisis source dan dokumentasi repo, bukan
dari pengujian pada ECU fisik. Tidak ada frame di sini yang membuktikan
respons dari kendaraan tertentu.

## Batasan

- Hanya membangun request; tidak mengirim byte ke port serial.
- Tidak memvalidasi respons (lihat sampel 05).
- Tidak memeriksa apakah service/parameter diterima oleh ECU.
- Batas maksimum panjang frame `0xFE` dari dokumentasi tidak ditegakkan di
  sampel ini.
- Frame yang checksum-nya benar belum tentu dijawab ECU; checksum hanya
  memastikan frame tidak rusak saat dikirim.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan frame query ID `72 07 72 00 00 05 10` (dari `docs/KLINE_PROTOCOL.md`) ke self-test. Berapa `length` dan `header` yang harus dipakai?
2. Buat `buildFrame` menolak `params` lebih dari 4 byte, lalu tambahkan test untuk penolakan itu.
3. Uji apa yang terjadi jika checksum dihitung termasuk byte checksum terakhir (yang masih 0). Jelaskan hasilnya.
4. Bandingkan frame tabel `71/7A` (`72 05 71 7A 9E`) dengan request di `LiveDataProfileParserTest.kt`. Apakah keduanya identik?
