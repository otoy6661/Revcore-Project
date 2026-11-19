# Pusat Pembelajaran RevCore

> **RevCore harus menjadi jembatan, bukan pagar.**

Repository ini adalah jembatan untuk memahami cara kerja aplikasi diagnostik: dari
Android, Kotlin, Gradle, dan Git sampai frame K-Line, checksum, DTC, dan Live Data.
Jembatan berarti pengetahuan dibuka seluas mungkin, bukan dikunci agar orang bergantung
pada satu pihak. Karena itu materi di sini:

- tidak mewajibkan pembelian alat, lisensi, atau layanan apa pun;
- tidak menyembunyikan langkah yang diperlukan untuk membangun dan menguji sendiri;
- tidak menghalangi siapa pun mengembangkan aplikasi dengan identitasnya sendiri;
- tetap menghormati batas hak cipta, privasi, dan materi yang bukan milik publik.

Batas tersebut bukan pagar pengetahuan. Itu batas tanggung jawab: jangan mengarang
hasil, jangan membocorkan rahasia, dan jangan mengklaim sesuatu yang belum diuji.

## Untuk siapa materi ini

Materi ditulis untuk **pemula total**. Tidak diasumsikan bahwa pembaca sudah pernah
membuat aplikasi Android, memakai Gradle, atau membaca frame hexadecimal. Setiap istilah
diperkenalkan sebelum dipakai. Pembaca yang sudah berpengalaman boleh langsung ke
[referensi protokol](04-referensi-protokol-dan-data.md) atau
[katalog kegagalan](05-katalog-kegagalan-dan-pemulihan.md).

## Peta belajar

| Dokumen | Isi | Hasil yang diharapkan |
|---|---|---|
| [00 — Mulai dari sini](00-mulai-dari-sini.md) | Jalur belajar, glosarium, cara membaca source | Tahu urutan belajar dan istilah dasar |
| [01 — Dasar Android, Kotlin, Gradle, Git](01-dasar-android-kotlin-gradle-git.md) | Perangkat, JDK, SDK, Gradle, struktur proyek, Git | Dapat membuka dan menjalankan proyek |
| [02 — Membangun APK](02-membangun-apk.md) | Debug APK, signing, install, APK vs AAB vs AAR | Dapat menghasilkan dan memasang APK sendiri |
| [03 — Konsep aplikasi diagnostik](03-konsep-aplikasi-diagnostik.md) | Arsitektur, transport, protokol, DTC, Live Data | Paham alur data dari aplikasi ke ECU |
| [04 — Referensi protokol dan data](04-referensi-protokol-dan-data.md) | 16 topik dengan asal, metode, validasi, batas | Dapat membaca frame dengan benar |
| [05 — Katalog kegagalan dan pemulihan](05-katalog-kegagalan-dan-pemulihan.md) | 18 kasus dengan 12 field | Dapat mendiagnosis kegagalan tanpa menebak |
| [06 — Panduan pengembangan dengan AI](06-panduan-pengembangan-dengan-ai.md) | Prompt siap pakai dan disiplin review | Memakai AI tanpa kehilangan kendali |
| [07 — Kontributor dan kemandirian](07-kontributor-dan-kemandirian.md) | Fork, PR, menambah profil, aplikasi sendiri | Dapat berkontribusi atau membangun mandiri |
| [08 — Contoh progresif](08-contoh-progresif.md) | 16 langkah dari Hello World sampai database sendiri | Belajar dengan proyek yang dapat dijalankan |
| [09 — Inventaris dan klasifikasi](09-inventaris-dan-klasifikasi.md) | Apa yang dipertahankan, diperbaiki, ditambah, dan belum terverifikasi | Tahu batas dan temuan terbuka |

## Label bukti

Setiap klaim teknis di materi ini memakai salah satu label berikut. Label menjelaskan
**seberapa kuat buktinya**, bukan seberapa menarik klaimnya.

| Label | Arti |
|---|---|
| Synthetic | Data buatan untuk simulasi |
| Reconstructed | Hasil rekonstruksi berdasarkan analisis |
| Source-analyzed | Ditemukan melalui analisis source |
| Hardware-observed | Diamati dari komunikasi perangkat keras |
| Vehicle-tested | Diuji pada kendaraan yang disebutkan |
| Verified | Memiliki bukti pengujian yang memadai |

Repository juga memakai label lama untuk provenans berkas: `ORIGINAL`, `DECOMPILED`,
`RECONSTRUCTED`, `REFERENCE`, dan `UNVERIFIED`. Artinya dijelaskan di
[NOTICE.md](../NOTICE.md) §3. Kedua sistem label tidak saling menggantikan: label berkas
menjelaskan asal materi, label bukti menjelaskan tingkat pengujian.

**Tidak ada materi di repository ini yang berstatus Hardware-observed atau
Vehicle-tested.** Semua frame contoh adalah Synthetic atau Source-analyzed. Unit test
membuktikan logika perangkat lunak, bukan kompatibilitas kendaraan.

## Apa yang ada dan tidak ada di repository

Yang ada:

- pustaka Android `:core` dengan Communication Engine, protocol baseline Honda PGM-FI,
  parser, DTC, Live Data generik, database contoh, dan 153 unit test;
- dokumentasi developer untuk setiap modul publik;
- contoh data sintetis di `examples/`;
- pusat pembelajaran ini dan proyek contoh di `samples/`.

Yang tidak ada:

- aplikasi RevCore lengkap dan Application Layer (UI, navigasi, lifecycle);
- mekanisme Security/Integrity;
- berkas MAP mentah, firmware, BIN/ROM pihak lain, dan kunci kontainer;
- kredensial, keystore, atau token.

Database kendaraan, profil ECM, inventaris MAP, dan tabel Live Data hasil pengembangan
RevCore tersedia di `data/` dan dijelaskan di [docs/LIVE_DATA_REFERENCE.md](../docs/LIVE_DATA_REFERENCE.md).

Batas lengkap ada di [docs/SECURITY_AND_INTEGRITY.md](../docs/SECURITY_AND_INTEGRITY.md)
dan [NOTICE.md](../NOTICE.md).

## Aturan belajar yang tidak boleh dilanggar

1. Jangan menyebut hasil simulasi sebagai hasil pengujian kendaraan.
2. Jangan menyalin frame, offset, atau rumus dari tebakan. Cari asalnya.
3. Jangan menambahkan operasi tulis ke ECU (flash, remap, security access) berdasarkan
   materi ini. Repository publik hanya membahas baca, diagnostik, dan clear DTC.
4. Jangan menaruh keystore, kata sandi, token, atau data pribadi di source, laporan,
   issue, atau commit.
5. Jangan mengunggah APK atau materi analisis ke layanan pihak ketiga tanpa keputusan
   pemilik.
6. Jangan menghapus atau melemahkan test hanya agar build terlihat lulus.

## Cara memakai pusat pembelajaran ini

- Pemula: baca 00, 01, 02 berurutan, lalu kerjakan `samples/01-hello-android`.
- Ingin paham protokol: baca 03, lalu 04 sambil membuka `docs/KLINE_PROTOCOL.md` dan
  `docs/CHECKSUM.md`, lalu kerjakan `samples/03-checksum` sampai `samples/07-live-data-decoder`.
- Ingin berkontribusi: baca 06 dan 07, lalu `CONTRIBUTING.md`.
- Menghadapi error: cari gejalanya di 05 sebelum mengubah kode.

Setiap proyek contoh berdiri sendiri: punya README, perintah build, input/output, dan
batasnya sendiri. Tidak ada contoh yang mengubah source produksi `:core`.
