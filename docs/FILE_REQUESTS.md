# PERMINTAAN BERKAS TAMBAHAN

Developer dapat meminta berkas tambahan atau mengusulkan konten yang belum ada di
repository. Permintaan ditinjau berdasarkan kebutuhan teknis, keamanan, hak penggunaan,
provenance, dan persetujuan pemilik.

## 1. Yang dapat diminta

- dokumentasi format atau protokol tambahan;
- contoh sintetis untuk kasus yang belum tercakup;
- klarifikasi perilaku kode publik;
- profil atau dataset yang Anda miliki haknya dan ingin sumbangkan;
- perbaikan fixture atau test.

## 2. Yang tidak dijanjikan

Repository ini tidak menjanjikan bahwa semua berkas internal dapat diberikan. Materi
berikut tetap dikecualikan:

- source Application Layer dan Security/Integrity;
- berkas mentah MAP/profil/firmware pihak lain, firmware ECU, atau binary ROM;
- keystore, private key, token, atau kredensial;
- catatan analisis internal dan data pribadi.

Database kendaraan, profil ECM, inventaris MAP, dan tabel Live Data hasil pengembangan
RevCore sudah tersedia di `data/` dan tidak perlu diminta terpisah.

## 3. Kriteria peninjauan

1. **Kebutuhan teknis** — apakah berkas diperlukan untuk menggunakan atau mempelajari
   modul publik.
2. **Keamanan dan privasi** — apakah berkas membocorkan rahasia, kunci, atau data pribadi.
3. **Hak penggunaan** — apakah Anda atau pemilik berhak mendistribusikan berkas.
4. **Provenance** — apakah asal berkas jelas dan tidak menyamarkan materi pihak ketiga.
5. **Persetujuan pemilik** — keputusan akhir ada pada pemilik proyek.

## 4. Cara mengajukan

Buka issue dengan informasi:

- tujuan dan penggunaan berkas;
- nama/format berkas yang diminta;
- bagian kode atau dokumentasi yang memerlukannya;
- dasar hak penggunaan (bila Anda mengusulkan berkas baru);
- apakah Anda dapat menyediakan versi sintetis.

Jangan mengunggah materi proprietary, APK, firmware, atau kredensial ke issue. Jelaskan
jenis materi dan tunggu instruksi pemilik.

## 5. Kemungkinan hasil

| Hasil | Arti |
|---|---|
| Diterima | Berkas ditambahkan atau dokumentasi diperbarui |
| Diterima dengan redaksi | Sebagian materi dihapus/digeneralisasi |
| Ditahan | Menunggu bukti hak atau keputusan pemilik |
| Ditolak | Tidak memenuhi kriteria teknis, keamanan, atau hak |

Tidak ada SLA. Jangan mengulang permintaan yang sama tanpa informasi baru.
