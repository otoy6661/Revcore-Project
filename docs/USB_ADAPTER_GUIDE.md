# PANDUAN ADAPTER USB

Dokumen ini menjelaskan penanganan adapter USB pada `:core`: chipset yang dikenali, urutan membuka port, perbedaan transport library dan driver FTDI native, serta batas pengujian.

## 1. Dua jalur transport

| Jalur | Kelas | Kapan dipakai |
|---|---|---|
| Library | `UsbSerialLibraryTransport` | Default untuk semua adapter |
| FTDI native | `FtdiSerialDriver` | Hanya jika `preferNativeFtdi=true` dan VID = `0x0403` |

Keduanya mengimplementasikan `UsbTransport`:

```kotlin
interface UsbTransport {
    val isOpen: Boolean
    val currentBaudRate: Int
    val driverName: String
    val chipTypeDescription: String

    fun open(): Boolean
    fun close()
    fun setBaudRate(baud: Int): Boolean
    fun setBreak(enable: Boolean): Boolean
    fun purgeRxTx(): Boolean
    fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean

    suspend fun write(data: ByteArray, timeoutMs: Int = 1000): Int
    suspend fun read(bufferSize: Int = 256, timeoutMs: Int = 100): ByteArray
}
```

Layer di atas tidak boleh mengetahui apakah byte berasal dari FTDI, CH340, atau CP210x.

## 2. Chipset yang dikenali

Prober kustom mendaftarkan VID/PID berikut:

| Chipset | VID | PID | Driver |
|---|---:|---:|---|
| FTDI FT232R | `0x0403` | `0x6001` | `FtdiSerialDriver` (native) atau library |
| FTDI FT2232 | `0x0403` | `0x6010` | `FtdiSerialDriver` |
| FTDI FT232H | `0x0403` | `0x6014` | `FtdiSerialDriver` |
| FTDI FT4232H | `0x0403` | `0x6011` | `FtdiSerialDriver` |
| WCH CH340 | `0x1A86` | `0x7523` | `Ch34xSerialDriver` |
| WCH CH341 | `0x1A86` | `0x5523` | `Ch34xSerialDriver` |
| WCH CH34x lain | `0x1A86` | `0x7522` | `Ch34xSerialDriver` |
| Silicon Labs CP2102 | `0x10C4` | `0xEA60` | `Cp21xxSerialDriver` |
| Silicon Labs CP2105 | `0x10C4` | `0xEA70` | `Cp21xxSerialDriver` |
| Silicon Labs CP2108 | `0x10C4` | `0xEA71` | `Cp21xxSerialDriver` |
| Prolific PL2303 | `0x067B` | `0x2303` | `ProlificSerialDriver` |
| Prolific lain | `0x067B` | `0x23A3` | `ProlificSerialDriver` |

Prober library dijalankan sebagai fallback untuk perangkat yang tidak ada di tabel kustom. Perangkat itu tidak memicu auto-connect; pembukaan hanya berhasil bila `prober.probeDevice()` mengenalinya.

Catatan: `AdapterConnectionPolicy.SUPPORTED_VENDOR_IDS` hanya memuat `0x0403`, `0x1A86`, `0x10C4`, dan `0x067B`. VID CDC-ACM di luar daftar itu tidak memicu auto-connect, walau driver library mungkin tersedia.

## 3. Urutan membuka port

`UsbSerialLibraryTransport.open()`:

```text
1. cek izin USB; jika belum ada → gagal
2. probe driver (tabel kustom, lalu prober default)
3. pilih port index 0
4. UsbManager.openDevice()
5. port.open(connection)
6. setParameters(10400, 8, 1, 0)     // 8N1
7. DTR = true, RTS = true
8. jika FTDI: latency timer 2 ms + flow control None
9. purge buffer RX/TX
10. tandai isOpen = true
```

`FtdiSerialDriver.open()`:

```text
1. cek izin USB
2. openDevice()
3. claim interface 0
4. cari bulk endpoint IN dan OUT
5. reset SIO
6. disable flow control
7. latency timer 2 ms
8. 8N1, 10400 bps
9. DTR/RTS high
10. purge RX/TX
```

Jika salah satu langkah kritis gagal, `open()` mengembalikan `false` dan tidak ada transaksi K-Line yang dikirim.

## 4. Izin USB

`core` tidak meminta izin sendiri. Aplikasi pemanggil wajib:

1. mendeteksi `USB_DEVICE_ATTACHED`,
2. memanggil `UsbManager.requestPermission`,
3. meneruskan `UsbDevice` yang sudah diizinkan ke `connect()`.

`connect()` memeriksa `usbManager.hasPermission(device)`. Tanpa izin, hasilnya `false` dan log mencatat `USB_PERMISSION_MISSING`.

## 5. Auto-connect

`AdapterConnectionPolicy` adalah logika murni tanpa tipe Android:

| Kondisi | Aksi |
|---|---|
| Vendor tidak didukung | `IGNORE_UNSUPPORTED` |
| Sudah terhubung | `NO_OP_ALREADY_CONNECTED` |
| Auto-connect dijeda atau izin ditolak | `WAIT_FOR_USER` |
| Izin ada | `OPEN_ADAPTER` |
| Izin belum ada | `REQUEST_PERMISSION` |

`shouldTeardown()` memutus sesi jika device yang dilepas adalah device terpilih, atau jika device terpilih tidak lagi terpasang.

`UsbAutoConnectState` memisahkan “adapter terbuka” dari “ECU teridentifikasi”. Adapter bisa `OPENING_ADAPTER` sementara ECU masih `SEARCHING_ECU`.

## 6. Parameter serial

| Parameter | Nilai |
|---|---|
| Baud awal | 10400 |
| Fallback identifikasi | 9600 |
| Data bits | 8 |
| Stop bits | 1 |
| Parity | None |
| Flow control | None |
| DTR | High |
| RTS | High |
| FTDI latency | 2 ms |

Baud 38400 dan 57600 tersedia sebagai konstanta, tetapi bukan jalur identifikasi baseline. Jangan memakainya tanpa bukti dari perangkat.

## 7. Perbedaan library dan FTDI native

| Aspek | Library | FTDI native |
|---|---|---|
| Dependensi | `usb-serial-for-android` | Control/bulk transfer langsung |
| Dukungan chipset | FTDI, CH34x, CP21xx, Prolific, CDC | FTDI saja |
| Kontrol latency | Control transfer manual | SIO request langsung |
| Status line | Terbatas | Dua byte status modem/line |
| Risiko | Lebih teruji di banyak adapter | Lebih spesifik, perlu pengujian hardware |

Driver native membaca dua byte status di awal paket dan memisahkannya dari payload. Jika panjang kurang dari dua byte, hasilnya dianggap tidak valid. Ini mencegah status line disalahartikan sebagai data K-Line.

## 8. Hardware dan wiring

Yang perlu diperhatikan (panduan umum, bukan spesifikasi kendaraan):

- Gunakan adapter USB-to-K-Line yang sesuai level tegangan kendaraan; jangan menghubungkan pin USB langsung ke bus K-Line.
- Pastikan ground adapter dan ground kendaraan terhubung.
- DTR/RTS high adalah default; beberapa adapter memakai pin ini untuk level shifter.
- Panjang kabel berlebih dan koneksi longgar adalah penyebab umum frame tidak lengkap.
- Jangan mengubah level tegangan atau pinout tanpa dokumentasi adapter.

`core` tidak mengetahui topologi kabel. Jika adapter tidak mengirim BREAK, `setBreak()` mengembalikan `false`; engine mencatatnya dan tetap mencoba query.

## 9. Troubleshooting

| Gejala | Pemeriksaan |
|---|---|
| `USB_PERMISSION_MISSING` | Izin belum diberikan; panggil `requestPermission` |
| `UNSUPPORTED_SERIAL_CHIPSET` | VID/PID tidak ada di prober; cek chipset |
| `NO_SERIAL_PORT` | Driver tidak mengekspos port |
| `USB_OPEN_DEVICE_FAILED` | Device dipakai proses lain atau koneksi USB bermasalah |
| `BREAK_FAILED` | Adapter tidak mendukung `setBreak`; cek driver |
| `PURGE_FAILED` | Buffer hardware tidak mendukung purge; tetap bisa berjalan dengan risiko byte lama |
| Hanya echo | ECU tidak menjawab; cek kontak, K-Line, dan ground |
| Frame tidak lengkap | Baud/timing atau kabel; lihat `DEBUGGING_GUIDE.md` |

## 10. Batas pengujian

- Prober dan kebijakan koneksi diuji dengan unit test tanpa hardware.
- Pembukaan port nyata, permission Android, dan perilaku listrik adapter **belum diverifikasi** di repo ini.
- Tidak ada klaim bahwa semua adapter dengan VID yang sama berperilaku identik.
