# Sampel 07 - Live data decoder

## Tujuan

Mengubah payload biner menjadi nilai sensor memakai tabel field generik
(offset, panjang, urutan byte, skala, bias), lalu menampilkan tabel
`field | raw | value | unit`. Layout dan rumus diturunkan langsung dari source
test yang dipublikasikan, bukan ditebak.

## Yang dipelajari

- **Big-endian**: byte pertama adalah bit paling tinggi. `0B B8` = `0x0BB8` =
  3000, bukan 47115.
- **Skala dan bias**: nilai akhir = `raw * skala + bias`. Contoh ECT
  `0x50` (80) dengan bias `-40` menjadi 40 C.
- **Field tidak dipublikasikan** ditampilkan `UNSUPPORTED`, tidak pernah 0.
- **Status data**: `VALID`, `NOT_RESPONDING` (byte belum diterima),
  `INVALID_RESPONSE` (nilai mustahil / 0xFF tanpa cadangan).
- **Little-endian** dan nilai **signed** 16-bit untuk field yang membutuhkannya.
- Mengapa `0` adalah pengukuran sah ("mesin mati"), bukan tanda "data hilang".

## Prasyarat

- JDK (Temurin 21 atau lebih baru). Sampel memakai `Locale.ROOT` agar desimal
  selalu memakai titik, apa pun locale sistem.
- Tidak butuh Android, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/07-live-data-decoder
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Payload uji ditulis langsung di kode:
`0B B8 80 40 50 40 00 64 F0 3C 5A C8` (dari `SampleLiveData.payload` di
`LiveDataProfileParserTest.kt`).

| Field | Offset | Len | Skala | Bias | Raw | Value | Unit |
|---|---:|---:|---:|---:|---:|---:|---|
| RPM | 0 | 2 (BE) | 1.0 | 0 | 3000 | 3000 | rpm |
| TPS_ADC | 2 | 1 | 1.0 | 0 | 128 | 128 | count |
| TPS_PERCENT | 2 | 1 | 100/255 | 0 | 128 | 50.196 | % |
| TPS_VOLTAGE | 2 | 1 | 5/255 | 0 | 128 | 2.510 | V |
| TPS_DEGREES | 3 | 1 | 1.0 | 0 | 64 | 64 | deg |
| ECT | 4 (alt 10) | 1 | 1.0 | -40 | 80 | 40 | C |
| IAT | 5 | 1 | 1.0 | -40 | 64 | 24 | C |
| MAP | 6 | 2 (BE) | 1.0 | 0 | 100 | 100 | kPa |
| BATTERY | 8 | 1 | 0.05 | 0 | 240 | 12 | V |
| SPEED | 9 | 1 | 1.0 | 0 | 60 | 60 | km/h |
| INJECTOR | 11 | 1 | 0.01 | 0 | 200 | 2 | ms |
| AFR | - | - | - | - | - | UNSUPPORTED | - |

Contoh potongan output:

```text
RPM            3000         3000 rpm      VALID
ECT              80           40 C        VALID
AFR              --  UNSUPPORTED -        UNSUPPORTED
PASS ECT (40)
---
Ringkasan: PASS=24 FAIL=0
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Mengapa 0 bukan penanda "data hilang"

`0x00` pada RPM adalah nilai sah: mesin mati / kontak ON tetapi belum distarter.
Jika program memakai `0` untuk "tidak ada data", dua keadaan berbeda akan
terlihat sama: **mesin diam** dan **byte tidak pernah diterima**. Sampel ini
memakai status terpisah:

| Keadaan | Status | Nilai angka |
|---|---|---|
| Byte ada dan wajar | `VALID` | angka sebenarnya (boleh 0) |
| Byte belum diterima | `NOT_RESPONDING` | tidak diisi |
| Tabel tidak memuat sensor | `UNSUPPORTED` | tidak diisi |
| Nilai mustahil / 0xFF tanpa cadangan | `INVALID_RESPONSE` | tidak diisi |

Nilai di luar rentang **ditolak**, bukan dipotong ke batas (tidak di-clamp),
supaya kesalahan tabel atau payload cepat terlihat.

## Batasan

- Layout dan tabel adalah **sintetis** dari `SampleLiveData.profile`; bukan
  layout ECU Honda. Tabel hasil rekonstruksi RevCore tersedia terpisah di
  [docs/LIVE_DATA_REFERENCE.md](../../docs/LIVE_DATA_REFERENCE.md) dan `data/live_data/`,
  tetapi tidak dipakai oleh sampel ini.
- Request frame (`72 05 71 7A 9E`) tidak dikirim; payload diberikan langsung.
- Sampel tidak menerapkan `minPayload` untuk membatalkan seluruh frame; hanya
  field yang byte-nya tidak ada yang menjadi `NOT_RESPONDING`.
- Field `AFR` tidak ada di profil; sampel menambahkannya sebagai contoh
  `UNSUPPORTED` agar perilakunya terlihat.
- Tidak memeriksa echo tabel (`table echo` di index 3) seperti
  `extractLivePayload` di RevCore.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan field `MAP_VOLT` dengan skala `5.0 / 5000.0` pada offset 6 (2 byte)
   dan self-test yang sesuai. Berapa volt untuk raw 100?
2. Buat field baru pada offset 11 dengan little-endian dan buktikan hasilnya
   berbeda dari versi big-endian.
3. Ubah `decode` agar mencetak baris log langkah demi langkah untuk semua field
   (bukan hanya lewat helper demo), lalu bandingkan dengan tabel.
4. Mengapa mengubah nilai `ECT` yang mustahil menjadi `150` (clamp) berbahaya
   saat mendiagnosis kendaraan? Jelaskan dalam 3-4 kalimat.
