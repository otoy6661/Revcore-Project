# Sesi Diagnostik

## Tujuan

Menyusun satu sesi diagnostik utuh di atas abstraksi `Transport`: connect → identify → baca DTC → hapus DTC → baca ulang untuk verifikasi, dengan status hasil yang tegas dan tidak pernah "mengarang" keberhasilan.

## Yang dipelajari

- Urutan sesi dan kandidat frame:
  - Baca DTC: `72 05 74 01 14`, lalu `72 05 71 20 F8`, lalu `72 05 00 00 89`.
  - Hapus DTC: `72 05 60 01 28`, lalu `72 05 74 02 13`, lalu `72 04 04 86`.
- Status baca: `NOT_PERFORMED`, `OK_EMPTY`, `OK_CODES`, `NO_RESPONSE`, `UNRECOGNIZED`.
- Status hapus: `SUCCESS_VERIFIED`, `CLEARED_UNVERIFIED`, `RE_DETECTED_ACTIVE_FAULT`, `REJECTED_OR_TIMEOUT`.
- Aturan keselamatan data (lihat `docs/DTC_ENGINE.md`):
  1. `SUCCESS_VERIFIED` **hanya** setelah pembacaan ulang valid yang benar-benar kosong.
  2. `NO_RESPONSE` **tidak** mengosongkan daftar DTC sebelumnya.
  3. Respons negatif `0x7F` tidak boleh tampil sebagai "tidak ada DTC".

| Status | Arti (ringkas `docs/DTC_ENGINE.md`) |
|---|---|
| `NOT_PERFORMED` | Belum ada pembacaan. |
| `OK_EMPTY` | Frame valid, tidak ada kode. |
| `OK_CODES` | Frame valid, ada kode. |
| `NO_RESPONSE` | Tidak ada frame valid / semua kandidat gagal. |
| `UNRECOGNIZED` | Frame valid, format tidak dapat dipastikan. |
| `SUCCESS_VERIFIED` | Clear diakui dan baca ulang tidak menemukan DTC. |
| `RE_DETECTED_ACTIVE_FAULT` | Clear diakui, tetapi kode muncul lagi. |
| `CLEARED_UNVERIFIED` | Clear diakui, verifikasi gagal (`NO_RESPONSE`/`UNRECOGNIZED`). |
| `REJECTED_OR_TIMEOUT` | Tidak ada ACK valid. |

## Prasyarat

- JDK 17 atau lebih baru (diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android, kabel, adapter, atau library eksternal.

## Cara menjalankan

Jalankan dari **direktori sampel ini**:

```bash
cd samples/12-sesi-diagnostik
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: tidak ada berkas — setiap skenario adalah daftar respons ECU sintetis di dalam `src/Main.java`.

Output yang diharapkan (ringkasnya):

```text
Skenario A: DTC hilang setelah clear
  read     -> OK_CODES [P0107, P0117]
  clear    -> ACK diterima
  re-read  -> OK_EMPTY []
  clear    -> SUCCESS_VERIFIED

Skenario B: DTC muncul lagi        -> RE_DETECTED_ACTIVE_FAULT
Skenario C: baca ulang tanpa respons -> CLEARED_UNVERIFIED (daftar lama tetap ada)
```

Ditutup dengan `Ringkasan: PASS=n FAIL=0` dan `HASIL: OK` (kode keluar 0). Ada juga skenario tambahan D (tanpa ACK → `REJECTED_OR_TIMEOUT`) dan E (ACK + baca ulang `0x7F` → `CLEARED_UNVERIFIED`).

Frame respons sintetis yang dipakai:

- DTC: `72 0A 74 01 02 01 01 07 01 03` → payload `02 01 01 07 01` → `P0107`, `P0117`.
- ACK hapus: `72 05 60 01 00 28`.
- Kosong: `72 04 74 01 03 12` (payload berisi echo `03`, tanpa pasangan kode).
- Negatif: `72 06 7F 74 12 83`.

## Batasan

- Seluruh ECU adalah simulasi; tidak ada kendaraan atau perangkat keras yang diuji.
- Pemetaan kode hanya untuk dua pasangan yang tercatat di repo (`01/01` → P0107, `07/01` → P0117). Pasangan lain ditampilkan sebagai `DTC 0xXXYY (belum terverifikasi)`, tidak ditebak.
- Jeda 250 ms sebelum baca ulang (yang disebut `docs/DTC_ENGINE.md`) tidak disimulasikan; sampel langsung membaca ulang.
- Tidak ada penyimpanan riwayat sesi, log berkas, atau mode "hanya baca".
- **Clear DTC tidak menjamin lampu MIL padam.** `SUCCESS_VERIFIED` hanya berarti ECU tidak lagi melaporkan kode pada pembacaan ulang saat itu; siklus kontak dan uji jalan tetap diperlukan.
- Aturan byte panjang mengikuti frame baseline repo: `length` sama dengan ukuran seluruh frame.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan skenario F: identify gagal, lalu pastikan sesi berhenti sebelum baca DTC dan tidak pernah menyentuh clear.
2. Tambahkan skenario dengan respons `OK_EMPTY` palsu yang ditolak checksum, lalu pastikan hasilnya `NO_RESPONSE`, bukan `SUCCESS_VERIFIED`.
3. Tambahkan simulasi jeda 250 ms dengan `Thread.sleep` di antara ACK dan baca ulang, dan catat waktunya.
4. Buat kelas `DtcReport` yang menyimpan riwayat status per sesi dan mencetaknya sebagai tabel.
5. Tambahkan kandidat baca yang pertama dijawab dengan `NO_RESPONSE` tetapi kandidat kedua berhasil, untuk menguji perpindahan kandidat.
