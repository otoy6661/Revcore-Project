# RevCore — Public Developer Repository

Repository developer untuk mempelajari dan mengembangkan **Communication Engine**, **Protocol Engine**, dan **Diagnostic Engine** RevCore di atas K-Line Honda PGM-FI. Isi repository disusun dari APK RevCore terbaru sebagai referensi perilaku, dicocokkan dengan source proyek RevCore, lalu dibatasi pada modul yang boleh dipublikasikan.

Repository ini ditujukan untuk kontributor, pelajar protokol diagnostik, dan pengembang aplikasi diagnostik mandiri yang ingin memakai atau memperluas lapisan komunikasi RevCore. Repository ini **bukan** source lengkap aplikasi RevCore dan **bukan** jaminan kompatibilitas kendaraan. Contoh data sintetis tetap tersedia di `examples/`; di samping itu, direktori `data/` memuat hasil pengembangan RevCore sendiri — database kendaraan, profil ECM, inventaris MAP, dan tabel Live Data — dengan status bukti yang dicantumkan apa adanya.

## Mulai belajar

Pemula total dapat memulai dari [education/README.md](education/README.md): jalur belajar
Android/Kotlin/Gradle/Git, cara membangun APK, konsep aplikasi diagnostik, referensi
protokol, katalog kegagalan, panduan AI, dan panduan kontributor. Proyek contoh yang
dapat dijalankan ada di [samples/README.md](samples/README.md); contoh 01 menghasilkan
APK debug yang sudah diverifikasi.

> **Status penting**
>
> - Ini adalah repository rilis **v0.3.0**. Status paket dan unggahan dicatat di [FINAL_PUBLICATION_READINESS.md](FINAL_PUBLICATION_READINESS.md) §12.
> - Ini **bukan** proyek Open Source berlisensi OSI. Hak penggunaan mengikuti [NOTICE.md](NOTICE.md).
> - Application Layer dan Security/Integrity **tidak** menjadi target publikasi. Lihat [docs/SECURITY_AND_INTEGRITY.md](docs/SECURITY_AND_INTEGRITY.md).
> - Status fungsi berdasarkan laporan pemilik dicatat di [docs/STATUS_PENGUJIAN.md](docs/STATUS_PENGUJIAN.md). Status itu bukan `VERIFIED` dan bukan klaim kompatibilitas kendaraan.
> - ZIP rilis, bila dibuat setelah instruksi pemilik, disusun dari isi repository tanpa cache/build dan diperiksa terhadap daftar materi terlarang.

## 1. Referensi APK

| Item | Nilai |
|---|---|
| Berkas | `RevCore build 161126.apk` |
| Ukuran | 39.689.642 byte |
| SHA-256 | `b2b6c5bd68cc9c727c2babe0c510b928a6135fa7ef5e9d8847275887c8647432` |
| Package | `com.revcore.android` |
| Version | `1.0` (versionCode `1`) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| DEX | 1 berkas `classes.dex` (`dex\n038`), 3.006 class definition, 18.722 method ID |
| Native library | 4 berkas `libandroidx.graphics.path.so` (arm64-v8a, armeabi-v7a, x86, x86_64) |
| Entry APK | 253 |

APK **tidak** dienkripsi dan tidak memerlukan key untuk dianalisis. DEX dapat dibaca sebagai `dex\n038`, aset dapat diekstrak, dan kelas aplikasi memiliki pemetaan R8. Karena itu tidak ada permintaan kunci kepada pemilik. Rincian ada di [audit/APK_COMPONENT_INVENTORY.md](audit/APK_COMPONENT_INVENTORY.md) dan [docs/APK_ANALYSIS.md](docs/APK_ANALYSIS.md).

## 2. Cakupan publikasi

Yang dipublikasikan:

1. Communication Engine (sesi, lifecycle, single-flight, logging).
2. Penanganan adapter USB dan serial (FTDI, CH340, CP210x, Prolific, CDC-ACM).
3. Implementasi K-Line (wake pulse, echo filter, TX/RX, timeout, retry).
4. Protocol Engine Honda PGM-FI baseline dan profil keluarga ECU yang tersedia.
5. Frame parser, checksum, dan transceiver.
6. Antarmuka identifikasi ECU/ECM, kanonikalisasi identitas, dan resolver kandidat.
7. ECU Database Engine dan struktur model ECM.
8. Diagnostic Engine.
9. Pembacaan dan penghapusan DTC.
10. Parser Live Data generik, mesin polling, dan decoder sensor/PID.
11. MAP/Data Engine generik (parser JSON, validasi, interpolasi bilinear).
12. Analisis ROM read-only (pembacaan word, checksum, diff blok).
13. Session log dan utilitas penyimpanan.
14. Model data internal yang diperlukan modul di atas.
15. Unit test, fixture sintetis, contoh, dan dokumentasi developer.
16. Database kendaraan hasil kompilasi RevCore (489 record, status `terdaftar`) di [data/vehicle_database/ecm_database.json](data/vehicle_database/ecm_database.json).
17. Profil ECM hasil rekonstruksi (163 profil) di [data/vehicle_database/ecm_profiles.json](data/vehicle_database/ecm_profiles.json), termasuk tautan profil ke inventaris MAP.
18. Inventaris MAP lengkap: 162 berkas, ukuran, SHA-256, identifikasi format kontainer, dan status analisis di [data/map_library/inventory.csv](data/map_library/inventory.csv).
19. Tabel Live Data hasil pengembangan (7 tabel, 84 field), rumus, skala, bias, dan contoh decoding di [data/live_data/live_tables.json](data/live_data/live_tables.json) dan [docs/LIVE_DATA_REFERENCE.md](docs/LIVE_DATA_REFERENCE.md).
20. Dokumentasi protokol, temuan, eksperimen, kasus gagal, bug yang tercatat, simulasi ECU, dan panduan pengembangan di [docs/TEMUAN_DAN_EKSPERIMEN.md](docs/TEMUAN_DAN_EKSPERIMEN.md).

Yang **tidak** dipublikasikan:

- Source dan detail implementasi Application Layer (UI, navigasi, state layar, lifecycle aplikasi).
- Source dan detail implementasi komponen keamanan internal.
- Kunci, IV, dan mekanisme kripto kontainer MAP.
- Berkas MAP mentah, firmware, dan image BIN/definisi peta eksternal. Verifikasi 2026-11-19: 0 dari 162 MAP merupakan hasil karya RevCore (162/162 byte-identik dengan aset pihak lain), dan 700 image BIN + 177 definisi eksternal tidak dimasukkan. Analisis, inventaris, hash, dan struktur tetap dipublikasikan di `data/` — lihat [docs/KLASIFIKASI_MATERI.md](docs/KLASIFIKASI_MATERI.md).
- Keystore, private key, token, atau kredensial apa pun.

Modul publik tetap memuat **integration boundary** minimal: pustaka `:core` tidak memiliki kode security, dan aplikasi pemanggil bertanggung jawab atas izin USB, lifecycle, serta kebijakan integritasnya sendiri.

## 3. Struktur repository

```text
RevCore_Public_Developer_Repository/
├── README.md
├── CONTRIBUTING.md
├── NOTICE.md
├── CHANGELOG.md
├── LICENSE-DEPENDENCIES.md
├── THIRD_PARTY_NOTICES.md
├── PUBLICATION_MANIFEST.md
├── FINAL_PUBLICATION_READINESS.md
├── core/                         # pustaka Android :core
│   └── src/
│       ├── main/java/com/revcore/android/
│       │   ├── core/comm/        # wire engine, frame codec, sesi, live profile
│       │   ├── core/protocol/    # protocol engine, parser, DTC, telemetry
│       │   ├── core/storage/     # layout penyimpanan dan penamaan berkas
│       │   ├── core/tuning/      # session log dan progress terukur
│       │   ├── core/ftdi/        # driver FTDI native
│       │   ├── core/usb/         # transport USB serial dan kebijakan adapter
│       │   ├── data/ecu/         # model ECM, katalog, identitas ECU
│       │   ├── data/map/         # parser, validasi, interpolasi MAP generik
│       │   └── data/rom/         # ROM read-only: word, checksum, diff blok
│       ├── main/assets/          # ecm_database.json (3 record sintetis untuk build)
│       └── test/java/            # 153 unit test
├── examples/
│   ├── protocol_frames/          # frame request sintetis
│   ├── ecu_responses/            # respons identifikasi sintetis
│   ├── dtc/                      # contoh DTC sintetis
│   ├── sensor_data/              # payload Live Data sintetis 0x7A
│   ├── vehicle_db/               # template database kendaraan sintetis
│   └── maps/                     # MAP sintetis 8x11
├── data/                         # hasil pengembangan: database, profil ECM, inventaris MAP, Live Data
│   ├── vehicle_database/         # ecm_database.json (489 record), ecm_profiles.json (163 profil)
│   ├── map_library/              # inventory.csv (162 berkas MAP: hash, format, status)
│   └── live_data/                # live_tables.json (7 tabel, 84 field)
├── docs/                         # dokumen developer (Bahasa Indonesia)
├── education/                    # pusat pembelajaran: jalur, referensi, katalog kegagalan
├── samples/                      # 16 contoh progresif; contoh 01 menghasilkan APK
├── tools/                        # validator fixture dan dataset
└── audit/                        # inventaris, pemetaan, status, audit
```

Struktur mempertahankan package asli (`com.revcore.android.core.*`, `com.revcore.android.data.ecu`) agar source dapat dibangun dan dipetakan kembali ke APK. Alasan tidak memecah menjadi folder konseptual `core/serial`, `core/kline`, dan seterusnya dijelaskan di [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 4. Build dan test

Prasyarat: JDK 21 atau lebih baru, Android SDK Platform 36, Gradle 9.6.0. Repository tidak menyertakan Gradle Wrapper; gunakan instalasi Gradle 9.6.0.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
gradle --offline clean test
gradle --offline assembleDebug
```

Validasi fixture dan dataset sintetis tidak memerlukan Android SDK:

```bash
py tools/validate_fixtures.py --verbose
py tools/validate_datasets.py --verbose --demo
```

Jangan membuat `local.properties` yang berisi path pribadi di dalam repository. Gunakan variabel lingkungan `ANDROID_HOME` atau `sdk.dir` di luar repository. Rincian ada di [docs/BUILD_GUIDE.md](docs/BUILD_GUIDE.md) dan [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md).

Hasil verifikasi terakhir (2026-11-19):

- `clean test`: **BUILD SUCCESSFUL**, 153 test, 0 gagal, 0 error, 0 dilewati.
- `assembleDebug`: **BUILD SUCCESSFUL**, menghasilkan `core/build/outputs/aar/core-debug.aar`.
- `validate_fixtures.py`: 14 frame sintetis valid dan konsisten.
- `validate_datasets.py`: 5 pemeriksaan dataset sintetis lulus.
- Pengujian di atas **tidak** menggunakan hardware ECU. Tidak ada klaim bahwa protokol telah diverifikasi pada kendaraan.

Perintah di atas membangun **AAR**, bukan APK. Contoh APK debug yang sudah diverifikasi
ada di [samples/01-hello-android](samples/01-hello-android/README.md); langkah lengkapnya
di [education/02-membangun-apk.md](education/02-membangun-apk.md).

## 5. Titik masuk utama

| Kebutuhan | Kelas |
|---|---|
| Membuka adapter dan mengelola sesi | `com.revcore.android.core.protocol.HondaProtocolEngine` |
| Transport USB serial | `com.revcore.android.core.usb.UsbSerialLibraryTransport` |
| Driver FTDI langsung | `com.revcore.android.core.ftdi.FtdiSerialDriver` |
| Transaksi wire K-Line | `com.revcore.android.core.comm.KlineWireEngine` |
| Lifecycle sesi dan single-flight | `com.revcore.android.core.comm.EcuSessionManager` |
| Framing/checksum | `com.revcore.android.core.comm.HondaFrameCodec`, `HondaChecksum` |
| Identifikasi ECU | `HondaProtocolEngine.identifyEcu`, `EcmDatabase.resolveIdentity` |
| DTC | `HondaProtocolEngine.readDtcs`, `clearDtcs`, `HondaDtcDatabase` |
| Parser Live Data | `com.revcore.android.core.comm.LiveDataFrameParser` |
| MAP/Data Engine | `com.revcore.android.data.map.MapDocument` |
| Analisis ROM read-only | `com.revcore.android.data.rom.RomImage` |
| Layout penyimpanan | `com.revcore.android.core.storage.RevCoreStorage` |
| Session log dan progress | `com.revcore.android.core.tuning.TuningLogStore` |
| Model ECM | `com.revcore.android.data.ecu.EcmModel`, `EcuIdentity` |

## 6. Memakai Communication Core

### 6.1 Jalur tingkat tinggi

`HondaProtocolEngine` mengelola pembukaan transport, wake pulse, sesi, identifikasi, DTC, dan Live Data. Aplikasi pemanggil tetap bertanggung jawab meminta izin USB dan menyediakan `UsbManager`.

```kotlin
val engine = HondaProtocolEngine(usbManager)

// device harus sudah memperoleh izin USB dari aplikasi pemanggil
if (engine.connect(device)) {
    val model = engine.identifyEcu()
    val telemetry = engine.queryLiveData() // null bila profil Live Data belum tersedia
    val dtc = engine.readDtcs()
    // tampilkan hasil; jangan menganggap NO_RESPONSE sebagai "tidak ada DTC"
    engine.disconnect()
}
```

Panggilan `connect`, `identifyEcu`, `readDtcs`, `clearDtcs`, dan `queryLiveData` adalah `suspend` dan berjalan pada `Dispatchers.IO`. Panggil dari coroutine scope aplikasi dan tangani pembatalan.

### 6.2 Jalur tingkat rendah

Untuk protokol atau transport sendiri, pakai `UsbTransport` dan `KlineWireEngine` secara langsung:

```kotlin
val wire = KlineWireEngine()
wire.wakePulse(transport, baudRate = 10400, lowMs = 70, highMs = 130)
val request = HondaFrameCodec.buildBaseline(0x71, 0x00)
val outcome = wire.transceive(transport, request)
```

`UsbTransport` hanya mendefinisikan operasi adapter (buka/tutup, baud, BREAK, purge, DTR/RTS, baca/tulis). `KlineWireEngine` menangani timing, echo TX, validasi checksum, timeout, dan retry. `HondaFrameCodec` membangun frame, menghitung checksum, dan memvalidasi bentuk frame. Jangan melewati pemeriksaan echo/checksum hanya untuk mempermudah integrasi.

### 6.3 Status sesi dan kegagalan

- `CommunicationSniffer` mencatat TX/RX, error adapter, dan statistik sesi. Log tidak boleh memuat data pribadi.
- `EcuSessionManager` dan `SingleFlightGate` mencegah transaksi bersamaan pada satu bus.
- Hasil DTC membedakan `NO_RESPONSE`, `UNRECOGNIZED`, `OK_EMPTY`, dan `OK_CODES`. Jangan menyamakan kegagalan dengan daftar kosong.
- `queryLiveData()` mengembalikan `null` bila katalog profil kosong; tidak ada frame yang dikirim dan tidak ada nilai yang dikarang.

## 7. Menambah protokol secara mandiri

Repository ini hanya memuat baseline Honda PGM-FI. Profil Keihin KWP, Shindengen, Denso, dan Keihin H99B sengaja kosong karena frame-nya belum terbukti. Untuk menambah protokol lain:

1. Implementasikan `UsbTransport` untuk adapter Anda, atau pakai transport yang sudah ada.
2. Tentukan format frame, header yang diterima, dan algoritma checksum Anda sendiri. Jangan memakai header Honda bila protokolnya berbeda.
3. Jika berbasis K-Line, pakai `KlineWireEngine` untuk wake pulse, echo, timeout, dan retry; jika bukan, tulis engine wire terpisah.
4. Definisikan `LiveTableProfile` dengan request, `payloadStart`, `minPayload`, `LiveFieldSpec`, dan label bukti `REFERENCE` atau `UNVERIFIED` selama belum diuji di perangkat.
5. Tambahkan unit test dengan frame sintetis: checksum valid/tidak valid, echo, respons negatif, payload pendek, dan nilai di luar rentang.
6. Dokumentasikan frame request/response dalam hex, sumber bukti, dan batas yang belum diketahui.

Jangan menambahkan operasi tulis ke ECU (flash, remap, security access). Repository publik hanya mencakup baca/diagnostik dan clear DTC yang sudah ada.

## 8. Menambahkan database kendaraan sendiri

Database contoh di `core/src/main/assets/ecm_database.json` hanya berisi tiga record `SAMPLE-`. Database kendaraan hasil kompilasi RevCore (489 record) tersedia terpisah di [data/vehicle_database/ecm_database.json](data/vehicle_database/ecm_database.json) dan **tidak** dimuat otomatis oleh build. Aplikasi dapat memakai database sendiri selama hak datanya jelas.

1. Baca skema lengkap di [docs/ECM_DATABASE_SCHEMA.md](docs/ECM_DATABASE_SCHEMA.md).
2. Mulai dari [examples/vehicle_db/vehicle_template.json](examples/vehicle_db/vehicle_template.json).
3. Isi field wajib: `id`, `partNumber`, `ecmId`, offset (boleh `"0x0"` bila tidak diketahui), dan tanda verifikasi `false` bila belum diuji.
4. Beri awalan `SAMPLE-` hanya untuk data sintetis. Untuk data nyata, gunakan awalan Anda sendiri dan cantumkan `source`.
5. Validasi dengan `py tools/validate_datasets.py --verbose`.
6. Ingat aturan identitas: pencocokan hanya exact atau relasi prefix/suffix terbatas; lebih dari satu kandidat berarti `AMBIGUOUS`, bukan pilihan acak.

`EcmDatabase.init(context)` memuat aset bernama `ecm_database.json`. Bila aplikasi menyediakan aset dengan nama sama, pastikan penggabungan/penimpaan sesuai kebijakan aplikasi dan tetap lolos validator. Untuk materi pihak lain, unggah hanya bila hak distribusinya jelas.

Panduan kontribusi dataset ada di [docs/DATASET_CONTRIBUTION.md](docs/DATASET_CONTRIBUTION.md). Cakupan database hasil kompilasi RevCore, status buktinya, dan batas klaim dicatat di [docs/VEHICLE_DATABASE_INVENTORY.md](docs/VEHICLE_DATABASE_INVENTORY.md); tidak ada kendaraan yang dinyatakan didukung atau teruji tanpa bukti pengujian fisik.

## 9. Membuat dan memvalidasi file MAP

Format MAP generik dijelaskan di [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md), dengan contoh [examples/maps/synthetic_map.json](examples/maps/synthetic_map.json). Inventaris 162 berkas MAP hasil pekerjaan RevCore — ukuran, SHA-256, identifikasi format, dan status analisis — ada di [docs/MAP_INVENTORY.md](docs/MAP_INVENTORY.md) dan [data/map_library/inventory.csv](data/map_library/inventory.csv). Berkas MAP mentah yang merupakan salinan aset pihak lain tidak didistribusikan; alasan dan ruang lingkupnya dijelaskan di [docs/KLASIFIKASI_MATERI.md](docs/KLASIFIKASI_MATERI.md).

Contoh ringkas (template lengkap berukuran 8×11):

```json
{
  "format": "revcore-synthetic-map",
  "version": 1,
  "id": "SAMPLE-MAP-001",
  "status": "REFERENCE",
  "units": {"rpm": "rpm", "load": "%", "value": "ms"},
  "axes": {
    "rpm": [1000, 2000],
    "load": [0, 50, 100]
  },
  "cells": [
    [1.00, 1.05, 1.10],
    [1.20, 1.25, 1.30]
  ]
}
```

Aturan penting:

- Sumbu harus naik ketat dan jumlah baris/kolom harus cocok dengan `cells`.
- Sel harus numerik dan berada pada rentang yang masuk akal untuk satuannya.
- `status` wajib salah satu label bukti yang didokumentasikan; jangan menaikkan status tanpa pengujian.
- Interpolasi bilinear boleh dipakai untuk membaca nilai antara, bukan untuk mengarang sel yang hilang.

Parser Kotlin tersedia tanpa dependensi JSON eksternal:

```kotlin
val result = MapDocument.parse(jsonText)
if (result is MapParseResult.Success) {
    val document = result.document
    val value = document.sampleBilinear(rpm = 3500.0, load = 45.0)
}
```

`MapDocument.parse` menolak ID non-`SAMPLE-`, sumbu tidak menaik, bentuk sel yang tidak cocok, dan nilai non-finite. `sampleBilinear` tidak melakukan ekstrapolasi di luar sumbu.

Validasi:

```bash
py tools/validate_datasets.py --verbose --demo
```

Perintah di atas memeriksa struktur, sumbu, dimensi, dan rentang sel, lalu mencetak contoh interpolasi bilinear. Lulus validator bukan bukti bahwa MAP cocok untuk ECU tertentu. Jangan menambahkan jalur flashing, remap otomatis, atau klaim kompatibilitas.

## 10. Melaporkan bug

Sertakan informasi berikut (format lengkap di [CONTRIBUTING.md](CONTRIBUTING.md) §7):

1. Versi/commit repository dan APK referensi yang digunakan.
2. Modul dan fungsi yang terlibat.
3. Langkah reproduksi.
4. Frame TX/RX atau log `CommunicationSniffer` yang relevan, sudah disanitasi.
5. Perilaku yang diharapkan dan yang terjadi.
6. Jenis adapter (chip, VID:PID), motor/ECU bila diketahui, dan kondisi kontak.
7. Status pengujian: unit test, simulasi, atau hardware nyata.

Jangan menyertakan keystore, password, token, atau data pribadi. Jangan melaporkan hasil simulasi sebagai hasil hardware.

## 11. Mengirim kontribusi

Baca [CONTRIBUTING.md](CONTRIBUTING.md) dan [NOTICE.md](NOTICE.md). Ringkasnya:

- Satu commit untuk satu tujuan; pesan commit menjelaskan **mengapa**.
- Setiap perubahan parser, checksum, framing, identitas, DTC, atau Live Data harus punya unit test.
- Gunakan data sintetis atau data yang hak penggunaannya jelas.
- Jangan mengubah perilaku hanya agar test lulus, dan jangan melemahkan assertion.
- Pertahankan label `ORIGINAL`, `DECOMPILED`, `RECONSTRUCTED`, `REFERENCE`, `UNVERIFIED`.
- Jangan menambahkan dependency tanpa pembahasan dan pembaruan notice lisensi.
- Jangan menambahkan akses jaringan, telemetri, iklan, atau unggahan data.

## 12. Meminta berkas tambahan

Developer boleh mengajukan permintaan berkas atau mengusulkan isi repository yang belum tersedia. Permintaan ditinjau berdasarkan kebutuhan teknis, keamanan/privasi, hak penggunaan dan redistribusi, provenance, serta persetujuan pemilik. Tidak semua berkas internal dapat diberikan. Rincian ada di [docs/FILE_REQUESTS.md](docs/FILE_REQUESTS.md).

## 13. Batasan yang harus dibaca sebelum berkontribusi

- Repository ini **bukan source lengkap aplikasi RevCore**. UI, navigasi, tuning, storage, dan security tidak disertakan.
- Tidak semua profil ECU tersedia. Profil Keihin KWP, Shindengen, Denso, dan Keihin H99B kosong dan berstatus `UNVERIFIED`.
- Komunikasi hanya divalidasi dengan unit test, fixture sintetis, dan pengamatan komunikasi. **Belum ada uji kendaraan fisik** yang didokumentasikan untuk modul publik, dan tidak ada klaim kompatibilitas kendaraan.
- AFR tidak dihitung di host. ECU hanya mempublikasikan tegangan O2 narrowband dan STF; tanpa data lebar pita yang valid, AFR harus dilaporkan `UNSUPPORTED`, bukan dihitung dari O2 narrowband.
- Pembacaan DTC yang gagal tidak boleh ditampilkan sebagai daftar kosong. Clear DTC baru dianggap berhasil jika pembacaan ulang benar-benar kosong.
- `core/src/main/assets/ecm_database.json` hanya berisi tiga record sintetis. Database kendaraan hasil kompilasi RevCore ada terpisah di `data/vehicle_database/` dengan status `terdaftar`; tidak ada yang diklaim teruji.
- Tabel Live Data hasil pengembangan ada di `data/live_data/live_tables.json`. Layout dan skala berstatus `RECONSTRUCTED`; respons ECU masih `OWNER_REPORTED`/`UNVERIFIED`. Tanpa profil yang disediakan pemanggil, `queryLiveData()` tidak mengirim frame dan mengembalikan `null`.
- Analisis ROM bersifat read-only. Repository tidak menyertakan jalur tulis, flashing, checksum repair, atau workflow remap.
- Modul tuning, remap, flash, backup, dan UI tidak termasuk cakupan publikasi.
- Jangan mengunggah kredensial dalam bentuk apa pun, materi komponen keamanan internal, atau berkas mentah pihak lain yang hak publikasinya belum jelas.

## 14. Dokumentasi

| Dokumen | Isi |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arsitektur, layer, dependency, batas publikasi |
| [docs/APK_ANALYSIS.md](docs/APK_ANALYSIS.md) | Analisis APK yang boleh dipublikasikan |
| [docs/SOURCE_CODE_GUIDE.md](docs/SOURCE_CODE_GUIDE.md) | Peta package dan panduan membaca source |
| [docs/COMMUNICATION_ENGINE.md](docs/COMMUNICATION_ENGINE.md) | Mesin komunikasi dan lifecycle |
| [docs/USB_ADAPTER_GUIDE.md](docs/USB_ADAPTER_GUIDE.md) | Adapter USB, chipset, permission, wiring |
| [docs/KLINE_PROTOCOL.md](docs/KLINE_PROTOCOL.md) | Fisik, framing, timing, katalog request |
| [docs/COMMUNICATION_FLOW.md](docs/COMMUNICATION_FLOW.md) | Alur connect, identify, live, DTC |
| [docs/FRAME_PARSER.md](docs/FRAME_PARSER.md) | Parsing, echo, validasi |
| [docs/CHECKSUM.md](docs/CHECKSUM.md) | Algoritma checksum dan contoh |
| [docs/ECU_IDENTIFICATION.md](docs/ECU_IDENTIFICATION.md) | Identitas, kanonikalisasi, pencocokan |
| [docs/ECM_DATABASE_SCHEMA.md](docs/ECM_DATABASE_SCHEMA.md) | Skema database kendaraan/ECU |
| [docs/VEHICLE_DATABASE_INVENTORY.md](docs/VEHICLE_DATABASE_INVENTORY.md) | Cakupan database hasil kompilasi dan status bukti |
| [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md) | Format MAP generik dan interpolasi |
| [docs/MAP_INVENTORY.md](docs/MAP_INVENTORY.md) | Inventaris MAP, ukuran, SHA-256, dan status analisis |
| [docs/LIVE_DATA_REFERENCE.md](docs/LIVE_DATA_REFERENCE.md) | Tabel Live Data, rumus, skala, dan contoh decoding |
| [docs/TEMUAN_DAN_EKSPERIMEN.md](docs/TEMUAN_DAN_EKSPERIMEN.md) | Temuan, eksperimen, kasus gagal, dan bug tercatat |
| [docs/KLASIFIKASI_MATERI.md](docs/KLASIFIKASI_MATERI.md) | Klasifikasi hasil RevCore vs referensi vs berkas pihak lain |
| [docs/STATUS_PENGUJIAN.md](docs/STATUS_PENGUJIAN.md) | Status fungsi menurut laporan pemilik dan kosakata status |
| [docs/ROM_ANALYSIS.md](docs/ROM_ANALYSIS.md) | Analisis ROM read-only, checksum, dan diff blok |
| [docs/STORAGE_AND_LOGS.md](docs/STORAGE_AND_LOGS.md) | Layout penyimpanan, penamaan berkas, dan session log |
| [docs/DATASET_CONTRIBUTION.md](docs/DATASET_CONTRIBUTION.md) | Cara menambah kendaraan, profil, dan MAP |
| [docs/DIAGNOSTIC_ENGINE.md](docs/DIAGNOSTIC_ENGINE.md) | Orkestrasi diagnostik |
| [docs/DTC_ENGINE.md](docs/DTC_ENGINE.md) | Baca/hapus DTC dan arti hasil |
| [docs/LIVE_DATA_ENGINE.md](docs/LIVE_DATA_ENGINE.md) | Parser dan polling Live Data generik |
| [docs/SENSOR_DECODING.md](docs/SENSOR_DECODING.md) | Skala, rumus, plausibilitas |
| [docs/FILE_REQUESTS.md](docs/FILE_REQUESTS.md) | Kebijakan permintaan berkas tambahan |
| [docs/SECURITY_AND_INTEGRITY.md](docs/SECURITY_AND_INTEGRITY.md) | Pernyataan pengecualian cakupan |
| [docs/BUILD_GUIDE.md](docs/BUILD_GUIDE.md) | Build, dependency, troubleshooting |
| [docs/DEBUGGING_GUIDE.md](docs/DEBUGGING_GUIDE.md) | Log, status, diagnosis kegagalan |
| [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) | Test, fixture, batas pengujian |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | Lisensi dependency langsung dan transitif |
| [FINAL_PUBLICATION_READINESS.md](FINAL_PUBLICATION_READINESS.md) | Ringkasan kesiapan, audit, blocker, dan rekomendasi GO/NO-GO |
| [tools/README.md](tools/README.md) | Pemakaian validator fixture dan dataset |
| [education/README.md](education/README.md) | Pusat pembelajaran: jalur pemula, referensi, kegagalan, AI, kontributor |
| [samples/README.md](samples/README.md) | 16 contoh progresif dari Hello Android sampai database sendiri |

## 15. Kontribusi dan persetujuan pemilik

Baca [CONTRIBUTING.md](CONTRIBUTING.md) dan [NOTICE.md](NOTICE.md). Setiap kontribusi harus menjaga label bukti (`ORIGINAL`, `DECOMPILED`, `RECONSTRUCTED`, `REFERENCE`, `UNVERIFIED`), tidak mengarang hasil pengujian, dan tidak memasukkan materi terlarang.

Rilis `v0.3.0` disiapkan berdasarkan perintah rilis akhir pemilik. Batas publikasi yang masih berlaku adalah hal teknis, bukan persetujuan: tanpa UI/Application Layer, tanpa komponen keamanan internal dan mekanismenya, tanpa kredensial/data pribadi, dan tanpa berkas mentah pihak lain yang hak publikasinya belum jelas. Hasil analisis, database kendaraan, profil ECM, inventaris MAP, tabel Live Data, rumus, dokumentasi protokol, dan temuan eksperimen RevCore **termasuk** cakupan publikasi. Tidak ada klaim pengujian kendaraan tanpa bukti fisik.
