# CHECKSUM

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini menjelaskan checksum frame K-Line dan checksum ROM yang tersedia di `HondaChecksum`. Keduanya fungsi berbeda dan tidak boleh dipertukarkan.

## 1. Checksum frame

Frame K-Line memakai **additive 8-bit two's complement**:

```text
for setiap byte sebelum checksum:
    sum = (sum + byte) mod 256

checksum = (256 - sum) mod 256
```

Implementasi:

```kotlin
fun calculatePacketChecksum(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Byte {
    var sum = 0
    val end = offset + length
    for (i in offset until end) sum += bytes[i].toInt() and 0xFF
    return ((256 - (sum and 0xFF)) and 0xFF).toByte()
}
```

Validasi:

```kotlin
fun validatePacket(packetBytes: ByteArray): Boolean {
    if (packetBytes.size < 4) return false
    val computed = calculatePacketChecksum(packetBytes, 0, packetBytes.size - 1)
    return computed == packetBytes.last()
}
```

`validatePacket` selalu menghitung seluruh byte kecuali byte terakhir dan membandingkannya dengan byte terakhir. Tidak ada mode “checksum opsional”.

## 2. Sifat matematis

- Penjumlahan hanya 8 bit; carry di luar 8 bit dibuang.
- Dua frame dengan checksum valid dapat memiliki isi berbeda; checksum bukan tanda tangan.
- Satu byte yang berubah selalu mengubah checksum, tetapi checksum yang cocok tidak membuktikan keaslian pengirim.
- Nilai `0x00` valid jika jumlah byte sebelum checksum adalah kelipatan 256.

## 3. Contoh

### 3.1 Tester present

```text
frame tanpa checksum: FE 04 72
sum = 0xFE + 0x04 + 0x72 = 0x174
sum mod 256 = 0x74
checksum = 0x100 - 0x74 = 0x8C
frame: FE 04 72 8C
```

### 3.2 Query ID 00/F0

```text
72 05 00 F0
sum = 0x167 → 0x67
checksum = 0x99
frame: 72 05 00 F0 99
```

### 3.3 Query ID 72/00

```text
72 07 72 00 00 05
sum = 0xF0
checksum = 0x10
frame: 72 07 72 00 00 05 10
```

### 3.4 Live Data tabel sintetis 0x7A

```text
72 05 71 7A
sum = 0x162 → 0x62
checksum = 0x9E
frame: 72 05 71 7A 9E
```

Request katalog adalah 5 byte: `72 05 71 7A 9E`. Di sini `0x7A` adalah parameter tabel dan `0x9E` adalah checksum dari `72 05 71 7A`. Jangan menambahkan checksum kedua. Tabel `0x7A` hanya contoh sintetis; `:core` tidak mengirim tabel live data apa pun tanpa profil yang disediakan pemanggil.

### 3.5 Respons identifikasi sintetis

```text
72 0C 71 53 41 4D 50 4C 45 30 31
sum = 0x312 → 0x12
checksum = 0xEE
frame: 72 0C 71 53 41 4D 50 4C 45 30 31 EE
```

Payload ASCII: `SAMPLE01`.

### 3.6 Respons DTC sintetis

```text
72 0A 74 01 02 01 01 07 01
sum = 0xFD
checksum = 0x03
frame: 72 0A 74 01 02 01 01 07 01 03
```

## 4. Panjang dan checksum

Declared length mencakup checksum. Contoh:

```text
72 05 71 7A 9E
 │  │  │  │  └ checksum
 │  │  │  └ parameter tabel sintetis
 │  │  └ service
 │  └ total 5 byte
 └ header
```

Jika panjang diubah tanpa menghitung ulang checksum, frame ditolak. Jika checksum dihitung atas panjang yang salah, frame juga ditolak.

## 5. Checksum ROM

`HondaChecksum` juga menyediakan dua fungsi untuk data ROM, bukan untuk frame wire.

### 5.1 Keihin 16-bit

```kotlin
fun calculateKeihin16BitChecksum(romData: ByteArray, startAddr: Int, endAddr: Int): Int
```

- Membaca pasangan byte big-endian menjadi word 16-bit.
- Menjumlahkan word dengan wrap `0xFFFF`.
- Loop berhenti sebelum `min(endAddr, size - 1)`.
- Hasilnya integer 16-bit, bukan byte.

Fungsi ini tidak dipakai oleh jalur diagnostik. Jangan memakainya untuk memvalidasi frame K-Line.

### 5.2 Inverted 8-bit

```kotlin
fun calculateInverted8BitChecksum(romData: ByteArray, startAddr: Int, endAddr: Int): Byte
```

- Menjumlahkan byte dengan wrap 8-bit.
- Mengembalikan `sum xor 0xFF`.

Ini juga checksum ROM, bukan checksum frame. Perhatikan perbedaan operasi: frame memakai two's complement (`0x100 - sum`), ROM memakai XOR (`sum xor 0xFF`). Keduanya sama hanya jika `sum == 0`.

## 6. Kesalahan umum

| Kesalahan | Akibat |
|---|---|
| Menghitung checksum termasuk byte checksum | Frame ditolak |
| Lupa bahwa declared length termasuk checksum | Kandidat tidak lengkap |
| Menambahkan checksum pada request 5 byte yang sudah punya checksum | Frame jadi 6 byte dan salah |
| Memakai checksum ROM untuk frame | Nilai tidak cocok |
| Menganggap checksum valid = ECU asli | Klaim berlebihan |

## 7. Pengujian

`HondaFrameCodecTest`, `KlineTransceiverTest`, dan contoh di `examples/protocol_frames/requests.json` memverifikasi:

- checksum request katalog,
- checksum respons sintetis,
- penolakan checksum salah,
- penolakan declared length yang tidak cocok.

Semua contoh adalah data sintetis dan tidak membuktikan respons ECU nyata.
