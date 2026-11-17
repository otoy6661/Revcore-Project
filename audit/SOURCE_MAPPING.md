# PEMETAAN SOURCE

Dokumen ini mencatat asal setiap berkas source di repository terhadap proyek pemilik dan APK referensi. Tujuannya agar pembaca dapat membedakan source asli, hasil adaptasi, dan materi contoh.

## 1. Metode

1. APK `RevCore build 161126.apk` dibaca sebagai referensi perilaku; APK tidak diubah dan tidak di-repack.
2. Source proyek pemilik di `work/revcore` (modul `app`) dipakai sebagai pembanding. Proyek asli tidak diubah, tidak dihapus, dan tidak ditimpa.
3. Berkas staging dibandingkan byte-per-byte dengan berkas proyek menggunakan `cmp`.
4. Nama class dikorelasikan dengan `mapping.txt` dari output build lokal. `mapping.txt` tidak disalin ke repository.
5. Berkas yang berbeda dari proyek diberi status `RECONSTRUCTED` dan alasan perubahan dicatat.

Perintah pembandingan yang dipakai:

```bash
cmp "revcore/app/src/main/java/<path>" \
    "RevCore_Public_Developer_Repository/core/src/main/java/<path>"
```

## 2. Ringkasan hasil

| Kelompok | Jumlah | Status | Hasil |
|---|---:|---|---|
| Source utama | 53 file, 9.791 baris | Campuran | 44 `ORIGINAL`, 9 `RECONSTRUCTED` |
| Test | 19 file, 2.517 baris, 153 `@Test` | Campuran | 15 `ORIGINAL`, 4 `RECONSTRUCTED` |
| Dataset `ecm_database.json` | 1 file | `REFERENCE` | Sintetis, bukan data APK |
| Fixture `examples/` | 6 file | `REFERENCE` | Sintetis |
| Dokumentasi dan audit | Lihat manifest | `REFERENCE` | Ditulis untuk repository ini |

Berkas yang berbeda dari proyek:

- `core/src/main/java/com/revcore/android/core/comm/Evidence.kt`
- `core/src/main/java/com/revcore/android/core/comm/HondaEcuProfiles.kt`
- `core/src/main/java/com/revcore/android/core/comm/HondaFrameCodec.kt`
- `core/src/main/java/com/revcore/android/core/comm/LiveDataProfile.kt`
- `core/src/main/java/com/revcore/android/core/protocol/HondaProtocolEngine.kt`
- `core/src/main/java/com/revcore/android/core/protocol/legacy/LegacyHondaProtocolEngine.kt`
- `core/src/main/java/com/revcore/android/data/ecu/EcmDatabase.kt`
- `core/src/main/java/com/revcore/android/core/storage/RevCoreStorage.kt`
- `core/src/main/java/com/revcore/android/data/rom/RomImage.kt`
- `core/src/test/java/com/revcore/android/core/comm/EcuProfilesTest.kt`
- `core/src/test/java/com/revcore/android/core/comm/LiveDataProfileParserTest.kt`
- `core/src/test/java/com/revcore/android/core/protocol/BaselineProtocolTest.kt`
- `core/src/test/java/com/revcore/android/data/ecu/EcmCatalogAndIdentityTest.kt`

Tujuh berkas source dan empat test baru ditulis khusus untuk repository publik:
`MapDocument.kt`, `MiniJson.kt`, `RomImage.kt`, `RomImageTest.kt`, `MapDocumentTest.kt`,
`RevCoreStorageTest.kt`, dan `TuningLogTest.kt` (tujuh berkas), ditambah
`CalibrationMap.kt`, `ModifiedBlock.kt`, dan `TuningLog.kt` yang disalin utuh dari proyek.
Tidak ada berkas proyek lama yang hilang dari kelompok publik.

## 3. Status source utama

Semua berkas di bawah berada di `core/src/main/java/com/revcore/android/`.

| Berkas | Baris | Status | Catatan |
|---|---:|---|---|
| `core/comm/EcuFamily.kt` | 43 | `ORIGINAL` | Identik dengan proyek |
| `core/comm/EcuSessionManager.kt` | 178 | `ORIGINAL` | Identik |
| `core/comm/Evidence.kt` | 143 | `RECONSTRUCTED` | Nama pustaka pihak ketiga digeneralisasi; alamat dan status bukti dipertahankan |
| `core/comm/HondaEcuProfiles.kt` | 313 | `RECONSTRUCTED` | Probe dan tabel Live Data ditarik; baseline identifikasi/DTC dipertahankan |
| `core/comm/HondaFrameCodec.kt` | 92 | `RECONSTRUCTED` | Komentar sumber dianonimkan; perilaku checksum tidak berubah |
| `core/comm/KlineWireEngine.kt` | 453 | `ORIGINAL` | Identik |
| `core/comm/LiveDataProfile.kt` | 282 | `RECONSTRUCTED` | Katalog bawaan dikosongkan; parser generik dipertahankan |
| `core/comm/LivePollPolicy.kt` | 26 | `ORIGINAL` | Identik |
| `core/ftdi/FtdiConstants.kt` | 46 | `ORIGINAL` | Identik |
| `core/ftdi/FtdiSerialDriver.kt` | 411 | `ORIGINAL` | Identik |
| `core/protocol/ClearDtcResult.kt` | 17 | `ORIGINAL` | Identik |
| `core/protocol/CommDataStage.kt` | 13 | `ORIGINAL` | Identik |
| `core/protocol/CommEventCategory.kt` | 13 | `ORIGINAL` | Identik |
| `core/protocol/CommLogEntry.kt` | 8 | `ORIGINAL` | Identik |
| `core/protocol/CommSessionStatus.kt` | 20 | `ORIGINAL` | Identik |
| `core/protocol/CommunicationSniffer.kt` | 307 | `ORIGINAL` | Identik |
| `core/protocol/ConnectionPipelineStep.kt` | 14 | `ORIGINAL` | Identik |
| `core/protocol/DiagnosticStage.kt` | 16 | `ORIGINAL` | Identik |
| `core/protocol/DtcReadResult.kt` | 20 | `ORIGINAL` | Identik |
| `core/protocol/HondaChecksum.kt` | 44 | `ORIGINAL` | Identik |
| `core/protocol/HondaDtc.kt` | 13 | `ORIGINAL` | Identik |
| `core/protocol/HondaDtcDatabase.kt` | 150 | `ORIGINAL` | Identik |
| `core/protocol/HondaIdentifierParser.kt` | 61 | `ORIGINAL` | Identik |
| `core/protocol/HondaProtocolEngine.kt` | 1.787 | `RECONSTRUCTED` | Probe Live Data dihapus; identifikasi dan DTC tidak berubah |
| `core/protocol/IgnitionState.kt` | 9 | `ORIGINAL` | Identik |
| `core/protocol/KlineEchoFilter.kt` | 130 | `ORIGINAL` | Identik; implementasi alternatif |
| `core/protocol/KlinePacket.kt` | 110 | `ORIGINAL` | Identik |
| `core/protocol/KlineTransceiver.kt` | 471 | `ORIGINAL` | Identik; implementasi alternatif |
| `core/protocol/LiveTelemetry.kt` | 66 | `ORIGINAL` | Identik |
| `core/protocol/LogDirection.kt` | 8 | `ORIGINAL` | Identik |
| `core/protocol/ProtocolPhase.kt` | 10 | `ORIGINAL` | Identik |
| `core/protocol/SingleFlightGate.kt` | 30 | `ORIGINAL` | Identik |
| `core/protocol/SnifferLogEvent.kt` | 71 | `ORIGINAL` | Identik |
| `core/protocol/SnifferStats.kt` | 24 | `ORIGINAL` | Identik |
| `core/protocol/TimelineStageItem.kt` | 28 | `ORIGINAL` | Identik |
| `core/protocol/legacy/LegacyHondaProtocolEngine.kt` | 1.203 | `RECONSTRUCTED` | Query Live Data legacy dihapus; referensi rollback dipertahankan |
| `core/storage/RevCoreStorage.kt` | 180 | `RECONSTRUCTED` | `ContextCompat` diganti `Context.checkSelfPermission`; perilaku layout/penamaan tidak berubah |
| `core/tuning/TuningLog.kt` | 424 | `ORIGINAL` | Disalin utuh; log sesi dan progress terukur |
| `core/usb/AdapterConnectionPolicy.kt` | 50 | `ORIGINAL` | Identik |
| `core/usb/UsbAutoConnectState.kt` | 19 | `ORIGINAL` | Identik |
| `core/usb/UsbSerialLibraryTransport.kt` | 531 | `ORIGINAL` | Identik |
| `core/usb/UsbTransport.kt` | 18 | `ORIGINAL` | Identik |
| `data/ecu/ChecksumType.kt` | 8 | `ORIGINAL` | Identik |
| `data/ecu/EcmDatabase.kt` | 634 | `RECONSTRUCTED` | Lihat §4 |
| `data/ecu/EcmModel.kt` | 60 | `ORIGINAL` | Identik |
| `data/ecu/EcuIdCanonicalizer.kt` | 397 | `ORIGINAL` | Identik |
| `data/ecu/EcuIdentity.kt` | 237 | `ORIGINAL` | Identik |
| `data/ecu/TempSensorType.kt` | 11 | `ORIGINAL` | Identik |
| `data/map/MapDocument.kt` | 197 | `ORIGINAL` | Implementasi generik baru; parser, validasi, interpolasi |
| `data/map/MiniJson.kt` | 184 | `ORIGINAL` | Pembaca JSON terbatas baru; tanpa dependensi eksternal |
| `data/rom/CalibrationMap.kt` | 62 | `ORIGINAL` | Disalin utuh |
| `data/rom/ModifiedBlock.kt` | 38 | `ORIGINAL` | Disalin utuh |
| `data/rom/RomImage.kt` | 111 | `RECONSTRUCTED` | Adaptasi read-only dari logika perbandingan; tanpa fungsi tulis |

## 4. Perubahan source utama

Sembilan file source utama berbeda dari proyek. Tujuh perubahan pertama bersifat pengurangan atau pengaburan materi yang tidak boleh dipublikasikan; dua perubahan terakhir adalah adaptasi agar modul baru tidak bergantung pada `androidx.core` dan tidak memuat jalur tulis ECU.

| Berkas | Perubahan | Alasan |
|---|---|---|
| `Evidence.kt` | Nama pustaka pihak ketiga digeneralisasi menjadi "reference analysis image"; alamat, status, dan catatan bukti dipertahankan | Menghapus ketergantungan identitas pihak ketiga tanpa menyamarkan provenance |
| `HondaEcuProfiles.kt` | Probe Live Data, tabel primer, dan tabel ekstensi ditarik; katalog identifikasi dan DTC dipertahankan | Hak penggunaan frame/konstanta Live Data belum jelas |
| `HondaFrameCodec.kt` | Komentar yang menyebut nama pustaka dianonimkan | Perilaku framing/checksum tidak berubah |
| `LiveDataProfile.kt` | `HondaLiveDataProfiles` dikosongkan; parser generik, `LiveFieldSpec`, dan state dipertahankan | Katalog bawaan ditarik sampai hak dan validasi jelas |
| `HondaProtocolEngine.kt` | Pemanggilan probe Live Data pada jalur identifikasi dihapus; alur identifikasi dan DTC tidak berubah | Tidak ada frame Live Data yang dikirim tanpa profil pemanggil |
| `LegacyHondaProtocolEngine.kt` | `queryLiveData()` tidak lagi mengirim frame; katalog legacy ditarik | Sama dengan engine utama |
| `EcmDatabase.kt` | Lihat §4.1 | Kait security dihapus dan fallback diganti sintetis |
| `RevCoreStorage.kt` | `ContextCompat.checkSelfPermission` diganti `context.checkSelfPermission` | Menghindari dependency `androidx.core` tambahan; izin dan perilaku layout sama |
| `RomImage.kt` | Ditulis read-only dari logika perbandingan ROM; fungsi tulis/checksum repair tidak disertakan | Mencegah publikasi jalur flashing dan perilaku yang belum terbukti |

### 4.1 `EcmDatabase.kt`

Perubahan pada file ini bersifat pengurangan, bukan penambahan perilaku protokol.

| Perubahan | Alasan | Dampak |
|---|---|---|
| Import dan pemanggilan `IntegrityTrust` / `RevCoreIntegrity` dihapus dari jalur pemuatan aset | Security/Integrity bukan target publikasi (Addendum A) | Pemuatan aset kembali ke jalur biasa; pustaka `:core` tidak bergantung pada package security |
| Tiga record fallback kendaraan nyata diganti `SAMPLE-ECM-001`, `SAMPLE-ECM-002`, `SAMPLE-ECM-003` | Database kendaraan tidak boleh dipublikasikan tanpa hak yang terbukti | Katalog publik hanya berisi data sintetis |
| Asset `ecm_database.json` dibaca melalui `AssetManager` tanpa gerbang integritas | Menjaga `:core` mandiri | Aplikasi pemanggil bertanggung jawab atas kebijakan integritasnya |

Tidak ada perubahan pada struktur `EcmModel`, algoritma pencocokan identitas, atau API publik `resolveIdentity`.

## 5. Status test

| Berkas | `@Test` | Status | Catatan |
|---|---:|---|---|
| `core/comm/EcuProfilesTest.kt` | 9 | `RECONSTRUCTED` | Ekspektasi disesuaikan: katalog Live Data kosong, identifikasi 4 frame |
| `core/comm/EcuSessionManagerTest.kt` | 8 | `ORIGINAL` | Identik |
| `core/comm/HondaFrameCodecTest.kt` | 10 | `ORIGINAL` | Identik |
| `core/comm/KlineWireEngineTest.kt` | 8 | `ORIGINAL` | Identik |
| `core/comm/LiveDataProfileParserTest.kt` | 16 | `RECONSTRUCTED` | Hanya memakai profil sintetis; tidak ada tabel Honda bawaan |
| `core/comm/LivePollPolicyTest.kt` | 2 | `ORIGINAL` | Identik |
| `core/protocol/BaselineProtocolTest.kt` | 6 | `RECONSTRUCTED` | Memakai frame Live Data sintetis 0x7A |
| `core/protocol/HondaIdentifierParserTest.kt` | 7 | `ORIGINAL` | Identik |
| `core/protocol/KlineTransceiverTest.kt` | 14 | `ORIGINAL` | Identik |
| `core/protocol/SingleFlightGateTest.kt` | 3 | `ORIGINAL` | Identik |
| `core/usb/AdapterConnectionPolicyTest.kt` | 12 | `ORIGINAL` | Identik |
| `data/ecu/EcmCatalogAndIdentityTest.kt` | 4 | `RECONSTRUCTED` | ID record disesuaikan dengan dataset sintetis |
| `data/ecu/EcmIdentityMatcherTest.kt` | 9 | `ORIGINAL` | Identik |
| `data/ecu/EcmIdentityTest.kt` | 13 | `ORIGINAL` | Identik |
| `data/ecu/EcuIdCanonicalizerTest.kt` | 14 | `ORIGINAL` | Identik |
| `data/map/MapDocumentTest.kt` | 8 | `ORIGINAL` | Baru; parser, validasi, interpolasi generik |
| `data/rom/RomImageTest.kt` | 5 | `ORIGINAL` | Baru; word, checksum, diff, ukuran berbeda |
| `core/storage/RevCoreStorageTest.kt` | 3 | `ORIGINAL` | Baru; sanitasi dan penamaan berkas |
| `core/tuning/TuningLogTest.kt` | 2 | `ORIGINAL` | Baru; progress dan terminal event |
| **Total** | **153** | | |

Empat test disesuaikan tanpa melonggarkan validasi protokol. `LiveDataProfileParserTest` sekarang menguji parser dengan profil sintetis, bukan tabel Honda; `EcuProfilesTest` memastikan katalog bawaan kosong; `BaselineProtocolTest` memakai frame sintetis 0x7A; dan `EcmCatalogAndIdentityTest` memakai ID dataset sintetis (`SAMPLE-ECM-001`). Pemeriksaan AFR `UNSUPPORTED` dan ketiadaan `O2_RAW` tetap dipertahankan. Delapan belas test baru mencakup engine MAP, ROM read-only, storage, dan session log.

## 6. Status data dan fixture

| Berkas | Status | Asal |
|---|---|---|
| `core/src/main/assets/ecm_database.json` | `REFERENCE` | Ditulis ulang; 3 record sintetis |
| `examples/protocol_frames/requests.json` | `REFERENCE` | Frame dari katalog request, bukan rekaman ECU |
| `examples/ecu_responses/identification.json` | `REFERENCE` | Respons sintetis |
| `examples/dtc/dtc_examples.json` | `REFERENCE` | Respons sintetis |
| `examples/sensor_data/live_data.json` | `REFERENCE` | Payload sintetis |

Tidak ada rekaman ECU, database kendaraan nyata, MAP, atau firmware di berkas tersebut.

## 7. Batas

- `ORIGINAL` berarti identik dengan proyek pemilik, bukan jaminan bahwa perilaku telah diuji pada hardware.
- Source proyek sendiri adalah hasil pengembangan sebelumnya; statusnya tidak dinaikkan menjadi bukti protokol ECU.
- `mapping.txt`, `seeds.txt`, dan output build tidak disertakan.
- Perubahan di masa depan pada berkas mana pun harus memperbarui tabel di dokumen ini dan di [RECONSTRUCTION_STATUS.md](RECONSTRUCTION_STATUS.md).
