# SENSOR DECODING

Dokumen ini menjelaskan model decoding sensor Live Data yang dipakai `:core`. Modelnya
generik; layout yang ditampilkan di sini adalah **contoh sintetis milik repository ini**,
bukan layout ECU Honda dan bukan hasil transkrip firmware pihak ketiga.

## 1. Status materi

Repository ini tidak menyertakan layout, offset, skala, atau request frame Live Data Honda.
Materi tersebut pernah dianalisis, tetapi hak penggunaannya belum diselesaikan, sehingga
ditarik dari materi publik dan dari katalog bawaan `:core`.

Yang tetap tersedia:

- kelas `LiveTableProfile` dan `LiveFieldSpec`,
- `LiveDataFrameParser` generik,
- validasi frame dan checksum,
- aturan `0xFF` alternatif,
- state per parameter dan gating mesin berputar,
- contoh sintetis `0x7A` beserta unit test.

## 2. Model field

`LiveFieldSpec` mendeskripsikan satu sensor di dalam payload:

| Properti | Arti |
|---|---|
| `sensor` | Identifier `LiveSensor` |
| `offset` | Offset byte di dalam payload (0-based) |
| `length` | 1 atau 2 byte, big-endian |
| `scale` | Pengali |
| `bias` | Penambah setelah skala |
| `validMin` / `validMax` | Rentang nilai akhir |
| `unit` | Satuan |
| `alternateOffset` | Offset cadangan bila byte utama `0xFF` |
| `engineRunningOnly` | Hanya bermakna saat RPM > 0 |

Nilai akhir dihitung sebagai:

```text
value = raw * scale + bias
```

`raw` dibaca big-endian. Untuk `length = 2`, byte pertama adalah high byte.

## 3. Batas payload

`LiveTableProfile.payloadStart` menentukan index byte payload pertama di dalam frame
respons. Untuk frame contoh:

```text
header | length | service echo | table echo | payload ... | checksum
  0        1          2              3           4..           n-1
```

Pada contoh ini `payloadStart = 4` karena ada echo service `0x71` dan echo tabel `0x7A`.
Profil lain dapat memakai `payloadStart` berbeda; parser tidak mengasumsikan layout tetap.

## 4. Contoh tabel sintetis 0x7A

Fixture: `examples/sensor_data/live_data.json`.

Request:

```text
72 05 71 7A 9E
```

Respons:

```text
72 11 71 7A 0B B8 80 40 50 40 00 64 F0 3C 5A C8 CD
```

Payload (12 byte, mulai index 4):

```text
0B B8 80 40 50 40 00 64 F0 3C 5A C8
```

Layout:

| Sensor | Offset | Len | Scale | Bias | Rentang | Satuan | Catatan |
|---|---:|---:|---:|---:|---|---|---|
| `RPM` | 0 | 2 | 1 | 0 | 0..20000 | rpm | |
| `TPS_ADC` | 2 | 1 | 1 | 0 | 0..255 | count | byte ADC mentah |
| `TPS_PERCENT` | 2 | 1 | 100/255 | 0 | 0..100 | % | dari byte yang sama |
| `TPS_VOLTAGE` | 2 | 1 | 5/255 | 0 | 0..5 | V | dari byte yang sama |
| `TPS_DEGREES` | 3 | 1 | 1 | 0 | -20..120 | deg | contoh pemetaan terpisah |
| `ECT` | 4 | 1 | 1 | -40 | -40..150 | C | alternate offset 10 |
| `IAT` | 5 | 1 | 1 | -40 | -40..150 | C | |
| `MAP` | 6 | 2 | 1 | 0 | 0..5000 | kPa | |
| `BATTERY` | 8 | 1 | 0.05 | 0 | 5..20 | V | |
| `SPEED` | 9 | 1 | 1 | 0 | 0..300 | km/h | |
| `INJECTOR` | 11 | 1 | 0.01 | 0 | 0..50 | ms | |

Payload byte 10 (`0x5A`) adalah byte ECT alternatif. Ia hanya dipakai bila byte utama
(payload offset 4) bernilai `0xFF`.

## 5. Decoding contoh

| Sensor | Raw | Perhitungan | Hasil |
|---|---|---|---|
| `RPM` | `0B B8` | `3000 × 1` | 3000 rpm |
| `TPS_ADC` | `80` | `128` | 128 count |
| `TPS_PERCENT` | `80` | `128 × 100/255` | 50.196 % |
| `TPS_VOLTAGE` | `80` | `128 × 5/255` | 2.5098 V |
| `TPS_DEGREES` | `40` | `64 × 1` | 64 deg |
| `ECT` | `50` | `80 - 40` | 40 C |
| `IAT` | `40` | `64 - 40` | 24 C |
| `MAP` | `00 64` | `100 × 1` | 100 kPa |
| `BATTERY` | `F0` | `240 × 0.05` | 12.0 V |
| `SPEED` | `3C` | `60 × 1` | 60 km/h |
| `INJECTOR` | `C8` | `200 × 0.01` | 2.00 ms |

Byte ECT alternatif `0x5A` diabaikan karena byte utama `0x50` bukan `0xFF`.

## 6. Aturan `0xFF`

- Jika byte utama bukan `0xFF`, alternate tidak dibaca.
- Jika byte utama `0xFF` dan `alternateOffset` dideklarasikan, parser membaca alternate.
- Jika byte utama `0xFF` dan tidak ada alternate, field ditolak dengan
  `INVALID_RESPONSE`.
- Nilai `0xFF` tidak pernah diperlakukan sebagai angka mentah.

## 7. Validasi dan state

Setiap field diperiksa setelah skala dan bias:

- di luar `validMin..validMax` → `INVALID_RESPONSE`;
- payload terlalu pendek → `NOT_RESPONDING`;
- belum ada data → `WAITING_FOR_DATA`;
- data lama tidak diperbarui → `STALE`;
- sensor khusus mesin berputar dan RPM = 0 → `NOT_AVAILABLE_IN_KOEO`;
- sensor tidak didefinisikan profil → `UNSUPPORTED`.

Field yang gagal **tidak** diubah menjadi nol. Nilai tidak dimasukkan ke `values`, dan UI
harus membaca `parameterStates`/`stateOf()`.

## 8. Gating mesin berputar

Field dengan `engineRunningOnly = true` (mis. O2 dan STF bila didefinisikan) hanya
ditampilkan sebagai `VALID` bila:

- data frame valid, dan
- `RPM` valid dan > 0.

Saat key-on/engine-off, nilai tersebut menjadi `NOT_AVAILABLE_IN_KOEO`, bukan `0`.

## 9. AFR

AFR selalu `UNSUPPORTED`. Repository ini tidak menghitung AFR/lambda dari tegangan O2
narrowband. ECU yang hanya mempublikasikan tegangan O2 tidak menyediakan AFR numerik;
menurunkannya di host akan menghasilkan angka yang terlihat pasti tetapi tidak berasal
dari ECU.

## 10. Menambahkan layout sendiri

1. Pastikan Anda memiliki hak untuk menggunakan dan mempublikasikan layout tersebut.
2. Buat `LiveTableProfile` dengan request frame lengkap termasuk checksum.
3. Tentukan `payloadStart`, `minPayload`, dan `minFrameSize`.
4. Tambahkan `LiveFieldSpec` untuk setiap sensor, termasuk rentang valid.
5. Tandai `verification` sesuai bukti: `VERIFIED`, `RECONSTRUCTED`, `REFERENCE`, atau
   `HYPOTHESIS`.
6. Tambahkan unit test dengan payload sintetis dan frame negatif (checksum salah,
   payload pendek, nilai di luar rentang).
7. Jangan menambahkan layout ke katalog bawaan tanpa persetujuan pemilik dan bukti
   tangkapan perangkat keras.

## 11. Batas

- Contoh `0x7A` tidak membuktikan ECU mana pun menerima tabel tersebut.
- Offset dan skala contoh hanya untuk demonstrasi parser.
- Tidak ada klaim kompatibilitas kendaraan.
- Tidak ada perhitungan AFR/lambda di host.
- Validasi fixture hanya membuktikan konsistensi internal frame, bukan penerimaan ECU.
