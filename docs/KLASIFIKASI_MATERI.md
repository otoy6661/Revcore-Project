# KLASIFIKASI MATERI

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini memisahkan tiga kategori materi agar batas publikasi jelas. Ketidakpastian satu berkas tidak dipakai untuk menahan seluruh koleksi.

## 1. Hasil pengembangan RevCore (disertakan)

Materi berikut disusun melalui analisis, eksperimen, rekonstruksi, atau implementasi RevCore. Materi ini **bukan** salinan berkas pihak lain.

| Materi | Lokasi publik | Status |
|---|---|---|
| Database kendaraan 489 record | `data/vehicle_database/ecm_database.json` | Terdaftar; 0 terverifikasi hardware |
| Profil ECM 163 record | `data/vehicle_database/ecm_profiles.json` | Rekonstruksi; 0 teruji kendaraan |
| Inventaris MAP | `data/map_library/inventory.csv` | Dianalisis |
| Tabel Live Data | `data/live_data/live_tables.json` | Rekonstruksi; sebagian ditampilkan |
| Dokumentasi protokol | `docs/` | Analisis dan pengujian terbatas |
| Parser, checksum, framing | `core/` | Diuji unit test |
| Contoh dan eksperimen | `examples/`, `samples/` | Sintetis atau bereferensi |
| Temuan dan bug | `docs/TEMUAN_DAN_EKSPERIMEN.md` | Sebagian terbuka |

Catatan: fakta yang dipelajari dari sumber publik lalu diverifikasi, digabung, atau diimplementasikan ulang oleh RevCore tetap termasuk hasil pengembangan RevCore. Yang tidak boleh disalin adalah **berkas asli** milik pihak lain.

## 2. Referensi eksternal (dipakai sebagai pembanding, tidak disalin)

| Bentuk | Perlakuan |
|---|---|
| Informasi publik tentang kendaraan/ECM | Diverifikasi silang, digabung, dan dicatat statusnya |
| Dokumentasi protokol komunitas | Dibandingkan dengan hasil uji RevCore |
| Perangkat lunak pembanding | Dianalisis untuk memahami perilaku; tidak disertakan |
| Database referensi eksternal | Dibandingkan terbatas; tidak disalin utuh |
| Definisi peta eksternal | Dibandingkan; tidak disertakan |

Identitas program, URL, nama berkas, dan asal spesifik tidak dicantumkan dalam repository publik.

## 3. Berkas mentah pihak lain (dipisahkan)

| Materi | Jumlah | Lokasi internal | Alasan pemisahan | Bukti |
|---|---:|---|---|---|
| Berkas MAP mentah | 162 | arsip aset analisis internal (path tidak dipublikasikan) | Arsip aset pihak lain, diekstrak tanpa perubahan; nama berkas dipertahankan | SHA-256 arsip `acf6bb8e…589a7`; 162 hash di manifest; tidak ada bukti dibuat ulang |
| Image BIN eksternal | 700 | pustaka analisis eksternal | Bukan hasil karya RevCore; hak tidak jelas | Tidak disertakan sesuai keputusan pemilik 2026-11-19 |
| Definisi peta eksternal | 177 (174 XDF + 3 ADX) | pustaka analisis eksternal | Bukan hasil karya RevCore | Tidak disertakan sesuai keputusan pemilik 2026-11-19 |
| Firmware ECU | beberapa | `docs/` internal | Bukan milik RevCore | Tidak disalin ke repository publik |
| Aset APK RevCore | isi APK | APK 161126 | Build utuh; hanya untuk GitHub Releases | SHA-256 APK diverifikasi |

Pemisahan ini **spesifik pada berkas mentah**, bukan pada pengetahuan yang diperoleh darinya. Analisis, rumus, parser, pemetaan, dan dokumentasi tetap dipublikasikan.

Keputusan pemilik 2026-11-19:

1. Dari 162 berkas MAP, **0 berkas** dapat dipastikan sebagai hasil pekerjaan, rekonstruksi,
   atau eksperimen mandiri RevCore. Verifikasi byte-per-byte menunjukkan 162/162 identik
   dengan aset pihak lain. Karena itu tidak ada berkas MAP biner yang dilampirkan;
   inventaris dan analisisnya tetap tersedia di `data/map_library/`.
2. MAP-093 yang belum dikenali tetap dicantumkan dan dianalisis; status format yang belum
   dikenali bukan alasan untuk menghapus hasil rekonstruksi.
3. 700 image BIN dan 177 definisi peta eksternal tidak dimasukkan ke repository.
   Dokumentasi dan hasil analisis yang memakai materi tersebut sebagai referensi tetap
   dipublikasikan.

## 4. Batas yang tetap berlaku

Materi berikut tidak dipublikasikan:

- Seluruh materi Gate 10, termasuk source, struktur, algoritma, konfigurasi, parameter, dokumentasi teknis, diagram, dan log internal.
- Struktur hierarki UI, layout, navigasi internal, hubungan antarkomponen, dan implementasi UI.
- Kredensial, token, private key, keystore, dan data pribadi.
- Berkas mentah pihak lain yang hak publikasinya belum jelas.
- Kunci dan IV kontainer MAP.

Dokumentasi publik cukup menyatakan bahwa beberapa komponen internal tidak disertakan. Rincian teknisnya tidak dijelaskan.

## 5. Aturan keputusan

1. Jika materi adalah hasil analisis atau implementasi RevCore, masukkan ke publikasi.
2. Jika materi adalah berkas asli pihak lain, pisahkan dan laporkan secara spesifik.
3. Jangan menahan seluruh koleksi karena satu berkas bermasalah.
4. Jangan mengubah berkas asli untuk kebutuhan publikasi.
5. Jangan mengklaim hasil pengujian yang belum dilakukan.
