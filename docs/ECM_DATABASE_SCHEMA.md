# SKEMA DATABASE ECM

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini menjelaskan format `ecm_database.json` yang dibaca `:core`. Skema ini generik.
Aset bawaan `:core` hanya berisi record sintetis berawalan `SAMPLE-`; database hasil
kompilasi RevCore (489 record, status `terdaftar`) dipublikasikan terpisah di
[`data/vehicle_database/ecm_database.json`](../data/vehicle_database/ecm_database.json)
dan tidak dimuat otomatis oleh pustaka.

## 1. Lokasi dan bentuk berkas

- Aset bawaan: `core/src/main/assets/ecm_database.json`.
- Template kontribusi: `examples/vehicle_db/vehicle_template.json`.
- Bentuk yang diterima: array record, atau object dengan array `records`.

```json
[
  { "id": "SAMPLE-VEHICLE-001", "partNumber": "00000-SAMPLE-VEH-001" }
]
```

Setiap record adalah satu model ECM/kendaraan. `EcmDatabase` memetakan record ke
`com.revcore.android.data.ecu.EcmModel`.

## 2. Field

| Field | Tipe JSON | Wajib | Arti |
|---|---|---|---|
| `id` | string | Ya | Identifier unik. Di repo publik wajib berawalan `SAMPLE-` |
| `partNumber` | string | Ya | Nomor part sesuai label; boleh sintetis |
| `ecmId` | string | Disarankan | ID ECM kanonik 10 atau 12 digit hex (5/6 byte) |
| `startOffset` | string/number | Tidak | Offset awal data; `"0x0"` bila belum diketahui |
| `bikeName` | string | Ya | Nama tampilan model |
| `yearRange` | string | Tidak | Rentang tahun, mis. `"2019-2022"` |
| `manufacturer` | string | Tidak | Produsen; gunakan `"Belum terverifikasi"` bila tidak ada bukti |
| `mcuType` | string | Tidak | Tipe MCU; jangan diisi tanpa bukti |
| `flashSizeKb` | integer | Tidak | Ukuran flash dalam KB; `0` = belum diketahui |
| `stockRevLimit` | integer | Tidak | Batas RPM bawaan; `0` = belum diketahui |
| `maxSafeRevLimit` | integer | Tidak | Batas aman maksimum; `0` = belum diketahui |
| `recommendedRevLimit` | integer | Tidak | Rekomendasi; `0` = belum diketahui |
| `revLimitSource` | string | Tidak | Sumber nilai; default `"unverified"` |
| `tempSensorType` | string | Tidak | `ECT`, `CHT`, atau nilai yang didukung kode |
| `checksumType` | string | Tidak | Jenis checksum; default `KEIHIN_16BIT_ADD` |
| `checksumOffset` | string/number | Tidak | Offset checksum; `"0x0"` bila belum diketahui |
| `revLimiterOffset` | string/number | Tidak | Offset limiter |
| `fuelMapOffset` | string/number | Tidak | Offset peta bahan bakar |
| `fuelMapRows` | integer | Tidak | Jumlah baris peta bahan bakar |
| `fuelMapCols` | integer | Tidak | Jumlah kolom peta bahan bakar |
| `ignitionMapOffset` | string/number | Tidak | Offset peta pengapian |
| `ignitionMapRows` | integer | Tidak | Jumlah baris peta pengapian |
| `ignitionMapCols` | integer | Tidak | Jumlah kolom peta pengapian |
| `idleTargetOffset` | string/number | Tidak | Offset target idle |
| `isVerifiedDiagnostic` | boolean | Tidak | `true` hanya bila diagnosis terbukti pada hardware |
| `isVerifiedRemap` | boolean | Tidak | `true` hanya bila remap terbukti pada hardware |
| `category` | string | Tidak | Kategori tampilan, mis. `Matic`, `Bebek`, `Sport` |
| `statusNotes` | string | Ya | Catatan status dan batas bukti |
| `source` | string | Ya | Asal data, mis. `sample-synthetic` |
| `defaultBaudRate` | integer | Tidak | Baud default; `10400` untuk baseline K-Line |
| `country` | string | Tidak | Negara pasar; dipakai untuk kelayakan katalog |
| `displacementCc` | number | Tidak | Kapasitas mesin dalam cc |
| `calibrationId` | string | Tidak | ID kalibrasi bila ada bukti |
| `pinout` | string | Tidak | Catatan pinout; jangan mengarang |
| `protocol` | string | Tidak | Protokol yang terbukti; kosong bila belum |

Offset boleh ditulis sebagai string hex (`"0x1A2B"`) atau angka desimal. Gunakan `"0x0"`
dan angka `0` untuk nilai yang belum diketahui; jangan mengisi offset tebakan.

## 3. Aturan identitas

- `ecmId` kanonik adalah 5 atau 6 byte (10 atau 12 digit hex) tanpa spasi.
- Jangan memotong ID agar terlihat cocok. ID terpotong tidak boleh memilih model.
- Jika dua record memiliki identitas yang sama atau saling tumpang tindih, resolusi harus
  menghasilkan status ambigu, bukan memilih salah satu secara diam-diam.
- `partNumber` dan `ecmId` adalah dua jalur pencocokan yang berbeda; jangan mencampurnya.

## 4. Aturan status bukti

- Record baru harus memakai `isVerifiedDiagnostic: false` dan `isVerifiedRemap: false`
  kecuali ada bukti pengujian perangkat keras.
- `revLimitSource` harus `"unverified"` bila nilai RPM belum terbukti.
- `statusNotes` wajib menjelaskan asal data dan apa yang belum dibuktikan.
- Jangan menaikkan status hanya karena data "terlihat benar".

## 5. Contoh sintetis

Lihat `examples/vehicle_db/vehicle_template.json`. Record tersebut:

- memakai ID `SAMPLE-VEHICLE-001`;
- memakai nama dan produsen fiktif;
- mengisi semua offset dengan `"0x0"`;
- menandai diagnostic dan remap sebagai `false`;
- menyatakan batas bukti di `statusNotes`.

## 6. Validasi

```bash
py tools/validate_datasets.py --verbose
```

Validator memeriksa:

- JSON dapat dibaca;
- `id`, `partNumber`, `bikeName`, `statusNotes`, dan `source` ada;
- ID publik berawalan `SAMPLE-`;
- `ecmId` kosong atau 10/12 digit hex;
- offset dapat diparse sebagai bilangan;
- nilai numerik tidak negatif;
- `displacementCc` berada pada rentang wajar atau `0`;
- tidak ada field yang menyatakan verifikasi tanpa catatan.

## 7. Kontribusi data kendaraan

1. Gunakan template di `examples/vehicle_db/vehicle_template.json`.
2. Isi hanya data yang Anda miliki haknya dan dapat Anda buktikan.
3. Jangan menyalin database pihak ketiga, termasuk dengan mengganti nama atau format.
4. Jangan memasukkan VIN, nomor plat, atau data pribadi.
5. Tandai status bukti dengan jujur.
6. Lampirkan sumber dan metode verifikasi di `statusNotes`.
7. Jalankan validator dan lampirkan hasilnya di pull request/issue.

Ketentuan lengkap ada di [DATASET_CONTRIBUTION.md](DATASET_CONTRIBUTION.md).
