# MESIN KOMUNIKASI

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini menjelaskan `KlineWireEngine`, `EcuSessionManager`, dan cara keduanya dipakai `HondaProtocolEngine`. Tujuannya agar developer memahami siapa yang memiliki buffer, timing, dan keputusan valid/tidaknya sebuah respons.

## 1. Pembagian tanggung jawab

```text
HondaProtocolEngine
  └─ EcuSessionManager          lifecycle + satu gate transaksi
       └─ KlineWireEngine       wake, TX, RX, echo, pemilihan frame
            └─ UsbTransport     byte ke adapter
```

| Layer | Memutuskan | Tidak memutuskan |
|---|---|---|
| `UsbTransport` | Berhasil membuka port, jumlah byte terbaca/tertulis | Arti byte, batas frame |
| `KlineWireEngine` | Kapan frame dianggap lengkap, valid, dan bukan echo | Arti payload, model ECU |
| `EcuSessionManager` | Kapan sesi dianggap siap, gagal, atau lost | Isi perintah |
| `HondaProtocolEngine` | Perintah apa yang dikirim dan bagaimana hasilnya ditafsirkan | Detail USB |

## 2. Lifecycle sesi

`CommLifecycle` memiliki state berikut:

| State | Arti |
|---|---|
| `DISCONNECTED` | Tidak ada transport |
| `ADAPTER_OPEN` | Port terbuka, belum ada transaksi valid |
| `LINE_READY` | Wake pulse selesai; belum ada respons ECU |
| `INITIALIZING` | Urutan inisialisasi berjalan |
| `IDENTIFYING` | Query identifikasi berjalan |
| `ECU_READY` | Minimal satu frame valid diterima |
| `MONITORING` | Live data aktif |
| `SUSPECTED_LOSS` | Ambang kegagalan tercapai |
| `RECOVERY_PROBE` | Probe pemulihan berjalan |
| `DISCONNECTING` | Teardown |

Aturan penting:

- `markAlive()` menurunkan `consecutiveFailures` ke 0 dan memindahkan state non-operasional ke `ECU_READY`.
- `markFailure()` menaikkan hitungan. Setelah ambang (default 2) tercapai dan state sedang `ECU_READY`/`MONITORING`, state menjadi `SUSPECTED_LOSS`.
- `markIdentified(verified)` **tidak** menaikkan status bukti family. Family hanya berubah dari header respons atau dari ID yang cocok database.
- `attach()` dan `detach()` mereset antrean TX internal wire engine agar echo dari koneksi lama tidak terbawa.

## 3. Single-flight

`EcuSessionManager.transact` memakai `Mutex`. Semua operasi berikut melewati gate yang sama:

- tester present dan query identifikasi,
- probe Live Data,
- baca dan clear DTC,
- probe pemulihan.

Konsekuensi:

1. Dua coroutine tidak dapat menulis ke K-Line bersamaan.
2. Operasi yang menunggu akan berjalan berurutan, bukan dibatalkan.
3. `expectedTransport` mencegah transaksi tertinggal memakai transport yang sudah diganti setelah reconnect.

`HondaProtocolEngine` juga memegang `commLock` pada level operasi. Ini bukan duplikasi yang berbahaya: lock luar melindungi urutan beberapa transaksi (misalnya identifikasi multi-query), sedangkan gate dalam melindungi satu transaksi.

## 4. Konfigurasi transaksi

`KlineWireConfig` menentukan perilaku wire:

| Field | Default | Fungsi |
|---|---:|---|
| `timeoutMs` | 700 | Batas total menunggu frame |
| `retries` | 1 | Jumlah percobaan |
| `interByteMs` | 0 | Jeda antar-byte; 0 berarti satu transfer |
| `echoWindowMs` | 0 | Jendela baca awal sebelum loop timeout |
| `quietMs` | 0 | Berhenti setelah jeda sepi |
| `postWriteDelayMs` | 0 | Jeda setelah TX |
| `acceptedHeaders` | header baseline | Header yang boleh menjadi kandidat |
| `recentTxWindowMs` | 1500 | Usia maksimum TX yang masih dianggap echo |
| `maxRxBuffer` | 1024 | Batas buffer sebelum byte tertua dibuang |
| `keepRxBuffer` | 512 | Byte yang dipertahankan saat buffer penuh |

Nilai default engine untuk transaksi identifikasi/DTC/Live:

- identifikasi: timeout 600–800 ms per query, satu percobaan,
- Live Data: timeout 300 ms, jeda 2 ms setelah TX,
- DTC: timeout 700 ms,
- clear: timeout 800 ms.

## 5. Alur satu transaksi

```text
transceive(request)
  ├─ purge RX/TX
  ├─ buang byte RX lama (maks. 8 × 512 byte, timeout 5 ms)
  ├─ catat request sebagai TX terbaru
  ├─ tulis request (satu transfer atau paced)
  ├─ baca sampai timeout
  ├─ hapus echo di awal buffer
  ├─ pindai kandidat frame
  │    ├─ header dikenal?
  │    ├─ declared length ≥ 4 dan cukup byte?
  │    ├─ bukan TX sendiri?
  │    └─ checksum valid?
  ├─ frame ditemukan → CommandOutcome.Frame
  └─ tidak ditemukan → CommandOutcome.Failure(reason, raw)
```

`CommandOutcome.Failure` selalu membawa alasan yang dapat dibedakan: timeout, hanya echo, checksum gagal, frame tidak lengkap, atau tidak ada frame valid.

## 6. Echo dan TX sendiri

Adapter K-Line sering mengembalikan byte yang baru dikirim. Engine menangani dua bentuk:

1. **Echo di awal buffer** — `stripLeadingEcho` membandingkan awal RX dengan request dan membuangnya jika identik.
2. **Echo yang muncul kemudian** — setiap TX dicatat dalam antrean 32 frame selama 1500 ms. Kandidat yang identik dengan TX sendiri ditolak meskipun checksum-nya valid.

Window 1500 ms mencakup retry. Tanpa window ini, echo dari percobaan pertama dapat diterima sebagai respons pada percobaan kedua.

## 7. Buffer dan fragmentasi

USB tidak menjamin satu `read()` menghasilkan satu frame. Engine:

- menambahkan setiap potongan ke `ByteArrayOutputStream`,
- memindai ulang setelah setiap potongan,
- membuang byte tertua jika buffer melebihi 1024 byte, menyisakan 512 byte terakhir,
- mencatat byte yang dibuang ke log sebelum parsing.

Byte yang dibuang tidak boleh dianggap sebagai awal frame. Karena itu buffer besar tidak otomatis membuat frame valid.

## 8. Timeout dan retry

- Satu percobaan membaca sampai `timeoutMs`; tidak ada timeout per byte.
- Retry default satu kali untuk transaksi kritis.
- Jeda antar-percobaan 50–60 ms.
- Kegagalan menaikkan hitungan sesi; keberhasilan mereset.
- `HondaProtocolEngine` dapat menurunkan ambang kegagalan menjadi 1 untuk perintah manual, dan memakai 2–4 untuk operasi latar.

## 9. Wake pulse

`wakePulse` melakukan:

```text
set baud (10400)
purge
BREAK ON  → tunggu 70 ms
BREAK OFF → tunggu 130 ms
purge
```

Yang dilaporkan adalah **waktu host**, bukan durasi bus terukur. Jika adapter menolak BREAK, hasilnya `breakAccepted=false`; engine tetap melanjutkan query dan mencatat kegagalan adapter.

## 10. Deteksi family

Setiap frame valid diperiksa header-nya oleh `EcuFamilyResolver`:

| Header | Family | Status bukti |
|---|---|---|
| `0x7E` | Keihin H99B | `HYPOTHESIS` |
| `0x7C` | Shindengen H99B | `HYPOTHESIS` |
| lainnya | tidak berubah | — |

Deteksi family hanya memilih profil routing. Ini bukan identifikasi kendaraan dan tidak boleh ditampilkan sebagai model.

## 11. Hubungan dengan engine orkestrasi

`HondaProtocolEngine` memakai `legacyTransceive` untuk semua operasi baseline: satu percobaan, tanpa pacing, timeout sesuai perintah. Nama "legacy" merujuk pada perilaku baseline yang terbukti, bukan pada `LegacyHondaProtocolEngine`.

`LegacyHondaProtocolEngine` disertakan sebagai referensi rollback dan tidak dipakai oleh `HondaProtocolEngine`.

## 12. Batasan

- Keberhasilan satu frame tidak membuktikan ECU, kendaraan, atau firmware tertentu.
- Wake pulse 70/130 ms adalah nilai host-side yang dipertahankan dari baseline; bukan hasil pengukuran bus.
- Family non-baseline memiliki katalog kosong. Engine menolak mengirim frame yang tidak direkonstruksi.
- Pengujian unit memakai transport palsu; perilaku adapter nyata tetap memerlukan pengujian perangkat.
