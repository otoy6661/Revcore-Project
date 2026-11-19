# DTC ENGINE

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini menjelaskan pembacaan dan penghapusan DTC: query yang dikirim, format payload yang diterima, arti status, dan kapan hasil boleh disebut terverifikasi.

## 1. Prinsip

1. DTC tidak dikenal tetap ditampilkan sebagai kode mentah, bukan P-code karangan.
2. Pembacaan yang gagal tidak mengosongkan daftar lama.
3. `OK_EMPTY` hanya dari frame valid yang benar-benar tidak memuat kode.
4. Clear tidak dianggap berhasil sampai pembacaan ulang tidak menemukan DTC.
5. Clear tidak menjamin lampu MIL padam.

## 2. Query baca

Urutan dari profil baseline:

| Urutan | Label | Frame |
|---:|---|---|
| 1 | `74/01` | `72 05 74 01 14` |
| 2 | `71/20` | `72 05 71 20 F8` |
| 3 | `00/00` | `72 05 00 00 89` |

Engine berhenti pada query pertama yang menghasilkan frame valid dan payload yang dapat diparse. Jika respons negatif, query berikutnya dicoba.

## 3. Format payload

`parseDtcPacket` menerima:

### 3.1 Count-prefixed

```text
[count][code1][sub1][code2][sub2]...
```

- `count` 1..10,
- payload harus memuat minimal `1 + count*2` byte,
- semua kode harus 1..99 dan bukan byte header protokol.

Contoh sintetis:

```text
frame   : 72 0A 74 01 02 01 01 07 01 03
payload : 02 01 01 07 01
           │  └┬┘ └┬┘
         count  P0107 P0117
hasil   : P0107, P0117
```

### 3.2 Pair stream

```text
[code1][sub1][code2][sub2]...
```

Jika count-prefixed tidak valid, parser memindai pasangan. Byte `0x00`, `0xFF`, dan byte header protokol dilewati. Subcode di luar 0..15 dianggap 0.

### 3.3 Echo service

`HondaDtcDatabase.parseDtcResponse` juga menerima:

- `[0x03][pairs...]`,
- `[0x00][pairs...]`,
- count-prefixed,
- pair stream tanpa echo.

Echo di depan dibuang sebelum parsing. Jika struktur tidak dapat dipastikan, hasilnya `null`, bukan daftar kosong.

## 4. Database DTC

15 kode dikenal:

| Kode | Sub | Standard | Judul |
|---:|---:|---|---|
| 1 | 1 | P0107 | MAP Sensor Voltage Low |
| 1 | 2 | P0108 | MAP Sensor Voltage High |
| 7 | 0 | P0115 | ECT/EOT Circuit Malfunction |
| 7 | 1 | P0117 | ECT/EOT Voltage Low |
| 7 | 2 | P0118 | ECT/EOT Voltage High |
| 8 | 1 | P0122 | TP Sensor Voltage Low |
| 8 | 2 | P0123 | TP Sensor Voltage High |
| 9 | 1 | P0112 | IAT Sensor Voltage Low |
| 9 | 2 | P0113 | IAT Sensor Voltage High |
| 11 | 1 | P0500 | Vehicle Speed Sensor |
| 12 | 1 | P0201 | Fuel Injector Circuit |
| 21 | 1 | P0135 | O2 Sensor Circuit |
| 29 | 1 | P0505 | IACV Circuit |
| 33 | 1 | P0605 | ECM Internal Memory |
| 54 | 1 | P1601 | Bank Angle Sensor |

Kode di luar tabel:

```text
standardCode = "DTC XXYY"
title        = "Kode ECM 0xXXYY (belum terverifikasi)"
isKnown      = false
```

Tidak ada deskripsi gejala yang dikarang.

## 5. State baca

| State | Arti |
|---|---|
| `NOT_PERFORMED` | Belum ada pembacaan |
| `OK_EMPTY` | Frame valid, tidak ada kode |
| `OK_CODES` | Frame valid, ada kode |
| `NO_RESPONSE` | Tidak ada frame valid / semua query gagal |
| `UNRECOGNIZED` | Frame valid, format tidak dapat dipastikan |

`NO_RESPONSE` tidak mengubah `activeDtcs`. Ini mencegah tampilan “tidak ada DTC” setelah kabel lepas.

## 6. Query clear

Kandidat baseline:

| Urutan | Label | Frame |
|---:|---|---|
| 1 | `60/01` | `72 05 60 01 28` |
| 2 | `74/02` | `72 05 74 02 13` |
| 3 | `04` | `72 04 04 86` |

Kandidat dikirim sampai ada respons yang:

- bukan `0x7F`,
- header-nya termasuk `clearAckHeaders` (`0x02`, `0x72`, `0x7E`, `0x7C`).

Tidak ada ACK → `REJECTED_OR_TIMEOUT`.

## 7. Verifikasi clear

Setelah ACK:

```text
tunggu 250 ms
baca ulang DTC
  ├─ OK_EMPTY → SUCCESS_VERIFIED
  ├─ OK_CODES → RE_DETECTED_ACTIVE_FAULT
  ├─ NO_RESPONSE → CLEARED_UNVERIFIED
  └─ UNRECOGNIZED → CLEARED_UNVERIFIED
```

| Status | Arti |
|---|---|
| `SUCCESS_VERIFIED` | ECU mengakui clear dan tidak ada DTC terbaca |
| `RE_DETECTED_ACTIVE_FAULT` | Clear diakui, tetapi kode muncul lagi |
| `CLEARED_UNVERIFIED` | Clear diakui, verifikasi gagal |
| `REJECTED_OR_TIMEOUT` | Tidak ada ACK valid |
| `UNSUPPORTED_FAMILY` | Katalog clear belum direkonstruksi |
| `NO_ADAPTER` | Transport tidak terbuka |

`SUCCESS_VERIFIED` tidak menjamin MIL padam. Siklus kontak dan uji jalan tetap diperlukan.

## 8. Family dan katalog kosong

`activeProfile` bergantung pada family. Family selain baseline memiliki `dtcRead` dan `dtcClear` kosong. Dalam kondisi itu engine:

- tidak mengirim frame,
- mengembalikan `NoResponse` atau `UNSUPPORTED_FAMILY`,
- mencatat alasan.

Ini lebih benar daripada meminjam frame family lain.

## 9. Contoh negatif

```text
frame : 72 06 7F 74 12 83
service 0x7F → ECU menolak query 0x74
```

Frame valid secara checksum, tetapi bukan daftar DTC. Parser tidak boleh mengembalikan daftar kosong yang terlihat seperti “tidak ada DTC”.

## 10. Pengujian dan batas

- `BaselineProtocolTest` dan `KlineTransceiverTest` menguji frame dan outcome.
- `HondaDtcDatabase` diuji melalui jalur parser dan contoh sintetis.
- Tidak ada ECU nyata dalam pengujian.
- Arti kode mengikuti database repo; kode di luar database tidak diverifikasi terhadap buku servis.
