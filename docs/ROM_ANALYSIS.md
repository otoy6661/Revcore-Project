# ANALISIS ROM READ-ONLY

Dokumen ini menjelaskan `com.revcore.android.data.rom.RomImage`, utilitas untuk membaca
dan membandingkan image ROM/ECU yang Anda miliki. Modul ini **read-only**: tidak ada
fungsi tulis, flashing, repair checksum, atau remap.

## 1. Ruang lingkup

`RomImage` menyediakan:

- akses byte dan word 16-bit big/little-endian;
- checksum aditif 16-bit;
- CRC-16 gaya Modbus;
- diff blok terhadap image lain.

`RomImage` tidak mengetahui model ECU, offset peta, atau arti kalibrasi. Offset dan
rentang checksum selalu diberikan pemanggil. Repository tidak menyertakan image ROM nyata.

## 2. API

```kotlin
val stock = RomImage(bytes = stockBytes, name = "stock.bin")
val tuned = RomImage(bytes = tunedBytes, name = "tuned.bin")

val word = stock.word16(offset = 0x100, bigEndian = true)
val sum = stock.additiveChecksum16()
val crc = stock.crc16()
val diff = stock.diff(tuned)
```

| Method | Hasil |
|---|---|
| `byteAt(offset)` | `Int` 0..255 atau `null` bila di luar batas |
| `word16(offset, bigEndian)` | `Int` 0..65535 atau `null` |
| `additiveChecksum16(start, endExclusive)` | Jumlah 8-bit modulo 65536 atau `null` |
| `crc16(start, endExclusive)` | CRC-16 atau `null` |
| `diff(other)` | `RomDiffResult` |

## 3. Diff

`diff` membandingkan byte yang sama-sama tersedia dan menggabungkan perubahan berurutan
menjadi satu `ModifiedBlock`. Bila ukuran image berbeda:

- `sizeMismatch = true`;
- byte tambahan pada salah satu image dilaporkan sebagai satu blok tambahan;
- `totalBytesDifferent` hanya menghitung byte pada panjang yang dibandingkan.

Blok menyimpan byte asli dan byte modifikasi, sehingga analisis dapat menampilkan isi
perubahan tanpa menulis ulang image.

## 4. Checksum

Checksum dihitung dari rentang yang diminta. `RomImage` tidak menebak lokasi checksum dan
tidak memperbaiki checksum. Jika checksum suatu ECU memakai algoritma lain, implementasikan
fungsi terpisah dan dokumentasikan referensinya. Jangan memberi label `VERIFIED` tanpa
pengujian pada image yang diketahui.

## 5. Batas bukti

Semua analisis ROM bersifat `UNVERIFIED` sampai dibandingkan dengan dump yang sah dan
diuji pada perangkat. Diff yang rapi bukan bukti bahwa perubahan aman atau dapat ditulis
ke ECU.

Repository tidak menyediakan:

- jalur flashing atau write;
- checksum repair otomatis;
- definisi peta proprietary;
- image ROM, BIN, atau firmware pihak ketiga.

## 6. Contoh

```kotlin
val result = RomImage(stock).diff(RomImage(candidate))
println("${result.totalBytesDifferent} byte berbeda dalam ${result.modifiedBlocks.size} blok")
result.modifiedBlocks.forEach { block ->
    println("${block.label}: ${block.length} byte @ ${block.offset}")
}
```
