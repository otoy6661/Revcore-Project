# 08 — Contoh Progresif

Enam belas langkah berikut membawa pembaca dari "belum pernah membuat aplikasi" sampai
"memiliki database kendaraan sintetis sendiri". Langkah 01–13 adalah proyek yang dapat
dijalankan. Langkah 14–16 adalah panduan yang memerlukan keputusan dan perangkat Anda
sendiri; langkah itu **belum dieksekusi** di repository ini.

## Status yang dipakai

| Status | Arti |
|---|---|
| Build APK terverifikasi | APK benar-benar dibangun dan hash dicatat |
| Logika terverifikasi | Self-test berjalan dan semua assertion lulus |
| Panduan | Langkah tertulis; belum dijalankan sebagai proyek utuh |
| Perlu hardware | Tidak dapat diselesaikan tanpa adapter/ECU |

## Peta 16 langkah

| Langkah | Topik | Direktori | Status |
|---:|---|---|---|
| 01 | Hello Android | [01-hello-android](../samples/01-hello-android/) | Build APK terverifikasi |
| 02 | Java dan Kotlin dasar | [02-java-dan-kotlin-dasar](../samples/02-java-dan-kotlin-dasar/) | Logika terverifikasi |
| 03 | Checksum | [03-checksum](../samples/03-checksum/) | Logika terverifikasi |
| 04 | Menyusun frame | [04-frame-builder](../samples/04-frame-builder/) | Logika terverifikasi |
| 05 | Membaca frame | [05-frame-parser](../samples/05-frame-parser/) | Logika terverifikasi |
| 06 | Parser DTC | [06-dtc-parser](../samples/06-dtc-parser/) | Logika terverifikasi |
| 07 | Decoder Live Data | [07-live-data-decoder](../samples/07-live-data-decoder/) | Logika terverifikasi |
| 08 | Parser JSON mini | [08-json-mini-parser](../samples/08-json-mini-parser/) | Logika terverifikasi |
| 09 | Profil ECU | [09-ecu-profil](../samples/09-ecu-profil/) | Logika terverifikasi |
| 10 | ECU simulasi | [10-ecu-simulasi](../samples/10-ecu-simulasi/) | Logika terverifikasi |
| 11 | Abstraksi transport | [11-abstraksi-transport](../samples/11-abstraksi-transport/) | Logika terverifikasi |
| 12 | Sesi diagnostik | [12-sesi-diagnostik](../samples/12-sesi-diagnostik/) | Logika terverifikasi |
| 13 | Database kendaraan | [13-database-kendaraan](../samples/13-database-kendaraan/) | Logika terverifikasi |
| 14 | Memakai `:core` di aplikasi sendiri | [02 — Membangun APK](02-membangun-apk.md) | Panduan |
| 15 | Menguji adapter nyata | [05 — Katalog kegagalan](05-katalog-kegagalan-dan-pemulihan.md) | Perlu hardware |
| 16 | Aplikasi diagnostik milikmu | [07 — Kemandirian](07-kontributor-dan-kemandirian.md) | Panduan |

## Ringkasan setiap langkah

### 01 — Hello Android

Membuat Activity, manifest, dan APK debug. Input: tidak ada. Output: APK yang menghitung
tombol. Batas: tidak memakai `:core`, tidak ada ECU. Status build tercatat di README
contoh.

### 02 — Java dan Kotlin dasar

Menunjukkan variabel, class, method, dan loop. Input: tidak ada. Output: perhitungan dan
tabel PASS. Batas: contoh bahasa, bukan protokol.

### 03 — Checksum

Menghitung checksum komplemen dua. Input: byte frame tanpa checksum. Output: satu byte
checksum. Self-test memakai frame `72 05 74 01` → `14`. Batas: checksum bukan tanda
tangan.

### 04 — Menyusun frame

Membangun `[header][length][service][params][checksum]`. Input: header, service,
parameter. Output: frame lengkap. Self-test mereproduksi delapan frame request yang
tercatat di dokumentasi. Batas: frame request kandidat, bukan bukti ECU menerimanya.

### 05 — Membaca frame

Memvalidasi ukuran, header, dan checksum; memisahkan payload; mendeteksi `0x7F`. Input:
string hex. Output: struktur frame atau alasan penolakan. Batas: frame valid belum tentu
berisi data yang diminta.

### 06 — Parser DTC

Mendukung count-prefixed dan pair stream. Input: payload. Output: daftar kode dikenal
atau `DTC XXYY`. Batas: kode tak dikenal tidak diberi P-code; payload tak dapat diparse
menghasilkan kegagalan, bukan daftar kosong.

### 07 — Decoder Live Data

Membaca field big-endian dan menerapkan skala/bias. Input: payload sintetis. Output:
tabel field, nilai, satuan. Batas: satu tabel sintetis; tidak ada katalog proprietary.

### 08 — Parser JSON mini

Membaca konfigurasi tanpa dependency. Input: `data/profile.json`. Output: struktur
profil. Batas: bukan parser JSON lengkap; JSON rusak harus gagal dengan pesan jelas.

### 09 — Profil ECU

Memodelkan family, request identitas, baca/clear DTC, dan field Live Data. Input: profil
sintetis. Output: validasi checksum dan lookup family. Batas: family lain kosong; jangan
meminjam frame.

### 10 — ECU simulasi

Menjawab frame di memori. Input: frame request. Output: frame response dengan checksum
valid atau `0x7F`. Batas: membuktikan logika software, bukan kompatibilitas kendaraan.

### 11 — Abstraksi transport

Memisahkan `Transport` dari USB. Input: byte. Output: log TX/RX dan respons simulator.
Batas: implementasi USB nyata belum diuji.

### 12 — Sesi diagnostik

Menjalankan connect → identify → baca DTC → clear → baca ulang. Input: skenario simulasi.
Output: state `SUCCESS_VERIFIED`, `RE_DETECTED_ACTIVE_FAULT`, atau `CLEARED_UNVERIFIED`.
Batas: MIL tidak dijamin padam.

### 13 — Database kendaraan

Menyimpan tiga record sintetis, lookup by id, dan validasi duplikat. Input:
`data/vehicles.json`. Output: hasil lookup dan daftar kesalahan validasi. Batas: tidak
ada identitas kendaraan nyata.

### 14 — Memakai `:core` di aplikasi sendiri

Panduan membuat proyek aplikasi, memakai AAR, meminta izin USB, dan menangani state.
Status: `REFERENCE`; belum diuji sebagai proyek aplikasi di repository ini.

### 15 — Menguji adapter nyata

Checklist: daya, ground, level tegangan, wiring, baud, wake pulse, log TX/RX, dan
kondisi kontak. Status: perlu hardware; belum dieksekusi.

### 16 — Aplikasi diagnostik milikmu

Panduan identitas, atribusi, lisensi, dan batas distribusi. Status: panduan; keputusan
tetap milik pembuat aplikasi dan pemilik materi.

## Cara menjalankan contoh 02–13

```bash
cd samples/03-checksum
javac -d out src/Main.java
java -cp out Main
```

Semua contoh memakai Java tanpa Gradle. Contoh yang membaca berkas data harus dijalankan
dari direktori contohnya.

## Mengapa urutannya seperti ini

- Langkah 01 memastikan alat build bekerja sebelum logika diperkenalkan.
- Langkah 02–05 membangun pemahaman byte dari bawah: bahasa, checksum, frame, parser.
- Langkah 06–07 masuk ke data diagnostik: DTC dan sensor.
- Langkah 08–11 memisahkan konfigurasi, profil, simulasi, dan transport.
- Langkah 12 menyatukan semuanya menjadi sesi.
- Langkah 13 menutup dengan data kendaraan yang dapat divalidasi.
- Langkah 14–16 membawa pengetahuan ke aplikasi dan tanggung jawab nyata.

Setiap langkah sengaja kecil. Jangan melompat ke sesi diagnostik sebelum memahami frame;
hasil yang terlihat benar tanpa pemahaman adalah kebetulan yang berbahaya.

## Aturan mengerjakan contoh

1. Jalankan self-test sebelum mengubah apa pun.
2. Ubah satu hal, lalu jalankan lagi.
3. Baca pesan FAIL; jangan menghapus assertion agar lulus.
4. Jangan menambahkan data nyata ke contoh.
5. Jangan menganggap contoh sebagai bukti hardware.
