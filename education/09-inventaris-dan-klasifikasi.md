# 09 — Inventaris dan Klasifikasi Perubahan

Dokumen ini mencatat audit yang mendasari pusat pembelajaran: apa yang dipertahankan,
diperbaiki, ditambahkan, diduplikasi, tidak boleh dipublikasikan, dan belum dapat
diverifikasi. Tujuannya agar pembaca dapat membedakan materi lama, koreksi, dan materi
baru.

## 1. Metode audit

Audit dilakukan pada working tree `main` (rilis v0.3.0) pada 2026-11-19:

- membaca 125 berkas yang dilacak Git;
- memeriksa 24 dokumen `docs/`, 8 dokumen akar, 6 dokumen `audit/`;
- menghitung 53 source Kotlin, 19 test, dan 153 `@Test`;
- membandingkan klaim dokumentasi dengan source, test, dan fixture;
- menjalankan validator fixture dan dataset;
- menjalankan unit test `:core`;
- membangun APK contoh di `samples/01-hello-android`;
- memindai materi baru untuk pola rahasia.

Tidak ada ECU fisik yang digunakan.

> **Catatan revisi (19 November 2026).** Bagian ini adalah snapshot sebelum Work Order
> revisi final membuka publikasi database, profil ECM, inventaris MAP, dan tabel Live
> Data. Dataset baru ada di `data/`; ringkasan klasifikasi terbaru ada di
> [docs/KLASIFIKASI_MATERI.md](../docs/KLASIFIKASI_MATERI.md).

## 2. Dipertahankan

| Materi | Alasan |
|---|---|
| `core/` dan seluruh source produksi | Identitas, perilaku, package, dan keamanan tidak boleh diubah tanpa instruksi |
| 24 dokumen `docs/` | Dokumentasi developer yang sudah ada; hanya koreksi faktual terarah |
| `examples/` sintetis | Data referensi yang aman dipublikasikan |
| `tools/` validator | Alat validasi yang dapat dijalankan ulang |
| `audit/` | Catatan audit historis; tidak dihapus agar bukti tetap utuh |
| `NOTICE.md`, lisensi dependency | Ketentuan pemilik dan pihak ketiga |

## 3. Diperbaiki

Koreksi berikut hanya menyentuh dokumentasi, bukan perilaku produksi:

| Berkas | Koreksi |
|---|---|
| `README.md` | Menambah jalur belajar, membedakan AAR dan APK, memperbaiki state DTC, mencatat 14 frame |
| `CONTRIBUTING.md` | Status publikasi yang sudah usang |
| `NOTICE.md` | Status publikasi dan batas distribusi ulang |
| `docs/SOURCE_CODE_GUIDE.md` | Jumlah baris dan path engine legacy |
| `docs/LIVE_DATA_ENGINE.md` | Urutan argumen `parse(payload, profile)` |
| `docs/DIAGNOSTIC_ENGINE.md` | `DTC_READ_CONFIRMED` sebagai status sesi |
| `docs/ARCHITECTURE.md` | Probe live yang nonaktif pada katalog publik |
| `docs/USB_ADAPTER_GUIDE.md` | Klaim fallback prober yang terlalu luas |
| `docs/APK_ANALYSIS.md` | Catatan status historis |
| `FINAL_PUBLICATION_READINESS.md` | Catatan status historis dan konflik provenance |
| `PUBLICATION_MANIFEST.md` | Catatan konflik provenance |

## 4. Ditambahkan

| Materi | Isi |
|---|---|
| `education/README.md` | Filosofi jembatan, peta belajar, label bukti |
| `education/00` | Jalur belajar, glosarium, cara membaca source |
| `education/01` | Android, Kotlin, JDK, SDK, Gradle, Git |
| `education/02` | Build APK, signing, APK vs AAB vs AAR |
| `education/03` | Konsep aplikasi diagnostik |
| `education/04` | 16 topik referensi protokol dan data |
| `education/05` | 26 kasus kegagalan dengan 12 field |
| `education/06` | Panduan AI dan prompt siap pakai |
| `education/07` | Kontributor dan kemandirian |
| `education/08` | Peta 16 contoh progresif |
| `samples/01`–`13` | Proyek contoh terisolasi, masing-masing dengan README dan self-test |
| `samples/.gitignore` | Mencegah build output, APK, keystore, dan berkas IDE ikut commit |

## 5. Duplikasi yang diketahui

Duplikasi berikut ditemukan, tetapi tidak dihapus karena sebagian merupakan catatan
audit historis:

| Duplikasi | Keputusan |
|---|---|
| `audit/APK_COMPONENT_INVENTORY.md` dan `docs/APK_ANALYSIS.md` | Dipertahankan; pembaca diarahkan ke `docs/` untuk ringkasan |
| Tabel dependency di tiga berkas | Dipertahankan; `LICENSE-DEPENDENCIES.md` menjadi rujukan lisensi |
| Ringkasan publikasi di `PUBLICATION_MANIFEST`, `FINAL_PUBLICATION_READINESS`, dan `audit/PUBLICATION_AUDIT` | Dipertahankan sebagai snapshot; konflik provenance ditandai terbuka |
| Peta test di `docs/TESTING_GUIDE.md` dan `audit/SOURCE_MAPPING.md` | Dipertahankan; test menjadi sumber perilaku |
| Model state Live Data di beberapa dokumen | Diringkas di `education/03`; dokumen asal tetap rinci |

Menghapus duplikasi audit akan menghilangkan jejak bukti. Konsolidasi yang aman adalah
menetapkan rujukan utama dan menandai snapshot, seperti yang dilakukan di sini.

## 6. Tidak boleh dipublikasikan

| Materi | Alasan |
|---|---|
| Application Layer (UI, navigasi, lifecycle layar) | Dikecualikan Addendum A/F |
| Komponen keamanan internal | Dikecualikan; mekanisme tidak dijelaskan |
| Layout tabel Live Data dari program lain | Bukan hasil RevCore |
| Berkas MAP mentah, firmware, BIN/ROM pihak lain | Salinan aset dengan hak belum jelas |
| Keystore, private key, token, kredensial | Keamanan |
| Source atau aset pihak ketiga tanpa izin | Hak cipta |

Database kendaraan, profil ECM, inventaris MAP, tabel Live Data, rumus, dan temuan hasil
pengembangan RevCore **termasuk** yang dipublikasikan; lihat
[docs/KLASIFIKASI_MATERI.md](../docs/KLASIFIKASI_MATERI.md).

## 7. Belum dapat diverifikasi

| Hal | Alasan | Label |
|---|---|---|
| Komunikasi dengan ECU nyata | Tidak ada perangkat | Hardware unverified |
| Timing wake 70/130 ms | Rekonstruksi, bukan pengukuran | Reconstructed |
| Jumlah provenance source | Dokumen saling bertentangan | Temuan terbuka |
| Klaim provenance biner di `Evidence.kt` | Tidak dapat direproduksi dari repository | UNVERIFIED |
| Perilaku parser DTC pada payload ambigu | Perubahan produksi menunggu keputusan pemilik | Investigating/Open |
| Langkah 14–16 contoh | Memerlukan aplikasi/hardware | Panduan |
| APK rilis bertanda tangan | Tidak dibuat dalam pekerjaan ini | Belum dieksekusi |

## 8. Hasil verifikasi pekerjaan ini

- Unit test `:core`: 153 test, 0 gagal, 0 error, 0 dilewati, BUILD SUCCESSFUL (2026-11-19).
- `gradle --offline assembleDebug` (root): BUILD SUCCESSFUL, menghasilkan AAR 540.282
  byte. Hash AAR berbeda dari snapshot audit; ukuran/hash AAR tidak reproducible
  byte-per-byte.
- `validate_fixtures.py`: 14 frame valid.
- `validate_datasets.py --demo`: 5 pemeriksaan lulus, interpolasi 1.34.
- `samples/01-hello-android`: APK debug 844.858 byte, SHA-256
  `9c3763af6378a80a030c24f2a282fe9d4116bf9fdb5af6a1c5f3ee7aaf5da01d`.
- Contoh 02–13: self-test dijalankan dari direktori masing-masing.
- Tidak ada source produksi, package name, identitas, UI, atau mekanisme
  keamanan internal yang diubah.
- Tidak ada APK yang diunggah atau didistribusikan.

## 9. Keputusan yang menunggu pemilik

1. Apakah perilaku parser DTC (count-prefix longgar dan pelaporan `OK_EMPTY` untuk
   payload tak dikenal) akan diperbaiki.
2. Apakah temuan provenance akan direkonsiliasi terhadap proyek sumber.
3. Apakah APK contoh atau APK RevCore akan didistribusikan; keputusan dan detail
   distribusi sepenuhnya milik pemilik.
4. Apakah tabel Live Data hasil rekonstruksi akan diaktifkan sebagai katalog bawaan
   `:core`; saat ini tabel dipublikasikan di `data/live_data/` dan harus didaftarkan
   oleh pemanggil.
