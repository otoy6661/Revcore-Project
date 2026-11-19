# Sampel 05 - Frame parser

## Tujuan

Membaca teks hex menjadi bagian-bagian frame (`header`, `length`, `service`,
`payload`, `checksum`), memvalidasinya, dan mendeteksi respons negatif
(service `0x7F`) dengan benar.

## Yang dipelajari

- Layout frame: `[header][length][service][payload...][checksum]`.
- `length` adalah jumlah total byte frame (termasuk checksum), sesuai
  `docs/KLINE_PROTOCOL.md`. Jadi panjang `payload = length - 4`.
- Validasi berlapis: ukuran minimum, header, declared length, lalu checksum.
- Prinsip `docs/FRAME_PARSER.md`: parser **tidak memperbaiki** frame; frame
  yang gagal langsung ditolak dengan alasan.
- Service `0x7F` = respons negatif. Dua byte setelahnya adalah service yang
  ditolak dan kode kesalahan.

## Prasyarat

- JDK (Temurin 21 atau lebih baru) terpasang, `javac` dan `java` ada di PATH.
- Sudah membaca sampel 03 (checksum) dan sampel 04 (frame builder).
- Tidak butuh Android, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/05-frame-parser
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Tidak ada input. Frame uji ditulis langsung di `Main.java` sebagai teks hex.

Contoh output untuk frame valid:

```text
frame: 72 05 74 01 14
  VALID  header=0x72 length=0x05 service=0x74 payload=[01] checksum=0x14
PASS valid 74 01 (service 0x74, payload [01])
```

Contoh output untuk frame ditolak:

```text
frame: 72 05 74 01
  DITOLAK: Declared length 0x05 (5) tidak cocok dengan jumlah byte 4
PASS terpotong ditolak (Declared length 0x05 (5) tidak cocok dengan jumlah byte 4)
```

Contoh output untuk respons negatif:

```text
frame: 72 06 7F 74 12 83
  VALID  header=0x72 length=0x06 service=0x7F payload=[74 12] checksum=0x83
  -> RESPONS NEGATIF: service ditolak=0x74, kode=12
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Catatan: respons negatif bukan daftar DTC

Frame `72 06 7F 74 12 83` **valid secara checksum**, tetapi artinya ECU
menolak query `0x74`. Frame ini **bukan** daftar DTC dan **bukan** bukti
"tidak ada DTC". Ini juga dijelaskan di `docs/DTC_ENGINE.md` bagian 9.

Kesalahan umum pemula: menganggap setiap frame valid sebagai data. Valid
secara checksum hanya berarti "frame tidak rusak waktu dikirim".

## Contoh frame yang ditolak

| Frame | Alasan |
|---|---|
| `72 05 74 01` | Terpotong: declared length 5, byte diterima 4 |
| `72 05 74 01 15` | Checksum salah: terbaca `15`, seharusnya `14` |
| `02 05 74 01 AE` | Header bukan `0x72` |
| `72 03 74` | Kurang dari 4 byte |
| `72 0Z 74 01 14` | Teks hex tidak valid |

## Batasan

- Hanya menerima header `0x72`. Header lain (`0x02`, `0x7E`, `0x7C`, `0xFE`,
  `0x7D`, `0x7B`) yang disebut `docs/KLINE_PROTOCOL.md` tidak diterima di sini.
- Tidak membersihkan echo, tidak mencari header di tengah noise, tidak
  menggabungkan potongan buffer seperti `KlineWireEngine` di RevCore.
- Tidak memeriksa arti payload; checksum valid tidak berarti isi payload benar.
- Tidak berkomunikasi dengan kendaraan.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Ubah kode agar menerima header `0x02`, lalu tambahkan test untuk frame
   `02 05 74 01 AE`. Mengapa checksum-nya `AE`?
2. Tambahkan pemeriksaan: frame tanpa byte payload (`72 04 04 86`) harus
   menghasilkan `payload = []`.
3. Buat method `payloadHex()` pada `ParsedFrame` dan pakai di `tampilkan()`.
4. Jelaskan mengapa parser tidak boleh "membetulkan" checksum yang salah.
