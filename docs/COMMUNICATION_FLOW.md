# ALUR KOMUNIKASI

Dokumen ini menggambarkan urutan operasi dari membuka adapter sampai membaca Live Data dan DTC, termasuk jalur gagal. Diagram memakai teks agar mudah ditelusuri bersama log.

## 1. Connect

```text
connect(device)
  ├─ disconnect() (bersihkan sesi lama)
  ├─ pilih transport:
  │    ├─ preferNativeFtdi && VID 0x0403 → FtdiSerialDriver
  │    └─ lainnya → UsbSerialLibraryTransport
  ├─ transport.open()
  │    └─ gagal → return false, status adapter tidak siap
  ├─ session.attach(transport)
  ├─ isAdapterOpen = true, isConnected = true
  ├─ isKlineInitialized = false
  ├─ isEcuIdentified = false
  ├─ connectionStep = INTERFACE_CLAIMED
  ├─ wake pulse 70/130 ms @10400
  ├─ connectionStep = TRANSFER_LAYER_ACTIVE
  └─ return true
```

`connect()` **tidak** mengidentifikasi ECU. Koneksi adapter dan identifikasi ECU adalah dua status berbeda.

## 2. Wake pulse

```text
performKlineWakeup()
  ├─ set baud 10400
  ├─ purge RX/TX
  ├─ reset antrean TX
  ├─ BREAK ON  → delay 70 ms
  ├─ BREAK OFF → delay 130 ms
  └─ purge RX/TX
```

Jika `setBreak()` gagal, operasi tetap selesai dengan `breakAccepted=false`. Engine mencatat kegagalan dan melanjutkan; tidak ada klaim bahwa wake berhasil.

## 3. Identifikasi ECU

```text
identifyEcu()
  ├─ reset status identifikasi
  ├─ wake pulse
  ├─ baselineIdentifyAtBaud(10400)
  │    ├─ tester present
  │    ├─ query 0x00/F0
  │    ├─ query 0x72/00
  │    └─ query 0x71/00
  │
  ├─ ada frame valid?
  │    ├─ ID lengkap ditemukan → canonicalize → resolve database
  │    └─ frame valid tanpa ID → lanjut
  │
  ├─ tidak ada frame valid → baselineIdentifyAtBaud(9600)
  ├─ masih tidak ada → set 10400 + wake ulang → baselineIdentifyAtBaud(10400)
  ├─ masih tidak ada → query 0x1A (dengan parameter, lalu tanpa parameter)
  ├─ masih tidak ada → probe presence:
  │    ├─ FE 04 72 8C
  │    ├─ 72 05 00 F0 99
  │    └─ 72 05 72 00 17
  │
  └─ hasil:
       ├─ ID lengkap + cocok database → model diterbitkan
       ├─ frame valid tetapi ID tidak lengkap → placeholder "belum teridentifikasi"
       └─ tidak ada frame valid → null, ignition/LINK_LOST
```

Aturan:

- Query `0x1A` hanya dipakai setelah seluruh query baseline gagal.
- Probe presence yang menerima frame valid membuktikan ECU menjawab, tetapi **tidak** menghasilkan ID.
- ID yang tidak lengkap tidak dipotong menjadi ID pendek. Ia disimpan sebagai kandidat, bukan model.
- Nama model hanya diterbitkan untuk pencocokan `EXACT` tunggal.

## 4. Live Data

```text
queryLiveData()
  ├─ tidak ada transport → UNAVAILABLE
  ├─ profil family tidak punya katalog live → UNSUPPORTED_PROFILE
  ├─ coba tabel cache (jika ada)
  │    ├─ berhasil → pakai
  │    └─ gagal 3× → buang cache
  ├─ probe 17 → 13 → 11 → 10
  ├─ tidak ada → tabel darurat 16
  ├─ tidak ada → PID fallback 0x21 (cooldown 10 s)
  ├─ frame valid
  │    ├─ echo tabel harus cocok
  │    ├─ payload diparse sesuai layout
  │    ├─ field di luar rentang dibuang
  │    ├─ extension 16/20/D1 dirotasi satu per siklus
  │    └─ LiveTelemetry diterbitkan
  └─ tidak ada frame valid
       ├─ data lama → STALE
       └─ belum ada data → WAITING_RESPONSE
```

Urutan probe dan timeout mengikuti katalog `HondaEcuProfiles`. Satu tabel yang berhasil disimpan sebagai cache agar polling berikutnya tidak mencoba semua tabel.

## 5. Extension table

```text
refreshExtensionTables()
  ├─ batasi satu tabel per 400 ms
  ├─ pilih 16/20/D1 secara rotasi
  ├─ lewati tabel yang gagal 2× selama 15 s
  ├─ berhasil → ganti nilai sensor tabel tersebut
  └─ gagal → tarik nilai lama tabel tersebut
```

Nilai primary selalu menang atas extension untuk sensor yang sama. Extension tidak boleh menimpa RPM/TPS dari tabel utama.

## 6. DTC read

```text
readDtcs()
  ├─ profil tidak punya katalog DTC → NoResponse + alasan
  ├─ untuk setiap query 74/01, 71/20, 00/00:
  │    ├─ frame valid?
  │    ├─ respons negatif? → coba berikutnya
  │    ├─ payload diparse
  │    ├─ kode dikenal → deskripsi
  │    └─ kode tidak dikenal → kode mentah, bukan P-code karangan
  └─ semua gagal → NO_RESPONSE, daftar lama tidak diubah
```

Daftar DTC lama **tidak** dikosongkan saat pembacaan gagal. `OK_EMPTY` hanya diterbitkan jika frame valid benar-benar tidak memuat kode.

## 7. DTC clear

```text
clearDtcs()
  ├─ tidak ada transport → NO_ADAPTER
  ├─ profil tidak punya katalog clear → UNSUPPORTED_FAMILY
  ├─ kirim kandidat clear sampai ada ACK valid
  ├─ ACK diterima → tunggu 250 ms
  ├─ baca ulang DTC
  │    ├─ kosong → SUCCESS_VERIFIED
  │    ├─ masih ada kode → RE_DETECTED_ACTIVE_FAULT
  │    ├─ gagal baca → CLEARED_UNVERIFIED
  │    └─ format tidak dikenal → CLEARED_UNVERIFIED
  └─ tidak ada ACK → REJECTED_OR_TIMEOUT
```

`SUCCESS_VERIFIED` berarti ECU mengakui clear dan tidak ada DTC yang terbaca lagi. Ini tidak menjamin lampu MIL padam; siklus kontak dan uji jalan tetap diperlukan.

## 8. Kegagalan dan pemulihan

```text
transaksi gagal
  ├─ markFailure()
  ├─ hitungan mencapai ambang
  │    ├─ state ECU_READY/MONITORING → SUSPECTED_LOSS
  │    ├─ isEcuAlive = false
  │    ├─ isKlineInitialized = false
  │    ├─ ignition = LINK_LOST
  │    └─ live data lama ditandai STALE
  └─ operasi berikutnya dapat memulai probe/recovery
```

`LINK_LOST` berarti tidak ada balasan valid. Itu bukan bukti kontak OFF. Engine tidak boleh menampilkan “kontak mati” tanpa bukti terpisah.

## 9. Urutan yang dilarang

- Menjalankan `queryLiveData()` dan `readDtcs()` bersamaan.
- Memakai `LegacyHondaProtocolEngine` dan `HondaProtocolEngine` pada transport yang sama.
- Mengirim request family yang katalognya kosong.
- Menganggap `connect()` sukses sebagai ECU teridentifikasi.
- Menganggap `OK_EMPTY` dari frame yang tidak valid.

## 10. Ringkasan state

| Kejadian | Lifecycle | `isEcuIdentified` |
|---|---|---|
| Port dibuka | `ADAPTER_OPEN` | false |
| Wake selesai | `LINE_READY` | false |
| Query identifikasi mulai | `IDENTIFYING` | false |
| Frame valid pertama | `ECU_READY` | false |
| ID cocok database | `ECU_READY` | true |
| Live data aktif | `MONITORING` | tetap |
| Dua kegagalan | `SUSPECTED_LOSS` | tidak diubah otomatis |
| Disconnect | `DISCONNECTED` | false |
