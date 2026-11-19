# CONTRIBUTING

Terima kasih telah membantu RevCore. Panduan ini berlaku untuk seluruh kontribusi ke repository developer publik: source `:core`, test, contoh, dan dokumentasi.

Sebelum mulai, baca [NOTICE.md](NOTICE.md). Kontribusi tidak mengubah kepemilikan atau ketentuan izin pemilik.

## 1. Prinsip kerja

1. **Bukti lebih penting daripada kelengkapan tampilan.** Jangan menambah fitur yang terlihat lengkap dengan mengarang frame, respons, nilai sensor, atau hasil uji.
2. **Jangan mengubah perilaku hanya agar test lulus.** Jika test dan implementasi berbeda, tentukan mana yang didukung bukti, dokumentasikan keputusan, lalu perbaiki salah satunya secara eksplisit.
3. **Pisahkan status bukti.** Gunakan label `ORIGINAL`, `DECOMPILED`, `RECONSTRUCTED`, `REFERENCE`, `UNVERIFIED` pada source, dokumentasi, atau catatan audit.
4. **Batasi cakupan.** Application Layer dan Security/Integrity bukan target publikasi. Jangan menambahkan source, diagram, atau detail internalnya.
5. **Jangan memasukkan materi terlarang.** Database ECM lengkap, MAP/profil/firmware pihak ketiga yang haknya tidak jelas, keystore, token, dan data pribadi tidak boleh masuk repository.

## 2. Standar perubahan source

- Bahasa kode: Kotlin. Pertahankan package yang sudah ada agar pemetaan ke APK tetap dapat ditelusuri.
- Ikuti gaya file di sekitarnya: 4 spasi, nama class `PascalCase`, fungsi `camelCase`, konstanta `UPPER_SNAKE_CASE`.
- Pertahankan API publik yang sudah dipakai test dan contoh. Perubahan signature harus disertai alasan dan pembaruan dokumentasi.
- Jangan menambah dependency tanpa pembahasan. Setiap dependency baru harus dicatat di [audit/DEPENDENCY_AUDIT.md](audit/DEPENDENCY_AUDIT.md) dan [LICENSE-DEPENDENCIES.md](LICENSE-DEPENDENCIES.md).
- Jangan menambahkan akses jaringan, telemetri, iklan, atau unggahan data. Pustaka ini bekerja lokal dengan adapter USB.
- Jangan menambahkan operasi tulis ke ECU (flash, remap, security access) tanpa bukti protokol dan persetujuan pemilik. Repository publik ini hanya mencakup operasi baca/diagnostik dan clear DTC yang sudah ada.
- Jangan menghapus pemeriksaan keamanan panggilan (validasi checksum, panjang, header, echo, rentang nilai) demi menyederhanakan kode.

## 3. Standar bukti dan penamaan

Saat menambahkan perilaku komunikasi, sertakan:

- frame request dan respons yang menjadi dasar (hex);
- sumber bukti: katalog baseline, respons terekam, referensi firmware, atau hipotesis;
- label status;
- batas yang belum diketahui.

Jangan menulis "terbukti", "teruji", atau "kompatibel" tanpa bukti pengujian. Gunakan kalimat seperti "direkonstruksi dari katalog baseline" atau "belum diverifikasi pada hardware".

## 4. Test

- Setiap perubahan parser, checksum, framing, identitas, DTC, atau live data harus memiliki unit test.
- Gunakan data sintetis atau data yang hak penggunaannya jelas. Jangan memakai respons ECU nyata tanpa izin.
- Fixture harus menyatakan bahwa data sintetis. Contoh ada di `examples/`.
- Jalankan seluruh test sebelum mengirim perubahan:

```bash
export ANDROID_HOME=/path/to/Android/Sdk
gradle --offline clean test
```

- Test yang memerlukan hardware harus diberi anotasi/dokumentasi terpisah dan tidak boleh dilaporkan sebagai lulus jika hardware tidak tersedia.
- Jangan menambahkan test yang selalu lulus tanpa assertion berarti.

Rincian ada di [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md).

## 5. Dokumentasi

- Dokumentasi wajib Bahasa Indonesia. Nama teknis (USB, FTDI, CH340, CP210x, K-Line, ECU, ECM, DTC, PID, TX/RX, API, nama class/function/package) tetap dipertahankan.
- Perubahan perilaku harus memperbarui dokumen terkait di `docs/`.
- Perubahan status rekonstruksi harus memperbarui [audit/RECONSTRUCTION_STATUS.md](audit/RECONSTRUCTION_STATUS.md).
- Jangan menyalin laporan internal Application Layer atau Security/Integrity ke repository publik.

## 6. Menambahkan kendaraan, profil ECU, atau MAP

- **Kendaraan/ECM**: mulai dari `examples/vehicle_db/vehicle_template.json`. Ikuti
  [docs/ECM_DATABASE_SCHEMA.md](docs/ECM_DATABASE_SCHEMA.md). Data publik harus memakai ID
  `SAMPLE-`, offset `"0x0"` bila belum terbukti, dan flag verifikasi `false` bila belum
  diuji pada hardware.
- **Profil Live Data**: buat `LiveTableProfile` milik Anda dan tandai `verification`
  sesuai bukti. Jangan menambahkan tabel ke katalog bawaan tanpa persetujuan pemilik dan
  capture perangkat keras.
- **MAP**: mulai dari `examples/maps/synthetic_map.json`. Ikuti
  [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md). Jangan mengunggah `.map`, BIN, ROM, atau
  firmware.
- Jalankan validator dataset:

```bash
py tools/validate_datasets.py --verbose
```

Ketentuan lengkap ada di [docs/DATASET_CONTRIBUTION.md](docs/DATASET_CONTRIBUTION.md).
Permintaan berkas tambahan dijelaskan di [docs/FILE_REQUESTS.md](docs/FILE_REQUESTS.md).

## 7. Melaporkan bug

Sertakan:

1. Versi/commit repository dan APK referensi yang digunakan.
2. Modul dan fungsi yang terlibat.
3. Langkah reproduksi.
4. Frame TX/RX atau log `CommunicationSniffer` yang relevan, jika ada. Hapus data pribadi.
5. Perilaku yang diharapkan dan yang terjadi.
6. Jenis hardware: adapter (VID/PID), motor/ECU jika diketahui, kondisi kontak.
7. Status pengujian: unit test, simulasi, atau hardware nyata.

Jangan menyertakan keystore, password, token, atau data pribadi dalam laporan.

## 8. Melaporkan hasil uji hardware

Karena repository publik belum memiliki uji hardware, laporan uji dari kontributor sangat berharga. Gunakan format:

```text
Tanggal:
APK/repository:
Adapter (chip, VID:PID):
Kabel/transceiver:
Motor/tahun:
ECU/ECM ID (jika terbaca):
Kondisi: kontak ON / mesin hidup
Langkah:
Frame TX:
Frame RX:
Hasil:
Bukti (log/screenshot yang disanitasi):
Batasan:
```

Pisahkan dengan jelas: berhasil, gagal, tidak dapat dijalankan, dan membutuhkan dataset privat.

## 9. Commit dan pull request

- Commit kecil dan fokus. Satu commit untuk satu tujuan.
- Pesan commit menjelaskan **mengapa**, bukan hanya **apa**.
- Sertakan ringkasan test yang dijalankan.
- Jangan menyertakan build output, APK/AAB, AAR, keystore, `local.properties`, atau file IDE.
- Pull request harus menjelaskan: masalah, bukti, perubahan, test, risiko, dan batas yang belum terselesaikan.

## 10. Yang akan ditolak

- Frame atau respons yang dikarang.
- Klaim kompatibilitas atau hasil uji tanpa bukti.
- Source Application Layer atau Security/Integrity.
- Dataset kendaraan, MAP, firmware, atau aset pihak ketiga tanpa izin.
- Kredensial dalam bentuk apa pun.
- Perubahan yang mematikan validasi keamanan atau menyembunyikan kegagalan.
- Perubahan yang membuat test lulus dengan melemahkan assertion.

## 11. Persetujuan

Repository ini sudah dipublikasikan sebagai rilis **v0.3.0**. Kontribusi tetap mengikuti
persetujuan pemilik. Perubahan materi publik, pembuatan ZIP rilis baru, unggahan APK,
atau distribusi di luar repository memerlukan persetujuan pemilik. Repository publik
bukan izin untuk mendistribusikan ulang materi atau APK.
