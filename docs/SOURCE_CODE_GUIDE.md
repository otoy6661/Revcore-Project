# PANDUAN SOURCE CODE

Dokumen ini memetakan isi `core/src/main` dan menjelaskan urutan membaca source. Package dipertahankan seperti pada proyek asli agar source dapat dibangun dan dipetakan kembali ke APK.

## 1. Peta package

```text
com.revcore.android
├── core
│   ├── comm        framing, wire engine, sesi, profil ECU, Live Data
│   ├── ftdi        driver FTDI native (control transfer langsung)
│   ├── protocol    orkestrasi Honda PGM-FI, DTC, telemetry, log
│   │   └── legacy  engine lama sebagai referensi rollback
│   └── usb         transport USB serial dan kebijakan koneksi
└── data.ecu        model ECM, identitas, kanonikalisasi, katalog
```

## 2. Urutan membaca yang disarankan

1. `data/ecu/EcuIdentity.kt` dan `EcuIdCanonicalizer.kt` — memahami apa yang dianggap identitas.
2. `core/comm/HondaFrameCodec.kt` dan `core/protocol/HondaChecksum.kt` — bentuk frame dan validasi.
3. `core/comm/KlineWireEngine.kt` — transaksi wire, echo, timeout.
4. `core/usb/UsbTransport.kt` dan `UsbSerialLibraryTransport.kt` — perpindahan byte.
5. `core/comm/EcuSessionManager.kt` — lifecycle dan single-flight.
6. `core/comm/HondaEcuProfiles.kt` dan `LiveDataProfile.kt` — katalog request dan layout.
7. `core/protocol/HondaProtocolEngine.kt` — orkestrasi seluruh operasi.

## 3. Daftar file utama

### 3.1 `core/comm`

| File | Baris | Peran |
|---|---:|---|
| `HondaFrameCodec.kt` | 92 | Konstanta header, build frame, validasi bentuk, strip echo, hex |
| `KlineWireEngine.kt` | 453 | Wake pulse, transceive, retry, buffer RX, pemilihan frame |
| `EcuSessionManager.kt` | 178 | Lifecycle sesi, mutex transaksi, deteksi family, snapshot |
| `HondaEcuProfiles.kt` | 313 | Katalog request per family, wake timing, katalog kosong untuk family yang belum terbukti |
| `LiveDataProfile.kt` | 282 | Layout tabel, parser payload, state parameter |
| `LivePollPolicy.kt` | 26 | Interval polling dan backoff |
| `EcuFamily.kt` | 43 | Enum family dan resolver header respons |
| `Evidence.kt` | 143 | Registry status bukti elemen komunikasi |

### 3.2 `core/protocol`

| File | Baris | Peran |
|---|---:|---|
| `HondaProtocolEngine.kt` | 1787 | API utama: connect, wake, identify, live, DTC, clear |
| `KlinePacket.kt` | 110 | Model paket, konstanta service/header, parse dasar |
| `KlineTransceiver.kt` | 471 | Implementasi transceiver alternatif dengan outcome dan diagnostik |
| `KlineEchoFilter.kt` | 130 | Filter echo berbasis buffer |
| `HondaChecksum.kt` | 44 | Checksum frame dan checksum ROM |
| `HondaIdentifierParser.kt` | 61 | Parser respons identifikasi yang ketat |
| `HondaDtcDatabase.kt` | 150 | 15 DTC dikenal, parser respons DTC |
| `HondaDtc.kt` | 13 | Model DTC |
| `DtcReadResult.kt` | 20 | Hasil baca DTC dan state |
| `ClearDtcResult.kt` | 17 | Hasil clear DTC |
| `LiveTelemetry.kt` | 66 | Model telemetry dan state |
| `CommunicationSniffer.kt` | 307 | Event, statistik, timeline diagnostik |
| `SingleFlightGate.kt` | 30 | Gate operasi tunggal |
| `legacy/LegacyHondaProtocolEngine.kt` | 1203 | Engine lama; referensi rollback, bukan jalur utama |

### 3.3 `core/usb` dan `core/ftdi`

| File | Baris | Peran |
|---|---:|---|
| `UsbTransport.kt` | 18 | Interface transport |
| `UsbSerialLibraryTransport.kt` | 531 | Transport berbasis usb-serial-for-android, prober kustom |
| `AdapterConnectionPolicy.kt` | 50 | Kebijakan auto-connect dan teardown |
| `UsbAutoConnectState.kt` | 19 | State auto-connect |
| `FtdiConstants.kt` | 46 | VID/PID, request SIO, baud, timing |
| `FtdiSerialDriver.kt` | 411 | Driver FTDI native |

### 3.4 `data/ecu`

| File | Baris | Peran |
|---|---:|---|
| `EcmModel.kt` | 60 | Model record ECM |
| `EcuIdentity.kt` | 237 | Identitas mentah, kanonik, hasil pencocokan |
| `EcuIdCanonicalizer.kt` | 397 | Pemilihan field ID lengkap |
| `EcmDatabase.kt` | 634 | Muat aset, pencarian, resolusi identitas |
| `TempSensorType.kt` | 11 | Jenis sensor suhu |
| `ChecksumType.kt` | 8 | Jenis checksum ROM |

### 3.5 `data/map`

| File | Baris | Peran |
|---|---:|---|
| `MapDocument.kt` | 197 | Model MAP generik, validasi, parser hasil, interpolasi bilinear |
| `MiniJson.kt` | 184 | Pembaca JSON terbatas tanpa dependensi eksternal |

### 3.6 `data/rom`

| File | Baris | Peran |
|---|---:|---|
| `RomImage.kt` | 111 | Pembacaan word, checksum, dan diff blok read-only |
| `CalibrationMap.kt` | 62 | Model tabel kalibrasi (data, sumbu, batas) |
| `ModifiedBlock.kt` | 38 | Blok byte asli/modifikasi |

### 3.7 `core/storage` dan `core/tuning`

| File | Baris | Peran |
|---|---:|---|
| `RevCoreStorage.kt` | 180 | Root penyimpanan, izin, penamaan aman, berkas unik |
| `TuningLog.kt` | 424 | Session log, progress terukur, ekspor teks |

## 4. Entry point

`HondaProtocolEngine` adalah satu-satunya kelas yang perlu dipanggil aplikasi. API utamanya:

```kotlin
class HondaProtocolEngine(usbManager: UsbManager) {
    suspend fun connect(device: UsbDevice, preferNativeFtdi: Boolean = false): Boolean
    suspend fun performKlineWakeup(forceBreakPulse: Boolean = false): Boolean
    suspend fun identifyEcu(onPhase: ((ProtocolPhase) -> Unit)? = null): EcmModel?
    suspend fun sendCommand(command: ByteArray, timeoutMs: Int = 800, sleepMs: Long = 0): ByteArray?
    suspend fun queryLiveData(): LiveTelemetry?
    suspend fun readDtcs(): DtcReadResult
    suspend fun clearDtcs(): ClearDtcResult
}
```

Observable state tersedia sebagai `StateFlow`: `commLifecycle`, `ecuFamily`, `connectionStep`, `isAdapterOpen`, `isConnected`, `isKlineInitialized`, `isEcuIdentified`, `isEcuAlive`, `ignitionState`, `connectedModel`, `ecuIdentity`, `identityResolution`, `liveTelemetry`, `liveDataState`, `activeDtcs`, `dtcReadState`, `commLogs`.

## 5. Status rekonstruksi

| Kelompok | Status | Dasar |
|---|---|---|
| 44 file source utama | `ORIGINAL` | Identik dengan proyek pemilik atau ditulis baru untuk repo publik tanpa materi pihak ketiga |
| 9 file disesuaikan untuk publikasi | `RECONSTRUCTED` | `Evidence.kt`, `HondaEcuProfiles.kt`, `HondaFrameCodec.kt`, `LiveDataProfile.kt`, `HondaProtocolEngine.kt`, `LegacyHondaProtocolEngine.kt`, `EcmDatabase.kt`, `RevCoreStorage.kt`, `RomImage.kt`; lihat [audit/SOURCE_MAPPING.md](../audit/SOURCE_MAPPING.md) §4 |
| `RomImage.kt` | `RECONSTRUCTED` | Adaptasi read-only dari logika perbandingan ROM; tidak memuat fungsi tulis |
| `MapDocument.kt`, `MiniJson.kt` | `ORIGINAL` | Implementasi generik baru untuk format JSON sintetis; bukan salinan pihak ketiga |
| `LegacyHondaProtocolEngine.kt` | `RECONSTRUCTED` | Referensi rollback; query Live Data legacy dihapus |
| Layout Live Data | `UNRESOLVED` | Katalog bawaan kosong; hanya parser generik dan contoh sintetis 0x7A |
| Profil family selain baseline | `UNRESOLVED` | Katalog sengaja kosong |
| Contoh JSON | `REFERENCE` (sintetis) | Dibuat untuk dokumentasi, bukan rekaman ECU |

Label ini menjelaskan asal materi, bukan jaminan bahwa perilaku sudah diuji pada kendaraan.

## 6. Catatan navigasi

- `KlineWireEngine` adalah jalur utama; `KlineTransceiver` dan `KlineEchoFilter` adalah implementasi alternatif yang dipertahankan. Jangan mencampur keduanya dalam satu alur.
- `HondaProtocolEngine` memakai `KlineWireEngine` melalui `EcuSessionManager`.
- Semua operasi wire memakai `commLock`. Jangan memanggil dua operasi secara paralel dari aplikasi.
- `activeProfile` bergantung pada family hasil deteksi header. Jika family belum terbukti, profil bisa kosong dan operasi DTC/Live akan menolak mengirim frame.
- Dokumentasi perilaku ada di `docs/`; jangan menganggap komentar source sebagai spesifikasi kendaraan.
