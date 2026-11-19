# 00 — Mulai dari Sini

Dokumen ini untuk pembaca yang belum pernah membuat aplikasi Android. Tidak perlu
takut: aplikasi diagnostik pada dasarnya adalah program yang mengirim byte, menerima
byte, lalu menampilkan hasilnya dengan jujur. Semua bagian lain dibangun di atas ide
sederhana itu.

## 1. Gambaran besar

Aplikasi diagnostik memiliki enam lapisan:

```text
Layar aplikasi
  → Communication Engine   (mengelola sesi, antrean, log)
  → Protocol Engine        (menyusun dan memahami frame)
  → Transport              (mengirim byte ke adapter)
  → Adapter USB            (FTDI, CH340, CP210x, ...)
  → ECU kendaraan          (menjawab atau diam)
```

Di repository ini, lapisan kedua sampai keempat tersedia sebagai pustaka `:core`.
Lapisan layar dan keputusan produk adalah milik aplikasi pemanggil.

## 2. Urutan belajar yang disarankan

### Jalur A — Pemula total (Android dan APK)

1. Pasang JDK 21 atau lebih baru (Temurin), Android Studio, Android SDK Platform 36,
   dan Git. Rincian ada di [01 — Dasar Android](01-dasar-android-kotlin-gradle-git.md).
2. Baca struktur proyek dan istilah Gradle di dokumen yang sama.
3. Jalankan `samples/01-hello-android` sampai APK debug benar-benar terpasang di
   perangkat atau emulator.
4. Baca [02 — Membangun APK](02-membangun-apk.md) untuk memahami signing dan perbedaan
   APK, AAB, AAR, dan library.
5. Baru setelah itu buka source `:core`.

### Jalur B — Ingin memahami protokol

1. Baca [03 — Konsep aplikasi diagnostik](03-konsep-aplikasi-diagnostik.md).
2. Kerjakan `samples/03-checksum`, `04-frame-builder`, `05-frame-parser`.
3. Baca [04 — Referensi protokol dan data](04-referensi-protokol-dan-data.md) sambil
   membandingkan dengan `docs/KLINE_PROTOCOL.md`, `docs/CHECKSUM.md`, dan
   `docs/FRAME_PARSER.md`.
4. Lanjutkan ke `samples/06-dtc-parser` dan `07-live-data-decoder`.
5. Tutup dengan `samples/10-ecu-simulasi` dan `12-sesi-diagnostik`.

### Jalur C — Ingin berkontribusi atau membangun aplikasi sendiri

1. Selesaikan Jalur A agar dapat menjalankan test.
2. Baca [06 — Panduan AI](06-panduan-pengembangan-dengan-ai.md) dan
   [07 — Kontributor dan kemandirian](07-kontributor-dan-kemandirian.md).
3. Baca `CONTRIBUTING.md` dan `NOTICE.md`.
4. Mulai dari perubahan kecil: perbaikan dokumentasi, satu unit test, atau satu sensor
   dengan bukti yang jelas.

## 3. Glosarium

| Istilah | Arti sederhana |
|---|---|
| Android | Sistem operasi ponsel; aplikasi berjalan di dalamnya. |
| Activity | Satu layar aplikasi Android. |
| Manifest | Berkas `AndroidManifest.xml` yang mendeklarasikan Activity, izin, dan identitas aplikasi. |
| Kotlin | Bahasa utama RevCore; berjalan di JVM, dapat memanggil Java. |
| Java | Bahasa yang lebih tua; banyak pustaka Android memakainya. |
| JDK | Java Development Kit; berisi compiler `javac` dan runtime `java`. |
| SDK | Software Development Kit; kumpulan alat dan pustaka untuk platform tertentu. |
| Gradle | Alat build yang mengompilasi source menjadi AAR atau APK. |
| AGP | Android Gradle Plugin; menghubungkan Gradle dengan Android. |
| Wrapper | Skrip `gradlew` yang mengunduh versi Gradle yang benar. |
| AAR | Arsip pustaka Android; tidak dapat dipasang sendiri. |
| APK | Paket aplikasi Android; dapat dipasang di perangkat. |
| AAB | Android App Bundle; format unggah ke Play Store, bukan untuk dipasang langsung. |
| Git | Sistem pencatat riwayat perubahan berkas. |
| Commit | Satu catatan perubahan beserta penjelasannya. |
| Branch | Cabang kerja agar perubahan tidak mengganggu jalur utama. |
| Port serial | Jalur komunikasi byte; di ponsel biasanya lewat USB. |
| USB OTG | Kemampuan ponsel menjadi host untuk perangkat USB, termasuk adapter serial. |
| FTDI / CH340 / CP210x | Chip adapter USB-ke-serial yang umum. |
| K-Line | Bus komunikasi satu kabel yang dipakai banyak ECU sepeda motor Honda. |
| TX / RX | Transmit (kirim) dan Receive (terima). |
| Frame | Satu paket byte yang dikirim atau diterima. |
| Header | Byte pertama frame yang menandai jenis sumber/penerima. |
| Payload | Isi frame setelah service. |
| Checksum | Byte terakhir untuk memeriksa apakah frame rusak. |
| Service | Kode operasi di dalam frame, misalnya baca DTC. |
| Echo | Byte yang dikirim kembali oleh adapter sehingga tampak sebagai RX. |
| Parser | Kode yang mengubah byte menjadi struktur data bermakna. |
| ECU | Electronic Control Unit; komputer yang mengatur mesin. |
| ECM | Engine Control Module; istilah lain untuk ECU mesin. |
| DTC | Diagnostic Trouble Code; kode gangguan yang disimpan ECU. |
| MIL | Malfunction Indicator Lamp; lampu indikator gangguan. |
| Live Data | Nilai sensor yang dikirim ECU saat mesin bekerja. |
| Profil ECU | Kumpulan request, layout payload, dan batas untuk satu keluarga ECU. |
| MAP | Tabel kalibrasi (misalnya bahan bakar terhadap RPM dan beban). |
| Database kendaraan | Daftar identitas ECU yang dikenal beserta catatannya. |
| Validasi | Pemeriksaan bahwa data berbentuk benar dan konsisten. |
| Evidence label | Penanda seberapa kuat bukti suatu klaim. |

## 4. Cara membaca source `:core`

Jangan mulai dari file terpanjang. Mulai dari alur yang ingin dipahami:

1. **Bentuk frame.** Buka `core/comm/HondaFrameCodec.kt` dan `core/protocol/HondaChecksum.kt`.
   Cari fungsi yang membangun frame dan menghitung checksum.
2. **Perpindahan byte.** Buka `core/usb/UsbTransport.kt`. Perhatikan bahwa interface ini
   hanya berbicara tentang buka, tutup, baca, tulis, dan kontrol adapter.
3. **Transaksi.** Buka `core/comm/KlineWireEngine.kt`. Cari wake pulse, pengiriman,
   pembuangan echo, timeout, dan retry.
4. **Sesi.** Buka `core/comm/EcuSessionManager.kt`. Perhatikan single-flight dan status.
5. **Orkestrasi.** Baru buka `core/protocol/HondaProtocolEngine.kt`. Baca nama fungsi
   publiknya lebih dulu, lalu satu fungsi privat pada satu waktu.
6. **Bukti.** Setiap kali menemukan angka ajaib, cari asalnya di `docs/`, `examples/`,
   atau test. Jika tidak ada asal, anggap `UNVERIFIED`.

Tips praktis:

- Gunakan pencarian teks untuk nama fungsi, bukan membaca 1.700 baris sekaligus.
- Buka test yang menyebut fungsi tersebut; test sering menjelaskan niat kode.
- Bedakan "kode ada" dari "perilaku terbukti". Keberadaan kelas membuktikan kelas itu
  dikompilasi, bukan bahwa ia bekerja di ECU nyata.
- Jangan mengubah source produksi untuk belajar. Buat proyek di `samples/`.

## 5. Checklist sebelum percaya pada sebuah hasil

- [ ] Data uji sintetis atau nyata? Jika sintetis, jangan sebut hasil kendaraan.
- [ ] Frame memiliki checksum yang benar?
- [ ] Respons positif, negatif, atau tidak ada respons? Ketiganya berbeda.
- [ ] Nilai sensor berasal dari payload, bukan nilai cadangan?
- [ ] DTC kosong berasal dari frame valid, bukan dari kegagalan komunikasi?
- [ ] Perubahan sudah diuji dengan `gradle --offline test` bila menyentuh `:core`?
- [ ] Tidak ada rahasia atau data pribadi di diff?

## 6. Jika tersesat

- Error build: lihat [02 — Membangun APK](02-membangun-apk.md) bagian troubleshooting
  dan [05 — Katalog kegagalan](05-katalog-kegagalan-dan-pemulihan.md).
- Istilah protokol: lihat [04 — Referensi protokol](04-referensi-protokol-dan-data.md).
- Bingung harus belajar apa: kembali ke peta di [README pusat pembelajaran](README.md).

Belajar yang baik tidak dimulai dari menghafal. Ia dimulai dari berani menjalankan satu
contoh kecil, melihat hasilnya, lalu bertanya mengapa.
