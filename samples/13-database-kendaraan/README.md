# Database Kendaraan

## Tujuan

Membuat database kendaraan kecil yang membaca record dari `data/vehicles.json` dengan parser JSON sendiri, lalu memvalidasi setiap record sebelum dipakai — khususnya menolak id ganda, nama kosong, family asing, dan identitas kendaraan nyata.

## Yang dipelajari

- Struktur record kendaraan: `id`, `displayName`, `family`, `ecuIdPattern`, `notes`.
- Pencarian berdasarkan `id` dan perilaku saat id tidak ditemukan (`null`, bukan record palsu).
- Validasi saat memuat:
  - **id unik** — id ganda langsung ditolak,
  - **nama tidak kosong**,
  - **family dikenal** (hanya `contoh-baseline` dan `contoh-family-lain` pada sampel ini),
  - **tanpa merek/model nyata** — daftar tolak sederhana dengan pencocokan kata utuh, sehingga `pcb` tidak salah ditolak karena mengandung `cb`.
- Pentingnya "gagal saat memuat": database yang setengah valid lebih berbahaya daripada database yang menolak diri sendiri.
- Parser JSON mini di sampel ini **mandiri**; caranya mirip sampel 08, tetapi kodenya tidak bergantung pada folder sampel lain.

## Prasyarat

- JDK 17 atau lebih baru (diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android, internet, atau library eksternal.

## Cara menjalankan

Jalankan dari **direktori sampel ini** karena berkas data dibaca dengan path relatif `data/vehicles.json`.

```bash
cd samples/13-database-kendaraan
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: `data/vehicles.json` berisi tiga record **sintetis** — `contoh-motor-a`, `contoh-motor-b` (keduanya `contoh-baseline`) dan `contoh-motor-c` (`contoh-family-lain`) — dengan pola ID `SIM-A-0001`, `SIM-B-0002`, `SIM-C-0003`.

Output yang diharapkan:

```text
-- Isi database (3 record) --
  contoh-motor-a | Contoh Motor A | contoh-baseline | SIM-A-0001 | ...
  ...
lookup contoh-motor-b: Contoh Motor B
lookup tidak-ada       : tidak ditemukan
```

lalu baris `PASS`/`FAIL` untuk tiap pengujian, `Ringkasan: PASS=11 FAIL=0`, dan `HASIL: OK ...` dengan kode keluar 0.

Pengujian memeriksa: 3 record termuat; lookup berhasil untuk id dikenal dan `null` untuk id asing; semua family dikenal; **id ganda ditolak**; nama kosong, family asing, nama merek nyata, JSON rusak, dan record bernama nyata tanpa bukti semuanya ditolak dengan pesan yang menyebutkan masalahnya.

## Batasan

- Seluruh data **sintetis**. Tidak ada identitas kendaraan, VIN, nomor rangka, atau pola ECU nyata.
- Daftar tolak merek/model hanya contoh singkat, bukan alat pengamanan lengkap. Ia mencegah kesalahan tak sengaja, bukan penjamin kepatuhan.
- Parser JSON hanya mendukung tipe yang dipakai berkas ini (objek, larik, string, bilangan bulat, boolean, `null`); escape `\u` dan pecahan tidak didukung.
- Tidak ada penyimpanan, indeks, atau pencarian selain pencarian langsung berdasarkan `id`.
- Tidak ada koneksi ke kendaraan; `ecuIdPattern` hanya string contoh.

### Menambah record

1. Tambahkan objek baru di larik `vehicles` pada `data/vehicles.json`.
2. Wajib: `id` (unik), `displayName` (tidak kosong), `family` (harus salah satu family dikenal).
3. Opsional: `ecuIdPattern`, `notes`.
4. Jalankan ulang sampel. Jika record ditolak, pesan kesalahan akan menyebut alasannya.

### Penambahan data nyata

Menambahkan identitas kendaraan **nyata** tidak boleh dilakukan lewat sampel ini. Data nyata memerlukan **record bukti** (sumber, tanggal, metode verifikasi) dan **tinjauan hak** (hak untuk mempublikasikan data tersebut). Tanpa keduanya, record harus tetap sintetis.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan validasi bahwa `ecuIdPattern` harus diawali `SIM-` untuk data sintetis.
2. Tambahkan pencarian berdasarkan `family` yang mengembalikan daftar record.
3. Buat `data/vehicles-rusak.json` dengan id ganda dan pastikan program menolak memuatnya dengan pesan jelas.
4. Tambahkan field `evidence` (opsional) dan tolak record non-sintetis yang tidak memiliki record bukti.
5. Ganti daftar tolak statis dengan berkas `data/forbidden-words.txt` yang dibaca saat program mulai.
