# INVENTARIS DATABASE KENDARAAN

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini mencatat cakupan database kendaraan dan profil ECM yang dikembangkan RevCore. Dataset lengkap tersedia di [`data/vehicle_database/`](../data/vehicle_database/).

Dokumen dan dataset ini **bukan** daftar kendaraan yang didukung, teruji, atau kompatibel. Tidak ada satu pun entri yang telah diuji pada kendaraan fisik dalam pekerjaan ini.

## 1. Ringkasan

| Item | Nilai |
|---|---:|
| Record database internal | 489 |
| Nomor part unik | 472 |
| Label model/varian unik | 346 |
| Record dengan rentang tahun | 457 |
| Record dengan kapasitas mesin | 176 |
| `isVerifiedDiagnostic = true` | 0 |
| `isVerifiedRemap = true` | 0 |
| Batas RPM terisi (bukan 0) | 0 |
| Offset peta bahan bakar/pengapian terisi | 0 |
| Record database di repository publik | 489 record nyata + 3 record sintetis contoh |
| Profil ECM di repository publik | 163 (1 placeholder + 162 profil) |

Database lengkap dan profil ECM **disertakan** di `data/vehicle_database/`. Salinan publik mempertahankan nomor part, ID ECM, offset, dan catatan teknis, tetapi mengganti nama program, URL, dan nama berkas sumber dengan kategori provenance netral. Salinan internal tidak diubah.

## 2. Sumber dan hak distribusi

- Database disusun dari beberapa sumber publik, lalu digabung, dibandingkan, dan disusun ulang oleh RevCore.
- Identitas program, nama berkas sumber, URL, dan asal spesifik tidak diungkap.
- Kolom `source` memakai kategori netral: `public-reference`, `revcore-legacy`, atau `unverified`.
- Salinan publik dibuat terpisah; berkas internal tidak diubah.
- Tidak ada record yang diklaim sebagai hasil pengujian hardware.
- Keputusan publikasi data hasil pengembangan diambil oleh pemilik proyek.

### 2.1 Varian database yang diperiksa

| Bentuk | Status |
|---|---|
| Database utama (489 record) | Dipublikasikan di `data/vehicle_database/ecm_database.json` |
| Salinan build/verifikasi | Duplikat; tidak dihitung sebagai database independen |
| Suplemen dan turunan database | Tidak digabung; hak dan kualitas belum diperiksa selesai |
| Database referensi eksternal | Dibandingkan secara terbatas; tidak didistribusikan; identitas tidak diungkap |
| Database contoh repository | 3 record sintetis `SAMPLE-ECM-001..003` di `examples/` |
| Profil ECM | 163 record (1 placeholder + 162 profil); dipublikasikan di `data/vehicle_database/ecm_profiles.json` |

## 3. Cakupan database

### 3.1 Produsen ECM

| Nilai | Record |
|---|---:|
| Keihin | 344 |
| Shindengen | 132 |
| Belum terverifikasi | 13 |

### 3.2 Tipe MCU

| Nilai | Record |
|---|---:|
| R8C | 143 |
| V850 | 78 |
| RH850:v1 | 54 |
| M32R | 47 |
| MPC560 | 47 |
| Belum terverifikasi | 43 |
| SH705X | 19 |
| RH850 | 14 |
| M16C | 14 |
| H8C | 10 |
| SPC560 | 9 |
| H8 | 5 |
| SH705 | 4 |
| MPC560X | 1 |
| MPC5602D | 1 |

### 3.3 Ukuran flash

| Ukuran (KB) | Record |
|---|---:|
| 32 | 3 |
| 48 | 9 |
| 56 | 2 |
| 64 | 66 |
| 96 | 19 |
| 128 | 65 |
| 256 | 160 |
| 384 | 70 |
| 512 | 67 |
| 1024 | 15 |
| 0 (belum diketahui) | 13 |

### 3.4 Negara/pasar

| Nilai | Record |
|---|---:|
| Indonesia | 128 |
| unknown | 126 |
| Asia | 71 |
| Thailand | 71 |
| Vietnam | 32 |
| Malaysia | 27 |
| Philippines | 10 |
| Kosong | 24 |

### 3.5 Kategori

| Nilai | Record |
|---|---:|
| Matic | 197 |
| Sport | 117 |
| Cub/Sport | 106 |
| Universal | 37 |
| Bebek | 24 |
| Bebek / Underbone | 6 |
| Offroad | 1 |
| Lainnya | 1 |

## 4. Status bukti

| Status | Jumlah | Arti |
|---|---:|---|
| Dilaporkan pemilik berhasil (fungsi aplikasi) | 0 record DB | Status fungsi aplikasi dicatat terpisah; bukan bukti kecocokan kendaraan |
| Diuji perangkat lunak | 0 record DB | Unit test memakai fixture sintetis, bukan record nyata |
| Diamati pada hardware | 0 | Tidak ada observasi ECU fisik |
| Diuji pada kendaraan | 0 | Tidak ada uji kendaraan |
| Terdaftar di database | 489 | Record ada di database internal; belum diuji |
| Profil aplikasi | 163 record | 1 placeholder + 162 profil; keterkaitan ke kendaraan belum diverifikasi |
| Tidak diketahui | 489 | Kompatibilitas dan perilaku ECU belum diketahui |

Tidak ada kendaraan yang boleh disebut **didukung**, **kompatibel**, atau **teruji** berdasarkan tabel di atas.

## 5. Keluarga model pada bahan analisis

Bahan analisis menyebut keluarga model berikut. Penyebutan ini hanya menunjukkan cakupan bahan, bukan klaim dukungan:

ADV, BeAT, Blade, CB/CBR, CRF, Genio, MegaPro, PCX, Revo, Scoopy, Sonic, Spacy, Supra, Vario, Verza.

Label model/varian tersedia di dataset publik. Jumlah label unik adalah 346; angka itu tidak berarti 346 kendaraan teruji. Perbedaan label antar sumber dicatat di `statusNotes`.

## 6. Aturan status

- `terdaftar di database` tidak sama dengan `didukung`.
- `profil tersedia` tidak sama dengan `kompatibel`.
- `belum diuji` tidak sama dengan `gagal`.
- Status hanya dinaikkan bila ada bukti yang sesuai.
- Jika bug ditemukan, status awal tetap dicatat sebagai riwayat.
- Setiap record baru wajib memakai `isVerifiedDiagnostic: false` dan `isVerifiedRemap: false` sampai ada uji hardware.
- `revLimitSource` wajib `unverified` bila batas RPM belum terbukti.

## 7. Celah yang masih terbuka

| Celah | Status |
|---|---|
| Klasifikasi kepemilikan data | Hasil pengembangan RevCore; berkas mentah pihak lain tidak disertakan |
| Kecocokan record dengan kendaraan fisik | Tidak diuji |
| Validitas nomor part dan ID ECM | Sebagian belum diverifikasi |
| Offset peta dan batas RPM | Sebagian besar `0x0`/`unverified`; belum terbukti |
| Perbedaan model/varian dengan label mirip | Belum diselesaikan |
| Profil aplikasi ke record database | Belum diverifikasi menyeluruh |
| Pengujian pada kendaraan | Tidak dilakukan |
