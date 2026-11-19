# Inventaris MAP

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Direktori ini memuat **inventaris dan hasil analisis** MAP, bukan berkas MAP mentah. Berkas mentah dipisahkan karena merupakan salinan aset pihak lain.

## Berkas

| Berkas | Isi |
|---|---|
| [`inventory.csv`](inventory.csv) | 162 baris: `mapId`, ukuran, SHA-256, format, status analisis, kandidat profil, metode pencocokan, status tautan, status distribusi |

## Ringkasan

| Item | Nilai |
|---|---:|
| Berkas MAP | 162 |
| Total ukuran | 35.195.840 byte |
| SHA-256 unik | 161 (satu duplikat) |
| Tautan profil unik (`READY`) | 159 |
| Kandidat ganda (`AMBIGUOUS_PROFILE`) | 2 |
| Tidak dikenali (`UNRECOGNIZED`) | 1 |
| Berkas MAP biner hasil karya RevCore | **0** |
| Berkas mentah disertakan | **Tidak** |

## Keputusan distribusi 2026-11-19

Atas keputusan pemilik, hanya berkas MAP yang benar-benar merupakan hasil pekerjaan,
rekonstruksi, atau eksperimen mandiri RevCore yang boleh dilampirkan. Verifikasi
menunjukkan **0 dari 162 berkas** memenuhi syarat tersebut:

- Seluruh 162 berkas dibandingkan byte-per-byte dengan arsip sumber dan salinan aset
  aplikasi; hasilnya **162/162 identik**, 0 berbeda, 0 hilang.
- Tidak ada berkas yang dibuat ulang, disintesis, atau dimodifikasi oleh RevCore.
- Karena itu tidak ada berkas MAP biner yang dilampirkan. Inventaris, hash, struktur,
  tautan profil, dan analisisnya tetap dipublikasikan.
- MAP-093 (referensi publik, 432 byte) tetap dicantumkan walaupun formatnya belum
  dikenali; hasil rekonstruksi tidak dihapus hanya karena status formatnya.

## Mengapa berkas mentah tidak disertakan

Berkas MAP berasal dari satu arsip aset yang diterima sebagai bahan analisis. Arsip itu diekstrak tanpa perubahan, nama berkas dipertahankan, dan tidak ada berkas yang diganti atau disintesis. Karena itu, 162 berkas tersebut diklasifikasikan sebagai **salinan aset pihak lain**, bukan hasil karya RevCore.

Bukti yang dicatat:

- Arsip sumber: SHA-256 `acf6bb8ec0fd0f7636f9a4cc5945d5182d3a7d61dfa6c6b316174950ee9589a7`.
- 162 berkas, 35.195.840 byte, 161 SHA-256 unik.
- Setiap berkas memiliki hash yang cocok dengan manifest internal.
- Tidak ada bukti bahwa berkas dibuat ulang oleh RevCore.

Yang **tetap dipublikasikan** adalah hasil analisis RevCore: inventaris, ukuran, hash, struktur kontainer, pemetaan byte, tautan profil, dan status. Kunci dan IV kontainer tidak disertakan karena merupakan bagian dari aset pihak lain.

## Format

- Berkas berisi kontainer terenkripsi; payload adalah image flash ECU, bukan definisi peta.
- Panjang ciphertext = panjang plaintext + 16 byte padding.
- Tidak ada header, magic, versi, salt, atau indeks di dalam berkas.
- Definisi peta (offset tabel) tidak berada di dalam berkas; definisi berasal dari profil ECM.

Rincian struktur ada di [docs/MAP_INVENTORY.md](../../docs/MAP_INVENTORY.md).

## Kolom `inventory.csv`

| Kolom | Arti |
|---|---|
| `mapId` | ID aman `MAP-001`..`MAP-162`, diurutkan berdasarkan SHA-256 |
| `bytes` | Ukuran berkas |
| `sha256` | Hash berkas |
| `format` | Klasifikasi format |
| `analysisStatus` | Status pendaftaran/analisis |
| `profileCandidates` | Profil ECM yang cocok, memakai `publicId` |
| `matchMethod` | Metode pencocokan yang dipakai |
| `linkStatus` | `READY`, `AMBIGUOUS_PROFILE`, atau `UNRECOGNIZED` |
| `distributionStatus` | Alasan berkas mentah tidak disertakan |

## Batas

- `READY` berarti tautan profil memiliki bukti nama resource yang unik, bukan bahwa peta cocok untuk kendaraan.
- `AMBIGUOUS_PROFILE` berarti lebih dari satu profil memiliki klaim; tidak ada yang dipilih paksa.
- `UNRECOGNIZED` berarti berkas belum dapat dihubungkan ke profil.
- Tidak ada pengujian kendaraan.
- Tidak ada berkas mentah yang boleh ditambahkan tanpa keputusan pemilik.
