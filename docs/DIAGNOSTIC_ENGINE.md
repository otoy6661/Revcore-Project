# DIAGNOSTIC ENGINE

`HondaProtocolEngine` adalah fasad diagnostik. Dokumen ini menjelaskan API, state, threading, dan batas yang dijaga engine.

## 1. Tanggung jawab

Engine:

- membuka dan menutup transport,
- menjalankan wake pulse,
- mengurutkan query identifikasi,
- memilih profil berdasarkan family,
- mengirim Live Data dan DTC,
- menerbitkan telemetry, hasil, log, dan status,
- menjaga satu operasi wire pada satu waktu.

Engine **tidak**:

- memilih device USB atau meminta izin,
- menampilkan UI,
- menyimpan file pengguna,
- menulis ROM atau melakukan flashing,
- mengklaim kompatibilitas kendaraan.

## 2. API publik

```kotlin
class HondaProtocolEngine(private val usbManager: UsbManager) {

    suspend fun connect(device: UsbDevice, preferNativeFtdi: Boolean = false): Boolean
    suspend fun performKlineWakeup(forceBreakPulse: Boolean = false): Boolean
    suspend fun identifyEcu(onPhase: ((ProtocolPhase) -> Unit)? = null): EcmModel?
    suspend fun sendCommand(command: ByteArray, timeoutMs: Int = 800, sleepMs: Long = 0): ByteArray?
    suspend fun queryLiveData(): LiveTelemetry?
    suspend fun readDtcs(): DtcReadResult
    suspend fun clearDtcs(): ClearDtcResult
}
```

`sendCommand` adalah jalur manual untuk request yang sudah disusun pemanggil. Engine tetap memvalidasi frame respons melalui wire engine, tetapi tidak menafsirkan payload.

## 3. State yang dapat diamati

| StateFlow | Tipe | Arti |
|---|---|---|
| `commLifecycle` | `CommLifecycle` | Lifecycle sesi |
| `ecuFamily` | `HondaEcuFamily` | Family hasil routing header |
| `connectionStep` | `ConnectionPipelineStep` | Tahap koneksi |
| `isAdapterOpen` | `Boolean` | Port terbuka |
| `isConnected` | `Boolean` | Sesi aktif |
| `isKlineInitialized` | `Boolean` | Ada transaksi valid |
| `isEcuIdentified` | `Boolean` | ID cocok database |
| `isEcuAlive` | `Boolean` | ECU menjawab |
| `ignitionState` | `IgnitionState` | Status link/kontak |
| `connectedModel` | `EcmModel?` | Model terbit atau placeholder |
| `ecuIdentity` | `EcuIdentity?` | Identitas mentah dan kanonik |
| `identityResolution` | `IdentityResolution?` | Hasil pencocokan |
| `identificationError` | `String?` | Alasan ID tidak diterbitkan |
| `liveTelemetry` | `LiveTelemetry` | Nilai terakhir |
| `liveDataState` | `LiveDataState` | Status Live Data |
| `activeDtcs` | `List<HondaDtc>` | DTC terakhir yang terbaca |
| `dtcReadState` | `DtcReadState` | Status baca DTC |
| `dtcReadDetail` | `String` | Detail baca DTC |
| `commLogs` | `List<CommLogEntry>` | Log komunikasi (maks. 500) |

## 4. Connection pipeline

`ConnectionPipelineStep` menandai kemajuan:

```text
ADAPTER_DETECTED
  → PERMISSION_GRANTED
  → INTERFACE_CLAIMED
  → FTDI_INITIALIZED
  → TRANSFER_LAYER_ACTIVE
  → KLINE_ATTEMPTED
  → ECU_RESPONSE_RECEIVED
  → PROTOCOL_INITIALIZED
  → DIAGNOSTIC_READY
```

`FLASH_VERIFIED` ada di enum, tetapi bukan bagian dari alur diagnostik `:core`.

## 5. Fase identifikasi

`ProtocolPhase` diberikan melalui callback `onPhase`:

| Phase | Arti |
|---|---|
| `FAST_INIT` | Wake/init dimulai |
| `SEARCHING_IDENTIFICATION` | Query ID baseline |
| `DIAGNOSTIC_FALLBACK` | Query `0x1A`/presence |
| `ECU_RESPONDED` | Ada frame valid |
| `IDENTIFIED` | ID cocok database |
| `NO_RESPONSE` | Tidak ada frame valid |

Callback hanya untuk progres. Keputusan akhir tetap dari state dan hasil return.

## 6. Threading

- Semua operasi publik berjalan di `Dispatchers.IO`.
- `commLock` menserialkan operasi level engine.
- `EcuSessionManager` memiliki mutex transaksi tambahan.
- `connect()` dan `identifyEcu()` memakai `connectionGeneration`. Hasil dari koneksi lama tidak boleh diterbitkan setelah reconnect.

Konsekuensi: pemanggil tidak perlu membuat mutex sendiri, tetapi tetap harus menunggu `suspend` selesai sebelum memulai operasi berikutnya.

## 7. Arti kegagalan

| Kejadian | State | Bukan arti |
|---|---|---|
| `connect()` false | Adapter tidak siap | ECU rusak |
| `identifyEcu()` null tanpa frame | `NO_RESPONSE` | Kontak pasti OFF |
| Frame valid tanpa ID | `ECU_RESPONDED`, `isEcuIdentified=false` | Model teridentifikasi |
| `readDtcs()` `NoResponse` | Status sesi `DTC_READ_CONFIRMED` tidak tercapai | Tidak ada DTC |
| `queryLiveData()` null | `WAITING_RESPONSE`/`STALE` | Semua sensor nol |
| `ignitionState=LINK_LOST` | Tidak ada balasan valid | Kontak OFF terbukti |

`ignitionState=OFF_OR_DISCONNECTED` hanya boleh dipakai jika ada bukti terpisah; engine tidak menyimpulkannya dari timeout.

## 8. Log dan bukti

- `commLogs` menyimpan arah (`TX`, `RX`, `SYS`, `ERR`), hex, dan deskripsi.
- `CommunicationSniffer` menyimpan event terstruktur, statistik, dan timeline.
- `evidenceInventory` mengembalikan registry status bukti elemen komunikasi.
- `CommSessionStatus` memberi peringkat bukti sesi dari `USB_OPEN` sampai `OPERATION_CONFIRMED`.

Peringkat bukti tidak boleh dinaikkan tanpa peristiwa yang sesuai. Contoh: `LIVE_DATA_VALID` hanya setelah frame Live Data valid dan parsed.

## 9. Model telemetry

`LiveTelemetry` memuat nilai dan `parameterStates`. Nilai default 0 **bukan** nilai sensor. Pemanggil wajib memeriksa:

```kotlin
telemetry.stateOf(LiveSensor.RPM) == LiveParameterState.VALID
telemetry.isRpmAvailable
```

bukan hanya `engineRpm != 0`.

`isDataValid` berarti minimal satu parameter lolos pemeriksaan. RPM tidak wajib; frame key-on/engine-off tetap dapat valid.

## 10. Legacy engine

`LegacyHondaProtocolEngine` adalah salinan engine lama:

- disertakan untuk referensi rollback,
- tidak dipakai `HondaProtocolEngine`,
- tidak boleh dijalankan bersamaan pada transport yang sama,
- tidak menjadi target dokumentasi perilaku baru.

## 11. Batas publikasi

Dokumen ini hanya mencakup orkestrasi diagnostik. Application Layer dan Security/Integrity tidak dijelaskan di sini. Lihat [SECURITY_AND_INTEGRITY.md](SECURITY_AND_INTEGRITY.md) untuk pernyataan cakupan.
