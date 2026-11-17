# IDENTIFIKASI ECU

Dokumen ini menjelaskan cara `:core` mengubah respons identifikasi menjadi `EcuIdentity` dan kapan sebuah model boleh diterbitkan. Prinsip utamanya: **identifikasi bukan tebakan**.

## 1. Dua lapis masalah

| Lapis | Pertanyaan | Kelas |
|---|---|---|
| Ekstraksi | Di mana field ID yang lengkap di dalam payload? | `EcuIdCanonicalizer` |
| Pencocokan | Apakah field itu cocok dengan record database? | `EcmDatabase.resolveIdentity` |

Keduanya dipisahkan agar payload mentah tetap tersedia meskipun tidak ada field yang dapat dipastikan.

## 2. Parser respons

`HondaIdentifierParser` menerima frame yang:

- panjang minimal 5 byte,
- header dikenal,
- declared length sama dengan ukuran frame,
- checksum valid,
- service bukan `0x7F`,
- payload tidak kosong dan tidak seluruhnya `0x00`/`0xFF`.

Payload printable ASCII dipakai verbatim. Payload non-ASCII direpresentasikan sebagai hex. Tidak ada pemotongan byte awal atau akhir.

Contoh sintetis:

```text
frame   : 72 0C 71 53 41 4D 50 4C 45 30 31 EE
payload : 53 41 4D 50 4C 45 30 31
ASCII   : SAMPLE01
```

`SAMPLE01` bukan ID database yang valid (bukan 10/12 hex), sehingga canonical ID tetap null. Contoh ini hanya mendemonstrasikan parser.

## 3. Kanonikalisasi

`EcuIdCanonicalizer` bekerja dengan aturan berikut:

1. ID database dianggap lengkap jika 5 byte (10 karakter hex) atau 6 byte (12 karakter hex).
2. Jika identifier sudah berbentuk lengkap, dipakai apa adanya.
3. Jika payload memiliki layout yang terdokumentasi, field diambil sesuai `prefixBytes` dan `fieldBytes`.
4. Jika tidak ada layout, kandidat field lengkap di awal dan akhir payload dipertimbangkan.
5. Kandidat yang cocok dengan ID database yang diketahui dipilih lebih dulu.
6. Jika tidak ada yang cocok, field 5 byte di akhir dipilih sebagai default.
7. Byte di luar field disimpan sebagai `protocolPrefixHex` dan `trailingHex`.
8. Tidak ada pemotongan sewenang-wenang dan tidak ada penggabungan field.

Contoh layout `id.7200`:

```text
request  : 72 07 72 00 00 05 10
response : 72 0B 72 00 00 01 03 7A 0F 02 <checksum>
payload  : 00 00 01 03 7A 0F 02
            └─┬─┘ └─────┬─────┘
          selector   ECM ID 5 byte
canonical: 01037A0F02
```

Layout ini berstatus `RECONSTRUCTED_FROM_CAPTURED_RESPONSE`. Query lain tidak memakai layout karena tidak ada capture yang membuktikan panjang prefix-nya.

## 4. Bentuk identity

`EcuIdentity` memisahkan:

| Field | Isi |
|---|---|
| `rawResponseHex` | Frame lengkap |
| `payloadHex` | Semua byte antara service dan checksum |
| `payloadAscii` | Payload jika printable |
| `ecuIdRaw` | Identifier mentah |
| `ecuIdNormalized` | Bentuk tanpa separator dan case |
| `canonicalEcuId` | Field ID lengkap yang dipilih |
| `canonicalCandidates` | Kandidat field lengkap |
| `protocolPrefixHex` | Byte sebelum field |
| `trailingBytesHex` | Byte setelah field |
| `canonicalizationMethod` | Nama metode pemilihan |
| `alternateIdentifiers` | Token lengkap lain dalam payload multi-field |
| `responseSource` | Query asal (`0x00/F0`, `0x72/00`, `0x1A`, dan sebagainya) |

Tidak ada field yang menimpa field lain. `ecuIdRaw` dan `canonicalEcuId` dapat berbeda dan keduanya dipertahankan.

## 5. Urutan pencocokan

`resolveIdentity` menyusun query dari field yang tersedia, lalu:

### Fase 1 — exact

Mencocokkan setelah normalisasi format terhadap:

1. `model.id`,
2. `model.partNumber`,
3. `model.ecmId`.

Hasil:

| Jumlah model berbeda | Status | Model diterbitkan? |
|---|---:|---|---|
| 1 | `EXACT` | Ya |
| >1 | `AMBIGUOUS` | Tidak |
| 0 | lanjut fase 2 | — |

Normalisasi hanya membuang separator dan case. Tidak ada substring matching.

### Fase 2 — incomplete

Jika tidak ada exact match, kandidat prefix/suffix dipertimbangkan dengan batas:

- panjang nilai pendek minimal 8 karakter,
- selisih panjang maksimal 2 karakter.

Batas ini mencerminkan field 4 byte versus 5 byte.

| Jumlah kandidat | Status | Model diterbitkan? |
|---|---:|---|---|
| 1 | `PARTIAL_MATCH` | **Tidak** |
| >1 | `AMBIGUOUS` | Tidak |
| 0 | `NOT_FOUND` | Tidak |

`PARTIAL_MATCH` adalah bukti diagnostik, bukan identifikasi. Model tetap null.

## 6. Kualitas

| Quality | Arti |
|---|---|
| `VERIFIED` | Record database sendiri memiliki verifikasi hardware |
| `EXACT` | Exact match, tetapi record belum diverifikasi hardware |
| `CANDIDATE` | Identifier tidak lengkap |
| `AMBIGUOUS` | Lebih dari satu record |
| `UNKNOWN` | Tidak cocok, tetapi bentuk identifier terdokumentasi |
| `UNSUPPORTED` | Tidak ada identifier yang dapat dibaca |

Kualitas tidak pernah dinaikkan oleh UI atau pemanggil.

## 7. ECU yang menjawab tanpa ID

Jika ECU mengirim frame valid tetapi tidak ada ID lengkap:

```kotlin
EcmDatabase.createUnverifiedModel(
    "HONDA-KLINE",
    "ECM Belum Teridentifikasi (K-Line merespons)"
)
```

Model ini:

- `isEcuIdentified = false`,
- bukan record database,
- tidak memiliki offset ROM atau klaim kompatibilitas,
- hanya menandai bahwa ECU merespons.

## 8. Contoh keputusan

| Respons | Canonical | Hasil |
|---|---|---|
| `SAMPLE01` | null | Parser sukses, identitas tidak dipublikasikan |
| `01037A0F02` cocok 1 record | ya | `EXACT` |
| `01037A0F02` cocok 2 record | ya | `AMBIGUOUS`, tanpa model |
| `01037A0F` (4 byte) unik sebagai prefix | tidak | `PARTIAL_MATCH`, tanpa model |
| `01037A0F` cocok banyak | tidak | `AMBIGUOUS`, tanpa model |
| Frame valid tanpa ID | null | Placeholder belum teridentifikasi |

## 9. Database contoh

Repo hanya membawa tiga record sintetis:

```text
SAMPLE-ECM-001  ecmId 0100000001
SAMPLE-ECM-002  ecmId 0100000002
SAMPLE-ECM-003  ecmId 0100000003
```

Record ini tidak berasal dari kendaraan nyata dan tidak boleh dipakai untuk klaim kompatibilitas. Database lengkap tidak disertakan karena hak redistribusinya belum terbukti.

## 10. Larangan

- Jangan memotong ID agar “cocok” dengan record.
- Jangan memilih record pertama saat ada beberapa kandidat.
- Jangan menerbitkan model untuk `PARTIAL_MATCH`.
- Jangan menganggap ID ASCII yang tidak berbentuk hex sebagai ECM ID.
- Jangan menganggap family dari header sebagai model kendaraan.
- Jangan menaikkan status bukti tanpa pengujian perangkat.
