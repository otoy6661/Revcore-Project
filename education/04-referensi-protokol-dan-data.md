# 04 — Referensi Protokol dan Data

Dokumen ini adalah pustaka rujukan untuk 16 topik yang paling sering dibutuhkan saat
membaca atau memperluas `:core`. Setiap topik mencantumkan asal, metode, status
validasi, contoh pemakaian, batas, dan catatan distribusi.

Aturan utama:

- Analisis bukan bukti kompatibilitas universal.
- Jangan menambah TX/RX, offset, atau rumus dari tebakan.
- Frame yang checksum-nya valid belum tentu berisi data yang diminta.
- Tidak ada materi di sini yang berstatus Hardware-observed atau Vehicle-tested.

## Ringkasan 16 topik

| No | Topik | Label bukti | Ringkas |
|---:|---|---|---|
| 1 | Format frame | Source-analyzed | Layout dan arti setiap byte |
| 2 | TX/RX dan echo | Source-analyzed | Arah data dan pembuangan echo |
| 3 | Struktur payload | Source-analyzed | Isi setelah service |
| 4 | Checksum | Verified (unit test) | Komplemen dua 8 bit |
| 5 | Offset payload | Synthetic | Posisi field di dalam payload |
| 6 | Panjang field | Source-analyzed | 1–2 byte; batas minimum frame |
| 7 | Endianness | Source-analyzed | Big-endian untuk 2 byte |
| 8 | Rumus konversi | Synthetic | `nilai = raw × scale + bias` |
| 9 | Pemetaan sensor | Reconstructed | Generik; katalog bawaan kosong |
| 10 | Respons positif | Synthetic | Contoh frame DTC |
| 11 | Respons negatif | Synthetic | Service `0x7F` |
| 12 | Contoh kegagalan | Synthetic | Timeout, checksum, frame pendek |
| 13 | Perbedaan family ECU | Source-analyzed | Katalog kosong, tanpa pinjam frame |
| 14 | Wake-up dan timing | Reconstructed | 70/130 ms @10400 |
| 15 | Tester present | Source-analyzed | Keep-alive sebelum query |
| 16 | Lookup DTC | Source-analyzed | 15 kode dikenal; sisanya mentah |

## 1. Format frame

**Asal.** `core/comm/HondaFrameCodec.kt`, `docs/FRAME_PARSER.md`, `docs/KLINE_PROTOCOL.md`.

**Metode.** Analisis source dan pembacaan struktur frame pada dokumentasi protokol.

**Status validasi.** Source-analyzed. Bentuk frame diuji oleh unit test codec, bukan oleh
ECU fisik.

**Contoh pemakaian.**

```text
[header] [length] [service] [param...] [checksum]

72       05       74        01          14
```

- `header` request baseline: `0x72`.
- `length` adalah ukuran total frame, termasuk header, length, service, parameter, dan
  checksum. Frame di atas berukuran 5 byte.
- `service` menentukan operasi.
- `checksum` adalah byte terakhir.

Header yang dikenal kode: `0x72`, `0x02`, `0x7E`, `0x7C`, `0x7D`, `0x7B`, `0xFE`.

**Batas.** Header yang dikenal bukan berarti semua header berlaku untuk semua family.
Nilai dan arti header response bergantung pada family ECU.

**Catatan distribusi.** Deskripsi generik; tidak memuat tabel proprietary.

## 2. TX, RX, dan echo

**Asal.** `core/comm/HondaFrameCodec.kt` (`stripLeadingEcho`), `KlineEchoFilter.kt`,
`docs/FRAME_PARSER.md` §Echo.

**Metode.** Analisis source dan unit test filter echo.

**Status validasi.** Source-analyzed; perilaku adapter nyata belum diuji.

**Contoh pemakaian.** Adapter mengirim kembali byte TX di awal RX. Engine membuang echo
hanya bila byte itu persis sama dengan frame yang baru dikirim dan berada di awal buffer
setelah padding `0x00`. `0xFF` di awal tidak dibuang karena dapat merupakan data.

**Batas.** Echo tidak selalu identik; sebagian adapter menambahkan byte status. Jangan
menganggap pembuangan echo berhasil bila panjang atau isinya tidak cocok.

**Catatan distribusi.** Generik.

## 3. Struktur payload

**Asal.** `HondaFrameCodec.payload`, `HondaDtcDatabase`, `LiveDataProfile`.

**Metode.** Analisis source dan contoh sintetis.

**Status validasi.** Source-analyzed; contoh Synthetic.

**Contoh pemakaian.**

```text
frame   : 72 0A 74 01 02 01 01 07 01 03
service : 74
payload : 02 01 01 07 01
```

Payload adalah byte setelah service sampai sebelum checksum. Isinya bergantung pada
service: daftar DTC, identitas, atau nilai sensor.

**Batas.** Payload yang checksum-nya valid tetap dapat tidak dapat di-parse. Parser harus
mengembalikan kegagalan, bukan daftar kosong.

**Catatan distribusi.** Generik; tidak ada katalog proprietary.

## 4. Checksum

**Asal.** `core/protocol/HondaChecksum.kt`, `docs/CHECKSUM.md`.

**Metode.** Analisis source dan pengujian unit (`HondaFrameCodecTest`).

**Status validasi.** Verified pada level unit test: checksum dihitung dan divalidasi
terhadap frame sintetis.

**Contoh pemakaian.**

```text
frame tanpa checksum : 72 05 74 01
sum                  : 0x72 + 0x05 + 0x74 + 0x01 = 0xEC
checksum             : 0x100 - 0xEC = 0x14
frame lengkap        : 72 05 74 01 14
```

Rumus:

```text
checksum = (256 - (sum(header..payload) & 0xFF)) & 0xFF
```

Jumlah seluruh byte frame, termasuk checksum, menjadi `0x00` pada 8 bit.

**Batas.** Checksum mendeteksi kerusakan, bukan keaslian. Frame dengan checksum benar
dapat berasal dari sumber yang salah atau berisi respons negatif.

**Catatan distribusi.** Algoritma generik.

## 5. Offset payload

**Asal.** `LiveDataProfile.kt` (`LiveFieldSpec.offset`), `HondaDtcDatabase`,
`docs/LIVE_DATA_ENGINE.md`.

**Metode.** Analisis source; contoh field memakai tabel sintetis `0x7A`.

**Status validasi.** Synthetic. Offset tabel Live Data proprietary telah ditarik dari
repository; hanya model dan contoh sintetis yang tersisa.

**Contoh pemakaian.** Field didefinisikan relatif terhadap awal payload, bukan awal
frame. Pada contoh `0x7A`, payload `0B B8 80 40 50 40 00 64 F0 3C 5A C8` didekode
menjadi antara lain RPM 3000, TPS ADC 128, dan ECT 40 °C (lihat
`LiveDataProfileParserTest.kt`).

**Batas.** Offset tidak boleh ditebak dari urutan tampilan. Offset yang salah dapat
menghasilkan angka yang terlihat masuk akal tetapi salah.

**Catatan distribusi.** Katalog nyata tidak disertakan karena status hak penggunaan belum
jelas.

## 6. Panjang field

**Asal.** `LiveFieldSpec.length`, `LiveTableProfile.minPayload`, `minFrameSize`,
`HondaDtcDatabase`.

**Metode.** Analisis source.

**Status validasi.** Source-analyzed.

**Contoh pemakaian.**

- Field Live Data: 1 atau 2 byte.
- Frame minimum: `payloadStart + minPayload + 1` (checksum).
- Payload DTC count-prefixed: minimum `1 + count × 2` byte.
- Subcode DTC: nilai 4 bit (0–15).

**Batas.** Parser harus menolak payload yang lebih pendek dari minimum. Jangan membaca
byte checksum sebagai data.

**Catatan distribusi.** Generik.

## 7. Endianness

**Asal.** `LiveDataProfile.kt` dan `docs/LIVE_DATA_ENGINE.md`.

**Metode.** Analisis source.

**Status validasi.** Source-analyzed; contoh Synthetic.

**Contoh pemakaian.** Field 2 byte dibaca **big-endian**: byte pertama adalah high byte.

```text
0B B8 -> (0x0B << 8) | 0xB8 = 0x0BB8 = 3000
```

**Batas.** Tidak semua protokol memakai big-endian. Jika membuat profil untuk protokol
lain, tentukan urutannya dari documentation atau tangkapan, bukan dari kebiasaan.

**Catatan distribusi.** Generik.

## 8. Rumus konversi

**Asal.** `LiveFieldSpec.scale`, `bias`, `validMin`, `validMax`, `docs/SENSOR_DECODING.md`.

**Metode.** Analisis source dan contoh sintetis.

**Status validasi.** Synthetic.

**Contoh pemakaian.**

```text
nilai = raw × scale + bias
```

Contoh: ECT dengan `raw = 0x50` (80), `scale = 1`, `bias = -40` menghasilkan 40 °C.

Setelah konversi, nilai harus diperiksa terhadap `validMin`/`validMax`. Nilai di luar
rentang ditandai tidak valid, bukan ditampilkan sebagai fakta.

**Batas.** Rumus hanya berlaku untuk profil yang mendefinisikannya. Jangan memakai rumus
Honda untuk family lain.

**Catatan distribusi.** Generik; konstanta proprietary tidak disertakan.

## 9. Pemetaan sensor

**Asal.** `LiveSensor` (32 nilai), `HondaLiveDataProfiles` (kosong),
`docs/SENSOR_DECODING.md`.

**Metode.** Analisis source; katalog ditarik.

**Status validasi.** Reconstructed untuk model generik; katalog nyata `UNVERIFIED` dan
tidak dipublikasikan.

**Contoh pemakaian.** Pemanggil membuat `LiveTableProfile` sendiri: daftar `LiveFieldSpec`
dengan sensor, offset, panjang, skala, bias, satuan, dan label `REFERENCE` atau
`UNVERIFIED`.

**Batas.** AFR tidak dihitung dari sensor O2 narrowband. Jika ECU tidak mempublikasikan
data lebar pita, hasilnya `UNSUPPORTED`, bukan `14.7`.

**Catatan distribusi.** Tidak ada tabel sensor proprietary di repository.

## 10. Contoh respons positif

**Asal.** `docs/DTC_ENGINE.md` §3.1 dan fixture `examples/`.

**Metode.** Contoh sintetis dengan checksum valid.

**Status validasi.** Synthetic; struktur cocok dengan parser.

**Contoh pemakaian.**

```text
frame   : 72 0A 74 01 02 01 01 07 01 03
payload : 02 01 01 07 01
           │  └┬┘ └┬┘
         count  P0107 P0117
hasil   : P0107, P0117
```

**Batas.** Tidak membuktikan urutan query yang sama berlaku pada ECU nyata. Engine tetap
mencoba kandidat query secara berurutan.

**Catatan distribusi.** Frame sintetis; bukan tangkapan kendaraan.

## 11. Contoh respons negatif

**Asal.** `docs/DTC_ENGINE.md` §9, `HondaFrameCodec.NEGATIVE_RESPONSE_SERVICE`.

**Metode.** Contoh sintetis.

**Status validasi.** Synthetic.

**Contoh pemakaian.**

```text
frame   : 72 06 7F 74 12 83
service : 7F  -> ECU menolak service 0x74
```

Frame valid secara checksum, tetapi bukan daftar DTC. Parser tidak boleh mengembalikan
daftar kosong yang terlihat seperti "tidak ada DTC". Pada jalur baca, engine mencoba
query berikutnya; bila semua gagal, hasil akhirnya `NO_RESPONSE`.

**Batas.** Arti kode alasan `0x12` tidak diklaim tanpa dokumentasi family.

**Catatan distribusi.** Generik.

## 12. Contoh kegagalan

**Asal.** `docs/FRAME_PARSER.md`, `docs/DEBUGGING_GUIDE.md`, unit test.

**Metode.** Analisis source dan skenario sintetis.

**Status validasi.** Source-analyzed; perilaku hardware belum diuji.

**Contoh pemakaian.**

| Skenario | Gejala | Tindakan benar |
|---|---|---|
| Checksum salah | Byte terakhir tidak cocok | Buang frame, cari frame berikutnya |
| Frame pendek | Declared length melebihi byte tersedia | Tunggu sampai batas waktu, lalu gagal |
| Hanya echo | RX sama dengan TX | Buang echo, jangan parse sebagai response |
| Timeout | Tidak ada frame valid | Laporkan `NO_RESPONSE`, pertahankan data lama |
| Service `0x7F` | ECU menolak | Coba kandidat berikutnya, jangan anggap kosong |

**Batas.** Jangan "memperbaiki" checksum atau mengarang payload agar alur terlihat
berhasil.

**Catatan distribusi.** Generik.

## 13. Perbedaan family ECU

**Asal.** `HondaEcuProfiles.kt`, `EcuFamily.kt`, `docs/ARCHITECTURE.md`.

**Metode.** Analisis source.

**Status validasi.** Source-analyzed. Baseline Honda PGM-FI memiliki katalog; family lain
memiliki katalog kosong dengan alasan tertulis.

**Contoh pemakaian.** Family tanpa katalog mengembalikan `NoResponse`,
`UNSUPPORTED_FAMILY`, atau `null`; engine tidak mengirim frame dan tidak meminjam frame
family lain.

**Batas.** Header respons berbeda antar family. Menyamakan semua family adalah klaim
kompatibilitas yang tidak didukung bukti.

**Catatan distribusi.** Nama family disebut untuk kejelasan teknis; tidak ada frame
proprietary yang disertakan.

## 14. Wake-up dan timing K-Line

**Asal.** `KlineWireEngine.kt`, `HondaEcuProfiles.kt`, `docs/KLINE_PROTOCOL.md`.

**Metode.** Rekonstruksi dari analisis dan dokumentasi protokol.

**Status validasi.** Reconstructed; **belum** Hardware-observed di repository ini.

**Contoh pemakaian.** Baseline: wake pulse 70 ms rendah, 130 ms tinggi, baud 10400;
percobaan 9600 pada beberapa jalur.

**Batas.** Timing dapat berbeda per ECU/adapter. Jangan mengklaim keberhasilan wake
tanpa pengukuran atau respons nyata.

**Catatan distribusi.** Generik; bukan jaminan kompatibilitas.

## 15. Tester present

**Asal.** `HondaEcuProfiles.kt` (`testerPresent`), `docs/CHECKSUM.md` §3.1.

**Metode.** Analisis source.

**Status validasi.** Source-analyzed; frame contoh pada dokumentasi checksum adalah
Synthetic.

**Contoh pemakaian.** Tester present dikirim untuk menjaga sesi sebelum query identitas
atau diagnostik. Ia termasuk dalam daftar `identification` pada profil.

**Batas.** Interval dan kebutuhan tester present bergantung pada ECU. Jangan menganggap
satu frame cukup untuk semua family.

**Catatan distribusi.** Generik.

## 16. Lookup DTC

**Asal.** `HondaDtcDatabase.kt`, `docs/DTC_ENGINE.md` §4.

**Metode.** Analisis source.

**Status validasi.** Source-analyzed; 15 kode dikenal. Arti kode mengikuti database repo,
bukan buku servis yang diverifikasi.

**Contoh pemakaian.**

| Code | Sub | Standard | Judul |
|---:|---:|---|---|
| 1 | 1 | P0107 | MAP Sensor Voltage Low |
| 1 | 2 | P0108 | MAP Sensor Voltage High |
| 7 | 1 | P0117 | ECT/EOT Voltage Low |
| 8 | 1 | P0122 | TP Sensor Voltage Low |
| 54 | 1 | P1601 | Bank Angle Sensor |

Kode di luar tabel:

```text
standardCode = "DTC XXYY"
title        = "Kode ECM 0xXXYY (belum terverifikasi)"
isKnown      = false
```

**Batas.** Jangan mengarang P-code atau deskripsi gejala. Kode tak dikenal tetap
ditampilkan sebagai kode mentah.

**Catatan distribusi.** Tabel 15 kode berasal dari database repository; bukan database
lengkap pihak ketiga.

## Cara memakai pustaka ini dengan benar

1. Sebelum memakai angka, cari topiknya di sini dan buka file asal.
2. Periksa label bukti. Jika Synthetic atau Reconstructed, jangan menulis "terbukti di
   kendaraan".
3. Tambahkan test sintetis untuk setiap perilaku baru.
4. Dokumentasikan batas yang belum diketahui, bukan menyembunyikannya.
5. Jika menemukan konflik antara dokumen dan source, source adalah perilaku saat ini;
   dokumen harus diperbaiki, bukan source ditafsirkan ulang tanpa bukti.
