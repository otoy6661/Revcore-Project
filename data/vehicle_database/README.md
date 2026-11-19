# Database Kendaraan dan Profil ECM

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

## Berkas

| Berkas | Isi | Jumlah |
|---|---|---:|
| [`ecm_database.json`](ecm_database.json) | Database kendaraan/ECM | 489 record |
| [`ecm_profiles.json`](ecm_profiles.json) | Profil ECM untuk pemetaan tabel | 163 record (1 placeholder + 162 profil) |

## Status

| Status | Jumlah | Arti |
|---|---:|---|
| Terdaftar di database | 489 | Record tersedia; belum diuji |
| Profil aplikasi | 162 + 1 placeholder | Layout profil; keterkaitan ke kendaraan belum diverifikasi menyeluruh |
| `isVerifiedDiagnostic = true` | 0 | Tidak ada diagnosis yang diverifikasi |
| `isVerifiedRemap = true` | 0 | Tidak ada remap yang diverifikasi |
| Diamati pada hardware | 0 | Tidak ada observasi ECU fisik |
| Diuji pada kendaraan | 0 | Tidak ada uji kendaraan |
| Kompatibilitas | 489 `UNKNOWN` | Tidak ada klaim dukungan |

**Tidak ada kendaraan dalam dataset ini yang boleh disebut didukung, kompatibel, atau teruji.**

## Skema record database

Field utama per record:

| Field | Arti |
|---|---|
| `id` | Identifier record |
| `partNumber` | Nomor part ECU/ECM |
| `ecmId` | ID ECM |
| `bikeName`, `yearRange` | Nama model dan rentang tahun menurut sumber |
| `manufacturer`, `mcuType`, `flashSizeKb` | Informasi hardware ECM |
| `stockRevLimit`, `maxSafeRevLimit`, `recommendedRevLimit` | Batas RPM; saat ini 0 dan `unverified` |
| `revLimitSource` | Sumber batas RPM; saat ini `unverified` |
| `tempSensorType`, `checksumType` | Tipe sensor suhu dan checksum |
| `checksumOffset`, `startOffset`, offset tabel | Offset; sebagian besar `0x0` karena belum terverifikasi |
| `category`, `country`, `displacementCc` | Kategori, pasar, kapasitas |
| `calibrationId`, `pinout` | ID kalibrasi dan pinout bila tersedia |
| `isVerifiedDiagnostic`, `isVerifiedRemap` | Status verifikasi; semua `false` |
| `statusNotes` | Catatan verifikasi dan perbedaan antar sumber |
| `source` | Kategori provenance yang digeneralisasi |
| `supportStatus` | `terdaftar` untuk seluruh record |

## Provenance

- Dataset disusun dari beberapa sumber publik, lalu digabung, dibandingkan, dan diuji secara internal oleh RevCore.
- Nama program, URL, nama berkas, dan identitas spesifik sumber tidak dicantumkan.
- Kolom `source` memakai kategori netral: `public-reference`, `revcore-legacy`, atau `unverified`.
- Salinan internal tidak diubah. Berkas publik ini adalah salinan terpisah dengan provenance yang digeneralisasi.
- Field teknis (nomor part, ID ECM, MCU, checksum, offset) dipertahankan untuk pembelajaran. Field yang belum terbukti tetap ditandai `0`, `unverified`, atau `false`.
- Tidak ada berkas mentah pihak lain yang disertakan dalam dataset ini.

## Profil ECM

`ecm_profiles.json` memuat 163 entri:

- `index` 0 adalah placeholder `Data ECM` dengan semua nilai 0.
- `index` 1..162 adalah profil dengan `ecmId`, ukuran grid, offset tabel, mask, dan faktor konversi.
- `publicId` (`ECM-001`..`ECM-163`) adalah identifier publik yang stabil.
- `verification` bernilai `RECONSTRUCTED`; `vehicleTested` bernilai `false`.
- `mapAssetId` diisi bila profil dapat ditautkan ke satu aset MAP dengan bukti nama resource yang unik. Keterkaitan ini bukan bukti kecocokan kendaraan.

Field seperti `tpsh`, `rpmh`, `feul1`, `ig1`, `limit1`, dan `konversrpm` adalah nama field dari analisis format. Arti dan validitasnya belum tentu sama untuk setiap ECU.

## Batas

- Jangan memakai dataset untuk mengklaim dukungan kendaraan.
- Jangan menganggap offset `0x0` sebagai nilai yang valid.
- Jangan menganggap `mapAssetId` sebagai bukti peta tersebut cocok untuk kendaraan.
- Jangan menganggap `ecmId` yang sama selalu berarti kalibrasi yang sama.
- Data dapat berubah bila ada bukti baru.
