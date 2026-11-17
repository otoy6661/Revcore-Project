# FRAME PARSER

Dokumen ini menjelaskan bagaimana byte mentah dari USB berubah menjadi `CommandOutcome.Frame`. Fokusnya adalah `KlineWireEngine` karena itulah jalur yang dipakai `HondaProtocolEngine`.

## 1. Prinsip

1. Parser tidak memperbaiki frame. Jika tidak valid, frame ditolak.
2. Parser tidak menebak offset payload. Offset ditentukan oleh layout tabel setelah frame dinyatakan valid.
3. Parser tidak menggabungkan dua kandidat menjadi satu.
4. Hanya frame pertama yang valid yang dikembalikan; byte setelahnya tidak dianggap bagian dari frame itu.

## 2. Pipeline

```text
raw RX
  → stripLeadingEcho
  → extractFrame
       → cari header yang diterima
       → baca declared length
       → potong kandidat
       → tolak jika identik TX sendiri
       → isWellFormed (header, length, checksum)
  → CommandOutcome.Frame / Failure
```

## 3. Aturan penerimaan

`HondaFrameCodec.isWellFormed` memeriksa empat hal:

| Pemeriksaan | Aturan |
|---|---|
| Ukuran | `4 <= size <= 0xFE` |
| Header | ada di `acceptedHeaders` |
| Declared length | sama dengan ukuran kandidat |
| Checksum | byte terakhir = checksum seluruh byte sebelumnya |

Jika salah satu gagal, kandidat bukan frame valid.

## 4. Pencarian kandidat

`extractFrame` memindai byte demi byte:

```text
i = 0
selama i < buffer.size:
    jika buffer[i] bukan header yang diterima:
        i++, lanjut
    jika byte length belum tersedia:
        catat incomplete, i++, lanjut
    declared = buffer[i+1]
    jika declared < 4:
        i++, lanjut
    jika i + declared > buffer.size:
        catat incomplete, i++, lanjut
    kandidat = buffer[i .. i+declared-1]
    jika kandidat identik TX sendiri:
        catat echo, i += declared, lanjut
    jika isWellFormed(kandidat):
        kembalikan kandidat
    catat checksum gagal, i++, lanjut
```

Byte sampah sebelum header dilewati satu per satu. Ini penting karena adapter dapat mengirim noise atau sisa byte dari transaksi sebelumnya.

## 5. Header yang diterima

Default:

```text
0x72, 0x02, 0x7E, 0x7C, 0xFE, 0x7D, 0x7B
```

Header di luar daftar tidak pernah menjadi awal kandidat. Daftar ini dapat diubah melalui `KlineWireConfig.acceptedHeaders`, tetapi engine baseline memakai default.

## 6. Echo

Dua lapis:

1. **Echo di awal** — `stripLeadingEcho` membuang `0x00` di depan, lalu membandingkan byte dengan request. Jika cocok, request dibuang dari awal RX.
2. **Echo di tengah** — `recentTxFrames` menyimpan hingga 32 frame TX selama 1500 ms. Kandidat yang identik ditolak.

`0xFF` di awal **tidak** dibuang karena dapat merupakan data. Hanya `0x00` padding yang dibuang.

Jika seluruh isi buffer hanya TX sendiri, hasilnya `LOCAL_ECHO_ONLY`, bukan timeout biasa. Perbedaan ini penting saat debugging: ECU mungkin tidak terpasang, atau echo belum terfilter.

## 7. Buffer dan fragmentasi

- Setiap `read()` menambahkan potongan ke buffer.
- Parser dijalankan ulang setiap kali potongan baru masuk.
- Buffer maksimum 1024 byte. Jika terlampaui, byte tertua dibuang sampai tersisa 512 byte.
- Byte yang dibuang dicatat ke log sebelum parsing.

Membuang byte tertua dapat memotong awal frame. Karena itu buffer besar tidak menjamin parsing berhasil; ia hanya mencegah pertumbuhan memori tanpa batas.

## 8. Kegagalan

`CommandOutcome.Failure` membawa `reason` dan `raw`. Reason yang mungkin:

| Reason | Arti |
|---|---|
| `Tidak ada respons` | Tidak ada byte sama sekali |
| `Hanya echo TX sendiri...` | RX hanya berisi TX |
| `Tidak ada byte diterima dalam ... ms` | Timeout |
| `Frame kandidat ditolak: checksum tidak cocok` | Ada kandidat, checksum salah |
| `Frame K-Line belum lengkap (...)` | Declared length melebihi byte yang tersedia |
| `Tidak ada frame K-Line valid pada RX` | Ada byte, tidak ada kandidat valid |
| `Gagal menulis ke endpoint USB` | Write gagal |

Reason tidak boleh diterjemahkan menjadi “ECU rusak” atau “kontak mati”.

## 9. Payload

Setelah frame valid:

```kotlin
payload = frame.copyOfRange(3, frame.size - 1)
```

Untuk Live Data, payload dimulai di index 4 karena byte index 3 adalah echo tabel:

```text
[header][len][service][table echo][data...][checksum]
```

`extractLivePayload` memeriksa bahwa `table echo` sama dengan tabel yang diminta. Jika tidak cocok, frame tidak diparse. Ini mencegah layout tabel yang salah diterapkan pada respons tabel lain.

## 10. Parser alternatif

`KlineTransceiver` dan `KlineEchoFilter` menyediakan implementasi lain:

- `KlineEchoFilter` menyimpan buffer, menekan echo, lalu mencari header dan panjang.
- `KlineTransceiver` memakai outcome dan statistik yang lebih rinci.

Keduanya tidak dipakai oleh `HondaProtocolEngine`. Jika hendak memakainya, jangan mencampur dengan `KlineWireEngine` pada transport yang sama.

## 11. Pengujian

`HondaFrameCodecTest` dan `KlineWireEngineTest` mencakup:

- checksum valid dan invalid,
- declared length tidak cocok,
- header tidak dikenal,
- echo di awal dan di tengah,
- hanya echo,
- fragmentasi RX,
- buffer melebihi batas,
- timeout.

Test memakai transport palsu; tidak ada ECU nyata yang terlibat.

## 12. Batas

- Parser tidak memvalidasi arti service atau parameter.
- Parser tidak mengetahui family ECU.
- Satu frame valid tidak membuktikan ECU atau kendaraan tertentu.
- Frame dengan checksum valid tetapi layout payload salah tetap dapat lolos parser; lapisan pemanggil wajib memeriksa echo tabel dan rentang nilai.
