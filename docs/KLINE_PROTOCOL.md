# PROTOKOL K-LINE

Dokumen ini menjelaskan format frame, header, service, timing, dan katalog request yang dipakai `:core`. Semua contoh adalah frame sintetis atau frame katalog; tidak ada rekaman ECU nyata.

## 1. Lapisan fisik dan parameter

| Parameter | Nilai |
|---|---|
| Baud identifikasi | 10400 bps |
| Fallback identifikasi | 9600 bps |
| Data / stop / parity | 8N1 |
| Flow control | None |
| Wake LOW | 70 ms (host-side) |
| Wake HIGH | 130 ms (host-side) |
| Level idle | HIGH |
| Wake | BREAK LOW, lalu HIGH |

Wake pulse diukur sebagai waktu host. Nilai ini tidak membuktikan durasi listrik di bus.

## 2. Anatomi frame

```text
+--------+--------+---------+------------------+----------+
| header | length | service |     parameter    | checksum |
+--------+--------+---------+------------------+----------+
    1B       1B       1B          N byte            1B
```

- `header` — pemilih arah/family.
- `length` — **jumlah total byte frame, termasuk checksum**.
- `service` — operasi (query ID, live data, DTC, dan sebagainya).
- `parameter` — selector tabel, subfungsi, atau data.
- `checksum` — additive 8-bit two's complement dari seluruh byte sebelumnya.

Panjang minimum 4 byte. Panjang maksimum yang dijaga codec adalah `0xFE` (254). Frame dengan panjang deklarasi kurang dari 4 atau melebihi buffer ditolak.

## 3. Header

| Nilai | Nama | Pemakaian |
|---:|---|---|
| `0x72` | baseline request/response | Request utama dan respons baseline |
| `0x02` | diagnostic response | Respons diagnostik; juga header ACK clear |
| `0x7E` | Keihin response | Kandidat respons family Keihin |
| `0x7C` | Shindengen response | Kandidat respons family Shindengen |
| `0xFE` | broadcast | Tester present dan frame broadcast |
| `0x7D` | KWP request primary | Request KWP; katalog belum direkonstruksi |
| `0x7B` | KWP request alternate | Request KWP; katalog belum direkonstruksi |

`0x7E` dan `0x7C` hanya memilih profil routing. Keduanya belum terbukti sebagai identitas kendaraan.

## 4. Service yang terlihat

| Service | Arti di repo | Status |
|---:|---|---|
| `0x00` | query data / DTC baseline | Direkonstruksi |
| `0x1A` | query identifikasi fallback | Direkonstruksi |
| `0x21` | PID fallback Live Data | Direkonstruksi, dibatasi |
| `0x60` | clear DTC | Direkonstruksi |
| `0x71` | query ID / tabel Live Data | Direkonstruksi |
| `0x72` | query ID `72 00` / service live tertentu | Direkonstruksi |
| `0x74` | baca/clear DTC | Direkonstruksi |
| `0x7F` | respons negatif | Terverifikasi dari bentuk frame |
| `0xC1` | service live data pada respons | Terlihat di katalog service |

`KlinePacket` juga memuat konstanta untuk security access (`0x27`), read memory (`0x3C`), write memory (`0x3D`), dan actuator test (`0x01`). Konstanta itu **bukan** katalog yang dijalankan `:core`; tidak ada operasi tulis/security yang dikirim oleh engine diagnostik.

## 5. Request katalog baseline

### 5.1 Identifikasi dan presence

| Label | Frame | Panjang | Catatan |
|---|---|---:|---|
| Tester present | `FE 04 72 8C` | 4 | Broadcast, tanpa parameter |
| Query ID `00/F0` | `72 05 00 F0 99` | 5 | Selector `F0` |
| Query ID `72/00` | `72 07 72 00 00 05 10` | 7 | Selector `00 00`, field length `05` |
| Query ID `71/00` | `72 05 71 00 18` | 5 | Query part number |

### 5.2 Live Data

`:core` tidak menyertakan katalog tabel live data bawaan. Tabel Honda yang pernah
dianalisis tidak disertakan karena hak penggunaannya belum diselesaikan, dan `queryLiveData()`
mengembalikan `UNSUPPORTED_PROFILE` tanpa mengirim frame.

Bentuk request tabel bersifat generik: `[header][panjang][service 0x71][selector tabel][checksum]`.
Contoh sintetis yang dipakai fixture repository adalah:

```text
72 05 71 7A 9E
```

Di sini `0x7A` adalah selector tabel contoh (bukan tabel Honda) dan `0x9E` adalah checksum.
Pemanggil yang memiliki tabel sendiri dapat mendaftarkan `LiveTableProfile` beserta request
frame-nya; lihat `docs/LIVE_DATA_ENGINE.md` dan `docs/SENSOR_DECODING.md`.

### 5.3 DTC

| Operasi | Frame | Panjang |
|---|---|---:|
| Baca `74/01` | `72 05 74 01 14` | 5 |
| Baca `71/20` | `72 05 71 20 F8` | 5 |
| Baca `00/00` | `72 05 00 00 89` | 5 |
| Clear `60/01` | `72 05 60 01 28` | 5 |
| Clear `74/02` | `72 05 74 02 13` | 5 |
| Clear `04` | `72 04 04 86` | 4 |

## 6. Checksum

Rumus:

```text
sum   = jumlah seluruh byte sebelum checksum (mod 256)
check = (256 - sum) mod 256
```

Contoh `FE 04 72`:

```text
0xFE + 0x04 + 0x72 = 0x174
0x174 mod 256      = 0x74
(256 - 0x74)       = 0x8C
frame              = FE 04 72 8C
```

Detail dan contoh lain ada di [CHECKSUM.md](CHECKSUM.md).

## 7. Timing host

| Operasi | Timeout | Jeda setelah TX |
|---|---:|---:|
| Tester present | 600 ms | 10 ms |
| Query ID | 800 ms | 10 ms |
| Live probe | 300 ms | 5 ms |
| Live Data | 300 ms | 2 ms |
| DTC | 700 ms | 0 ms |
| Clear DTC | 800 ms | 0 ms |
| Fallback presence | 500 ms | 0 ms |
| `0x1A` | 700 ms | 0 ms |

Nilai ini adalah batas host, bukan timing ECU. Timeout tidak boleh diperlakukan sebagai bukti ECU mati.

## 8. Echo

Adapter dapat mengembalikan frame yang baru dikirim. Engine:

1. membandingkan awal RX dengan request dan membuangnya jika identik,
2. menolak kandidat frame yang identik dengan TX dalam 1500 ms terakhir,
3. menandai `LOCAL_ECHO_ONLY` jika seluruh RX hanya berisi TX sendiri.

Frame echo yang kebetulan checksum-valid tetap bukan respons.

## 9. Respons negatif

Service `0x7F` berarti ECU menolak permintaan. Perlakuan:

- identifikasi: respons negatif tidak menghasilkan ID,
- DTC read: query dilanjutkan ke kandidat berikutnya,
- clear: kandidat berikutnya dicoba,
- Live Data: frame negatif tidak diparse.

Respons negatif **bukan** bukti bahwa ECU tidak ada; ia justru bukti ECU menerima dan menolak perintah.

## 10. Batas dan larangan

- `:core` hanya mengirim request yang ada di katalog. Tidak ada frame security access, read/write memory, atau actuator test yang dikirim oleh operasi diagnostik.
- Family Keihin KWP, Shindengen, dan Denso memiliki katalog kosong. Mengirim frame family lain dilarang oleh desain.
- Panjang frame tidak boleh melebihi `0xFE`; frame lebih besar ditolak sebelum dikirim.
- Keberhasilan checksum hanya membuktikan integritas byte, bukan keaslian ECU.

## 11. Status bukti

| Elemen | Status |
|---|---|
| Rumus checksum aditif | `BINARY_EVIDENCE` |
| Batas panjang 0xFE | `BINARY_EVIDENCE` |
| Wake 70/130 ms | `RECONSTRUCTED` (host-side) |
| Katalog identifikasi baseline | `RECONSTRUCTED` |
| Katalog Live Data bawaan | `UNRESOLVED` (ditarik; hanya parser generik) |
| Header 0x7E/0x7C → family | `HYPOTHESIS` |
| Frame family selain baseline | `UNRESOLVED` |
