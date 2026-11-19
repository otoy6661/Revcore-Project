# 03 — Konsep Aplikasi Diagnostik

Dokumen ini menjelaskan istilah yang muncul di seluruh repository. Tujuannya bukan
menghafal, tetapi memahami **alur satu byte**: dari layar, ke adapter, ke ECU, lalu
kembali menjadi angka atau kode yang jujur.

## 1. Arsitektur berlapis

```text
┌─────────────────────────────────────────────┐
│ Aplikasi pemanggil                          │
│ layar, izin USB, lifecycle, penyimpanan     │
└───────────────────┬─────────────────────────┘
                    │ panggilan fungsi
┌───────────────────▼─────────────────────────┐
│ Communication Engine                        │
│ sesi, single-flight, log, status            │
├─────────────────────────────────────────────┤
│ Protocol Engine                             │
│ request, parser, DTC, Live Data, identitas  │
├─────────────────────────────────────────────┤
│ Transport                                   │
│ buka, tutup, baca, tulis, kontrol adapter   │
├─────────────────────────────────────────────┤
│ Adapter USB serial                          │
│ FTDI, CH340, CP210x, Prolific, CDC-ACM      │
└───────────────────┬─────────────────────────┘
                    │ K-Line (satu kabel)
┌───────────────────▼─────────────────────────┐
│ ECU                                         │
└─────────────────────────────────────────────┘
```

Arah dependensi hanya ke bawah. `:core` tidak mengenal layar aplikasi. Aplikasi
bertanggung jawab atas izin USB, lifecycle proses, preferensi, dan kebijakan
integritasnya sendiri.

## 2. Istilah satu per satu

### Communication Engine

Lapisan yang mengelola **sesi**: membuka transport, memastikan hanya satu transaksi
berjalan pada satu bus, mencatat TX/RX, dan menentukan status koneksi. Kelas utamanya
`EcuSessionManager` dan `KlineWireEngine`.

### Protocol Engine

Lapisan yang memahami **arti** byte: menyusun request, memvalidasi response, mem-parsing
payload, dan mengubahnya menjadi model seperti `EcmModel`, `HondaDtc`, atau
`LiveTelemetry`. Kelas utamanya `HondaProtocolEngine`.

### USB dan USB OTG

Ponsel biasanya bukan host USB. Dengan OTG, ponsel dapat menjadi host dan memberi daya
sekaligus berkomunikasi dengan adapter serial. Aplikasi harus meminta izin akses
perangkat melalui `UsbManager`; izin itu tidak diberikan otomatis.

### FTDI, CH340, CP210x, Prolific, CDC-ACM

Chip di dalam kabel/adapter yang mengubah USB menjadi serial UART. Setiap chip memiliki
perilaku berbeda. Adapter harus mendukung level tegangan yang sesuai dengan antarmuka
ECU. Jangan menyambungkan adapter 5V langsung ke jalur K-Line tanpa rangkaian antarmuka
yang benar.

### Serial dan baud rate

Serial berarti data dikirim satu bit demi satu pada kecepatan tertentu (baud). Baseline
Honda memakai 10400 baud, dengan percobaan 9600 pada beberapa jalur. Baud yang salah
menghasilkan byte rusak atau tidak ada respons.

### K-Line

Bus satu kabel dua arah. Untuk memulai komunikasi, tester mengirim **wake pulse**: tarik
jalur rendah selama beberapa milidetik, lalu lepaskan. Timing yang dipakai baseline:
70 ms rendah dan 130 ms tinggi pada 10400 baud (Source-analyzed; belum diuji hardware di
repository ini).

### TX dan RX

TX adalah byte yang dikirim tester. RX adalah byte yang diterima. Pada banyak adapter,
byte TX muncul kembali di RX sebagai **echo**. Echo harus dibuang sebelum response
di-parse, jika tidak frame akan terlihat rusak atau ganda.

### Frame

Satu unit komunikasi:

```text
[header] [length] [service] [payload...] [checksum]
```

- `header` menandai jenis frame (request tester atau response ECU);
- `length` adalah **ukuran total frame** dalam byte, termasuk header dan checksum;
- `service` menentukan operasi;
- `payload` membawa parameter atau data;
- `checksum` memvalidasi integritas.

Contoh request baca DTC:

```text
72 05 74 01 14
│  │  │  │  └─ checksum
│  │  │  └──── parameter 0x01
│  │  └─────── service 0x74
│  └────────── length 5
└───────────── header 0x72
```

### Checksum

Baseline memakai checksum komplemen dua dari jumlah byte:

```text
checksum = (256 - (sum(header..payload) & 0xFF)) & 0xFF
```

Efeknya: jumlah seluruh byte frame (termasuk checksum) menjadi `0x00` pada 8 bit.
Checksum yang salah berarti frame harus dibuang, bukan "diperbaiki".

### Payload dan parser

Payload adalah isi setelah service. Parser mengubah payload menjadi struktur. Parser
yang baik menolak payload pendek, nilai di luar rentang, dan format yang tidak dikenal.
Ia tidak boleh mengembalikan daftar kosong ketika sebenarnya gagal memahami.

### Response positif, negatif, dan tidak ada response

- **Positif**: service sesuai permintaan, payload berisi data.
- **Negatif**: service `0x7F`, diikuti service yang ditolak dan kode alasan.
- **Tidak ada**: tidak ada frame valid dalam batas waktu.

Ketiganya berbeda. Pada jalur baca DTC saat ini, response negatif dicoba dengan query
berikutnya; jika semua gagal, hasilnya `NO_RESPONSE`, bukan daftar kosong.

### Identifikasi ECU

ECU menjawab query identitas dengan nomor part, ID ECU, dan field lain. RevCore
menyimpan identitas mentah, membuat bentuk kanonik, lalu mencocokkan dengan database.
Hasil pencocokan: `EXACT`, `PARTIAL_MATCH`, `AMBIGUOUS`, atau `NOT_FOUND`. Model hanya
dipublikasikan untuk `EXACT`; kandidat ganda tidak dipilih secara acak.

### Sensor dan Live Data

Live Data adalah nilai sensor yang dikirim ECU saat mesin bekerja: RPM, suhu, posisi
throttle, dan sebagainya. Setiap field memiliki offset, panjang, urutan byte, skala,
bias, dan satuan. Nilai yang tidak dipublikasikan ECU harus tampil `UNSUPPORTED`, bukan
`0`.

State Live Data yang perlu dibedakan:

| State | Arti |
|---|---|
| `UNAVAILABLE` | Belum ada data |
| `WAITING_RESPONSE` | Request dikirim, menunggu |
| `ACTIVE` | Data segar |
| `STALE` | Data lama; jangan ditampilkan sebagai nilai sekarang |
| `UNSUPPORTED_PROFILE` | Tidak ada profil; tidak ada frame dikirim |
| `INVALID_PAYLOAD` | Frame ada tetapi tidak dapat di-parse |

### DTC

Diagnostic Trouble Code adalah kode gangguan yang disimpan ECU. Alur baca:

1. Kirim kandidat query (`74/01`, lalu `71/20`, lalu `00/00`).
2. Berhenti pada frame valid pertama yang payload-nya dapat di-parse.
3. Tampilkan kode dikenal dengan P-code; kode tak dikenal sebagai `DTC XXYY`, bukan
   P-code karangan.
4. Jika gagal, pertahankan daftar lama dan tandai `NO_RESPONSE`.

Clear DTC bukan sekadar mengirim perintah. Setelah ACK, tunggu 250 ms, baca ulang:

- kosong → `SUCCESS_VERIFIED`;
- masih ada kode → `RE_DETECTED_ACTIVE_FAULT`;
- gagal membaca → `CLEARED_UNVERIFIED`.

Clear yang berhasil tidak menjamin lampu MIL padam. Siklus kontak dan uji jalan tetap
diperlukan.

### Profil ECU

Profil mengelompokkan request, header yang diterima, timing wake, layout Live Data, dan
katalog DTC untuk satu keluarga ECU. Family yang belum terbukti memiliki katalog kosong.
Engine tidak boleh meminjam frame family lain hanya agar terlihat berfungsi.

### MAP

Tabel kalibrasi dua dimensi, misalnya nilai bahan bakar pada kombinasi RPM dan beban.
Repository menyediakan parser generik, validasi, dan interpolasi bilinear. MAP kendaraan
nyata dan workflow flashing tidak disertakan.

### Database kendaraan

Kumpulan record identitas ECU: nomor part, ID ECM, offset, dan status verifikasi.
Pencocokan harus exact atau relasi prefix/suffix terbatas. Data nyata memerlukan bukti,
hak penggunaan, dan peninjauan pemilik.

### Validasi

Pemeriksaan bentuk dan konsistensi data sebelum dipakai. Contoh di repository:

```bash
py tools/validate_fixtures.py --verbose
py tools/validate_datasets.py --verbose --demo
```

Validator membuktikan data sintetis konsisten. Ia tidak membuktikan kecocokan dengan
kendaraan.

## 3. Satu siklus lengkap

```text
Aplikasi meminta koneksi
  → transport membuka adapter
  → wake pulse dikirim
  → tester present / query identitas
  → ECU menjawab frame
  → checksum dan bentuk divalidasi
  → payload di-parse
  → identitas dicocokkan (EXACT saja yang dipublikasikan)
  → pengguna memilih baca DTC / Live Data
  → hasil ditampilkan dengan state yang jujur
  → transport ditutup
```

Kegagalan di titik mana pun harus terlihat sebagai kegagalan, bukan sebagai nilai nol.

## 4. Batas yang harus diingat

- Repository ini tidak berisi Application Layer dan mekanisme Security/Integrity.
- Tidak ada operasi tulis ke ECU (flash, remap, security access).
- Katalog Live Data bawaan kosong; pemanggil dapat menyediakan profil sendiri.
- Semua frame contoh sintetis; tidak ada ECU fisik dalam pengujian.
- Analisis source bukan bukti kompatibilitas universal.

Lanjutkan ke [04 — Referensi protokol dan data](04-referensi-protokol-dan-data.md) untuk
melihat setiap topik dengan status buktinya.
