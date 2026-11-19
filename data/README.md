# Data Referensi RevCore

Direktori ini memuat data hasil pengumpulan, analisis, dan penyusunan RevCore yang boleh dipelajari dan dipakai kembali. Data di sini adalah **referensi**, bukan katalog dukungan kendaraan.

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

## Isi

| Direktori | Isi | Status |
|---|---|---|
| [`vehicle_database/`](vehicle_database/) | 489 record database kendaraan dan 163 profil ECM | Terdaftar; 0 terverifikasi hardware |
| [`map_library/`](map_library/) | Inventaris 162 aset MAP, hash, dan tautan profil | Dianalisis; 0 berkas biner hasil RevCore; berkas mentah tidak disertakan |
| [`live_data/`](live_data/) | 7 tabel Live Data dan 84 definisi field | Rekonstruksi; sebagian ditampilkan di APK menurut pemilik |

Bahan pembanding eksternal berupa 700 image BIN dan 177 definisi peta tidak disertakan
sesuai keputusan pemilik 2026-11-19. Dokumentasi dan analisis RevCore yang memakai bahan
tersebut sebagai referensi tetap dipublikasikan.

## Prinsip data

1. **Status jujur.** `terdaftar` bukan `didukung`. `belum diuji` bukan `gagal`.
2. **Tidak ada klaim hardware tanpa bukti.** Tidak ada record yang dinyatakan teruji pada kendaraan fisik.
3. **Provenance digeneralisasi.** Nama program dan nama berkas sumber eksternal tidak dicantumkan. Salinan internal tidak diubah.
4. **Berkas mentah pihak lain dipisahkan.** Berkas MAP yang terbukti salinan aset pihak lain tidak disertakan; inventaris dan analisisnya tetap tersedia.
5. **Data dapat berubah.** Format dan field dapat berbeda antar ECU, tahun, dan pasar.

## Lisensi dan penggunaan

Hak penggunaan data mengikuti [NOTICE.md](../NOTICE.md). Data disediakan untuk pembelajaran dan pengembangan; tidak ada jaminan kebenaran, kelengkapan, atau kesesuaian untuk kendaraan tertentu.
