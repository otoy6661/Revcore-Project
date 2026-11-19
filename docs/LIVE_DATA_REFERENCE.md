# REFERENSI LIVE DATA

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini memuat tabel, request frame, pemetaan byte, skala, dan rumus Live Data yang dikembangkan RevCore. Data mesin dapat dibaca dalam JSON di [`data/live_data/live_tables.json`](../data/live_data/live_tables.json).

Dokumen ini **bukan** jaminan bahwa ECU tertentu menerima tabel tertentu. Layout berstatus `RECONSTRUCTED`; penerimaan oleh ECU hanya dapat dibuktikan oleh transaksi wire pada kendaraan. Delapan parameter dilaporkan tampil oleh pemilik proyek, tetapi laporan itu bukan verifikasi independen.

## 1. Ringkasan

| Item | Nilai |
|---|---:|
| Tabel Live Data | 7 |
| Definisi field | 84 |
| Payload start | byte index 4 (setelah header, length, service, echo tabel) |
| Urutan byte | big-endian |
| Status layout | `RECONSTRUCTED` |
| Pengujian kendaraan | Tidak dilakukan |

Parameter yang dilaporkan tampil oleh pemilik: RPM, TPS, EOT/ECT, Battery Voltage, Ignition Advance, Injector Pulse, Vehicle Speed, O2.

## 2. Alur dari adapter ke nilai

```text
USB OTG -> adapter K-Line -> wake/init -> request frame
  -> respons ECU -> validasi header/length/echo/checksum
  -> ekstraksi payload -> pilih LiveTableProfile
  -> baca offset, panjang, endian -> value = raw * scale + bias
  -> cek validMin/validMax -> state per sensor -> tampilan/log
```

Frame request dan respons memakai struktur yang sama seperti protokol K-Line RevCore: `header | length | service | params/payload | checksum`. Checksum adalah komplemen dua dari jumlah byte sebelumnya. Contoh request `72 05 71 7A 9E` dan respons sintetisnya dijelaskan di [SENSOR_DECODING.md](SENSOR_DECODING.md).

`payloadStart = 4` berarti payload dimulai pada index 4: setelah `0x72`, length, service echo `0x71`, dan echo tabel. Profil lain dapat memakai `payloadStart` berbeda; parser tidak mengasumsikan posisi tetap.

## 3. Rumus umum

```text
value = raw * scale + bias
raw   = b0 * 256 + b1            (2 byte, big-endian)
raw   = b0                         (1 byte)
```

Aturan tambahan:

- `0xFF` pada byte utama hanya dianggap tidak tersedia bila field mendeklarasikan `alternateOffsets`; parser lalu membaca offset cadangan.
- Nilai di luar `validMin..validMax` menjadi `INVALID_RESPONSE` dan tidak dimasukkan ke hasil.
- Field yang gagal tidak diubah menjadi nol.
- O2 dan STF hanya valid bila RPM > 0; saat key-on/engine-off menjadi `NOT_AVAILABLE_IN_KOEO`.
- AFR selalu `UNSUPPORTED`; tidak dihitung dari tegangan O2 narrowband.

## 4. Tabel dan pemetaan

### 4.1 Tabel `17` — `dat17`

- Request: `72 05 71 17 01`
- Payload minimum: 16 byte
- Status layout: `RECONSTRUCTED`
- Catatan: ECT utama pada payload offset 9; offset 5 hanya dipakai bila byte utama 0xFF. MODE payload 14, kecepatan payload 15.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm |  |
| `TPS_VOLTAGE` | 2 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TPS_DEGREES` | 3 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TPS_PERCENT` | 2 | 1 | 100/256 | 0 | 0..100 | % | Derivasi dari ADC TPS; belum ada kalibrasi zero throttle. |
| `TPS_ADC` | 2 | 1 | 1 | 0 | 0..255 | count |  |
| `ECT_VOLTAGE` | 8 | 1 | 5/256 | 0 | 0..5 | V | Alternate: 4 |
| `ECT` | 9 | 1 | 1 | -40 | -40..150 | C | Alternate: 5 |
| `IAT_VOLTAGE` | 6 | 1 | 5/256 | 0 | 0..5 | V |  |
| `IAT` | 7 | 1 | 1 | -40 | -40..100 | C |  |
| `BATTERY` | 10 | 1 | 0.1 | 0 | 5..20 | V |  |
| `INJECTOR` | 11 | 2 | 1/200 | 0 | 0..50 | ms |  |
| `IGNITION_ADVANCE` | 13 | 1 | 0.5 | -64 | -70..70 | deg |  |
| `MODE` | 14 | 1 | 1 | 0 | 0..255 | - |  |
| `SPEED` | 15 | 1 | 1 | 0 | 0..300 | km/h |  |

### 4.2 Tabel `13` — `dat13`

- Request: `72 05 71 13 05`
- Payload minimum: 14 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Tanpa kanal kecepatan dan mode.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm |  |
| `TPS_VOLTAGE` | 2 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TPS_DEGREES` | 3 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TPS_PERCENT` | 2 | 1 | 100/256 | 0 | 0..100 | % | Derivasi dari ADC TPS; belum ada kalibrasi zero throttle. |
| `TPS_ADC` | 2 | 1 | 1 | 0 | 0..255 | count |  |
| `ECT_VOLTAGE` | 4 | 1 | 5/256 | 0 | 0..5 | V |  |
| `ECT` | 5 | 1 | 1 | -40 | -40..150 | C |  |
| `IAT_VOLTAGE` | 6 | 1 | 5/256 | 0 | 0..5 | V |  |
| `IAT` | 7 | 1 | 1 | -40 | -40..100 | C |  |
| `MAP_VOLTAGE` | 8 | 1 | 5/256 | 0 | 0..5 | V |  |
| `MAP` | 9 | 1 | 1 | 0 | 0..255 | kPa |  |
| `BATTERY` | 10 | 1 | 0.1 | 0 | 5..20 | V |  |
| `INJECTOR` | 11 | 2 | 1/200 | 0 | 0..50 | ms |  |
| `IGNITION_ADVANCE` | 13 | 1 | 0.5 | -64 | -70..70 | deg |  |

### 4.3 Tabel `11` — `dat11`

- Request: `72 05 71 11 07`
- Payload minimum: 17 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Tabel baseline; memuat sensor inti termasuk kecepatan.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm |  |
| `TPS_VOLTAGE` | 2 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TPS_DEGREES` | 3 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TPS_PERCENT` | 2 | 1 | 100/256 | 0 | 0..100 | % | Derivasi dari ADC TPS; belum ada kalibrasi zero throttle. |
| `TPS_ADC` | 2 | 1 | 1 | 0 | 0..255 | count |  |
| `ECT_VOLTAGE` | 4 | 1 | 5/256 | 0 | 0..5 | V |  |
| `ECT` | 5 | 1 | 1 | -40 | -40..150 | C |  |
| `IAT_VOLTAGE` | 6 | 1 | 5/256 | 0 | 0..5 | V |  |
| `IAT` | 7 | 1 | 1 | -40 | -40..100 | C |  |
| `MAP_VOLTAGE` | 8 | 1 | 5/256 | 0 | 0..5 | V |  |
| `MAP` | 9 | 1 | 1 | 0 | 0..255 | kPa |  |
| `BATTERY` | 12 | 1 | 0.1 | 0 | 5..20 | V |  |
| `SPEED` | 13 | 1 | 1 | 0 | 0..300 | km/h |  |
| `INJECTOR` | 14 | 2 | 1/200 | 0 | 0..50 | ms |  |
| `IGNITION_ADVANCE` | 16 | 1 | 0.5 | -64 | -70..70 | deg |  |

### 4.4 Tabel `10` — `dat10`

- Request: `72 05 71 10 08`
- Payload minimum: 17 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Layout sama dengan dat11; frame request berbeda.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm |  |
| `TPS_VOLTAGE` | 2 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TPS_DEGREES` | 3 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TPS_PERCENT` | 2 | 1 | 100/256 | 0 | 0..100 | % | Derivasi dari ADC TPS; belum ada kalibrasi zero throttle. |
| `TPS_ADC` | 2 | 1 | 1 | 0 | 0..255 | count |  |
| `ECT_VOLTAGE` | 4 | 1 | 5/256 | 0 | 0..5 | V |  |
| `ECT` | 5 | 1 | 1 | -40 | -40..150 | C |  |
| `IAT_VOLTAGE` | 6 | 1 | 5/256 | 0 | 0..5 | V |  |
| `IAT` | 7 | 1 | 1 | -40 | -40..100 | C |  |
| `MAP_VOLTAGE` | 8 | 1 | 5/256 | 0 | 0..5 | V |  |
| `MAP` | 9 | 1 | 1 | 0 | 0..255 | kPa |  |
| `BATTERY` | 12 | 1 | 0.1 | 0 | 5..20 | V |  |
| `SPEED` | 13 | 1 | 1 | 0 | 0..300 | km/h |  |
| `INJECTOR` | 14 | 2 | 1/200 | 0 | 0..50 | ms |  |
| `IGNITION_ADVANCE` | 16 | 1 | 0.5 | -64 | -70..70 | deg |  |

### 4.5 Tabel `16` — `dat16`

- Request: `72 05 71 16 02`
- Payload minimum: 21 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Ekstensi ride-by-wire. RPM hanya dipakai bila tabel utama gagal. Injektor satu byte dengan skala 1/200 ms.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm |  |
| `AP1_VOLTAGE` | 2 | 1 | 5/256 | 0 | 0..5 | V |  |
| `AP2_VOLTAGE` | 3 | 1 | 5/256 | 0 | 0..5 | V |  |
| `AP1_DEGREES` | 4 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `AP2_DEGREES` | 5 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TP1_VOLTAGE` | 6 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TP2_VOLTAGE` | 7 | 1 | 5/256 | 0 | 0..5 | V |  |
| `TP1_DEGREES` | 8 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `TP2_DEGREES` | 9 | 1 | 0.5 | 0 | 0..127.5 | deg |  |
| `COR` | 10 | 1 | 1 | 0 | 0..255 | - |  |
| `ECT_VOLTAGE` | 11 | 1 | 5/256 | 0 | 0..5 | V |  |
| `ECT` | 12 | 1 | 1 | -40 | -40..150 | C |  |
| `IAT_VOLTAGE` | 13 | 1 | 5/256 | 0 | 0..5 | V |  |
| `IAT` | 14 | 1 | 1 | -40 | -40..100 | C |  |
| `MAP_VOLTAGE` | 15 | 1 | 5/256 | 0 | 0..5 | V |  |
| `MAP` | 16 | 1 | 1 | 0 | 0..255 | kPa |  |
| `BATTERY` | 17 | 1 | 0.1 | 0 | 5..20 | V |  |
| `SPEED` | 18 | 1 | 1 | 0 | 0..300 | km/h |  |
| `INJECTOR` | 19 | 1 | 1/200 | 0 | 0..50 | ms |  |
| `IGNITION_ADVANCE` | 20 | 1 | 0.5 | -64 | -70..70 | deg |  |

### 4.6 Tabel `20` — `dat20`

- Request: `72 05 71 20 F8`
- Payload minimum: 2 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Hanya tegangan O2 dan STF. AFR/lambda tidak disediakan ECU dan tidak diturunkan di host.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `O2` | 0 | 1 | 1/50,993698 | 0 | 0..5 | V | Tegangan O2 narrowband. Hanya mesin berputar |
| `STF` | 1 | 1 | 1/128,256516 | 0 | -2..2 | - | Short-term fuel trim. Hanya mesin berputar |

### 4.7 Tabel `D1` — `datD1`

- Request: `72 05 71 D1 47`
- Payload minimum: 7 byte
- Status layout: `RECONSTRUCTED`
- Catatan: Status gigi/netral dan flag. Nama fisik flag masih rekonstruksi.

| Sensor | Offset | Len | Skala | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `GEAR_STATE` | 0 | 1 | 1 | 0 | 0..255 | - |  |
| `FLAG_A` | 4 | 1 | 1 | 0 | 0..255 | - |  |
| `FLAG_B` | 5 | 1 | 1 | 0 | 0..255 | - |  |
| `GEAR_POSITION` | 6 | 1 | 1 | 0 | 0..255 | - |  |

## 5. Contoh decoding sintetis (tabel `13`)

Payload 14 byte berikut dibuat untuk demonstrasi, bukan tangkapan ECU:

```text
0B B8 80 40 50 5A 40 40 80 64 78 01 90 80
```

| Sensor | Raw | Perhitungan | Hasil |
|---|---:|---|---:|
| `RPM` | `0B B8` | 3000 × 1 | 3000 rpm |
| `TPS_ADC` | `80` | 128 | 128 count |
| `TPS_PERCENT` | `80` | 128 × 100/256 | 50,0 % |
| `TPS_VOLTAGE` | `80` | 128 × 5/256 | 2,5 V |
| `TPS_DEGREES` | `40` | 64 × 0,5 | 32,0 deg |
| `ECT_VOLTAGE` | `50` | 80 × 5/256 | 1,5625 V |
| `ECT` | `5A` | 90 − 40 | 50 °C |
| `IAT_VOLTAGE` | `40` | 64 × 5/256 | 1,25 V |
| `IAT` | `40` | 64 − 40 | 24 °C |
| `MAP_VOLTAGE` | `80` | 128 × 5/256 | 2,5 V |
| `MAP` | `64` | 100 × 1 | 100 kPa |
| `BATTERY` | `78` | 120 × 0,1 | 12,0 V |
| `INJECTOR` | `01 90` | 400 × 1/200 | 2,0 ms |
| `IGNITION_ADVANCE` | `80` | 128 × 0,5 − 64 | 0,0 deg |

## 6. Status yang diketahui

| Item | Status | Bukti |
|---|---|---|
| Delapan parameter tampil di APK | `OWNER_REPORTED` | Laporan Otoy; bukan verifikasi independen |
| Layout tabel dan offset | `RECONSTRUCTED` | Analisis perangkat lunak pembanding dan pengujian parser |
| Penerimaan tabel oleh ECU | `UNVERIFIED` per tabel | Hanya transaksi wire yang dapat membuktikan |
| TPS 0% saat gas tertutup | `BUG_REPORTED` | Formula belum memakai offset/kalibrasi zero throttle |
| Skala baterai dan injektor | `UNVERIFIED` | Implementasi memakai 0,1 V/count dan 1/200 ms/count; contoh sintetis lama memakai 0,05 V dan 0,01 ms |
| DTC E5/E6 dan subkode > 15 | `BUG_REPORTED` | Audit source; belum diperbaiki |
| Pengujian kendaraan fisik | `NOT_TESTED` | Tidak ada pengujian independen |

## 7. Cara memakai tabel ini

1. Gunakan tabel sebagai hipotesis, bukan kebenaran final.
2. Kirim request hanya setelah inisialisasi K-Line berhasil.
3. Validasi header, length, echo service, echo tabel, dan checksum sebelum memakai payload.
4. Terapkan `scale` dan `bias` sesuai field; jangan menebak offset yang tidak tercantum.
5. Tampilkan state per sensor; jangan mengganti nilai yang gagal dengan nol.
6. Simpan log TX/RX mentah agar hasil dapat dibandingkan.
7. Laporkan tabel yang tidak dijawab ECU sebagai `NOT_RESPONDING`, bukan sebagai nilai 0.

## 8. Batas

- Layout dapat berbeda antar ECU, tahun, dan varian pasar.
- Tabel `16` dan `D1` adalah ekstensi; tidak semua ECU menjawabnya.
- Nama fisik flag pada `D1` masih rekonstruksi.
- Data O2 adalah tegangan narrowband, bukan AFR.
- Dokumen ini tidak mengklaim kompatibilitas kendaraan.
