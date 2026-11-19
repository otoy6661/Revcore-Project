# NOTICE

## 1. Kepemilikan dan izin

Repository ini berisi materi teknis RevCore yang disiapkan untuk dipelajari oleh developer. Hak atas kode, dokumentasi, nama, dan identitas RevCore tetap berada pada pemiliknya.

Izin yang diberikan oleh pemilik ditujukan untuk:

- melihat dan mempelajari source;
- menganalisis arsitektur dan implementasi;
- membantu menemukan dan memperbaiki bug;
- mengembangkan fitur sesuai persetujuan pemilik.

Ketentuan yang berlaku:

1. Dilarang mengklaim kepemilikan atas RevCore, source, dokumentasi, atau turunannya.
2. Dilarang menjual ulang secara komersial tanpa izin tertulis dari pemilik.
3. Dilarang memodifikasi lalu mendistribusikan modifikasi tanpa izin pemilik.
4. Penggunaan dan kontribusi harus mengikuti persetujuan pemilik.
5. Repository ini **bukan** proyek Open Source berlisensi OSI. Jangan memberi label "OSI approved", "open source", atau lisensi OSI pada repository ini.

File `LICENSE-DEPENDENCIES.md` hanya mencatat lisensi pustaka pihak ketiga yang digunakan. File itu tidak mengubah ketentuan pemilik di atas.

## 2. Materi yang tidak disertakan

Repository ini sengaja tidak menyertakan:

- berkas MAP mentah, firmware, bootloader, dan binary ECU yang merupakan salinan aset pihak lain;
- kunci, IV, dan mekanisme kripto kontainer MAP;
- source Application Layer dan Security/Integrity, termasuk mekanisme internalnya;
- keystore, private key, sertifikat signing, token, atau kredensial;
- data pribadi pengguna atau data kendaraan yang dapat diidentifikasi.

`core/src/main/assets/ecm_database.json` hanya berisi tiga record sintetis untuk demonstrasi build. Database kendaraan hasil kompilasi RevCore tersedia terpisah di `data/vehicle_database/` dengan status `terdaftar`; tidak ada klaim pengujian atau batas RPM yang sudah terverifikasi.

## 3. Status bukti

Source dan dokumentasi memakai label status:

- `ORIGINAL` — source proyek pemilik, tidak diubah.
- `DECOMPILED` — hasil dekompilasi, bukan source asli.
- `RECONSTRUCTED` — ditulis ulang berdasarkan bukti bytecode, resource, perilaku, atau referensi.
- `REFERENCE` — dokumentasi/contoh penjelas.
- `UNVERIFIED` — belum dapat dipastikan.

Keberadaan sebuah kelas di APK dan pemetaan R8 membuktikan kelas itu ada pada build tersebut. Hal itu **tidak** membuktikan bahwa perilaku komunikasi telah diuji pada ECU fisik.

## 4. Merek dan referensi

Honda, PGM-FI, Keihin, Shindengen, Denso, FTDI, CH340, CP210x, Prolific, Android, Gradle, Kotlin, dan nama produk lain adalah merek atau nama dagang pemiliknya masing-masing. Penyebutan nama tersebut bersifat teknis dan tidak menyatakan afiliasi, dukungan, atau lisensi.

Sebagian konstanta hasil analisis pernah dibandingkan dengan referensi pihak ketiga. Yang dipublikasikan adalah hasil analisis, rekonstruksi, dan implementasi RevCore sendiri: database kendaraan, profil ECM, inventaris MAP, tabel Live Data, rumus, dan dokumentasi. Layout tabel dari program lain, berkas mentah pihak lain, dan kunci kontainer tidak disertakan. Bagian yang berasal dari rekonstruksi ditandai `RECONSTRUCTED` atau `REFERENCE` dan tidak diklaim sebagai hasil pengujian kendaraan.

## 5. Keamanan

Jangan memasukkan keystore, password, token, API key, atau kredensial ke repository, issue, test, contoh, log, atau commit. Jangan mengunggah APK atau materi analisis ke layanan eksternal.

Jika menemukan materi yang berpotensi melanggar hak pihak ketiga atau membocorkan rahasia, laporkan kepada pemilik dan jangan mempublikasikannya.

## 6. Persetujuan

Repository ini telah dipublikasikan sebagai rilis **v0.3.0** oleh pemilik. Publikasi
materi baru, perubahan lisensi, pembuatan ZIP rilis baru, unggahan APK, dan distribusi
di luar repository tetap memerlukan persetujuan pemilik. Dokumen ini tidak memberi izin
untuk mendistribusikan ulang materi atau APK.
