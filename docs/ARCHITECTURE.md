# ARCHITECTURE

Dokumen ini menjelaskan arsitektur modul publik RevCore, hubungan antar-modul, dan batas yang sengaja tidak dipublikasikan.

## 1. Prinsip arsitektur

1. **Pemisahan transport dan protokol.** Driver USB hanya memindahkan byte. Framing, echo, timeout, checksum, dan interpretasi berada di layer di atasnya.
2. **Satu transaksi pada satu waktu.** `EcuSessionManager` memegang mutex tunggal. Identifikasi, Live Data, DTC, dan recovery tidak boleh mengirim frame bersamaan.
3. **Tidak ada keberhasilan tanpa bukti.** Respons harus checksum-valid dan panjangnya cocok. Nilai sensor di luar rentang dibuang, bukan diubah menjadi nol. DTC yang gagal dibaca bukan "tidak ada DTC".
4. **Keluarga ECU dipisahkan.** Header respons hanya memilih profil routing, bukan mengklaim model kendaraan. Profil yang belum terbukti dibiarkan kosong.
5. **Label bukti melekat pada data.** `EvidenceStatus` membedakan bukti binary, xref, rekonstruksi, hipotesis, dan unresolved.

## 2. Diagram layer

```text
┌──────────────────────────────────────────────────────────────┐
│ Aplikasi pemanggil (tidak dipublikasikan)                    │
│ - izin USB, lifecycle, UI, kebijakan integritas               │
└───────────────────────────────┬──────────────────────────────┘
                                │ memanggil API publik
┌───────────────────────────────▼──────────────────────────────┐
│ HondaProtocolEngine (protocol/)                              │
│ - connect / identify / live / DTC / clear / disconnect        │
│ - StateFlow status, log, telemetry                            │
└───────────────┬───────────────────────────────┬──────────────┘
                │                               │
┌───────────────▼──────────────┐  ┌─────────────▼──────────────┐
│ EcuSessionManager (comm/)    │  │ Model & katalog (data/ecu) │
│ - lifecycle, mutex, family   │  │ - EcmModel, EcuIdentity    │
└───────────────┬──────────────┘  │ - EcmDatabase, canonicalizer│
                │                 └────────────────────────────┘
┌───────────────▼──────────────┐
│ KlineWireEngine (comm/)      │
│ - wake, purge, TX, RX scan   │
│ - echo, timeout, retry       │
└───────┬──────────────────────┘
        │ UsbTransport
┌───────▼──────────────────────────────────────────────────────┐
│ USB layer                                                    │
│ - UsbSerialLibraryTransport: FTDI/CH340/CP210x/Prolific/CDC  │
│ - FtdiSerialDriver: kontrol SIO FTDI langsung                │
└──────────────────────────────────────────────────────────────┘
```

## 3. Tanggung jawab per package

| Package | Tanggung jawab | Tidak bertanggung jawab atas |
|---|---|---|
| `core.usb` | Membuka device, permission check, memilih driver, 8N1, DTR/RTS, purge, read/write | Framing, checksum, arti byte |
| `core.ftdi` | Kontrol FTDI native: reset SIO, latency, baud divisor, break, bulk endpoint | Deteksi ECU, protokol Honda |
| `core.comm` | Framing, checksum, echo, scan frame, wake pulse, lifecycle sesi, profil Live Data | UI, penyimpanan, dataset kendaraan |
| `core.protocol` | Alur Honda PGM-FI, identifikasi, DTC, live data, telemetry, log | Membuka USB, memilih device |
| `core.protocol.legacy` | Salinan engine lama sebagai referensi rollback | Jalur utama |
| `data.ecu` | Model ECM, identitas, kanonikalisasi, pencocokan database | Komunikasi wire |

## 4. Alur data

### 4.1 Transaksi wire

```text
request ByteArray
  → KlineWireEngine.transceive
      → purge RX/TX
      → drain byte lama
      → register TX (untuk deteksi echo)
      → write (satu frame atau paced)
      → read sampai timeout
      → strip echo di awal
      → scan kandidat frame
          - header dikenal?
          - declared length cocok?
          - bukan echo TX sendiri?
          - checksum valid?
      → CommandOutcome.Frame / Failure
  → EcuSessionManager
      → markAlive / markFailure
      → deteksi family dari header
```

### 4.2 Identifikasi

```text
identifyEcu()
  → wake pulse 70/130 ms @10400
  → tester present
  → query ID 00/F0, 72/00, 71/00 (probe live nonaktif pada katalog publik)
  → jika tidak ada frame valid: ulang @9600
  → jika masih tidak ada: wake ulang + @10400
  → jika masih tidak ada: query 0x1A, lalu probe presence
  → canonicalisasi payload
  → EcmDatabase.resolveIdentity
      EXACT / PARTIAL_MATCH / AMBIGUOUS / NOT_FOUND
  → publish model hanya untuk EXACT
```

### 4.3 Live Data

```text
queryLiveData()
  → tabel cache (jika ada)
  → probe 17 → 13 → 11 → 10
  → tabel darurat 16
  → fallback PID 0x21 (cooldown 10 s)
  → parse payload sesuai profil
  → buang field di luar rentang
  → tandai sensor engine-running-only sebagai NOT_AVAILABLE_IN_KOEO
  → AFR selalu UNSUPPORTED
  → LiveTelemetry
```

### 4.4 DTC

```text
readDtcs()
  → katalog DTC profil aktif
  → 74/01 → 71/20 → 00/00
  → frame valid? service 0x7F?
  → parse count-prefixed atau pasangan kode
  → HondaDtc / kode mentah
  → OK_CODES / OK_EMPTY / NO_RESPONSE

clearDtcs()
  → 60/01 → 74/02 → 04
  → ACK header valid?
  → tunggu 250 ms
  → baca ulang
  → SUCCESS_VERIFIED hanya jika kosong
```

## 5. State dan lifecycle

`CommLifecycle`:

```text
DISCONNECTED
  → ADAPTER_OPEN
  → LINE_READY
  → INITIALIZING
  → IDENTIFYING
  → ECU_READY
  → MONITORING
  → SUSPECTED_LOSS
  → RECOVERY_PROBE
  → DISCONNECTING
```

Catatan:

- `ECU_READY` berarti ada frame valid, belum tentu identitas cocok database.
- `MONITORING` dimulai setelah Live Data berhasil diparse.
- Kegagalan berulang memindahkan sesi ke `SUSPECTED_LOSS`; kegagalan baca tidak boleh disimpulkan sebagai kontak OFF.
- `disconnect()` menaikkan `connectionGeneration`, sehingga koroutine identifikasi lama tidak dapat mempublikasikan hasil.

## 6. Model status yang terlihat pemanggil

| StateFlow | Arti |
|---|---|
| `connectionStep` | Tahap pipeline koneksi (`ConnectionPipelineStep`) |
| `isAdapterOpen` | Port USB terbuka |
| `isConnected` | Sesi adapter aktif |
| `isKlineInitialized` | Ada bukti frame valid |
| `isEcuIdentified` | ID cocok **persis** database |
| `isEcuAlive` | Respons valid terakhir |
| `ignitionState` | `UNKNOWN`, `CHECKING`, `ON_DETECTED`, `LINK_LOST` |
| `connectedModel` | Model atau placeholder belum terverifikasi |
| `ecuIdentity` | Identitas mentah + kanonik + payload |
| `identityResolution` | Hasil pencocokan dan kandidat |
| `identificationError` | Kesalahan decode yang spesifik |
| `liveTelemetry` | Nilai dan status per sensor |
| `liveDataState` | `UNAVAILABLE`, `WAITING_RESPONSE`, `ACTIVE`, `STALE`, `INVALID_PAYLOAD`, `UNSUPPORTED_PROFILE` |
| `activeDtcs` | Daftar DTC hasil bacaan terakhir |
| `dtcReadState` | `NOT_PERFORMED`, `OK_CODES`, `OK_EMPTY`, `NO_RESPONSE` |
| `commLogs` | Log TX/RX/SYS/ERR maksimum 500 entri |

## 7. Keputusan struktur repository

Base work order menyarankan folder konseptual `core/usb`, `core/kline`, `core/protocol`, dan seterusnya. Repository ini **mempertahankan package asli** `com.revcore.android.core.comm`, `core.protocol`, `core.ftdi`, dan `core.usb` karena:

1. Pemetaan R8 dan bukti APK memakai nama package tersebut.
2. Memindahkan file berisiko memutus import dan membuat build tidak lagi mencerminkan APK.
3. Base work order mengizinkan mempertahankan struktur asli jika pemindahan berisiko merusak build.

Pemetaan konsep ke package:

| Konsep base | Package aktual |
|---|---|
| `core/usb` | `com.revcore.android.core.usb` |
| `core/ftdi` | `com.revcore.android.core.ftdi` |
| `core/serial` | `core.usb` + `core.ftdi` |
| `core/kline` | `core.comm` (wire, frame, echo) + `core.protocol` (packet) |
| `core/protocol` | `com.revcore.android.core.protocol` |
| `core/ecu` | `com.revcore.android.data.ecu` |
| `core/diagnostics` | `HondaProtocolEngine` + `core.protocol` |
| `core/dtc` | `HondaDtcDatabase`, `HondaDtc`, `DtcReadResult` |
| `core/live_data` | `LiveDataProfile`, `LiveTelemetry`, `LivePollPolicy` |
| `core/communication` | `core.comm` |

## 8. Batas publikasi

Tidak ada dependensi dari `:core` ke package Application Layer atau Security/Integrity. `EcmDatabase` di repository ini membaca aset langsung tanpa pemeriksaan integritas tambahan. Aplikasi yang mengintegrasikan pustaka bertanggung jawab menentukan kebijakan integritasnya sendiri.

Untuk penjelasan pengecualian, lihat [SECURITY_AND_INTEGRITY.md](SECURITY_AND_INTEGRITY.md).

## 9. Keterbatasan arsitektur

- Tidak ada abstraksi CAN/ISO-TP; fokus pada K-Line Honda.
- Tidak ada operasi flash/remap.
- Profil family-specific belum lengkap.
- `LegacyHondaProtocolEngine` disertakan sebagai referensi, bukan jalur aktif.
- Semua pengujian berjalan di JVM; perilaku USB nyata bergantung pada driver dan perangkat.
