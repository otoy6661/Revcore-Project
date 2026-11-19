# Sampel 03 - Checksum tambah dua-komplemen

## Tujuan

Menghitung byte checksum untuk frame K-Line Honda dengan rumus tambah
dua-komplemen 8 bit, lalu membuktikan bahwa jumlah seluruh frame menjadi
`0x00` (modulo 256).

## Yang dipelajari

- Susunan frame: `[header][length][service][params...][checksum]`.
- Rumus `checksum = (256 - (sum(bytes) & 0xFF)) & 0xFF`, dengan `bytes` adalah
  header, length, service, dan params (posisi checksum tidak ikut dijumlahkan).
- Mengapa operasi `& 0xFF` perlu: Java menyimpan `byte` sebagai bilangan
  bertanda (-128..127), sedangkan protokol menghitung 0..255.
- Pola self-test dengan nilai acuan dan pengecekan "jumlah frame = 0x00".
- Membaca argumen CLI untuk memakai program sebagai kalkulator kecil.

## Prasyarat

- JDK (Temurin 21 atau lebih baru) terpasang, `javac` dan `java` ada di PATH.
- Tidak butuh Android, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/03-checksum
javac -d out src/Main.java

# Mode demo + self-test
java -cp out Main

# Mode CLI: cetak checksum untuk byte tertentu (hex, tanpa '0x')
java -cp out Main 72 05 74 01     # mencetak: 14
```

## Input dan output

Mode demo tidak butuh input. Mode CLI menerima daftar byte hex sebagai argumen.

| Input (hex) | Output checksum |
|---|---|
| `72 05 74 01` | `14` |
| `72 05 71 00` | `18` |
| `72 05 71 20` | `F8` |
| `72 05 00 00` | `89` |
| `72 05 60 01` | `28` |
| `72 05 71 7A` | `9E` |

Contoh potongan output mode demo:

```text
  72 05 71 7A -> checksum 0x9E (jumlah seluruh frame 8-bit = 0x00)
PASS checksum 72 05 71 7A (harap 0x9E)
---
Ringkasan: PASS=10 FAIL=0
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Catatan rumus

Untuk `72 05 71 7A`:

```text
0x72 + 0x05 + 0x71 + 0x7A = 0x162
0x162 & 0xFF              = 0x62
256 - 0x62                = 0x9E   (checksum)
0x162 + 0x9E = 0x200, dan 0x200 & 0xFF = 0x00
```

Karena itu frame lengkapnya `72 05 71 7A 9E` selalu berjumlah `0x00` di 8 bit.
ECU memakai sifat ini untuk memeriksa integritas frame; jika hasilnya bukan
`0x00`, frame dianggap rusak.

## Batasan

- Hanya menghitung checksum; tidak membangun frame lengkap (lihat sampel 04)
  dan tidak memvalidasi frame yang diterima (lihat sampel 05).
- Tidak mengirim atau menerima data dari kendaraan.
- Nilai acuan diambil dari dokumentasi dan test repo RevCore, bukan dari
  pengukuran ECU fisik.
- Angka pada mode CLI dianggap input hex; input tidak valid akan melempar
  `NumberFormatException` tanpa pesan khusus.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Hitung dengan tangan checksum untuk `72 05 74 02`, lalu cek lewat CLI. Apakah hasilnya `13`?
2. Tambahkan self-test untuk frame `72 04 04` (seharusnya `86`). Mengapa `length` di sini `04`?
3. Ubah mode CLI agar menolak argumen yang bukan 2 digit hex, lalu cetak pesan error yang jelas.
4. Jelaskan dalam satu paragraf: apa yang terjadi jika satu byte di tengah frame berubah, tetapi checksum tidak diperbarui?
