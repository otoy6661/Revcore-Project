# FLASHPRO Reverse Engineering Report (RevCore)

Sumber: `docs/flashpro/firmware.bin` dan `docs/flashpro/firmware.signed.flash`.
Alat: ekstraksi image ESP32, pembacaan rodata/teks, Ghidra 12.2 headless (Xtensa LE 32),
disassembly/decompilation fungsi yang tercatat di laporan ini.

Klasifikasi bukti yang dipakai:

- **TERBUKTI** — byte/konstanta/fungsi terlihat langsung di image atau decompile.
- **DIREKONSTRUKSI** — perilaku disimpulkan dari alur fungsi dan konstanta, belum diuji ECU fisik.
- **BELUM DIKETAHUI** — tidak ada bukti yang cukup; tidak diimplementasikan dan tidak diarang.

## Ringkasan status

| Aspek | Status |
|---|---|
| Forensik `firmware.bin` | Selesai; protokol, tabel live, decoder, dan 188 identitas kendaraan terekstrak |
| `firmware.signed.flash` | BELUM DIKETAHUI (container proprietary terenkripsi; tidak dipakai) |
| Live Monitor RevCore | Tabel 0x17/0x13/0x11/0x10/0x16/0x20/0xD1 dan decoder diterapkan; polling utama + ekstensi bergilir |
| Database kendaraan | 476 → 489 record; 175 nama diperbarui tanpa menghapus nama lama; 13 record baru |
| Build & tes | 108 unit test lulus, APK debug berhasil dibangun, asset 489 record valid |
| Pengujian hardware | **Belum dilakukan**; tidak ada klaim ECU fisik sudah membaca data |

---

## 1. Forensik firmware

### 1.1 `firmware.bin`

| Item | Nilai |
|---|---|
| Ukuran | 1.398.816 byte |
| SHA-256 | `fe0704fd322d2c981ab029faf63d7463a4d7521cc912e18fa411252e286b65e1` |
| Magic | `E9 05 02 20 10 30 08 40` (ESP32 app image, 5 segmen) |
| Entry | `0x40083010` |
| Toolchain | `esp-idf: v4.4.7 38eeba213a`, Arduino libraries |

Segmen yang berhasil diekstrak:

| Segmen | Alamat | Ukuran | Isi |
|---|---|---|---|
| 0 | `0x3f400020` | `0x3385c` | DROM / rodata (string, tabel) |
| 1 | `0x3ffbdb60` | `0x5d34` | DRAM |
| 2 | `0x40080000` | `0x6a58` | IRAM |
| 3 | `0x400d0020` | `0x1014a0` | Teks / kode |
| 4 | `0x40086a58` | `0x14328` | Data lanjutan |

48 byte terakhir setelah segmen terakhir tidak termasuk segmen image standar.
Firmware sumber tidak diubah.

### 1.2 `firmware.signed.flash`

| Item | Nilai |
|---|---|
| Ukuran | 1.249.608 byte |
| SHA-256 | `97ca99e9001d94cf0b16c70b684d755d490400d6cfa874c7d27341175df09508` |
| Header | `46 50 52 4f 54 41 30 31` = `FPROTA01` |
| Entropi | 8.000 (data terenkripsi/terkompresi penuh) |
| Magic ESP `E9` | Tidak ditemukan |

**Status: BELUM DIKETAHUI.** Container `FPROTA01` bersifat proprietary
(terenkripsi/bertanda tangan). Tidak ada kunci, algoritma verifikasi, atau tabel
dekripsi yang ditemukan di `firmware.bin`. Karena itu tidak ada byte ECU yang
diambil dari file ini; seluruh rekonstruksi protokol memakai `firmware.bin`.
File ini tidak dimodifikasi.

### 1.3 Bukti fungsi utama

| Alamat | Fungsi | Bukti |
|---|---|---|
| `0x400d8d48` | TX/RX transaksi K-Line | Mengirim frame, membuang echo, menerima sampai timeout |
| `0x400d6284` | Parser LiveData dat17/dat11/dat10/dat13/dat16/dat20/datD1 | 2.355 byte, seluruh offset sensor |
| `0x400dba6c` | Probe LiveData | Tabel probe 4 entri, timeout 200 ms |
| `0x400d75c8` | Pembentuk output sensor/JSON | Pemetaan field ke nama kunci |
| `0x400dc634` | Pemetaan tipe protokol | 1=ISO9141, 2=ISO14230-Slow, 3=ISO14230-Fast |
| `0x400dc768` | Dekoder parameter generik | Tabel konstanta 0x400d072c..0x400d074c |
| `0x400d8c1c`, `0x400db018`, `0x400dc9c4`, `0x400d9240`, `0x400e942c`, `0x400f463c`, `0x400f707c` | Pemakai baud 10400 | Semua membaca literal `0x28A0` di `0x400d0478` |

---

## 2. Protokol TX/RX ECU (jalur ESP32 ↔ ECU)

### 2.1 Konfigurasi transport — TERBUKTI

- Baud rate: **10.400 bps**. Literal `0x000028A0` di `0x400d0478`, dibaca oleh
  fungsi-fungsi UART di atas.
- Format: 8 data bit, tanpa parity, 1 stop bit (konfigurasi Arduino `Serial` yang
  dipakai bersama objek UART di `0x3ffcee78`).
- TX dan RX berada pada UART yang sama; echo lokal dibuang di host.
- Tidak ditemukan penggunaan GPIO flow-control atau line-toggle khusus.

### 2.2 Transaksi `FUN_400d8d48` — TERBUKTI

Parameter yang dipakai pemanggil LiveData:

| Parameter | Nilai | Arti |
|---|---|---|
| `param_3` | 5 | Panjang frame TX |
| `param_5` | 300 | Timeout total respons (ms) |
| `param_7` | 0x1E (30) | Batas waktu setelah byte pertama diterima (ms) |
| `param_9` | 0 | Jeda antar-byte TX |
| `param_10` | 1 | Buang echo |
| `param_11` | 1 | Kosongkan buffer RX setelah transaksi |
| `param_12` | `0xFFFF` | Sentinel: panjang echo = panjang frame TX (5) |

Perilaku:

1. Buffer RX dikosongkan.
2. Frame ditulis utuh (5 byte).
3. Setiap byte yang diterima dibandingkan dengan frame TX; 5 byte pertama yang
   identik dibuang sebagai echo.
4. Setelah byte pertama yang bukan echo, firmware menunggu maksimal 30 ms untuk
   sisa frame; total transaksi dibatasi 300 ms.
5. Buffer dibersihkan sebelum transaksi berikutnya.
6. Parser **tidak** memvalidasi checksum respons; validasi hanya pada sisi
   permintaan (checksum dibentuk sebagai komplemen dua).

Konsekuensi untuk RevCore: validasi checksum respons tetap dipertahankan
(lebih ketat), sehingga frame yang diterima harus menyertakan checksum di byte
terakhir.

### 2.3 Bentuk frame permintaan — TERBUKTI

Semua perintah LiveData yang ditemukan berukuran **5 byte**:

```
72 05 71 <tabel> <checksum>
checksum = (0x100 - ((72 + 05 + 71 + tabel) & 0xFF)) & 0xFF
```

| Nama | Frame | Checksum |
|---|---|---|
| dat17 | `72 05 71 17 01` | `0x01` |
| dat13 | `72 05 71 13 05` | `0x05` |
| dat11 | `72 05 71 11 07` | `0x07` |
| dat10 | `72 05 71 10 08` | `0x08` |
| dat16 | `72 05 71 16 02` | `0x02` |
| dat20 | `72 05 71 20 F8` | `0xF8` |
| datD1 | `72 05 71 D1 47` | `0x47` |

Tabel probe di `0x3f4060f4` (4 record × 16 byte, `{ptr, len=5, table, minLen}`):

| Urutan | Frame | Panjang respons minimum |
|---|---|---|
| 1 | `72 05 71 17 01` | 18 |
| 2 | `72 05 71 13 05` | 18 |
| 3 | `72 05 71 11 07` | 21 |
| 4 | `72 05 71 10 08` | 21 |

Probe menolak respons dengan `byte[1] == 0x05` (penanda error ECU) dan
menganggap tabel valid jika panjang ≥ `minLen`. Probe memakai timeout 200 ms.

### 2.4 Urutan pembacaan LiveData — TERBUKTI

`FUN_400d6284` mencoba secara berurutan:

1. dat17 (`0x17`)
2. jika gagal: dat11 (`0x11`)
3. jika gagal: dat10 (`0x10`)
4. jika gagal: dat13 (`0x13`)
5. selalu: dat16 (`0x16`)
6. selalu: dat20 (`0x20`)
7. selalu: datD1 (`0xD1`)

Syarat valid tabel utama: `byte[1] != 0x05` dan panjang respons:

| Tabel | Panjang minimum respons | Catatan |
|---|---|---|
| dat17 | 20 | membaca indeks 4..19 |
| dat11/dat10 | 21 | membaca indeks 4..20 |
| dat13 | 18 | membaca indeks 4..17 |
| dat16 | 25 | membaca indeks 4..24 |
| dat20 | ≥6 (implementasi memakai 2 byte data) | indeks 4,5 |
| datD1 | 11 | indeks 4..10 |

### 2.5 Wake-up dan inisialisasi — TERBUKTI / DIREKONSTRUKSI

String `DETECT: begin (universal wake)` berada tepat setelah rangkaian byte di
`0x3f405992`:

```
72 05 00 F0 99      (checksum valid: 72+05+00+F0 = 0x167 -> 0x99)
FE 04 72 8C         (checksum valid: FE+04+72 = 0x174 -> 0x8C)
FE 04 72 8C
```

Urutan ini dipakai sebagai wake universal untuk deteksi ECU. Frame
`FE 04 72 8C` juga dipakai sebagai tester-present.

Perintah keluarga ECU yang ditemukan (semua checksum valid):

| Label string | Frame |
|---|---|
| KEI Init | `7B 06 02 01 03 79` |
| KEI ModeSel | `7D 06 01 01 03 78` |
| KEI KeepAlive | `72 07 72 00 00 05 10` |
| KEI ID | `7E 06 01 01 00 7A` |
| SHIN 1A90 / 1A92 / 1A94 / 1A9A | `1A 90`, `1A 92`, `1A 94`, `1A 9A` |
| SHIN 2100 | `21 00` |
| ISO14230 Fast init | `C1 33 F1 81` |

Pemetaan tipe protokol (`FUN_400dc634`): `1 = ISO9141`, `2 = ISO14230-Slow`,
`3 = ISO14230-Fast`, lainnya `NONE`.

**BELUM DIKETAHUI:** timing 5-baud / slow-init ISO 9141 yang sebenarnya
(durasi low/high, alamat inisialisasi) tidak ditemukan sebagai konstanta yang
dapat dibuktikan. Karena itu RevCore tetap memakai wake pulse host-side yang
sudah ada; tidak ada timing baru yang dikarang.

### 2.6 Perbandingan dengan RevCore

| Komponen | FLASHPRO | RevCore sebelum perubahan | Tindakan |
|---|---|---|---|
| Baud | 10400 | 10400 (`FtdiConstants.BAUD_KLINE_DEFAULT`) | Tidak diubah |
| Frame live | 5 byte + checksum | 6 byte (`... 0x01 0x00` / `0x01 0x06` / `0x01 0x07`) | **Diperbaiki** |
| Tabel live | 0x17, 0x13, 0x11, 0x10 + 0x16/0x20/0xD1 | 0x17, 0x11, 0x10, 0x00 | **Diperbaiki** (0x13 masuk, 0x00 dihapus) |
| Probe | 0x17 → 0x13 → 0x11 → 0x10, 200 ms | 0x10 saja, 600 ms | **Diperbaiki** |
| Layout | Tabel-spesifik | Satu layout baseline untuk semua tabel | **Diperbaiki** |
| Echo | Buang 5 byte + heuristik | `KlineEchoFilter`/`stripEcho` | Dipertahankan |
| Timeout | 300 ms total, 30 ms setelah byte pertama | 250 ms | Disesuaikan ke 300 ms untuk live |
| Checksum respons | Tidak divalidasi | Divalidasi | Dipertahankan (lebih ketat) |

---

## 3. Live Monitor — tabel dan decoder

Offset di bawah adalah **indeks byte respons** (byte 0 = header `0x72`).
Di RevCore, payload dimulai pada byte 4 sehingga `payloadIndex = responseIndex - 4`.

Konstanta yang dipakai (TERBUKTI, dibaca dari literal pool `0x400d02a8`..`0x400d02fc`):

| Nilai | Konstanta |
|---|---|
| 5.0 | skala tegangan ADC |
| 1/256 = 0.00390625 | pembagi tegangan ADC |
| 0.5 | skala derajat / timing |
| 40.0 | bias suhu |
| 10.0 | pembagi baterai |
| 200.0 | pembagi injektor |
| -64.0 | bias ignition |
| 13.142708 | pembagi O2 |
| 1000.0 | pembagi lambda |
| 128.256516 | pembagi STF |
| 50.993698 | pembagi O2 voltage |
| 17.700001 | AFR intercept |
| -5.351 | AFR slope |

### 3.1 dat17 (`72 05 71 17 01`) — TERBUKTI

| Response index | Field | Konversi | Satuan |
|---|---|---|---|
| 4–5 | RPM | big-endian 16-bit | rpm |
| 6 | TPS voltage | `raw * 5 / 256` | V |
| 7 | TPS angle | `raw * 0.5` | deg |
| 8 | ECT voltage (fallback) | `raw * 5 / 256` | V |
| 9 | ECT (fallback) | `raw - 40` | °C |
| 10 | IAT voltage | `raw * 5 / 256` | V |
| 11 | IAT | `raw - 40` | °C |
| 12 | ECT voltage (utama) | `raw * 5 / 256` | V |
| 13 | ECT (utama) | `raw - 40` | °C |
| 14 | Battery | `raw / 10` | V |
| 15–16 | Injector | big-endian 16-bit `/ 200` | ms |
| 17 | Ignition | `-64 + raw * 0.5` | deg |
| 18 | Mode | enum `0x80`/`'g'`=1, `'f'`=2, `'<'`=3, `'Y'`=4 | – |
| 19 | VSS | `raw` | km/h |
| 2 | tableType | `raw` (0x17) | – |

Catatan: byte 12/13 adalah sumber ECT utama; byte 8/9 hanya dipakai jika byte
12 bernilai `0xFF`.

### 3.2 dat11 / dat10 (`72 05 71 11 07` / `72 05 71 10 08`) — TERBUKTI

| Response index | Field | Konversi |
|---|---|---|
| 4–5 | RPM | BE16 |
| 6 | TPS voltage | `raw * 5 / 256` |
| 7 | TPS angle | `raw * 0.5` |
| 8 | ECT voltage | `raw * 5 / 256` |
| 9 | ECT | `raw - 40` |
| 10 | IAT voltage | `raw * 5 / 256` |
| 11 | IAT | `raw - 40` |
| 12 | MAP voltage | `raw * 5 / 256` |
| 13 | MAP | `raw` kPa |
| 16 | Battery | `raw / 10` |
| 17 | VSS | `raw` km/h |
| 18–19 | Injector | BE16 `/ 200` ms |
| 20 | Ignition | `-64 + raw * 0.5` |
| 2 | tableType | `0x11` atau `0x10` |

### 3.3 dat13 (`72 05 71 13 05`) — TERBUKTI

| Response index | Field | Konversi |
|---|---|---|
| 4–5 | RPM | BE16 |
| 6 | TPS voltage | `raw * 5 / 256` |
| 7 | TPS angle | `raw * 0.5` |
| 8 | ECT voltage | `raw * 5 / 256` |
| 9 | ECT | `raw - 40` |
| 10 | IAT voltage | `raw * 5 / 256` |
| 11 | IAT | `raw - 40` |
| 12 | MAP voltage | `raw * 5 / 256` |
| 13 | MAP | `raw` kPa |
| 14 | Battery | `raw / 10` |
| 15–16 | Injector | BE16 `/ 200` ms |
| 17 | Ignition | `-64 + raw * 0.5` |
| 2 | tableType | `0x13` |

Tidak ada VSS/mode pada dat13.

### 3.4 dat16 (`72 05 71 16 02`) — TERBUKTI

| Response index | Field | Konversi |
|---|---|---|
| 4–5 | RPM (hanya jika tabel utama gagal) | BE16 |
| 6 | AP1 voltage | `raw * 5 / 256` |
| 7 | AP2 voltage | `raw * 5 / 256` |
| 8 | AP1 angle | `raw * 0.5` |
| 9 | AP2 angle | `raw * 0.5` |
| 10 | TP1 voltage | `raw * 5 / 256` |
| 11 | TP2 voltage | `raw * 5 / 256` |
| 12 | TP1 angle | `raw * 0.5` |
| 13 | TP2 angle | `raw * 0.5` |
| 14 | COR | `raw` |
| 15 | ECT voltage | `raw * 5 / 256` |
| 16 | ECT | `raw - 40` |
| 17 | IAT voltage | `raw * 5 / 256` |
| 18 | IAT | `raw - 40` |
| 19 | MAP voltage | `raw * 5 / 256` |
| 20 | MAP | `raw` kPa |
| 21 | Battery | `raw / 10` |
| 22 | VSS | `raw` km/h |
| 23 | Injector | `raw / 200` ms (satu byte) |
| 24 | Ignition | `-64 + raw * 0.5` |

### 3.5 dat20 (`72 05 71 20 F8`) — TERBUKTI

| Response index | Field | Konversi |
|---|---|---|
| 4–5 | O2 raw | BE16 `/ 13.142708 / 1000` |
| 5 | STF | `raw / 128.256516` |
| 4 | O2 voltage | `raw / 50.993698` V |
| – | AFR | `17.7 - 5.351 * o2Voltage` |

### 3.6 datD1 (`72 05 71 D1 47`) — TERBUKTI (pemetaan string)

| Response index | Field | Nilai |
|---|---|---|
| 4 | Gear/neutral state | `1` → "Netral"; `0` → "OnGear"; `2` → state lain |
| 8 | Fuel/flag A | `1`/`5` → "ON"; `0`/`4` → "OFF"; lainnya "---" |
| 9 | Fan/flag B | `1` → "ON"; lainnya "OFF" |
| 10 | Gear position | `1`, `2`, `4`, `8`, `0x10`, `0x20`, `0x80` → string posisi gigi |

Arti nama kunci JSON (`FUN_400d75c8`) untuk field ini:
`+0x84 fastIdle`, `+0x94 bankAngle`, `+0xa4 starter`, `+0xb4 sideStand`,
`+0xc4 gear`, `+0xd4 fuelPump`, `+0xe4 fan`, `+0xf4 gearPos`.
Karena indeks D1 di atas menulis ke offset tersebut, pemetaan yang
diimplementasikan hanya yang dapat dibuktikan: byte 4 → status gigi/netral,
byte 8/9 → flag ON/OFF, byte 10 → posisi gigi. Nama fisik flag 8/9
(fuel pump vs fan) tetap **DIREKONSTRUKSI**.

---

## 4. Database kendaraan

### 4.1 Tabel identitas utama

- Lokasi: `0x3f404550` – `0x3f404a7f`, record **16 byte**:
  `{ecmId_ptr, partNumber_ptr, u32 flag, bikeName_ptr}`.
- Jumlah record pada tabel ini: **83**.
- `u32 flag` hanya bernilai `0x40000` atau `0x60000`; artinya **BELUM DIKETAHUI**
  dan tidak dipakai untuk pencocokan.

Contoh (TERBUKTI):

| ECM ID | Part number | Nama kendaraan |
|---|---|---|
| `01048D0F01` | `30400-K0WL-NB1` | ADV 160 SMARTKEY (2023) |
| `01046B0F01` | `30400-K2SA-N02` | VARIO 160 SMARTKEY (2022-2023) |
| `0101C40F01` | `30400-K59A-A11` | VARIO 150 LED (2016-2017) |
| `0104040F01` | `30400-K1ZG-N21` | PCX 160 SMARTKEY (2021) |
| `0106250F01` | `30400-K0WW-NE2` | ADV 160 (K0WW-NE2) 2025 |

### 4.2 Tabel identitas tambahan

Pemindaian seluruh rodata (pasangan pointer `ECM ID 10 hex` + `part number`
`30400-…`/`38770-…` + pointer nama) menghasilkan **188 ECM ID unik** pada
`0x3f404550`–`0x3f4055e0`. Ini mencakup tabel utama 83 record dan tabel
tambahan (Thailand/Philippines, model 38770, dan entri yang ditambahkan
manual). Data lengkap: `docs/flashpro/extracted_vehicle_rows.json`.

Validasi:

- 188 ECM ID unik, tidak ada ECM ID ganda.
- 175 part number unik; 4 part number dipakai oleh dua ECM ID berbeda
  (varian ECU, bukan konflik identitas):
  `30400-K2VJ-H31`, `38770-K15-601`, `38770-K45-NL1`, `38770-K56-N11`.
- Tidak ada record yang memetakan satu ECM ID ke dua kendaraan.
- Leading zero dipertahankan; ID dibaca sebagai teks, bukan angka.

### 4.3 Perbandingan dengan database RevCore (sebelum integrasi)

| Metrik | Jumlah |
|---|---|
| Record RevCore lama | 476 |
| ECM ID FLASHPRO unik | 188 |
| Cocok persis dengan ECM ID RevCore | 175 |
| ECM ID baru | 13 |
| Konflik ECM ID (satu ID → dua kendaraan) | 0 |
| Nama kendaraan berbeda pada 175 record yang cocok | 175 |
| Part number berbeda untuk ECM ID yang sama | 13 |
| Record yang belum dapat dipetakan | 0 |
| Record setelah merge | 489 |
| ECM ID duplikat yang sudah ada sebelum merge | 1 (`0100CE0D01` × 3 record) |

13 ECM ID baru:

```
01058E0F11 30400-K1AL-NB1  BeAT NEW 110 (2024)
01058B0F11 30400-K1AL-N81  BeAT NEW 110 (2024)
01058C0F11 30400-K1AL-N91  BeAT NEW 110 (2024)
01058D0F11 30400-K1AL-NA1  BeAT NEW 110 (2024)
0105030F11 30400-K1ZN-N41  PCX 160 SMARTKEY (2023)
0104DA0F01 30400-K2VJ-H31  VARIO 125 (K2VJ-H31) 2025
0101CD1811 38770-K56-M01   SONIC 150 (2016-2017)
0101C90F11 38770-K56-N01   SONIC 150 (2016-2017)
0104A70F01 38770-K64-NN1   CBR 250 RR (2024)
0104A90F01 38770-K64-NP1   CBR 250 RR (2024)
0803130F01 38770-K15-601   CB 150R CIP (K15-601) — TODO verifikasi prefix 08
0101CA0F11 38770-K56-N11   SUPRA GTR150 (K56-N11) variant
0105260D01 38770-K1M-T31   Dream 110 (K1M-T31)
```

Catatan penting: seluruh 175 record yang cocok memiliki nama berbeda antara
RevCore dan tabel firmware. Contoh: `0101C40F01` tercatat "Wave 110" padahal
firmware menyebut "VARIO 150 LED (2016-2017)", dan `0102E30F01` tercatat
"CB 500 F" padahal firmware menyebut "BeAT 110 NON ISS (2018-2019)".

Keputusan merge yang benar-benar diterapkan:

- Nama firmware menjadi `bikeName` untuk **semua** 175 record yang cocok,
  bukan hanya record tanpa nama. Nama RevCore lama tidak dihapus; ia disimpan
  di `statusNotes` dengan format
  `Nama RevCore sebelumnya: "<nama lama>".`, sehingga dapat dipulihkan.
- `partNumber` RevCore **dipertahankan** (13 record berbeda dari firmware).
  Part firmware dicatat di `statusNotes` (lihat `Identitas FLASHPRO: … part …`),
  bukan menimpa nilai kanonis RevCore.
- 13 record baru memakai `id = "FP-<ecmId>"`, `source = "flashpro"`, dan nilai
  teknis yang belum terbukti diisi eksplisit sebagai belum terverifikasi
  (`manufacturer`/`mcuType` = "Belum terverifikasi", offset = 0,
  `isVerifiedDiagnostic/Remap = false`). Catatan asal-usul dan tabel rodata
  ditulis di `statusNotes`.
- `displacementCc` untuk 8 dari 13 record baru diwarisi dari record lain
  dengan part number identik; catatan pewarisan ditulis di `statusNotes`.
- Merge bersifat idempoten dan dapat diulang: `tools/flashpro_merge_db.ps1`
  membuat backup, memperbarui record yang ada, menambah yang baru, lalu
  memvalidasi JSON sebelum mengganti file.

---

## 5. Adaptasi ESP32 → FTDI USB OTG

- FT232RL adalah **USB-to-UART bridge**, bukan transceiver K-Line. Perangkat
  keras RevCore tetap memerlukan transceiver K-Line eksternal (pull-up,
  diode/transistor RX-TX, kontrol K-Line).
- Baud 10400 8N1 dapat dihasilkan FT232RL. BREAK dapat diminta melalui
  `FTDI` `setBreakOn/Off` bila driver mendukung; firmware sumber tidak
  membuktikan penggunaan BREAK, sehingga wake yang dipakai tetap pulse
  host-side yang sudah ada.
- Echo lokal harus dibuang di host (FT232RL mengembalikan byte yang dikirim);
  RevCore sudah melakukannya di `KlineTransceiver.stripEcho`.
- Tidak ada JSON Bluetooth pada jalur ini; frame mentah 5 byte dikirim
  langsung ke UART.
- Penanganan disconnect/reconnect, permission USB, dan timeout tetap memakai
  arsitektur RevCore yang ada; tidak diubah.

---

## 6. Implementasi yang diterapkan pada RevCore

| File | Perubahan |
|---|---|
| `app/src/main/java/com/revcore/android/core/comm/LiveDataProfile.kt` | Ditulis ulang: 32 sensor, profil tabel 0x17/0x13/0x11/0x10/0x16/0x20/0xD1, parser dengan rentang valid dan `minFrameSize` |
| `app/src/main/java/com/revcore/android/core/comm/HondaEcuProfiles.kt` | Frame live 5 byte sesuai checksum firmware, urutan probe 17→13→11→10, katalog ekstensi 16/20/D1 |
| `app/src/main/java/com/revcore/android/core/protocol/HondaProtocolEngine.kt` | Pemilihan profil per tabel, validasi echo tabel dan panjang minimum, polling ekstensi bergilir, merge nilai primer/ekstensi |
| `app/src/main/assets/ecm_database.json` | 175 nama diperbarui, 13 record baru, 476 → 489 record |
| `app/src/test/java/com/revcore/android/core/comm/LiveDataProfileParserTest.kt` | 14 tes decoder dan rangkaian request |
| `app/src/test/java/com/revcore/android/core/comm/EcuProfilesTest.kt` | Tes urutan probe dan checksum frame 5 byte |
| `tools/flashpro_merge_db.ps1` | Skrip merge database idempoten (backup, update, append, validasi) |
| `docs/flashpro/ECM_DATABASE_MERGE_REPORT.txt` | Laporan hasil merge yang dihasilkan skrip |

### 6.1 Decoder live data

`HondaLiveDataProfiles` mendefinisikan tujuh tabel dengan offset, skala, dan
rentang valid yang ditranskripsi dari decoder firmware. Semua layout berstatus
`VERIFIED` untuk makna offset/skala; status ini tidak menyatakan bahwa setiap
ECU menjawab tabel tersebut.

| Tabel | Request | Min. payload | Sensor utama |
|---|---|---|---|
| 0x17 | `72 05 71 17 01` | 16 | RPM, TPS (V/derajat/%), ECT (byte 9, fallback byte 5), IAT, baterai, injektor, ignition, mode, kecepatan |
| 0x13 | `72 05 71 13 05` | 14 | RPM, TPS, ECT, IAT, MAP, baterai, injektor, ignition |
| 0x11 | `72 05 71 11 07` | 17 | RPM, TPS, ECT, IAT, MAP, baterai, kecepatan, injektor, ignition |
| 0x10 | `72 05 71 10 08` | 17 | Layout sama dengan 0x11 |
| 0x16 | `72 05 71 16 02` | 21 | RPM, AP1/AP2, TP1/TP2, COR, ECT, IAT, MAP, baterai, kecepatan, injektor, ignition |
| 0x20 | `72 05 71 20 F8` | 2 | O2 raw, O2 voltage, STF, AFR |
| 0xD1 | `72 05 71 D1 47` | 7 | Status gigi, dua flag, posisi gigi |

Byte `0xFF` hanya diperlakukan sebagai "tidak ada nilai" pada field yang
memang mendeklarasikan offset alternatif (pasangan ECT dat17). Field lain tetap
dibaca apa adanya lalu diuji rentang valid; nilai di luar rentang dihilangkan,
tidak diisi nol.

### 6.2 Alur polling

1. Tabel yang menjawab terakhir dicoba lebih dulu. Tiga kegagalan berturut-turut
   menghapus cache.
2. Bila cache gagal, probe dijalankan dalam urutan bukti firmware:
   0x17 → 0x13 → 0x11 → 0x10, masing-masing 300 ms.
3. Bila seluruh tabel primer gagal, 0x16 dicoba sebagai upaya terakhir dan hanya
   dipakai jika RPM-nya masuk akal.
4. Tabel ekstensi 0x16/0x20/0xD1 dipoll satu tabel per ±400 ms secara bergilir.
   Tabel yang gagal dua kali dilewati selama 15 detik dan sensor yang hanya
   berasal dari tabel itu dihapus, sehingga kegagalan ekstensi tidak menahan
   polling utama.
5. Nilai ekstensi hanya mengisi sensor yang tidak dibawa tabel primer; nilai
   primer selalu menang.
6. Fallback PID 0x21 dan seluruh mekanisme koneksi/timeout lama dipertahankan.

Frame respons divalidasi tiga lapis sebelum didekode: checksum/rangka oleh wire
engine, panjang minimum per tabel (`minFrameSize`), dan echo nomor tabel pada
payload. Respons negatif `0x7F`, frame pendek, atau echo tabel yang tidak cocok
ditolak tanpa menebak offset.

## 7. Build dan pengujian

| Langkah | Hasil |
|---|---|
| Toolchain | Gradle 9.6.0 + JBR 21.0.11 (JDK 25 tidak didukung AGP) |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL, 108 tes lulus |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| Artefak | `app/build/outputs/apk/debug/app-debug.apk` (20.836.799 byte) |
| Asset terpaket | `assets/ecm_database.json` 748.580 byte, 489 record, JSON valid |
| Resolusi identitas | 188 ECM ID firmware → 188 record unik, 0 ambigu, 0 tidak ditemukan, 0 nama tidak cocok |

Tes mencakup checksum dan urutan frame request, offset/skala tiap tabel,
preferensi ECT primer atas fallback, ketiadaan kecepatan pada dat13, konversi
O2/STF/AFR, status gigi, penolakan payload pendek, penolakan RPM/suhu tidak
masuk akal, aturan `0xFF`, dan `minFrameSize` setiap layout.

## 8. Keterbatasan dan hal yang belum terbukti

- **Tidak ada pengujian hardware.** Seluruh verifikasi bersifat statis
  (dekompilasi, unit test, build). Belum ada ECU Honda nyata yang dihubungi
  melalui FT232RL + transceiver K-Line, sehingga tidak ada klaim bahwa tabel
  tertentu dijawab oleh kendaraan tertentu.
- Pemetaan respons terhadap tabel bergantung pada echo tabel di payload. Bila
  ECU tertentu tidak menggemakan nomor tabel, frame ditolak (lebih aman daripada
  salah decode), dan Live Monitor akan melaporkan tidak ada data valid.
- `firmware.signed.flash` belum dianalisis; laporan ini hanya memakai
  `firmware.bin`.
- Label posisi gigi baru terbukti untuk dua keadaan (`GEAR_STATE` 1 → "N",
  0 → "OnGear"); gigi 1–6 tidak diberi label karena pemetaannya belum terbukti.
- Duplikat lama `0100CE0D01` (3 record: KWW-642/643/641) tidak diubah. Karena
  itu `findByEcmId` masih mengembalikan null untuk ID tersebut; memilih salah
  satu akan merupakan tebakan.
- Tumpang tindih permintaan DTC 0x71/0x20 dengan tabel ekstensi live 0x20 tidak
  diubah pada jalur DTC; jalur tersebut tetap memakai katalog yang ada.
- Skrip merge memakai Windows PowerShell 5.1 dan belum diuji pada shell lain.
