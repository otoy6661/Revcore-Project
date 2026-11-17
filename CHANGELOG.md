# CHANGELOG

Semua tanggal memakai format `YYYY-MM-DD`. Nomor rilis publikasi ditetapkan `0.3.0` pada Work Order rilis final.

## 0.1.0-dev — 2026-11-16

### Ditambahkan

- Pustaka Android `:core` mandiri untuk Communication Engine, Protocol Engine, dan Diagnostic Engine.
- Source publik untuk:
  - transport USB serial dan driver FTDI native;
  - wire engine K-Line dengan wake pulse, echo filter, timeout, retry, dan validasi frame;
  - lifecycle sesi dan single-flight (`EcuSessionManager`);
  - framing, checksum, dan parser identitas;
  - identifikasi ECU/ECM dengan exact match, ambiguity handling, dan placeholder belum terverifikasi;
  - pembacaan/penghapusan DTC dan database 15 kode referensi;
  - Live Data parser generik, validasi frame, rotasi extension, dan kebijakan polling;
  - model data ECM, identitas, dan katalog.
- 135 unit test yang berjalan tanpa hardware.
- Contoh sintetis untuk frame request, respons identifikasi, DTC, dan live data (tabel contoh 0x7A).
- Dokumentasi developer Bahasa Indonesia dan audit kepemilikan/keamanan.
- `ecm_database.json` contoh berisi tiga record sintetis.

### Diubah dari source proyek

- `EcmDatabase.kt`: kait Security/Integrity dihapus dari jalur pemuatan aset; pembacaan aset dikembalikan ke jalur biasa. Tiga record fallback kendaraan nyata diganti tiga record sintetis. Perubahan ini dicatat di [audit/SOURCE_MAPPING.md](audit/SOURCE_MAPPING.md).
- `EcmCatalogAndIdentityTest.kt`: ID record disesuaikan dengan dataset sintetis. Test Live Data (termasuk pemeriksaan AFR `UNSUPPORTED` dan ketiadaan `O2_RAW`) dipakai apa adanya dari proyek.

### Tidak disertakan

- Application Layer dan Security/Integrity.
- Database ECM lengkap, profil/MAP pihak ketiga yang hak redistribusinya belum terbukti, firmware, dan aset pihak ketiga lain.
- Katalog tabel Live Data bawaan: ditarik sampai hak penggunaan dan validasi perangkat keras selesai.
- Modul tuning, remap, flash, backup, dan UI.
- Keystore, private key, token, dan kredensial.

### Diverifikasi

- `gradle --offline clean test`: 135 test lulus, 0 gagal.
- `gradle --offline assembleDebug`: AAR debug berhasil dibangun.
- APK referensi `RevCore build 161126.apk`, SHA-256 `b2b6c5bd68cc9c727c2babe0c510b928a6135fa7ef5e9d8847275887c8647432`, terbaca tanpa key.

### Diketahui belum

- Tidak ada pengujian pada ECU fisik dalam repository ini.
- Profil Keihin KWP, Shindengen, Denso, dan Keihin H99B masih kosong karena frame belum terbukti.
- AFR tidak tersedia dari ECU; host tidak menurunkannya.
- Katalog Live Data bawaan kosong; `queryLiveData()` mengembalikan `UNSUPPORTED_PROFILE` sampai ada profil dengan hak yang jelas dan validasi perangkat keras.

## 0.2.0-dev — 2026-11-17

### Ditambahkan

- [docs/ECM_DATABASE_SCHEMA.md](docs/ECM_DATABASE_SCHEMA.md): skema database kendaraan/ECU generik dan aturan bukti.
- [examples/vehicle_db/vehicle_template.json](examples/vehicle_db/vehicle_template.json): template satu record kendaraan sintetis.
- [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md): format MAP generik, aturan indeks, skala, dan interpolasi bilinear.
- [examples/maps/synthetic_map.json](examples/maps/synthetic_map.json): MAP sintetis 8×11.
- [tools/validate_datasets.py](tools/validate_datasets.py): validator dan parser dataset tanpa dependency, termasuk demo interpolasi.
- [docs/DATASET_CONTRIBUTION.md](docs/DATASET_CONTRIBUTION.md): prosedur menambah kendaraan, profil ECU, profil Live Data, dan MAP.
- [docs/FILE_REQUESTS.md](docs/FILE_REQUESTS.md): kebijakan permintaan berkas dan kriteria peninjauan.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): teks lisensi dependency langsung dan transitif.
- Bagian README untuk memakai Communication Core, menambah protokol, database kendaraan, MAP, laporan bug, kontribusi, dan permintaan berkas.

### Diubah

- Konstanta dan katalog Live Data bawaan yang hak penggunaannya belum jelas ditarik dari source, test, fixture, dan dokumentasi publik. Parser generik dan contoh sintetis 0x7A dipertahankan.
- Nama dan rujukan pihak ketiga yang tidak diperlukan dihapus dari dokumen publik; provenance tetap dicatat secara faktual.
- Inventaris manifest, audit, dan panduan pengujian disesuaikan dengan 112 berkas, 46 source (8.595 baris), dan 15 test (2.274 baris, 135 `@Test`).

### Diverifikasi

- `gradle --offline clean test`: 135 test lulus, 0 gagal, 0 error, 0 dilewati.
- `gradle --offline assembleDebug`: AAR debug berhasil dibangun.
- `py tools/validate_fixtures.py --verbose`: seluruh fixture sintetis konsisten.
- `py tools/validate_datasets.py --verbose --demo`: 5 pemeriksaan dataset lulus; demo bilinear 1,34 ms pada 3500 rpm dan 45% load.
- Tidak ada pengujian hardware ECU dalam verifikasi ini.

## 0.3.0 — 2026-11-17

### Ditambahkan

- **MAP/Data Engine publik:** `data/map/MapDocument.kt` (model, parser, validator, interpolasi bilinear tanpa ekstrapolasi) dan `MiniJson.kt` (parser JSON mandiri tanpa dependency). ID wajib berawalan `SAMPLE-`.
- **Analisis ROM read-only:** `data/rom/RomImage.kt` dengan pembacaan word 16-bit, checksum aditif, CRC-16/Modbus, dan diff blok. Tidak ada jalur tulis, flash, atau perbaikan checksum. `CalibrationMap.kt` dan `ModifiedBlock.kt` disalin apa adanya dari proyek.
- **Storage utilitas tanpa UI:** `core/storage/RevCoreStorage.kt` untuk folder `Revcore/`, sanitasi nama, dan penamaan berkas unik. Satu adaptasi izin untuk menghindari dependency `androidx.core`.
- **Session log diagnostik:** `core/tuning/TuningLog.kt` dengan status, progres terukur, deduplikasi event terminal, dan ekspor teks. Tanpa UI.
- **Dokumentasi:** [docs/ROM_ANALYSIS.md](docs/ROM_ANALYSIS.md) dan [docs/STORAGE_AND_LOGS.md](docs/STORAGE_AND_LOGS.md).
- **Test:** `MapDocumentTest` (8), `RomImageTest` (5), `RevCoreStorageTest` (3), `TuningLogTest` (2).

### Diubah

- README, manifest, audit, panduan source, dan panduan pengujian disesuaikan dengan 53 source (9.791 baris), 19 test (2.517 baris, 153 `@Test`), dan 24 dokumen `docs/`.
- Status rilis ditetapkan `0.3.0`; bahasa "menunggu persetujuan" diganti dengan batas cakupan teknis yang berlaku.

### Tidak disertakan

- `data/flashpro`: container crypto, profil, matcher, flash engine, remap workflow, dan editor berasal dari referensi pihak ketiga atau jalur flash; tidak dipublikasikan.
- UI/Application Layer, Security/Integrity, dan komponen khusus Gate 10.
- Database/MAP nyata, firmware, dan katalog Live Data proprietary.

### Diverifikasi

- `gradle --offline clean test`: 153 test lulus, 0 gagal.
- `gradle --offline assembleDebug`: AAR debug berhasil dibangun.
- `py tools/validate_fixtures.py --verbose` dan `py tools/validate_datasets.py --verbose --demo`: lulus.
- Tidak ada pengujian ECU fisik.

## Riwayat referensi internal (bukan changelog publikasi)

- Build `141126`, `151126`, dan `161126` adalah APK yang dianalisis. Build `161126` menjadi referensi utama perilaku.
- Perubahan penting pada build terbaru: penanganan AFR di Live Monitor menjadi jujur (`UNSUPPORTED`, tanpa derivasi host), dan penambahan modul storage aplikasi. UI/Application Layer storage tidak termasuk publikasi; utilitas storage tanpa UI disertakan pada 0.3.0.
