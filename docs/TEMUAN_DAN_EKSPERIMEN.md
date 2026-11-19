# TEMUAN DAN EKSPERIMEN

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini mencatat eksperimen yang berhasil, yang gagal, dan yang belum selesai. Tujuannya agar pembaca tidak mengulang kesalahan yang sama dan dapat membedakan implementasi yang selesai dari yang baru diuji sebagian.

## 1. Kosakata hasil

| Istilah | Arti |
|---|---|
| `BERHASIL` | Tujuan eksperimen tercapai dan ada bukti yang sesuai |
| `SEBAGIAN` | Sebagian tujuan tercapai; sebagian lain terbuka |
| `GAGAL` | Pendekatan terbukti tidak bekerja |
| `BELUM SELESAI` | Pekerjaan berhenti sebelum kesimpulan |
| `DIPERTANYAKAN` | Ada bukti yang saling bertentangan |
| `TIDAK DIUJI` | Belum ada pengujian yang relevan |

## 2. Ringkasan eksperimen

| Eksperimen | Hasil | Bukti |
|---|---|---|
| Framing dan checksum K-Line | `BERHASIL` | Unit test + fixture sintetis |
| Parser respons dan echo | `BERHASIL` | Unit test + negatif test |
| Tabel Live Data | `SEBAGIAN` | 7 tabel direkonstruksi; 8 parameter dilaporkan tampil |
| TPS 0% saat throttle tertutup | `BELUM SELESAI` | Bug dilaporkan pemilik; formula belum dikalibrasi |
| Skala Battery/Injector | `DIPERTANYAKAN` | Dua skala berbeda tercatat |
| Identifikasi EOT | `DIPERTANYAKAN` | EOT dilaporkan tampil; source publik hanya membaca ECT |
| Parser DTC E5/E6 | `GAGAL` (edge case) | Audit source; belum diperbaiki |
| Subkode DTC > 15 | `GAGAL` (edge case) | Audit source; belum diperbaiki |
| AFR dari tegangan O2 | `GAGAL` (pendekatan) | Dihapus; AFR sekarang `UNSUPPORTED` |
| Klasifikasi berkas MAP | `BERHASIL` (analisis) | 162 berkas diidentifikasi sebagai kontainer terenkripsi |
| Integrasi MAP ke kendaraan | `TIDAK DIUJI` | Tidak ada pengujian kendaraan |
| Adapter terputus sesekali | `BELUM SELESAI` | Prioritas pemilik; belum direproduksi |
| Layar tetap menyala | `BELUM SELESAI` | Prioritas pemilik; belum diverifikasi |

## 3. Framing dan checksum

**Tujuan:** memastikan frame request/response dapat dibangun dan divalidasi tanpa menebak.

**Yang berhasil:**

- Header baseline `0x72`, panjang, service, parameter, dan checksum dua-komplemen.
- Request Live Data contoh `72 05 71 7A 9E` dan respons sintetis `72 11 71 7A 0B B8 80 40 50 40 00 64 F0 3C 5A C8 CD`.
- Validasi panjang, echo service, echo tabel, dan checksum.
- Penolakan frame cacat: checksum salah, payload pendek, service error `0x7F`, dan echo tabel yang tidak cocok.

**Batas:** semua bukti berasal dari unit test dan fixture sintetis. Tidak ada tangkapan ECU fisik di repository.

## 4. Live Data

**Tujuan:** memetakan payload respons ke nilai sensor.

**Yang berhasil:**

- Tujuh tabel dipetakan: `17`, `13`, `11`, `10`, `16`, `20`, dan `D1`.
- 84 definisi field dengan offset, panjang, endian, skala, bias, rentang, dan state.
- Delapan parameter dilaporkan tampil oleh pemilik: RPM, TPS, EOT, Battery Voltage, Ignition Advance, Injector Pulse, Vehicle Speed, dan O2.

**Yang belum berhasil:**

- TPS saat throttle tertutup belum mencapai 0%. Formula `raw × 100/256` belum memakai offset atau kalibrasi zero throttle.
- Skala Battery dan Injector tidak konsisten antara contoh sintetis lama dan implementasi:
  - contoh lama: 0,05 V/count dan 0,01 ms/count;
  - implementasi: 0,1 V/count dan 1/200 ms/count.
  Perbedaan belum diselesaikan.
- EOT dilaporkan tampil, tetapi jalur source publik hanya membaca ECT. Apakah EOT berasal dari kanal terpisah masih `UNKNOWN`.
- Tabel lain belum diuji pada kendaraan.
- Penerimaan tabel oleh ECU hanya dapat dibuktikan melalui transaksi wire.

Rincian ada di [LIVE_DATA_REFERENCE.md](LIVE_DATA_REFERENCE.md).

## 5. DTC

**Yang berhasil:**

- Pembacaan dan penghapusan DTC berjalan menurut laporan pemilik.
- Pemetaan 15 kode referensi tersedia.
- Pembacaan ulang setelah clear dipakai untuk menentukan keberhasilan, bukan asumsi.

**Yang gagal atau belum selesai:**

- **E5:** validasi count-prefixed memakai `payload.size >= 1 + firstByte*2`, bukan sama dengan. Byte ekor dapat diterima.
- **E6:** payload yang gagal diurai menghasilkan daftar kosong, lalu dipetakan ke `OK_EMPTY`. "Tidak ada DTC" dan "paket tidak terbaca" tidak terpisah.
- **Subkode > 15:** subkode di luar 0..15 dipaksa menjadi 0; kode berbeda dapat bertabrakan.

Ketiganya ditemukan melalui audit source dan belum diperbaiki. Jangan menganggap pembacaan kosong selalu berarti tidak ada DTC.

## 6. AFR

**Pendekatan yang gagal:** menghitung AFR dari tegangan O2 narrowband.

ECU hanya mempublikasikan tegangan O2 dan STF. Turunan host menghasilkan angka yang terlihat pasti tetapi bukan data ECU. Pendekatan itu dihapus; AFR sekarang selalu `UNSUPPORTED`.

## 7. MAP

**Awalnya dianggap:** berkas `.map` adalah definisi peta.

**Hasil analisis:** berkas `.map` adalah kontainer terenkripsi yang berisi image flash ECU. Definisi peta berada di profil ECM, bukan di dalam berkas.

**Yang berhasil:**

- 162 berkas diinventarisasi dengan ukuran dan SHA-256.
- Struktur kontainer diidentifikasi: ciphertext = plaintext + 16 byte padding; tidak ada header atau indeks.
- 159 profil dapat ditautkan dengan bukti nama resource yang unik; 2 ambigu; 1 tidak dikenali.

**Yang tidak dilakukan:**

- Berkas mentah tidak disertakan karena merupakan salinan aset pihak lain.
- Kunci dan IV tidak dipublikasikan.
- Tidak ada penulisan, flashing, atau modifikasi berkas asli.

## 8. Adapter dan layar

Dua masalah ini tercatat sebagai prioritas pemilik tetapi belum diselesaikan:

- **Adapter terputus sesekali.** Belum ada reproduksi yang stabil; penyebab belum diketahui.
- **Layar tidak selalu tetap menyala saat aplikasi aktif.** Belum diverifikasi pada semua perangkat.

Keduanya berstatus `BELUM SELESAI`, bukan `GAGAL`.

## 9. Pelajaran

1. Jangan menganggap data yang tampil sebagai data yang benar; periksa asal setiap nilai.
2. Jangan mengubah nilai yang gagal menjadi nol.
3. Jangan menurunkan AFR dari sensor narrowband.
4. Jangan menganggap paket DTC yang gagal diurai sebagai "tidak ada DTC".
5. Jangan menganggap format berkas sebagai definisi hanya karena namanya.
6. Jangan mengklaim dukungan kendaraan tanpa pengujian kendaraan.
7. Simpan log TX/RX mentah agar temuan dapat diperiksa ulang.
