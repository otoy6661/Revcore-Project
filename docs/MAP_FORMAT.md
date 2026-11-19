# FORMAT MAP GENERIK

Dokumen ini menjelaskan format MAP contoh yang dipakai repository untuk demonstrasi parser
dan validator. Format ini **bukan** format biner ECU dan bukan definisi ROM.

## 1. Ruang lingkup

Repository publik menyertakan:

- template JSON sintetis `examples/maps/synthetic_map.json`;
- inventaris 162 berkas MAP hasil pekerjaan RevCore di `data/map_library/inventory.csv`
  (ukuran, SHA-256, identifikasi format, status analisis);
- parser Kotlin `com.revcore.android.data.map.MapDocument`;
- validator contoh di `tools/validate_datasets.py`;
- dokumentasi struktur dan aturan validasi.

## 2. Struktur berkas

```json
{
  "format": "revcore-synthetic-map",
  "version": 1,
  "id": "SAMPLE-MAP-001",
  "status": "REFERENCE",
  "units": { "rpm": "rpm", "load": "%", "value": "ms" },
  "axes": {
    "rpm": [1000, 2000, 3000],
    "load": [0, 50, 100]
  },
  "cells": [
    [1.0, 1.1, 1.2],
    [1.2, 1.3, 1.4],
    [1.4, 1.5, 1.6]
  ],
  "notes": "Contoh sintetis."
}
```

| Field | Tipe | Wajib | Arti |
|---|---|---|---|
| `format` | string | Ya | Harus `revcore-synthetic-map` |
| `version` | integer | Ya | Versi format; saat ini `1` |
| `id` | string | Ya | Identifier; di repo publik wajib `SAMPLE-` |
| `status` | string | Ya | `REFERENCE`, `RECONSTRUCTED`, `VERIFIED`, atau `UNVERIFIED` |
| `units.rpm` | string | Ya | Satuan sumbu RPM |
| `units.load` | string | Ya | Satuan sumbu beban |
| `units.value` | string | Ya | Satuan nilai sel |
| `axes.rpm` | array number | Ya | Sumbu RPM, menaik ketat |
| `axes.load` | array number | Ya | Sumbu beban, menaik ketat |
| `cells` | array of array | Ya | Nilai dalam satuan teknik, bukan raw |
| `notes` | string | Ya | Asal data dan batas bukti |

## 3. Aturan indeks

- `cells` memiliki jumlah baris sama dengan `axes.rpm.length`.
- Setiap baris memiliki jumlah kolom sama dengan `axes.load.length`.
- `cells[i][j]` adalah nilai pada `rpm[i]` dan `load[j]`.
- Tidak ada nilai default yang tersembunyi. Sel yang tidak diketahui tidak boleh diisi `0`
  tanpa catatan; gunakan `null` dan validator akan menolaknya untuk template publik.

## 4. Skala dan satuan

Template menyimpan nilai dalam satuan teknik. Jika data sumber berupa raw, konversi:

```text
value = raw * scale + bias
```

Simpan `scale`, `bias`, dan satuan di `notes` atau dokumen pendamping. Jangan menyimpan
nilai raw seolah-olah nilai teknik.

## 5. Contoh pembacaan dan interpolasi

Untuk `examples/maps/synthetic_map.json`:

- `rpm = 3500` berada di antara indeks 2 (3000) dan 3 (4000), fraksi `t = 0.5`.
- `load = 45` berada di antara indeks 4 (40) dan 5 (50), fraksi `u = 0.5`.
- Nilai sudut: `1.28`, `1.30`, `1.38`, `1.40`.
- Hasil bilinear: `(1.28 + 1.30 + 1.38 + 1.40) / 4 = 1.34 ms`.

Parser contoh menyediakan fungsi `sample_bilinear(map_data, rpm, load)` yang mengembalikan
nilai tersebut. Ini hanya demonstrasi matematika, bukan klaim perilaku ECU.

Parser Kotlin memiliki perilaku yang sama:

```kotlin
val result = MapDocument.parse(json)
when (result) {
    is MapParseResult.Success -> {
        val document = result.document
        val value = document.sampleBilinear(3500.0, 45.0) // 1.34
    }
    is MapParseResult.Failure -> println(result.issues)
}
```

`sampleBilinear` mengembalikan `null` di luar sumbu; tidak ada ekstrapolasi dan tidak ada
pengisian sel yang hilang.

## 6. Validasi

```bash
py tools/validate_datasets.py --verbose
```

Validator memeriksa:

- `format` dan `version` sesuai;
- `id` berawalan `SAMPLE-` untuk materi publik;
- sumbu tidak kosong, semuanya angka, dan menaik ketat;
- jumlah baris/kolom cocok dengan sumbu;
- semua sel angka dan berada pada rentang wajar yang dideklarasikan;
- `status` termasuk label yang diizinkan;
- `notes` menjelaskan asal data.

## 7. Kontribusi MAP

1. Gunakan template sintetis sebagai awal.
2. Jangan mengunggah MAP proprietary atau hasil ekstraksi firmware pihak ketiga.
3. Jangan mengunggah BIN/ROM/ECU image.
4. Jelaskan sumbu, satuan, skala, dan sumber data.
5. Tandai status bukti dengan jujur.
6. Jalankan validator dan lampirkan hasilnya.

MAP bukan bukti kompatibilitas. Repository ini tidak menyediakan jalur tulis ke ECU dan
tidak menyarankan penggunaan peta contoh pada kendaraan nyata.
