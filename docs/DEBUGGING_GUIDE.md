# Debugging Guide

Panduan mendiagnosis kegagalan komunikasi, identifikasi, Live Data, dan DTC tanpa
menebak. Prinsipnya: **status hanya boleh dinaikkan bila bukti wire benar-benar terlihat**.
Kegagalan tidak boleh diterjemahkan menjadi nilai nol, daftar kosong, atau "tidak ada
masalah".

## 1. Dua lapisan log

| Lapisan | Sumber | Isi |
|---|---|---|
| Callback logger | Parameter `logger: (LogDirection, String, String)` pada `KlineWireEngine` dan engine | TX/RX hex dan pesan singkat |
| `CommunicationSniffer` | Object global di `core.protocol` | Event terstruktur, statistik, status sesi, timeline, ekspor teks/CSV |
| `android.util.Log` | `FtdiSerialDriver`, `UsbSerialTransport`, `HondaProtocolEngine` | Error buka adapter, klaim interface, endpoint, penutupan transport |

`CommunicationSniffer` menyimpan maksimum 1000 event (event terlama dibuang). Event
memiliki `category`, `stage`, `status`, `hexString`, `asciiString`, `byteCount`,
`description`, `errorDetails`, dan `durationMs`.

## 2. Status sesi dan arti bukti

`CommSessionStatus` memiliki peringkat. Status tertinggi **tidak pernah diturunkan** oleh
observasi berikutnya; status terakhir tetap dicatat terpisah.

| Status | Rank | Bukti yang diperlukan |
|---|---:|---|
| `NONE` | 0 | Belum ada apa pun |
| `USB_OPEN` | 10 | Adapter berhasil dibuka |
| `INIT_ATTEMPTED` | 20 | Inisialisasi K-Line dicoba |
| `LOCAL_ECHO_ONLY` | 30 | Hanya echo TX sendiri yang diterima |
| `ECU_RESPONSE_VALID` | 40 | Frame checksum-valid diterima dari ECU |
| `ECU_ID_VALID` | 50 | Identitas ECU tervalidasi |
| `DTC_READ_CONFIRMED` | 60 | Pembacaan DTC terkonfirmasi |
| `LIVE_DATA_VALID` | 70 | Live data valid ter-parse |
| `OPERATION_CONFIRMED` | 80 | Operasi (misalnya clear) terverifikasi |

Frame negatif (`service = 0x7F`) yang checksum-nya valid tetap dihitung sebagai
`ECU_RESPONSE_VALID`; ECU memang menjawab, hanya menolak service/parameternya. Jangan
menaikkan status ke `ECU_ID_VALID` atau `LIVE_DATA_VALID` hanya karena frame negatif
diterima.

## 3. Statistik yang perlu dibaca lebih dulu

| Field `SnifferStats` | Arti |
|---|---|
| `txFrames` | Frame yang benar-benar ditulis |
| `rxFramesValid` / `rxFramesInvalid` | Frame valid / kandidat yang ditolak |
| `echoCount` | Echo lokal terdeteksi |
| `timeoutCount` | Tidak ada byte dalam jendela waktu |
| `retryCount` | Percobaan ulang |
| `usbErrorCount` | Gagal purge, tulis, buka, atau operasi USB |
| `parseFailureCount` | Frame valid checksum tetapi payload gagal di-parse |
| `initAttempts` / `initStrategies` | Strategi inisialisasi yang dicoba |
| `highestSessionStatus` | Bukti tertinggi yang pernah dicapai |
| `lastSessionStatus` | Observasi terakhir |

Byte TX/RX hanya dihitung sekali pada event `RAW_USB_OUT`/`RAW_USB_IN`, bukan pada event
mirror, agar tidak terjadi penghitungan ganda.

## 4. Status kegagalan wire

`KlineWireEngine.transceive()` mengembalikan `CommandOutcome.Failure(reason, raw)` dengan
status berikut:

| Status | Arti | Pemeriksaan pertama |
|---|---|---|
| `TIMEOUT` | Tidak ada byte sama sekali dalam `timeoutMs` (default 700 ms) | Kunci kontak ON, pin K-Line, ground, wake pulse |
| `LOCAL_ECHO_ONLY` | Hanya frame TX sendiri (utuh/sebagian) yang kembali | Transceiver half-duplex, ECU tidak merespons, baud |
| `CHECKSUM_FAILED` | Kandidat frame lengkap tetapi checksum salah | Baud/level sinyal, noise, batas frame, lihat hex mentah |
| `INCOMPLETE` | Panjang yang dideklarasikan melebihi byte yang diterima | Baud mismatch, RX terpotong, timeout terlalu pendek |
| `NO_VALID_FRAME` | Byte ada tetapi tidak ada kandidat valid | Header/echo/format; periksa hex mentah |
| `FAILED` | Gagal menulis ke endpoint USB | Kabel OTG, izin, adapter terlepas |

Detail tambahan:

- `LOCAL_ECHO_ONLY` tercatat saat body kosong setelah echo, atau body hanya berisi echo
  TX. Engine juga menolak kandidat yang identik dengan frame yang dikirim dalam jendela
  1500 ms.
- Buffer RX yang melebihi 1024 byte membuang byte terlama sampai tersisa 512 byte
  **sebelum parsing**; event `DISCARDED` mencatat jumlah yang dibuang.
- Retry default config adalah satu percobaan (`retries = 1` berarti `attempts = 1`).
  Retry hanya terjadi bila konfigurasi profil menetapkan nilai lebih besar.
- Semua kegagalan di atas juga menghasilkan event `ERROR` dan menaikkan `errorCount`
  bila status mengandung `FAIL`, `ERROR`, `TIMEOUT`, atau `REJECT`, atau bernilai
  `LOCAL_ECHO_ONLY`.

## 5. Alur diagnosis

### 5.1 Adapter tidak terbuka

1. Periksa `usbErrorCount` dan log `UsbSerialTransport`/`FtdiSerialDriver`.
2. Pastikan VID didukung: FTDI `0x0403`, CH340 `0x1A86`, CP210x `0x10C4`,
   Prolific `0x067B`, atau CDC-ACM yang dikenali library.
3. Pastikan izin USB diberikan aplikasi pemanggil. `:core` tidak meminta izin sendiri.
4. Untuk FTDI native, pastikan interface 0 dapat diklaim dan kedua bulk endpoint
   ditemukan.
5. Jika DTR/RTS gagal disetel, adapter mungkin tetap terbuka tetapi transceiver tidak
   aktif.

### 5.2 Hanya echo

- Artinya TX mencapai adapter dan kembali, tetapi ECU tidak menjawab.
- Periksa kunci kontak ON, pinout DLC (Pin 1 +12 V, Pin 2 GND, Pin 3 K-Line), dan
  transceiver half-duplex (misalnya L9637D/MC33290).
- Coba wake pulse; bila adapter menolak BREAK, `usbErrorCount` bertambah dan pulsa tidak
  terkirim.
- Jangan menaikkan timeout sebagai solusi pertama; echo-only bukan masalah waktu tunggu.

### 5.3 Checksum gagal atau frame tidak lengkap

- Bandingkan `rawBytes` pada event `RAW_USB_IN` dengan frame yang diharapkan.
- Baud salah atau noise menghasilkan byte acak; checksum hampir selalu gagal.
- `INCOMPLETE` dengan panjang konsisten biasanya berarti pembacaan terpotong, bukan
  checksum salah.
- Jangan "memperbaiki" checksum atau memotong frame agar lolos. Perbaiki fisik/baud.

### 5.4 Frame valid tetapi payload tidak masuk akal

Gejala: `PARSE_FAILED` atau `LIVE_DATA_UNSUPPORTED`, `parseFailureCount` naik.

- Periksa tabel echo dan offset payload. Respons dengan echo service `0x61`/`0x21`
  pada fallback PID sengaja tidak didekode.
- Nilai di luar rentang valid ditolak per field; nilai tidak diubah menjadi nol.
- Jangan menyimpulkan sensor rusak hanya dari satu field yang ditolak. Periksa field
  lain pada frame yang sama.

### 5.5 Live Data tidak muncul

| Gejala | Kemungkinan | Tindakan |
|---|---|---|
| `UNSUPPORTED_PROFILE` | Tidak ada katalog live data untuk profil tersebut | Daftarkan `LiveTableProfile` milik Anda; built-in catalog sengaja kosong |
| `WAITING_RESPONSE` | Belum pernah ada data valid | Periksa koneksi dan wake |
| `STALE` | Data lama ada, pembacaan terbaru gagal | Jangan tampilkan angka lama sebagai nilai saat ini |
| O2/STF `NOT_AVAILABLE_IN_KOEO` | RPM tidak valid atau nol | Nyalakan mesin; jangan menampilkan O2 sebagai 0 V |
| `AFR = UNSUPPORTED` | ECU tidak mempublikasikan AFR | Jangan menurunkan AFR dari tegangan O2 |
| `TPS_ADC = -1` | Tidak tersedia (fallback PID) | Jangan memakai persentase sebagai pengganti count |

### 5.6 DTC

- `NO_RESPONSE`/kegagalan baca **bukan** "tidak ada DTC". Jangan menampilkan daftar
  kosong.
- Respons baca harus mengikuti urutan `74/01 → 71/20 → 00/00`; echo `0x03`/`0x00`
  bukan data DTC.
- Jumlah DTC di luar `0..20` ditolak, bukan dipotong.
- Clear hanya `SUCCESS_VERIFIED` bila pembacaan ulang benar-benar kosong. ACK header
  `0x02`, `0x72`, `0x7E`, `0x7C` saja tidak cukup.
- Frame negatif `0x7F` berarti ECU menolak; jangan menandai clear berhasil.

### 5.7 Identifikasi ECU

- Pastikan ID kanonik 5/6 byte (10/12 hex) tidak terpotong.
- Exact match selalu menang. Partial match hanya untuk kandidat tidak lengkap dan tidak
  boleh memilih model saat ambigu.
- `HONDA-KLINE` adalah placeholder, bukan model kendaraan.
- Database di repository hanya berisi record sintetis `SAMPLE-`; ketiadaan match bukan
  bukti ECU tidak dikenal di dunia nyata.

## 6. Timeline diagnostik

`CommunicationSniffer.timeline` memiliki 12 stage:

`USB_DEVICE_DETECTED`, `USB_PERMISSION_GRANTED`, `USB_INTERFACE_OPENED`,
`FTDI_INIT_COMPLETED`, `KLINE_INIT_STARTED`, `USB_OUT_ATTEMPTED`,
`USB_OUT_COMPLETED`, `USB_IN_COMPLETED`, `FTDI_STATUS_PROCESSED`,
`PROTOCOL_INIT_COMPLETED`, `ECU_RESPONSE_RECEIVED`, `ECU_IDENTIFIED`.

`getLastFailure()` mengembalikan stage gagal terakhir beserta pesannya. Timeline di-reset
oleh `clear()`/`resetTimeline()`; jangan mereset saat sedang menangkap masalah.

## 7. Ekspor log

- `CommunicationSniffer.exportAsText(ecuName)` menghasilkan ringkasan sesi, timeline,
  checklist DLC, dan event (hex dipotong 32 karakter).
- `exportAsCsv()` menghasilkan seluruh event yang tersimpan.
- `setCapturePaused(true)` menghentikan penambahan event dan statistik, tetapi
  `logEvent()` tetap mengembalikan objek event.
- Log berisi hex komunikasi, bukan kredensial. Tetap jangan mengunggah log ke layanan
  eksternal tanpa persetujuan pemilik; log dapat memuat detail kendaraan.

## 8. Perintah `logcat`

```bash
adb logcat -s FtdiSerialDriver:V UsbSerialTransport:V HondaProtocolEngine:V
```

Untuk menangkap semua lalu menyaring:

```bash
adb logcat -v time > revcore-debug.log
```

Hentikan capture sebelum mengekspor log aplikasi agar urutan kejadian mudah dibaca.

## 9. Checklist reproduksi

1. Catat build/AAR yang dipakai dan versi adapter (VID/PID).
2. Catat posisi kunci kontak, apakah mesin menyala, dan apakah wake pulse dikirim.
3. Simpan `SnifferStats` sebelum dan sesudah percobaan.
4. Simpan frame TX dan RX mentah, bukan hanya pesan error.
5. Catat tabel Live Data aktif (`activeTableHex`) dan state per sensor.
6. Untuk DTC, catat status baca dan status verifikasi clear secara terpisah.
7. Jangan mengubah checksum, memotong frame, atau mengisi nilai default agar gejalanya
   hilang.

## 10. Yang tidak boleh dilakukan saat debugging

- Menganggap `TIMEOUT` sebagai "ECU tidak punya DTC".
- Mengisi sensor yang gagal dengan nol agar tampilan "lengkap".
- Menurunkan `ECU_RESPONSE_VALID` menjadi status lebih rendah hanya karena percobaan
  berikutnya gagal.
- Menyimpulkan kompatibilitas kendaraan dari satu respons sintetis.
- Mengunggah APK, firmware, atau log mentah ke layanan eksternal.
