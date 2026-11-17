# Testing Guide

Repository ini berisi unit test JVM untuk pustaka `:core`. Tidak ada test perangkat
keras, test instrumentasi, atau data rekaman ECU nyata.

## 1. Prinsip pengujian

- Semua frame, respons, dan payload adalah **sintetis** atau diturunkan dari katalog
  frame yang terdokumentasi.
- Test tidak boleh mengarang respons ECU, hasil pembacaan, atau kompatibilitas
  kendaraan.
- Test memeriksa perilaku kode, bukan membuktikan protokol pada kendaraan.
- Nilai yang gagal divalidasi harus tetap gagal; test tidak boleh "memperbaiki" data agar
  lolos.
- Waktu dan I/O disimulasikan; tidak ada `Thread.sleep()` yang menjadi bagian dari
  kontrak.

## 2. Klasifikasi test saat ini

| Kategori | Jumlah | Status |
|---|---:|---|
| Test JVM yang lulus | 153 method `@Test` di 19 berkas | Diharapkan lulus semua |
| Test gagal yang diketahui | 0 | Tidak ada |
| Test bergantung perangkat keras | 0 | Tidak disertakan |
| Test dengan dataset privat | 0 | Tidak disertakan |
| Test yang tidak dapat dijalankan | 0 | Tidak ada |

Verifikasi perangkat keras (adapter nyata, ECU, kendaraan) **tidak** termasuk hitungan
ini dan tidak boleh diklaim telah dilakukan.

## 3. Perintah

Dari root repository, dengan `GRADLE_BIN` sesuai [BUILD_GUIDE.md](BUILD_GUIDE.md):

```bash
"$GRADLE_BIN" test
```

Per varian (hanya debug yang tersedia pada konfigurasi ini):

```bash
"$GRADLE_BIN" :core:testDebugUnitTest
```

Satu berkas atau satu kelas:

```bash
"$GRADLE_BIN" :core:testDebugUnitTest --tests "com.revcore.android.core.comm.HondaFrameCodecTest"
"$GRADLE_BIN" :core:testDebugUnitTest --tests "com.revcore.android.core.protocol.*"
```

Laporan:

- HTML: `core/build/reports/tests/testDebugUnitTest/index.html`
- XML: `core/build/test-results/testDebugUnitTest/`

Pada konfigurasi ini task `test` menjalankan `testDebugUnitTest`; tidak ada task
`testReleaseUnitTest` yang terdaftar. Jumlah **method** adalah 153 dan jumlah eksekusi
sama dengan 153 pada laporan debug. Baca laporan per varian agar tidak menghitung ganda
bila varian lain ditambahkan di masa depan.

## 4. Peta test

| Berkas | Jumlah | Fokus |
|---|---:|---|
| `core/comm/HondaFrameCodecTest.kt` | 10 | Build frame, checksum, batas 254 byte, header, panjang, respons negatif, echo di kepala |
| `core/comm/KlineWireEngineTest.kt` | 8 | Respons valid, echo-only, echo berprefiks, checksum rusak, fragmen, echo tertunda, timeout, retry |
| `core/protocol/KlineTransceiverTest.kt` | 14 | Cermin perilaku transceiver, noise sebelum frame, kandidat tidak lengkap, respons negatif, urutan log, ekspor teks |
| `core/comm/EcuSessionManagerTest.kt` | 8 | Lifecycle, keluarga dari header, ambang kegagalan, penolakan transaksi saat tertutup, mismatch transport, detach |
| `core/comm/EcuProfilesTest.kt` | 9 | Pemisahan keluarga, katalog baseline, checksum, urutan probe, profil kosong tidak meminjam frame, status bukti |
| `core/comm/LiveDataProfileParserTest.kt` | 16 | Layout sintetis `0x7A`, aturan `0xFF`, payload pendek, KOEO, tanpa auto-zero, penolakan nilai, profil tak dikenal, AFR `UNSUPPORTED`, katalog bawaan kosong |
| `core/comm/LivePollPolicyTest.kt` | 2 | Interval adaptif dan backoff kegagalan |
| `core/protocol/BaselineProtocolTest.kt` | 6 | Checksum request identifikasi/DTC/live, pemetaan DTC count-prefixed, filter byte protokol, payload kosong |
| `core/protocol/HondaIdentifierParserTest.kt` | 7 | Preservasi byte, ID 5 byte, payload panjang, checksum rusak, respons negatif, payload kosong/`00`/`FF` |
| `core/protocol/SingleFlightGateTest.kt` | 3 | Penolakan pemanggil kedua, pelepasan gate, reset setelah pembatalan |
| `core/usb/AdapterConnectionPolicyTest.kt` | 12 | VID didukung, izin, penolakan izin, pause manual, detach, resume |
| `data/ecu/EcmCatalogAndIdentityTest.kt` | 4 | Katalog aktif, confidence eksplisit, truncation bukan identifikasi |
| `data/ecu/EcmIdentityMatcherTest.kt` | 9 | Normalisasi, leading zero, exact/partial, penolakan substring longgar, part number |
| `data/ecu/EcmIdentityTest.kt` | 13 | Resolusi exact, ID 4 byte bukan model, duplikat ambigu, placeholder, quality tidak naik |
| `data/ecu/EcuIdCanonicalizerTest.kt` | 14 | Selector/field, trailing byte, varian format, ID terpotong, ASCII vs hex, format 5/6 byte |
| `data/map/MapDocumentTest.kt` | 8 | Parse contoh, interpolasi, penolakan ekstrapolasi, ID non-publik, sumbu, bentuk sel, JSON rusak, contoh repo |
| `data/rom/RomImageTest.kt` | 5 | Word endian, checksum, penggabungan blok diff, ukuran berbeda, image identik |
| `core/storage/RevCoreStorageTest.kt` | 3 | Sanitasi nama, nama backup, ekstensi MAP/ekspor |
| `core/tuning/TuningLogTest.kt` | 2 | Persentase memerlukan total nyata, event terminal tidak dapat ditulis ulang |

Total: **153**.

## 5. Fixture dan contoh

| Lokasi | Isi | Status |
|---|---|---|
| `examples/protocol_frames/requests.json` | Frame request sintetis dari katalog | `REFERENCE` |
| `examples/ecu_responses/identification.json` | Respons identifikasi sintetis | `REFERENCE` |
| `examples/dtc/dtc_examples.json` | Payload DTC sintetis | `REFERENCE` |
| `examples/sensor_data/live_data.json` | Payload Live Data sintetis | `REFERENCE` |
| `examples/vehicle_db/vehicle_template.json` | 1 record kendaraan `SAMPLE-` | `REFERENCE` |
| `examples/maps/synthetic_map.json` | MAP sintetis 8×11 | `REFERENCE` |
| `core/src/main/assets/ecm_database.json` | 3 record `SAMPLE-` | `REFERENCE` |

Tidak ada capture kendaraan, VIN, nomor rangka, atau data pribadi. Contoh tidak boleh
dipromosikan menjadi bukti kompatibilitas.

## 6. Batas unit test

Yang **tidak** dibuktikan oleh test:

- Adapter USB nyata dapat dibuka pada ponsel tertentu.
- Level tegangan, terminasi, atau transceiver K-Line benar.
- ECU Honda tertentu merespons tabel/request tertentu.
- Timing 70/130 ms sesuai perilaku ECU nyata.
- Pembacaan DTC atau clear berhasil pada kendaraan.
- Checksum cocok dengan ECU di luar contoh yang dihitung manual.
- Perilaku multi-thread pada perangkat dengan beban nyata.

`unitTests.isReturnDefaultValues = true` membuat API Android yang tidak tersedia di JVM
mengembalikan nilai default. Test tidak boleh mengandalkan perilaku Android nyata.

## 7. Menambahkan test

1. Gunakan JUnit 4 dan pola yang sudah ada.
2. Susun byte frame secara eksplisit; jangan membaca data kendaraan.
3. Untuk waktu, gunakan `kotlinx-coroutines-test` atau clock yang diinjeksi
   (`KlineWireEngine` menerima `clock`), bukan tidur nyata.
4. Uji state dan alasan kegagalan, bukan hanya nilai numerik.
5. Pastikan frame yang dipakai benar-benar lolos checksum; hitung manual bila perlu.
6. Jangan menambahkan test yang dilewati (`@Ignore`) tanpa alasan dan tanpa catatan.
7. Beri nama test yang menyatakan perilaku, misalnya
   `echoOnly_isRejected` atau `shortPayload_marksMissingFieldsUnavailableWithoutZeroing`.
8. Jika menambahkan data contoh, tandai `REFERENCE`/sintetis dan jangan menyalin respons
   ECU nyata tanpa izin.

## 8. Menafsirkan kegagalan

| Gejala | Tindakan |
|---|---|
| Checksum test gagal | Periksa aritmetika dua's complement; jangan mengubah expected agar cocok |
| Echo test gagal | Periksa jendela 1500 ms dan aturan echo di kepala |
| Live parser gagal | Periksa offset payload vs frame dan aturan `0xFF` |
| Test identitas gagal | Periksa panjang kanonik, exact precedence, dan larangan truncation |
| Test gagal hanya di release | Periksa perbedaan konfigurasi varian; jangan mengabaikan |
| Semua test lulus tetapi hardware gagal | Itu mungkin; unit test bukan bukti hardware |

Jangan menandai test sebagai lulus dengan menghapus assertion atau melonggarkan rentang
tanpa bukti baru.

## 9. Hasil yang diharapkan

Setelah `clean test` pada repository ini:

- `BUILD SUCCESSFUL`
- 153 method test, 0 gagal, 0 error, 0 dilewati
- Tidak ada test perangkat keras yang dijalankan

Setelah `assembleDebug`:

- `BUILD SUCCESSFUL`
- `core/build/outputs/aar/core-debug.aar` terbentuk

Hasil ini hanya menyatakan kode dapat dikompilasi dan perilaku yang diuji sesuai
kontrak. Tidak ada klaim kompatibilitas kendaraan.
