# Abstraksi Transport

## Tujuan

Memisahkan lapisan diagnostik dari perangkat keras melalui satu antarmuka `Transport`, lalu menunjukkan bahwa lapisan diagnostik yang sama dapat berjalan di atas simulator maupun perekam byte tanpa perubahan kode.

## Yang dipelajari

- Antarmuka `Transport`:
  - `open()` — membuka kanal.
  - `close()` — menutup kanal.
  - `write(byte[] request)` — mengirim satu frame utuh.
  - `read(int timeoutMs)` — menunggu satu respons; `null` berarti tidak ada respons.
- Implementasi `SimulatedTransport`: membungkus logika ECU simulasi seperti sampel 10.
- Pola *decorator* `RecordingTransport`: membungkus transport lain, mencatat setiap byte yang lewat tanpa mengubah perilakunya.
- *Dependency inversion*: `DiagnosticLayer` bergantung pada `Transport`, bukan pada USB, Android, atau kelas simulator.
- Perilaku kontrak: `write` sebelum `open` dan `open` ganda ditolak dengan pesan jelas; frame checksum rusak tidak dibalas.

## Prasyarat

- JDK 17 atau lebih baru (diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android SDK, USB, atau library eksternal.
- Tidak ada kabel, adapter, atau kendaraan yang dibutuhkan.

## Cara menjalankan

Jalankan dari **direktori sampel ini**:

```bash
cd samples/11-abstraksi-transport
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: tidak ada berkas — semua frame ditulis di dalam `src/Main.java`.

Output yang diharapkan:

- Deskripsi transport (`RecordingTransport -> SimulatedTransport ...`).
- Hasil `identify` (`service=0x71 payload=01 02 03`).
- Log perekaman berurutan: `open()`, `TX 5 byte: ...`, `RX 8 byte: ...`, `close()`.
- Skenario tanpa respons yang menghasilkan `NO_RESPONSE`.
- Baris `PASS`/`FAIL`, `Ringkasan: PASS=n FAIL=0`, lalu `HASIL: OK ...` dengan kode keluar 0.

## Batasan

- Tidak ada USB, Bluetooth, UART, atau perangkat keras apa pun yang dipakai. Semua transport berjalan di memori.
- Simulator menjawab seketika; `timeoutMs` tidak benar-benar disimulasikan. Implementasi nyata wajib menghormati batas waktu dan menerapkan pembacaan bertimeout (mis. `SerialInputOutputManager` atau loop baca dengan deadline).
- Tidak ada pengurutan ulang, potongan frame, atau *byte stuffing*; transport menganggap satu `write` = satu frame dan satu `read` = satu frame lengkap.
- Tidak ada thread atau sinkronisasi; contoh ini berjalan single-thread.

### Pemetaan ke USB serial nyata — REFERENCE

Bagian ini hanya **referensi** untuk pengembang berikutnya. Tidak ada satu baris pun kode USB dijalankan atau diuji pada sampel ini.

| Komponen aplikasi | Peran |
|---|---|
| Aplikasi pemanggil (*caller app*) | Memegang `UsbManager`, meminta izin perangkat (`UsbManager.requestPermission`), lalu menyerahkan `UsbDeviceConnection` yang sudah diotorisasi. |
| `UsbSerialTransport implements Transport` | `open()` membuka antarmuka USB dan menetapkan baud/8N1; `write()` memanggil bulk transfer; `read()` membaca dari buffer yang diisi thread pembaca sampai `timeoutMs`. |
| Lapisan diagnostik | Tidak berubah sama sekali; hanya menerima `Transport`. |

Pemisahan ini disengaja: alur izin USB dimiliki oleh aplikasi pemanggil, sehingga pustaka diagnostik tidak perlu tahu apa pun soal Android.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan `FailingTransport` yang selalu melempar `TransportException` pada `read`, lalu pastikan lapisan diagnostik menanganinya.
2. Tambahkan dekorator `CountingTransport` yang menghitung jumlah byte TX/RX per sesi.
3. Buat `read` pada `SimulatedTransport` benar-benar menunggu (mis. `Thread.sleep`) lalu kembalikan `null` bila `timeoutMs` terlalu kecil.
4. Tulis kerangka `UsbSerialTransport` yang **hanya** berisi method bertipe `byte[]` dan belum memakai API Android, sebagai latihan pemetaan.
5. Ubah `DiagnosticLayer` agar mengirim tiga kandidat baca DTC berurutan dan berhenti pada respons valid pertama.
