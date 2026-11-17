# PUBLICATION_MANIFEST

Dokumen ini mencatat apa yang disertakan, apa yang dikecualikan, dan status gerbang kerja. Dokumen ini bukan lisensi.

## 1. Referensi analisis

| Item | Nilai |
|---|---|
| APK utama | `RevCore build 161126.apk` |
| Ukuran | 39.689.642 byte |
| SHA-256 | `b2b6c5bd68cc9c727c2babe0c510b928a6135fa7ef5e9d8847275887c8647432` |
| APK pembanding | `RevCore build 151126.apk` (39.673.258 byte) |
| APK pembanding | `RevCore build 141126.apk` (56.409.037 byte) |
| Source pembanding | Proyek RevCore di `work/revcore` (tidak diubah) |

APK tidak dienkripsi; tidak ada key yang diperlukan atau digunakan. Analisis dilakukan lokal dan tidak mengunggah APK/materi analisis ke layanan eksternal.

## 2. Isi repository

### 2.1 Source `:core`

- 53 file Kotlin utama, 9.791 baris.
- 19 file test Kotlin, 153 method `@Test`.
- Package: `com.revcore.android.core.comm`, `core.protocol`, `core.protocol.legacy`, `core.storage`, `core.tuning`, `core.ftdi`, `core.usb`, `data.ecu`, `data.map`, `data.rom`.

Status:

| Kelompok | Jumlah/status | Catatan |
|---|---|---|
| Source proyek pemilik tidak diubah | 42 file | `ORIGINAL`; termasuk `TuningLog.kt`, `CalibrationMap.kt`, `ModifiedBlock.kt` yang disalin utuh |
| Source baru untuk repo publik | 2 file | `ORIGINAL`; `MapDocument.kt`, `MiniJson.kt` — implementasi generik, bukan salinan pihak ketiga |
| File disesuaikan untuk publikasi | 9 file | `RECONSTRUCTED`; `Evidence.kt`, `HondaEcuProfiles.kt`, `HondaFrameCodec.kt`, `LiveDataProfile.kt`, `HondaProtocolEngine.kt`, `LegacyHondaProtocolEngine.kt`, `EcmDatabase.kt`, `RevCoreStorage.kt`, `RomImage.kt` |
| Test proyek | 15 file utuh | `ORIGINAL` |
| Test disesuaikan | 4 file | `RECONSTRUCTED`; `EcuProfilesTest.kt`, `LiveDataProfileParserTest.kt`, `BaselineProtocolTest.kt`, `EcmCatalogAndIdentityTest.kt` |
| `LegacyHondaProtocolEngine.kt` | 1 file (termasuk 9 file di atas) | `RECONSTRUCTED`; query Live Data legacy dihapus, referensi rollback dipertahankan |

Rincian perubahan per file ada di [audit/SOURCE_MAPPING.md](audit/SOURCE_MAPPING.md) §4 dan §5.

### 2.2 Data dan contoh

| Berkas | SHA-256 | Status |
|---|---|---|
| `core/src/main/assets/ecm_database.json` | `93807a4f20a57badb7742b3e35008d9ffd758f191de82c4ebbf905627268192f` | `REFERENCE`, 3 record sintetis |
| `examples/protocol_frames/requests.json` | `a2404bdbadc1d8d6b4ca9e35ce01f842850c06cf6336dce52f01085694e71bbe` | `REFERENCE`, frame sintetis dari katalog |
| `examples/ecu_responses/identification.json` | `ecdebac4e6b8f1260fad27ac27a6a2350f602431c5d338f1a254a0d86bd15660` | `REFERENCE`, respons sintetis |
| `examples/dtc/dtc_examples.json` | `135c1b86b5fd9223ba7a33fb6f5b032a2120ac846b127c468f6ae312f955dd89` | `REFERENCE`, respons sintetis |
| `examples/sensor_data/live_data.json` | `c943979835fe2b909414798e3b76f17cbb01c6e74882654acb67d0deae4c778b` | `REFERENCE`, payload sintetis |
| `examples/vehicle_db/vehicle_template.json` | `f13f8008233e36e7c96f8da17b44d7c8f21ff8a7010bb6ab040a243e9b4d945e` | `REFERENCE`, satu record kendaraan sintetis |
| `examples/maps/synthetic_map.json` | `bb321b47d27eabf588af0215b63497849180560ae6636f96ef50a1b192f6e829` | `REFERENCE`, MAP sintetis 8×11 |

Tidak ada data kendaraan nyata, hasil rekaman ECU, MAP, atau firmware dalam berkas di atas.

### 2.3 Dokumentasi dan audit

- Delapan dokumen akar: `README.md`, `CONTRIBUTING.md`, `NOTICE.md`, `CHANGELOG.md`, `LICENSE-DEPENDENCIES.md`, `THIRD_PARTY_NOTICES.md`, `PUBLICATION_MANIFEST.md`, `FINAL_PUBLICATION_READINESS.md`.
- Dua puluh empat dokumen di `docs/`.
- Enam dokumen audit di `audit/`: `APK_COMPONENT_INVENTORY.md`, `SOURCE_MAPPING.md`, `RECONSTRUCTION_STATUS.md`, `DEPENDENCY_AUDIT.md`, `SECURITY_AUDIT.md`, `PUBLICATION_AUDIT.md`.
- Utilitas pengembangan di `tools/`: `README.md`, `validate_fixtures.py` (`355e6d9b9614a2f8111ea94e58e7df20271df1aea6dc07eae10a721ec8a45dae`), dan `validate_datasets.py` (`8e8160a47a54cf7e7de5ba645444242bc501cca2325d61a2da435920ad87dbc0`).
- Template dan contoh tambahan: `examples/vehicle_db/vehicle_template.json` dan `examples/maps/synthetic_map.json`, keduanya sintetis dan berlabel `REFERENCE`.

## 3. Yang dikecualikan

| Materi | Alasan | Penanganan |
|---|---|---|
| Application Layer (UI, navigasi, state, lifecycle aplikasi) | Bukan target publikasi (Addendum A) | Tidak disalin; catatan internal terpisah |
| Security/Integrity (verifikasi APK, signature, hash, trust record, gate startup) | Bukan target publikasi (Addendum A/F) | Tidak disalin; hanya integration boundary yang didokumentasikan |
| `ecm_database.json` lengkap (748.580 byte di APK) | Hak redistribusi belum terbukti | Diganti 3 record sintetis |
| 162 berkas MAP pihak ketiga | Materi pihak ketiga, hak tidak jelas | Tidak disertakan |
| Manifest profil pihak ketiga | Profil pihak ketiga | Tidak disertakan |
| Metadata MAP pihak ketiga | Metadata pihak ketiga | Tidak disertakan |
| Workflow flash/remap, kripto kontainer MAP, definisi peta proprietary | Materi pihak ketiga dan jalur tulis ECU | Tidak disertakan; hanya engine MAP generik dan ROM read-only |
| Katalog tabel Live Data bawaan | Hak penggunaan belum jelas | Ditarik; hanya parser generik dan contoh sintetis 0x7A |
| `integrity_policy.json`, `ui_integrity_manifest.json` | Internal security | Tidak disertakan |
| Firmware, bootloader, binary ECU | Bukan milik RevCore / tidak jelas | Tidak disertakan |
| Keystore, private key, sertifikat signing | Rahasia | Tidak disertakan |
| `local.properties`, path pribadi | Data lokal | Tidak disertakan |
| APK/AAB/AAR/build output | Artefak build | Tidak disertakan; `.gitignore` mencegah |

## 4. Pemeriksaan kebocoran

Yang diperiksa sebelum publikasi:

- Tidak ada nilai private key, blok `BEGIN ... PRIVATE KEY`, keystore, `password=`, atau token di source/dokumen/contoh. Pola pencarian di dokumen audit adalah alat deteksi, bukan kredensial.
- Tidak ada path pengguna (`C:\Users\...`) di file repository.
- Tidak ada APK, AAR, jar, MAP, atau firmware di repository.
- Tidak ada source package internal `com.revcore.android.security` atau `ui`. Utilitas `core.storage` dan `core.tuning` disertakan tanpa UI dan tanpa lapisan Application.
- `ecm_database.json` hanya berisi record berawalan `SAMPLE-`.
- Dokumen security hanya berisi pernyataan cakupan, bukan detail implementasi.

Hasil pemeriksaan rinci ada di [audit/SECURITY_AUDIT.md](audit/SECURITY_AUDIT.md) dan [audit/PUBLICATION_AUDIT.md](audit/PUBLICATION_AUDIT.md).

## 5. Status gerbang

| Gate | Status | Catatan |
|---|---|---|
| 1 — APK Inventory | LULUS | 253 entry, 1 DEX, 3.006 class, 169 asset, 4 native lib |
| 2 — Feature Mapping | LULUS | Fitur publik dipetakan; Application/Security sengaja dikecualikan |
| 3 — Source Reconstruction | LULUS DENGAN CATATAN | Status dan bukti per modul tercatat |
| 4 — Legacy Correlation | LULUS | Source terbaru dipilih; perbedaan didokumentasikan |
| 5 — Repository Assembly | LULUS | `:core` dapat dibangun |
| 6 — Build and Test | LULUS (tanpa hardware) | 153 test lulus; AAR debug terbangun |
| 7 — Ownership and Security | LULUS DENGAN CATATAN | Materi terlarang dikeluarkan; katalog Live Data bawaan ditarik |
| 8 — Final Audit | LULUS | Audit akhir selesai; ZIP rilis diperiksa; unggahan mengikuti Work Order rilis final |

## 6. Keputusan cakupan yang berlaku

1. Publikasi source `:core` dan dokumentasi ini mengikuti perintah rilis akhir pemilik.
2. Katalog Live Data bawaan tetap kosong sampai hak penggunaan dan validasi perangkat keras selesai.
3. `LegacyHondaProtocolEngine.kt` disertakan sebagai referensi rollback dan ditandai `RECONSTRUCTED`.
4. Nama/alamat referensi internal pada `Evidence.kt` dipertahankan sebagai provenance file-relative.
5. `THIRD_PARTY_NOTICES.md` mencakup dependency langsung dan transitif.
6. ZIP rilis dibuat; unggahan GitHub bergantung pada ketersediaan remote dan akses.

## 7. Larangan yang tetap berlaku

- Jangan mengunggah APK, materi analisis, database/MAP nyata, atau firmware.
- Jangan menambahkan kredensial, keystore, atau private key.
- Jangan menyertakan UI, Application Layer, Security/Integrity, atau komponen khusus Gate 10.
- Jangan mengklaim pengujian kendaraan yang tidak dilakukan.
