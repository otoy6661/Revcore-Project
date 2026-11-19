# Sampel 06 - DTC parser

## Tujuan

Mengubah payload DTC menjadi daftar kode yang bisa dibaca manusia, sambil
membedakan tiga hasil yang berbeda: **ada kode**, **tidak ada kode yang sah
secara eksplisit**, dan **gagal memparse**. Kegagalan tidak boleh terlihat
seperti "tidak ada DTC".

## Yang dipelajari

- Dua bentuk payload DTC yang didukung `docs/DTC_ENGINE.md`:
  - count-prefixed: `[count][code][sub][code][sub]...`
  - pair stream: `[code][sub][code][sub]...`
- Kata "DTC" di tutorial ini mengikuti pemakaian di repo RevCore, yaitu kode
  mentah dari payload (bukan arti final kode P bila belum ada di tabel).
- `null` sebagai sinyal **gagal**, bukan daftar kosong.
- Menampilkan kode tak dikenal apa adanya (`DTC XXYY`) tanpa mengarang P-code.
- `List` dan objek kecil (`Dtc`) untuk menyimpan hasil parsing.

## Prasyarat

- JDK (Temurin 21 atau lebih baru) terpasang, `javac` dan `java` ada di PATH.
- Sudah membaca sampel 05 (frame parser) agar paham asal payload.
- Tidak butuh Android, Gradle, maupun jaringan.

## Cara menjalankan

```bash
cd samples/06-dtc-parser
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Tidak ada input. Payload uji ditulis langsung di `Main.java` sebagai array hex.

| Payload | Bentuk | Hasil |
|---|---|---|
| `02 01 01 07 01` | count-prefixed | `P0107, P0117` |
| `00 07 01 FF 08 01 21 20` | pair stream | `P0117, P0122, DTC 2100` |
| `01 05 0F` | pair stream | `DTC 050F` |
| `7F 74 12` | respons negatif | ERROR (null) |
| (kosong) | - | ERROR (null) |
| `05 01 01` | count gagal, pair stream | `DTC 0501, DTC 0100` |
| `00 FF 72` | hanya byte lewatan | ERROR (null) |
| `02 01 01 01 01` | count-prefixed | `P0107` (duplikat dibuang) |

Contoh potongan output:

```text
count-prefixed payload: 02 01 01 07 01
  hasil: P0107, P0117
PASS count-prefixed (P0107, P0117)
---
Ringkasan: PASS=14 FAIL=0
```

Exit code `0` jika semua test PASS, `1` jika ada yang FAIL.

## Aturan yang diikuti (docs/DTC_ENGINE.md)

1. Count-prefixed dipakai lebih dulu bila:
   - `count` bernilai 1..10,
   - payload minimal `1 + count*2` byte,
   - setiap `code` 1..99 dan bukan byte header protokol
     (`0x02, 0x72, 0x7E, 0x7C, 0x7D, 0x7B, 0xFE`).
   Jika satu syarat gagal, seluruh bentuk count-prefixed dibatalkan.
2. Pair stream dipakai sebagai cadangan: byte `0x00`, `0xFF`, byte header
   protokol, dan kode di luar 1..99 dilewati satu per satu.
3. Subcode di luar rentang 0..15 **dianggap 0** (lihat `21 20` -> sub 0).
4. Pasangan (code, sub) yang berulang hanya disimpan sekali.
5. Kode di luar tabel 15 kode ditampilkan `DTC XXYY` dengan deskripsi
   `Kode ECM 0xXXYY (belum terverifikasi)`. Tidak ada P-code karangan.

## OK_EMPTY vs gagal

Ini poin terpenting sampel ini:

| Keadaan | Arti | Ditampilkan sebagai |
|---|---|---|
| `OK_EMPTY` | Frame valid dan payload memang tidak memuat kode | "tidak ada DTC" |
| gagal parse | Payload kosong, respons negatif, atau struktur tidak jelas | ERROR, daftar lama **tidak** diganti |

Contoh dilindungi: payload `7F 74 12` (ECU menolak query) dan payload kosong
keduanya menghasilkan `null` di sampel ini, **bukan** daftar kosong. Pada
engine RevCore hal ini mencegah layar menampilkan "tidak ada DTC" hanya karena
kabel lepas. Catatan: kode asli `HondaDtcDatabase.parseDtcPacket` mengembalikan
daftar kosong untuk payload kosong; sampel ini sengaja lebih ketat agar
perbedaan error dan "kosong" terlihat jelas (lihat bagian Batasan).

## Batasan

- Tidak membaca frame; payload diberikan langsung sebagai array (lihat sampel 05).
- Tidak mengurai echo service `[0x03][pairs...]` / `[0x00][pairs...]` yang
  ditangani `HondaDtcDatabase.parseDtcResponse`; hanya `parseDtcPacket`.
- Karena payload kosong dianggap error, sampel ini tidak dapat menghasilkan
  `OK_EMPTY`. Ini keputusan pedagogis, bukan cermin persis kode RevCore.
- Arti kode mengikuti database repo dan belum diverifikasi terhadap buku servis.
- Tidak berkomunikasi dengan kendaraan.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Ubah sampel agar payload kosong menghasilkan `OK_EMPTY` sementara respons
   negatif tetap ERROR. Buat jenis hasil (enum) agar perbedaannya eksplisit.
2. Tambahkan payload count-prefixed yang memuat byte header (`0x72`) sebagai
   kode; pastikan seluruh bentuk count-prefixed dibatalkan.
3. Buat payload pair stream yang diakhiri satu byte yatim (misal `07`).
   Menurut aturan sampel, subcode-nya berapa?
4. Cari kode `(54, 1)` di tabel: apa nama standarnya? Lalu coba `(54, 2)`;
   mengapa hasilnya `DTC 3602`, bukan P1602?
