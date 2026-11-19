# AUDIT PUBLIKASI

Dokumen ini adalah pemeriksaan akhir repository untuk rilis `v0.3.0`. Isinya mencatat apa yang ada, apa yang diuji, apa yang dikecualikan, dan apa yang masih terbuka. Perintah rilis akhir pemilik menggantikan batas persetujuan sebelumnya; batas cakupan teknis tetap berlaku.

## 1. Status saat ini

| Item | Status |
|---|---|
| Lokasi | `RevCore_Public_Developer_Repository/` |
| Publikasi ke GitHub | Mengikuti Work Order rilis; status remote dan hasil dicatat di §12 |
| ZIP final | Dibuat dari isi repository; nama dan hash di §12 |
| Build | AAR debug berhasil dibangun |
| Test | 153 lulus, 0 gagal, tanpa hardware |
| Persetujuan pemilik | Diberikan melalui perintah rilis akhir |

APK referensi, materi analisis, dan repository ini tidak diunggah ke layanan eksternal.

## 2. Isi repository

Tidak termasuk `.gradle/`, `build/`, dan `core/build/` yang di-ignore.

| Kelompok | Jumlah | Keterangan |
|---|---:|---|
| Dokumen akar | 8 | README, CONTRIBUTING, NOTICE, CHANGELOG, LICENSE-DEPENDENCIES, THIRD_PARTY_NOTICES, PUBLICATION_MANIFEST, FINAL_PUBLICATION_READINESS |
| Konfigurasi build | 4 | `.gitignore`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` |
| `core/build.gradle.kts` | 1 | Konfigurasi pustaka |
| Source utama | 53 file, 9.791 baris | 44 `ORIGINAL`, 9 `RECONSTRUCTED` |
| Test | 19 file, 2.517 baris, 153 `@Test` | 15 `ORIGINAL`, 4 `RECONSTRUCTED` |
| Aset | 1 | `ecm_database.json` sintetis |
| Contoh | 6 | Frame, identifikasi, DTC, Live Data, template kendaraan, MAP sintetis |
| Dokumentasi `docs/` | 24 | Bahasa Indonesia |
| Utilitas `tools/` | 3 | README dan dua validator Python |
| Audit `audit/` | 6 | Termasuk dokumen ini |
| **Total berkas** | **125** | Tanpa cache/build |

## 3. Hash materi kunci

| Berkas | SHA-256 |
|---|---|
| `core/src/main/assets/ecm_database.json` | `93807a4f20a57badb7742b3e35008d9ffd758f191de82c4ebbf905627268192f` |
| `examples/protocol_frames/requests.json` | `a2404bdbadc1d8d6b4ca9e35ce01f842850c06cf6336dce52f01085694e71bbe` |
| `examples/ecu_responses/identification.json` | `ecdebac4e6b8f1260fad27ac27a6a2350f602431c5d338f1a254a0d86bd15660` |
| `examples/dtc/dtc_examples.json` | `135c1b86b5fd9223ba7a33fb6f5b032a2120ac846b127c468f6ae312f955dd89` |
| `examples/sensor_data/live_data.json` | `c943979835fe2b909414798e3b76f17cbb01c6e74882654acb67d0deae4c778b` |
| `examples/vehicle_db/vehicle_template.json` | `f13f8008233e36e7c96f8da17b44d7c8f21ff8a7010bb6ab040a243e9b4d945e` |
| `examples/maps/synthetic_map.json` | `bb321b47d27eabf588af0215b63497849180560ae6636f96ef50a1b192f6e829` |
| `tools/validate_fixtures.py` | `355e6d9b9614a2f8111ea94e58e7df20271df1aea6dc07eae10a721ec8a45dae` |
| `tools/validate_datasets.py` | `8e8160a47a54cf7e7de5ba645444242bc501cca2325d61a2da435920ad87dbc0` |

Hash dihitung ulang pada 2026-11-17 dan cocok dengan `PUBLICATION_MANIFEST.md`.

## 4. Bukti build dan test

| Pemeriksaan | Perintah | Hasil |
|---|---|---|
| Unit test | `gradle --offline clean test` | BUILD SUCCESSFUL; 153 test, 0 gagal, 0 error, 0 dilewati |
| AAR debug | `gradle --offline assembleDebug` | BUILD SUCCESSFUL; `core/build/outputs/aar/core-debug.aar`, 540.273 byte, SHA-256 `9b5f478b104870ac8c993fe2cc5a8fff308ac44a634552cefd8e4c13349111ee` |
| Validator fixture | `py tools/validate_fixtures.py --verbose` | LULUS; 14 frame valid |
| Hardware ECU | — | Tidak dilakukan |
| Uji signing/instalasi | — | Di luar cakupan repository |

Lingkungan verifikasi: Windows, JDK 25 runtime dengan target Java 21, Gradle 9.6.0 offline, Android SDK Platform 36. Tidak ada `local.properties` di repository.

Test dilakukan terhadap fixture sintetis. Hasil ini tidak membuktikan kompatibilitas ECU.

## 5. Batas publikasi

| Batas | Status | Dasar |
|---|---|---|
| 1 — APK Inventory | LULUS | 253 entry, 1 DEX, 3.006 class, 169 aset, 4 native lib; lihat [APK_COMPONENT_INVENTORY.md](APK_COMPONENT_INVENTORY.md) |
| 2 — Feature Mapping | LULUS | Modul publik dipetakan; Application/Security dikecualikan sesuai addendum |
| 3 — Source Reconstruction | LULUS DENGAN CATATAN | 44/53 source dan 15/19 test identik; sembilan source dan empat test disesuaikan, dicatat di [SOURCE_MAPPING.md](SOURCE_MAPPING.md) |
| 4 — Legacy Correlation | LULUS | Source terbaru menjadi acuan; legacy hanya referensi rollback |
| 5 — Repository Assembly | LULUS | `:core` mandiri dan dapat dibangun |
| 6 — Build and Test | LULUS (tanpa hardware) | 153 test lulus; AAR debug terbangun |
| 7 — Ownership and Security | LULUS DENGAN CATATAN | Tidak ada materi terlarang; dua butir hukum/teknis masih terbuka |
| 8 — Final Audit | LULUS | Audit akhir, validasi build/test, dan pemeriksaan ZIP selesai; unggahan mengikuti Work Order rilis |

## 6. Kepatuhan addendum

| Butir | Kewajiban | Bukti |
|---|---|---|
| A — Cakupan publikasi | Application Layer dan Security/Integrity tidak dipublikasikan | Tidak ada source/diagram/detail; hanya batas integrasi di [SECURITY_AND_INTEGRITY.md](../docs/SECURITY_AND_INTEGRITY.md) dan [SECURITY_AUDIT.md](SECURITY_AUDIT.md) |
| A — Catatan internal | Disimpan di direktori privat terpisah | Di luar repository publik |
| B — Kunci | Minta owner melalui input lokal aman jika diperlukan; jangan cetak/simpan/kirim | Tidak ada key yang diperlukan atau digunakan; prosedur dicatat di [SECURITY_AUDIT.md](SECURITY_AUDIT.md) |
| C — Prioritas publik | Communication, USB, K-Line, Protocol, parser/checksum, identifikasi, diagnostik, DTC, Live Data, model, test/dokumentasi | Semua ada di `core/src/main` dan `docs/` |
| D — Kedalaman analisis | Tidak dikurangi karena publikasi selektif | Analisis APK, R8, dan referensi dilakukan; publikasi dibatasi pada 11 kategori |

## 7. Yang dikecualikan

| Materi | Alasan | Penanganan |
|---|---|---|
| Application Layer | Di luar target publikasi | Tidak disalin |
| Security/Integrity | Di luar target publikasi | Tidak disalin; hanya batas integrasi |
| Database ECM bawaan APK 748.580 byte | Aset build internal | Build publik memakai 3 record sintetis; database hasil kompilasi RevCore dipublikasikan di `data/vehicle_database/` |
| Berkas mentah 162 `.map` dan profil APK | Salinan aset pihak lain | Berkas tidak disertakan; inventaris, hash, format, analisis, dan profil rekonstruksi dipublikasikan |
| Layout tabel Live Data dari program lain | Bukan hasil RevCore | Tidak disertakan; tabel hasil rekonstruksi RevCore dipublikasikan di `data/live_data/` |
| Aset keamanan internal (2 berkas) | Internal security | Tidak disertakan; nama dan ukuran tidak dipublikasikan |
| `mapping.txt`, `seeds.txt` | Nama internal Application/Security | Tidak disertakan |
| Firmware, bootloader, binary ECU | Bukan milik RevCore / tidak jelas | Tidak disertakan |
| Keystore, private key, sertifikat signing | Rahasia | Tidak disertakan |
| APK, AAR, JAR, build output | Artefak build | Di-ignore; tidak disertakan |

## 8. Kepatuhan kepemilikan

- [NOTICE.md](../NOTICE.md) mempertahankan ketentuan pemilik: izin melihat/mempelajari, larangan klaim kepemilikan, larangan penjualan ulang tanpa izin, larangan distribusi modifikasi tanpa izin, dan penggunaan sesuai persetujuan.
- Repository tidak diberi label "OSI approved", "open source", atau lisensi OSI.
- `LICENSE-DEPENDENCIES.md` hanya mencatat lisensi pihak ketiga dan tidak mengubah ketentuan pemilik.
- Tidak ada klaim bahwa source telah diuji pada kendaraan.
- Label status bukti dipakai konsisten di source, dokumentasi, dan audit.

## 9. Keputusan cakupan yang berlaku

1. Publikasi source `:core` dan dokumentasi mengikuti Work Order revisi final 19 November 2026.
2. Tabel Live Data hasil rekonstruksi RevCore dipublikasikan di `data/live_data/`; layout dari program lain tidak disertakan.
3. `LegacyHondaProtocolEngine.kt` disertakan sebagai referensi rollback dan ditandai `RECONSTRUCTED`.
4. Alamat file-relative di `Evidence.kt` dipertahankan sebagai provenance.
5. `THIRD_PARTY_NOTICES.md` mencakup dependency langsung dan transitif.
6. Commit, ZIP, push, dan release menunggu instruksi eksekusi pemilik.

## 10. Checklist rilis

- [x] Pemilik menetapkan cakupan dan perintah rilis final.
- [x] Cakupan teknis yang belum terbukti tetap diberi label dan tidak diklaim sebagai terverifikasi.
- [x] Keputusan legacy engine dan alamat file-relative diambil dan dicatat di §9.
- [x] `gradle --offline clean test` dijalankan ulang pada keadaan final: 153 test, 0 gagal.
- [x] `gradle --offline assembleDebug` dijalankan ulang pada keadaan final.
- [x] Validator fixture dan dataset dijalankan ulang.
- [x] ZIP dibuat tanpa `.gradle/`, `build/`, `core/build/`, `.kotlin/`, `local.properties`, atau artefak biner.
- [x] Isi ZIP diperiksa terhadap daftar materi terlarang.
- [x] Hash ZIP dicatat pada laporan rilis di luar ZIP.
- [ ] Unggahan GitHub dan release dibuat bila remote RevCore dan akses tersedia; jika tidak, hambatan dilaporkan.

## 11. Larangan yang tetap berlaku

- Jangan mengunggah APK, materi analisis, database/MAP nyata, atau firmware.
- Jangan menambahkan kredensial, keystore, private key, atau token.
- Jangan mempublikasikan source atau detail Application Layer dan Security/Integrity.
- Jangan mengklaim kompatibilitas atau hasil pengujian hardware yang belum dilakukan.
- Jangan menggunakan repository GitHub yang tidak terkait RevCore.

## 12. Status rilis

| Item | Nilai |
|---|---|
| Versi | `v0.3.0` |
| ZIP | `RevCore_Developer_Repository_v0.3.0.zip` |
| Isi ZIP | 125 entri berkas, tanpa cache/build/artefak biner |
| Build | AAR debug 540.273 byte, SHA-256 `9b5f478b104870ac8c993fe2cc5a8fff308ac44a634552cefd8e4c13349111ee` |
| Test | 153 lulus, 0 gagal, 0 error, 0 dilewati |
| Validator | Fixture 14 frame valid; dataset 5 pemeriksaan lulus |
| Repositori GitHub | Mengikuti ketersediaan remote dan akses; lihat laporan rilis |

Hash ZIP tidak ditulis di dalam ZIP untuk menghindari referensi diri; hash dicatat pada laporan rilis di luar arsip.

## 13. Kesimpulan

Repository konsisten dengan Work Order rilis dan batas cakupan. Materi terlarang tidak ditemukan, build dan test dapat direproduksi tanpa hardware, dan seluruh klaim yang tidak terbukti diberi label. ZIP rilis disiapkan dari isi repository; unggahan GitHub hanya dilakukan ke remote RevCore yang tersedia dan tidak pernah ke repository lain.
