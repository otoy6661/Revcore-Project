# Parser JSON Mini

## Tujuan

Membuat parser JSON kecil dari nol (tanpa library) yang cukup untuk membaca konfigurasi profil ECU, dan menunjukkan bahwa konfigurasi yang rusak harus gagal dengan pesan jelas, bukan diam-diam menjadi `null`.

## Yang dipelajari

- Struktur JSON dasar: objek, larik, string, bilangan bulat, boolean, dan `null`.
- Teknik *recursive descent*: satu metode per jenis nilai (`parseObject`, `parseArray`, `parseString`, `parseInteger`).
- Menjaga posisi karakter (`pos`) dan melaporkan baris/kolom saat menemukan kesalahan.
- Mengapa aplikasi diagnostik membutuhkan konfigurasi: frame permintaan, nama field live data, offset, skala, dan satuan berbeda per keluarga ECU. Nilai-nilai ini tidak boleh ditulis tersebar di kode, tetapi dimuat dari berkas agar mudah ditinjau dan diperbaiki.
- Mengapa konfigurasi rusak wajib gagal keras (*fail loudly*): jika parser mengembalikan `null` diam-diam, aplikasi bisa mengirim frame kosong, menampilkan "tidak ada DTC" padahal ECU tidak pernah dibaca, atau memakai offset yang salah. Kesalahan harus terlihat, bukan tersembunyi.

## Prasyarat

- JDK 17 atau lebih baru (contoh ini diuji dengan Temurin 25) yang menyediakan `javac` dan `java` di PATH.
- Tidak memerlukan Gradle, Android, internet, atau library eksternal.

## Cara menjalankan

Jalankan dari **direktori sampel ini** karena berkas data dibaca dengan path relatif `data/profile.json`.

```bash
cd samples/08-json-mini-parser
javac -d out src/Main.java
java -cp out Main
```

## Input dan output

Input: `data/profile.json`, sebuah profil ECU **sintetis** berisi nama family, daftar frame sebagai string hex, dan dua spesifikasi field live data.

Output yang diharapkan:

- Struktur hasil parse dicetak berjenjang (kunci, larik, dan nilai).
- Ringkasan profil (`family`, `identityRequests`, `dtcReadRequests`).
- Baris `PASS`/`FAIL` untuk setiap pengujian mandiri, lalu `Ringkasan: PASS=n FAIL=0`.
- Baris terakhir `HASIL: OK` dan kode keluar 0. Jika ada `FAIL`, kode keluar 1.

Pengujian memeriksa: JSON valid terparse dengan benar, dan tiga contoh JSON rusak (koma hilang, string tidak ditutup, koma akhir) ditolak dengan pesan kesalahan yang menyebut lokasi.

## Batasan

- Hanya mendukung bilangan **bulat**; bilangan pecahan dan notasi eksponen tidak didukung (cukup untuk sampel ini).
- Escape `\u` hanya menangani karakter BMP; pasangan surrogate tidak digabung.
- Tidak ada validasi skema: parser hanya menjawab "JSON valid atau tidak". Pemeriksaan arti field dilakukan di sampel 09.
- Ukuran berkas kecil; seluruh teks dibaca ke memori.

## Status bukti

Synthetic — data buatan untuk simulasi; tidak ada ECU fisik yang diuji.

## Latihan

1. Tambahkan dukungan bilangan pecahan (misalnya `scale: 0.5`) beserta pengujiannya.
2. Beri pesan khusus untuk duplikat kunci di dalam satu objek (sekarang kunci terakhir menang).
3. Buat `data/profile-rusak.json` dengan satu frame hex yang kurang satu byte, lalu pastikan parser tetap menerima JSON-nya tetapi lapisan validasi berikutnya menolak.
4. Tambahkan metode `asLong`, `asString`, `asList` bergaya akses aman, lalu bandingkan dengan cara akses langsung saat ini.
