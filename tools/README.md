# Tools

## `validate_fixtures.py`

Validator fixture tanpa dependensi pihak ketiga. Python 3.9+.

```bash
# Windows
py tools/validate_fixtures.py

# Linux / macOS
python3 tools/validate_fixtures.py
```

Opsi:

```bash
py tools/validate_fixtures.py --verbose
py tools/validate_fixtures.py --root /path/to/RevCore_Public_Developer_Repository
```

### Yang diperiksa

- Semua JSON contoh dapat dibaca sebagai UTF-8.
- Setiap frame K-Line memiliki header terdokumentasi
  (`0x72`, `0x02`, `0x7E`, `0x7C`, `0xFE`, `0x7D`, `0x7B`).
- Panjang yang dideklarasikan di byte 1 sama dengan ukuran frame sebenarnya.
- Byte terakhir sama dengan checksum additive dua's complement dari byte sebelumnya.
- `payloadHex` sama dengan potongan payload frame (indeks 3 untuk frame umum, indeks 4
  untuk Live Data karena ada echo tabel).
- `payloadAscii` sama dengan byte payload.
- `declaredLength` pada fixture identifikasi cocok dengan byte panjang.
- Setiap record `ecm_database.json` memakai `id` berawalan `SAMPLE-`.

### Yang tidak diperiksa

- Apakah ECU menerima request.
- Apakah tabel Live Data tersedia pada kendaraan tertentu.
- Apakah checksum ECU nyata sama dengan contoh.
- Semantik DTC, skala sensor, atau kecocokan identitas.
- Kebocoran rahasia, lisensi, atau hak redistribusi.

Validator ini memeriksa konsistensi internal fixture. Lulusnya validator bukan bukti
kompatibilitas kendaraan.

### Exit code

| Kode | Arti |
|---:|---|
| 0 | Semua pemeriksaan lulus |
| 1 | Ada frame/payload yang tidak konsisten |
| 2 | Root repository tidak ditemukan |

### Menambah fixture

Letakkan frame baru pada lokasi yang sudah dipetakan di `main()`:

| Berkas | Kunci yang dibaca |
|---|---|
| `examples/protocol_frames/requests.json` | `frames[].hex` |
| `examples/ecu_responses/identification.json` | `contoh[].frameHex`, `payloadHex`, `payloadAscii`, `declaredLength` |
| `examples/dtc/dtc_examples.json` | `request.hex`, `contohRespons[].frameHex` |
| `examples/sensor_data/live_data.json` | `<tabel>.responseHex`, `requestHex`, `payloadHex` |
| `core/src/main/assets/ecm_database.json` | `id` setiap record |

Jangan menambahkan respons ECU nyata, data kendaraan, atau materi pihak ketiga tanpa
izin. Semua contoh harus sintetis dan ditandai sebagai `REFERENCE`.

## `validate_datasets.py`

Validator skema untuk record ECM dan MAP sintetis, sekaligus contoh parser/interpolasi.
Python 3.9+, tanpa dependensi pihak ketiga.

```bash
py tools/validate_datasets.py --verbose
py tools/validate_datasets.py --verbose --demo
```

### Yang diperiksa

- `ecm_database.json` dan `examples/vehicle_db/vehicle_template.json` memakai field wajib.
- `id` publik berawalan `SAMPLE-`.
- `ecmId` kosong atau 10/12 digit hex.
- Offset dapat diparse dan nilai numerik tidak negatif.
- `examples/maps/synthetic_map.json` memakai format/versi yang benar.
- Sumbu RPM dan beban menaik ketat.
- Jumlah baris/kolom sel cocok dengan sumbu.
- Semua sel angka dan berada pada rentang generik 0..100.
- `--demo` menghitung contoh bilinear pada RPM 3500 dan beban 45%.

### Yang tidak diperiksa

- Apakah data cocok dengan kendaraan nyata.
- Apakah ECU menerima frame.
- Apakah peta aman untuk mesin atau flashing.
- Kebocoran rahasia, lisensi, atau hak redistribusi.

Validator ini hanya memeriksa konsistensi internal dataset sintetis.
