# LIVE DATA ENGINE

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini menjelaskan mesin Live Data generik di `:core`: bagaimana tabel dipilih,
dipolling, di-cache, dan bagaimana parameter yang tidak tersedia dilaporkan.

## 1. Tidak ada katalog bawaan

`:core` **tidak** mengirim tabel Live Data apa pun secara default.

```kotlin
object HondaLiveDataProfiles {
    val primaryTables: List<LiveTableProfile> = emptyList()
    val extendedTables: List<LiveTableProfile> = emptyList()
    val tables: List<LiveTableProfile> = emptyList()
    val all: List<LiveTableProfile> = emptyList()
    fun forTable(tableId: String): LiveTableProfile? = null
    val baseline: LiveTableProfile? = null
}
```

Alasannya: katalog bawaan sengaja kosong agar `:core` tidak mengirim frame tanpa
persetujuan pemanggil. Tabel hasil rekonstruksi RevCore tetap dipublikasikan sebagai data
di [LIVE_DATA_REFERENCE.md](LIVE_DATA_REFERENCE.md) dan `data/live_data/live_tables.json`;
semuanya berstatus `RECONSTRUCTED` dan belum diverifikasi oleh tangkapan perangkat keras.
Pemanggil mendaftarkannya secara eksplisit untuk mengaktifkan polling.

Konsekuensi perilaku:

- `HondaEcuProfiles.genericBaseline.hasLiveDataCatalog` bernilai `false`;
- `queryLiveData()` langsung mengembalikan `null` dan menetapkan
  `LiveDataState.UNSUPPORTED_PROFILE`;
- tidak ada frame yang ditransmisikan pada jalur ini;
- engine legacy juga menolak mengirim query Live Data.

## 2. Menyediakan profil sendiri

Parser dan mekanisme polling tetap lengkap. Pemanggil dapat membuat `LiveTableProfile`
miliknya sendiri, lalu:

- memanggil `LiveDataFrameParser.parse(payload, profile)` secara langsung, atau
- mendaftarkan request frame pada `HondaWireProfile` yang dikendalikan pemanggil.

Field penting pada `LiveTableProfile`:

| Field | Arti |
|---|---|
| `id` | Identifier tabel |
| `requestFrame` | Frame request lengkap termasuk checksum |
| `payloadStart` | Index byte payload pertama di dalam frame respons |
| `minPayload` | Jumlah byte payload minimum |
| `minFrameSize` | Ukuran frame minimum yang diterima |
| `verification` | `VERIFIED`, `RECONSTRUCTED`, `REFERENCE`, atau `HYPOTHESIS` |
| `fields` | Daftar `LiveFieldSpec` |

Field penting pada `LiveFieldSpec`:

| Field | Arti |
|---|---|
| `sensor` | Salah satu nilai `LiveSensor` |
| `offset` | Offset byte di dalam payload |
| `length` | 1 atau 2 byte, big-endian |
| `scale` | Pengali |
| `bias` | Penambah (mis. `-40.0` untuk suhu) |
| `validMin` / `validMax` | Rentang nilai setelah skala dan bias |
| `unit` | Satuan tampilan |
| `alternateOffset` | Offset cadangan bila byte utama `0xFF` |
| `engineRunningOnly` | Nilai hanya bermakna saat mesin berputar |

Contoh sintetis lengkap ada di `examples/sensor_data/live_data.json` dan diuji oleh
`LiveDataProfileParserTest.kt` (tabel `0x7A`).

## 3. Cache tabel

- Tabel yang berhasil disimpan sebagai `cachedLiveTable`.
- Polling berikutnya mencoba tabel cache lebih dulu.
- Tiga kegagalan berturut-turut membuang cache.
- Cache hanya menghindari probing ulang; cache tidak membuktikan tabel masih didukung ECU.

## 4. Rotasi extension

Jika profil mendeklarasikan tabel extension setelah tabel utama berhasil:

- extension dipolling satu per siklus,
- ada jarak minimum antar-polling extension,
- tabel yang gagal berulang dilewati untuk sementara,
- nilai lama tabel tersebut ditarik saat dilewati,
- nilai primary menang atas extension untuk sensor yang sama.

Tujuan rotasi adalah agar satu extension yang tidak menjawab tidak menahan seluruh siklus.
Dengan katalog bawaan yang kosong, jalur ini tidak aktif.

## 5. Validasi frame

Sebuah respons Live Data dipakai hanya jika:

1. ukuran minimal sesuai profil,
2. header dikenal,
3. service bukan `0x7F`,
4. echo tabel sama dengan tabel yang diminta.

Jika echo tidak cocok, frame tidak diparse. Layout tabel lain tidak boleh diterapkan.

## 6. Parsing field

`LiveDataFrameParser.parse`:

1. memilih offset utama; jika byte utama `0xFF` dan field punya alternate, coba alternate,
2. membaca `length` byte big-endian,
3. menerapkan `scale` dan `bias`,
4. memeriksa `validMin..validMax`,
5. menandai `VALID`, `INVALID_RESPONSE`, atau `NOT_RESPONDING`.

Field yang gagal **tidak** diubah menjadi nol. Nilainya tidak masuk `values`.

`dataValid` benar jika minimal satu field valid. RPM tidak menjadi gerbang, sehingga frame
key-on/engine-off tetap dapat menampilkan TPS, ECT, IAT, dan battery.

## 7. State parameter

| State | Arti |
|---|---|
| `VALID` | Nilai lolos pemeriksaan |
| `NOT_RESPONDING` | Payload terlalu pendek / tidak ada data |
| `NOT_AVAILABLE_IN_KOEO` | Hanya bermakna saat mesin berputar |
| `UNSUPPORTED` | ECU/profil tidak menyediakan |
| `INVALID_RESPONSE` | Nilai di luar rentang atau byte utama tidak valid |
| `WAITING_FOR_DATA` | Belum ada polling sukses |
| `STALE` | Nilai terakhir tidak lagi diperbarui |

UI wajib memakai `parameterStates`/`stateOf()`, bukan nilai default 0.

## 8. Engine running

Sensor dengan `engineRunningOnly=true` hanya ditampilkan bila RPM valid dan > 0.

Jika RPM tidak valid atau 0, nilai yang sempat terbaca diubah menjadi
`NOT_AVAILABLE_IN_KOEO`. Nilai tidak dihapus dari hasil parser, tetapi tidak ditampilkan
sebagai valid. Contoh sensor tipikal: O2 dan STF (bila profil mendefinisikannya).

## 9. AFR

AFR selalu `UNSUPPORTED`.

Alasan:

- ECU hanya mempublikasikan tegangan O2 narrowband dan STF.
- Tidak ada kanal AFR/lambda numerik.
- Menghitung AFR dari tegangan O2 adalah derivasi host, bukan data ECU.

Engine menambahkan state `UNSUPPORTED` untuk AFR pada hasil yang tidak kosong. Baris AFR
tidak dihilangkan agar UI tidak tergoda menghitung sendiri.

## 10. Polling policy

`LivePollPolicy`:

| Parameter | Nilai |
|---|---:|
| Interval minimum | 150 ms |
| Interval maksimum | 1000 ms |
| Backoff awal | 300 ms |
| Backoff maksimum | 3000 ms |
| Kegagalan sebelum stop | 4 |

Interval normal = `transactionMs + 50 ms`, dijepit ke 150-1000 ms. Setiap kegagalan
menggandakan backoff.

## 11. Telemetry

`LiveTelemetry` membawa:

- nilai sensor,
- flag ketersediaan per sensor,
- `activeTableHex`,
- `parameterStates`,
- `isDataValid`,
- timestamp baca dan update.

Nilai default 0 adalah placeholder struktur, bukan pembacaan. `isEngineSpinning`
mensyaratkan data valid, RPM valid, dan RPM > 0.

## 12. Arti kegagalan

| Kondisi | State | Bukan arti |
|---|---|---|
| Semua tabel gagal, belum ada data | `WAITING_RESPONSE` | Semua sensor 0 |
| Semua tabel gagal, ada data lama | `STALE` | Sensor rusak |
| Profil family kosong | `UNSUPPORTED_PROFILE` | ECU tidak mendukung Live Data |
| Frame valid, field tidak masuk akal | `INVALID_PAYLOAD` | Nilai 0 |
| Tidak ada transport | `UNAVAILABLE` | Kendaraan mati |

## 13. Batas

- Layout contoh berasal dari repository ini sendiri, bukan dari ECU atau firmware pihak ketiga.
- Apakah ECU tertentu menjawab tabel tertentu hanya dapat ditentukan oleh transaksi wire.
- Extension dapat menambah sensor, tetapi tidak boleh menimpa nilai primary.
- Tidak ada perhitungan AFR/lambda di host.
- Tidak ada klaim bahwa semua kendaraan menyediakan semua tabel.
