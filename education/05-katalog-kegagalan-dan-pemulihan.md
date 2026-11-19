# 05 — Katalog Kegagalan dan Pemulihan

Katalog ini mencatat kegagalan yang benar-benar muncul saat mengerjakan repository ini
atau yang terbukti dari analisis source. Setiap kasus memakai 12 field yang sama. Solusi
hanya ditulis bila ada bukti; tebakan diberi status yang sesuai.

## Status yang dipakai

| Status | Arti |
|---|---|
| Open | Belum ada penyebab pasti atau solusi yang terbukti |
| Investigating | Ada dugaan kuat, masih dalam pengujian |
| Partially resolved | Sebagian jalur sudah tertangani |
| Resolved | Tertangani dan diverifikasi pada tingkat yang disebut di field Bukti |
| Hardware unverified | Perbaikan software ada, perilaku perangkat belum diuji |
| Not reproducible | Tidak dapat diulang pada environment yang tercatat |

Field **Bukti** selalu menyebut tingkat verifikasi. "Resolved" pada katalog ini berarti
logika atau dokumentasi sudah diperbaiki; ia tidak otomatis berarti "berfungsi di ECU".

---

## A. Android dan build

### A1. `SDK location not found`

**Gejala.** Build berhenti dengan pesan SDK tidak ditemukan.

**Kondisi.** `ANDROID_HOME` kosong dan tidak ada `local.properties`.

**Penyebab.** Gradle tidak mengetahui lokasi Android SDK.

**Diagnosis.** Periksa `echo $ANDROID_HOME` dan pastikan folder SDK memiliki
`platforms/android-36`.

**Bukti.** Reproduksi dan perbaikan pada environment verifikasi (2026-11-17/19);
`clean test` berhasil setelah `ANDROID_HOME` diisi.

**File/fungsi.** Konfigurasi environment; tidak ada file repository yang diubah.

**Solusi.** Set `ANDROID_HOME` ke folder SDK. Jangan membuat `local.properties` berisi
path pribadi di dalam repository.

**Cara uji ulang.** `gradle --offline clean test`.

**Regression test.** Instruksi build di README dan `docs/BUILD_GUIDE.md`; environment CI
harus menetapkan variabel yang sama.

**Dampak.** Seluruh build terblokir.

**Status.** Resolved.

### A2. Plugin `com.android.library` tidak ditemukan saat offline

**Gejala.** `Plugin [id: 'com.android.library', version: '9.1.1'] was not found`.

**Kondisi.** Cache Gradle belum memiliki penanda plugin AGP.

**Penyebab.** Resolusi plugin offline membutuhkan cache yang lengkap.

**Diagnosis.** Jalankan build online sekali, lalu ulangi offline.

**Bukti.** Dialami dan diatasi pada environment ini; `resolutionStrategy` di
`settings.gradle.kts` memetakan plugin ke artefak AGP.

**File/fungsi.** `settings.gradle.kts`.

**Solusi.** Build online sekali; jangan menghapus `resolutionStrategy`.

**Cara uji ulang.** `gradle --offline test`.

**Regression test.** Build bersih pada mesin dengan cache kosong memerlukan jaringan.

**Dampak.** Build offline gagal pada mesin baru.

**Status.** Partially resolved (bergantung cache).

### A3. JDK/toolchain tidak cocok

**Gejala.** `Unsupported class file major version` atau `No matching toolchains found
for language version 21`.

**Kondisi.** JDK terlalu lama atau JDK 21 tidak tersedia.

**Penyebab.** Gradle berjalan pada JDK yang tidak mendukung bytecode/toolchain target.

**Diagnosis.** `java -version`, `gradle --version`, lalu periksa toolchain di
`core/build.gradle.kts`.

**Bukti.** Build berhasil dengan JDK 25.0.3 dan toolchain Java 21 pada 2026-11-17.

**File/fungsi.** Konfigurasi `compileOptions`/toolchain di modul `:core`.

**Solusi.** Gunakan JDK 21 atau lebih baru; arahkan `JAVA_HOME` bila perlu.

**Cara uji ulang.** `gradle --offline clean test`.

**Regression test.** Catat versi JDK pada laporan build.

**Dampak.** Kompilasi gagal.

**Status.** Resolved (pada environment yang dicatat).

### A4. Root repository menghasilkan AAR, bukan APK

**Gejala.** `assembleDebug` berhasil tetapi tidak ada APK yang dapat dipasang.

**Kondisi.** `settings.gradle.kts` hanya memuat `:core`, sebuah library module.

**Penyebab.** Library tidak memiliki Activity, manifest aplikasi, atau titik masuk.

**Diagnosis.** Lihat daftar modul dan output `core/build/outputs/aar/`.

**Bukti.** `samples/01-hello-android` berhasil menghasilkan `app-debug.apk` 844.858 byte
dengan SHA-256 `9c3763af...` (2026-11-19).

**File/fungsi.** `settings.gradle.kts`, `samples/01-hello-android/`.

**Solusi.** Gunakan modul aplikasi terpisah (contoh 01) atau buat aplikasi sendiri yang
memakai AAR `:core`.

**Cara uji ulang.** `cd samples/01-hello-android && gradle assembleDebug` (dengan
`ANDROID_HOME` diatur).

**Regression test.** Jangan mengubah root menjadi aplikasi hanya agar terlihat dapat
menghasilkan APK.

**Dampak.** Kebingungan pemula; bukan kerusakan.

**Status.** Resolved (dokumentasi dan contoh terverifikasi).

### A5. Repository tidak memiliki Gradle Wrapper

**Gejala.** `./gradlew: No such file or directory`.

**Kondisi.** Wrapper tidak disertakan di root maupun di `samples/01-hello-android`.

**Penyebab.** Keputusan repository: tidak ada berkas biner wrapper (`*.jar`) di source;
build memakai instalasi Gradle 9.6.0.

**Diagnosis.** Periksa keberadaan `gradlew` dan `gradle --version`.

**Bukti.** `docs/BUILD_GUIDE.md` §2 menyatakan prasyarat Gradle 9.6.0 dan menyebut
repository sengaja tidak membawa wrapper.

**File/fungsi.** Root repository, `samples/01-hello-android/`.

**Solusi.** Pasang Gradle 9.6.0 dan jalankan `gradle`, atau buka folder contoh di Android
Studio yang akan membuat wrapper sendiri.

**Cara uji ulang.** `gradle --version`, lalu
`cd samples/01-hello-android && gradle assembleDebug`.

**Regression test.** Dokumentasikan versi Gradle pada setiap instruksi build.

**Dampak.** Pemula harus memasang Gradle secara manual.

**Status.** Resolved (instruksi diseragamkan; wrapper tidak disertakan).

---

## B. USB dan adapter

### B1. Izin USB ditolak atau tidak diminta

**Gejala.** Koneksi gagal, adapter tidak dapat dibuka, atau muncul `SecurityException`.

**Kondisi.** Aplikasi memanggil engine tanpa meminta izin `UsbManager`.

**Penyebab.** Izin USB adalah tanggung jawab aplikasi pemanggil, bukan `:core`.

**Diagnosis.** Periksa `UsbManager.hasPermission(device)` dan alur `PendingIntent`.

**Bukti.** Batas integrasi di `docs/SECURITY_AND_INTEGRITY.md` dan README §6.1; belum
diuji pada perangkat dalam repository ini.

**File/fungsi.** Aplikasi pemanggil; `UsbManager`, `HondaProtocolEngine.connect`.

**Solusi.** Minta izin saat perangkat terdeteksi; tangani penolakan, pencabutan, dan
perangkat dilepas.

**Cara uji ulang.** Pasang aplikasi di perangkat, sambungkan adapter, setujui dialog izin,
lalu buka sesi.

**Regression test.** Uji alur tolak izin dan cabut kabel.

**Dampak.** Tidak ada komunikasi.

**Status.** Hardware unverified.

### B2. Adapter tidak dikenali (VID/PID)

**Gejala.** Perangkat USB terdeteksi sistem, tetapi tidak ada adapter yang dapat dibuka.

**Kondisi.** Chip adapter tidak ada dalam daftar prober.

**Penyebab.** Dukungan VID/PID terbatas atau descriptor tidak standar.

**Diagnosis.** Catat VID:PID dan nama produk; cek jalur `prober.probeDevice()`.

**Bukti.** Analisis source `UsbSerialLibraryTransport.kt`; tanpa uji perangkat.

**File/fungsi.** `UsbSerialLibraryTransport`, `AdapterConnectionPolicy`.

**Solusi.** Tambahkan entri prober hanya dengan descriptor dan bukti yang jelas; jangan
menebak VID/PID.

**Cara uji ulang.** Log enumerasi USB dan hasil probe.

**Regression test.** Uji adapter yang sudah didukung agar tidak rusak.

**Dampak.** Adapter tertentu tidak dapat dipakai.

**Status.** Partially resolved.

### B3. Echo adapter dianggap response

**Gejala.** RX dimulai dengan byte yang sama persis dengan TX; parser melihat frame ganda
atau checksum salah.

**Kondisi.** Adapter meng-loopback byte TX.

**Penyebab.** Perilaku adapter half-duplex.

**Diagnosis.** Bandingkan RX dengan TX dan lihat log sniffer.

**Bukti.** `stripLeadingEcho` dan unit test codec; belum diuji pada adapter fisik.

**File/fungsi.** `HondaFrameCodec.stripLeadingEcho`, `KlineEchoFilter`.

**Solusi.** Biarkan engine membuang echo; jangan menonaktifkan filter untuk "mempermudah".

**Cara uji ulang.** Kirim frame sintetis dan periksa log TX/RX.

**Regression test.** Test echo identik, echo dengan padding `0x00`, dan `0xFF` di awal.

**Dampak.** Response valid dapat ditolak.

**Status.** Resolved (level software).

### B4. DTR/RTS berbeda antar adapter

**Gejala.** Sebagian adapter tidak memberi respons walau kabel benar.

**Kondisi.** Perilaku kontrol line berbeda.

**Penyebab.** Wiring dan default DTR/RTS tidak seragam.

**Diagnosis.** Uji adapter yang sama pada dua perangkat; catat perilaku kontrol line.

**Bukti.** `docs/USB_ADAPTER_GUIDE.md` menyebut default, tetapi belum ada pengukuran
perangkat di repository.

**File/fungsi.** `UsbTransport` (kontrol adapter), `FtdiSerialDriver`.

**Solusi.** Jadikan perilaku kontrol line dapat dikonfigurasi; verifikasi dengan adapter
nyata sebelum mengubah default.

**Cara uji ulang.** Sambungkan adapter, ukur jalur, kirim wake pulse.

**Regression test.** Uji minimal dua chipset.

**Dampak.** Koneksi gagal pada sebagian adapter.

**Status.** Hardware unverified.

### B5. Level tegangan adapter tidak sesuai K-Line

**Gejala.** Tidak ada komunikasi, atau berisiko merusak ECU/adapter.

**Kondisi.** Jalur UART 5V/3.3V disambungkan langsung tanpa antarmuka.

**Penyebab.** K-Line membutuhkan rangkaian antarmuka dan level yang benar.

**Diagnosis.** Periksa skematik adapter; jangan menebak dari warna kabel.

**Bukti.** Peringatan di `docs/USB_ADAPTER_GUIDE.md`; tidak ada pengukuran.

**File/fungsi.** Perangkat keras, bukan source.

**Solusi.** Gunakan rangkaian antarmuka yang sesuai. Ini bukan masalah software.

**Cara uji ulang.** Ukur level dengan alat yang tepat sebelum menyambung ECU.

**Regression test.** Tidak ada; keselamatan hardware.

**Dampak.** Kerusakan perangkat.

**Status.** Hardware unverified.

---

## C. Komunikasi ECU

### C1. Tidak ada respons setelah wake-up

**Gejala.** `NO_RESPONSE`; tidak ada frame valid.

**Kondisi.** Kontak mati, wiring salah, baud salah, atau adapter tidak aktif.

**Penyebab.** Belum dapat dipastikan tanpa pengukuran.

**Diagnosis.** Cek daya, ground, jalur K-Line, wake pulse, baud, dan log TX/RX.

**Bukti.** State `NO_RESPONSE` didefinisikan dan diuji pada level logika.

**File/fungsi.** `KlineWireEngine`, `HondaProtocolEngine.performKlineWakeup`.

**Solusi.** Ikuti checklist adapter dan wiring; jangan mengubah timing tanpa bukti.

**Cara uji ulang.** Ulangi wake dan query identitas dengan log aktif.

**Regression test.** Test timeout dan retry dengan transport simulasi.

**Dampak.** Diagnostik tidak dapat dimulai.

**Status.** Open (butuh hardware).

### C2. Respons negatif `0x7F` dianggap "tidak ada DTC"

**Gejala.** Aplikasi menampilkan daftar kosong padahal ECU menolak query.

**Kondisi.** Service `0x7F` pada response.

**Penyebab.** Menyamakan respons negatif dengan daftar kosong.

**Diagnosis.** Periksa byte service sebelum menampilkan hasil.

**Bukti.** `docs/DTC_ENGINE.md` §9; jalur baca mencoba query berikutnya dan berakhir
`NO_RESPONSE`.

**File/fungsi.** `HondaProtocolEngine.readDtcsInternal`, `HondaDtcDatabase`.

**Solusi.** Perlakukan `0x7F` sebagai kegagalan query, bukan hasil kosong.

**Cara uji ulang.** Kirim frame negatif sintetis.

**Regression test.** Test response negatif untuk setiap kandidat query.

**Dampak.** Pengguna salah menyimpulkan tidak ada gangguan.

**Status.** Resolved (level software).

### C3. Checksum tidak cocok

**Gejala.** Frame tampak lengkap tetapi ditolak.

**Kondisi.** Byte berubah saat transmisi atau frame salah dibaca.

**Penyebab.** Noise, baud salah, atau offset frame keliru.

**Diagnosis.** Hitung ulang checksum dan bandingkan byte terakhir; lihat log hex.

**Bukti.** `docs/CHECKSUM.md`; unit test checksum valid/tidak valid.

**File/fungsi.** `HondaChecksum`, `HondaFrameCodec.isWellFormed`.

**Solusi.** Buang frame, sinkronkan ulang buffer, dan jangan "memperbaiki" checksum.

**Cara uji ulang.** Kirim frame dengan satu byte diubah.

**Regression test.** Test checksum salah, checksum `0x00`, dan frame minimum.

**Dampak.** Data rusak diterima bila validasi dilewati.

**Status.** Resolved (unit test).

### C4. Frame terpotong

**Gejala.** Parser menunggu atau menolak frame.

**Kondisi.** Declared length lebih besar dari byte yang tersedia.

**Penyebab.** Timeout pembacaan atau frame terbagi.

**Diagnosis.** Bandingkan declared length dengan jumlah byte; periksa timeout.

**Bukti.** `docs/FRAME_PARSER.md` §Validasi; unit test bentuk frame.

**File/fungsi.** `KlineWireEngine`, `HondaFrameCodec.isWellFormed`.

**Solusi.** Tunggu hingga batas waktu, lalu gagalkan transaksi; jangan mem-parse sebagian.

**Cara uji ulang.** Kirim frame terpotong.

**Regression test.** Test frame kurang satu byte dan length berlebih.

**Dampak.** Parser dapat macet bila batas waktu tidak diterapkan.

**Status.** Resolved (level software).

### C5. Meminjam frame family lain

**Gejala.** Family tanpa katalog mengirim request yang tidak berlaku.

**Kondisi.** Katalog family kosong.

**Penyebab.** Keinginan agar fitur "terlihat berfungsi".

**Diagnosis.** Periksa `hasIdentificationCatalog`/`hasLiveDataCatalog` dan alasan family.

**Bukti.** `HondaEcuProfiles` memberi katalog kosong untuk family yang belum terbukti.

**File/fungsi.** `HondaEcuProfiles`, `EcuSessionManager`.

**Solusi.** Kembalikan `UNSUPPORTED_FAMILY`/`NoResponse`; jangan meminjam frame.

**Cara uji ulang.** Pilih family kosong dan pastikan tidak ada frame dikirim.

**Regression test.** Test setiap family kosong.

**Dampak.** Klaim kompatibilitas palsu.

**Status.** Resolved (by design).

---

## D. Live Data

### D1. Tidak ada katalog Live Data

**Gejala.** `queryLiveData()` mengembalikan `null` dan tidak ada frame dikirim.

**Kondisi.** `HondaLiveDataProfiles` kosong.

**Penyebab.** Konstanta proprietary ditarik karena status hak penggunaan belum jelas.

**Diagnosis.** Periksa `hasLiveDataCatalog` dan state `UNSUPPORTED_PROFILE`.

**Bukti.** `docs/LIVE_DATA_ENGINE.md`; test profil kosong.

**File/fungsi.** `HondaEcuProfiles`, `HondaProtocolEngine.queryLiveData`.

**Solusi.** Pemanggil menyediakan `LiveTableProfile` sendiri dengan label bukti yang
jujur.

**Cara uji ulang.** Panggil Live Data tanpa profil dan pastikan tidak ada TX.

**Regression test.** Test katalog kosong tidak mengirim frame.

**Dampak.** Fitur Live Data tidak tersedia secara bawaan.

**Status.** Resolved (by design).

### D2. Nilai lama ditampilkan sebagai nilai sekarang

**Gejala.** Angka tidak berubah walau mesin berubah.

**Kondisi.** Respons berhenti setelah data terakhir.

**Penyebab.** UI tidak menghormati state `STALE`.

**Diagnosis.** Periksa timestamp dan state telemetry.

**Bukti.** State `STALE` didefinisikan di `LiveTelemetry`.

**File/fungsi.** `LiveTelemetry`, pemanggil.

**Solusi.** Tandai data lama sebagai `STALE`; jangan tampilkan sebagai nilai aktif.

**Cara uji ulang.** Hentikan respons simulator dan periksa state.

**Regression test.** Test timeout → `STALE`.

**Dampak.** Keputusan pengguna berdasarkan data lama.

**Status.** Resolved (level software; UI tidak termasuk publikasi).

### D3. AFR dikarang dari O2 narrowband

**Gejala.** AFR menampilkan angka tertentu tanpa data lebar pita.

**Kondisi.** ECU hanya mempublikasikan tegangan O2 narrowband/STF.

**Penyebab.** Menghitung AFR dari sensor yang tidak mengukurnya.

**Diagnosis.** Periksa sumber field dan status dukungan.

**Bukti.** `LiveParameterStates` memetakan AFR ke `UNSUPPORTED`; README §13.

**File/fungsi.** `LiveDataProfile.LiveParameterStates`.

**Solusi.** Laporkan `UNSUPPORTED`, bukan `14.7`.

**Cara uji ulang.** Minta AFR pada profil tanpa field wideband.

**Regression test.** Test AFR selalu `UNSUPPORTED`.

**Dampak.** Kesalahan tuning yang berbahaya.

**Status.** Resolved.

### D4. Payload tidak valid diubah menjadi nol

**Gejala.** Semua sensor tampak `0` saat frame rusak.

**Kondisi.** Payload pendek atau field di luar rentang.

**Penyebab.** Parser mengganti nilai yang tidak ada dengan nol.

**Diagnosis.** Periksa `LiveDataFrameParser.parse` dan state hasil.

**Bukti.** Parser mengembalikan kegagalan/state tidak valid; unit test payload pendek.

**File/fungsi.** `LiveDataFrameParser`, `LiveTelemetry`.

**Solusi.** Jangan mengganti nilai hilang dengan nol; gunakan `INVALID_PAYLOAD` atau
`UNSUPPORTED`.

**Cara uji ulang.** Kirim payload pendek.

**Regression test.** Test payload kurang byte dan field `0xFF` alternatif.

**Dampak.** Data palsu terlihat normal.

**Status.** Resolved.

---

## E. DTC

### E1. Daftar DTC kosong setelah kabel lepas

**Gejala.** Layar menampilkan "tidak ada DTC" padahal komunikasi gagal.

**Kondisi.** Semua query gagal atau timeout.

**Penyebab.** Mengosongkan daftar pada `NO_RESPONSE`.

**Diagnosis.** Periksa state baca dan apakah daftar lama dipertahankan.

**Bukti.** `docs/DTC_ENGINE.md` §5; `NO_RESPONSE` tidak mengubah `activeDtcs`.

**File/fungsi.** `HondaProtocolEngine.readDtcs`, `DtcReadResult`.

**Solusi.** Pertahankan daftar lama dan tandai kegagalan.

**Cara uji ulang.** Putuskan kabel setelah pembacaan berhasil, lalu baca lagi.

**Regression test.** Test `NO_RESPONSE` tidak mengosongkan daftar.

**Dampak.** Pengguna melewatkan gangguan nyata.

**Status.** Resolved.

### E2. Clear diakui tetapi kode muncul lagi

**Gejala.** Clear tampak berhasil, lalu DTC yang sama kembali.

**Kondisi.** Gangguan aktif masih ada.

**Penyebab.** ECU menghapus riwayat, tetapi kondisi gangguan memicu kode baru.

**Diagnosis.** Baca ulang setelah ACK; bandingkan kode.

**Bukti.** Status `RE_DETECTED_ACTIVE_FAULT` dan alur verifikasi 250 ms.

**File/fungsi.** `HondaProtocolEngine.clearDtcs`, `ClearDtcResult`.

**Solusi.** Tampilkan `RE_DETECTED_ACTIVE_FAULT`; jangan sebut berhasil.

**Cara uji ulang.** Simulasi clear lalu response berisi kode yang sama.

**Regression test.** Test clear → baca ulang berisi kode.

**Dampak.** Perbaikan palsu.

**Status.** Resolved.

### E3. Clear diakui tetapi verifikasi gagal

**Gejala.** `CLEARED_UNVERIFIED`.

**Kondisi.** ACK diterima, pembacaan ulang `NO_RESPONSE` atau tidak dapat diparse.

**Penyebab.** Koneksi terputus setelah clear.

**Diagnosis.** Periksa log sesi setelah ACK.

**Bukti.** Alur verifikasi di `docs/DTC_ENGINE.md` §7.

**File/fungsi.** `HondaProtocolEngine.clearDtcs`.

**Solusi.** Laporkan belum terverifikasi; jangan mengklaim sukses.

**Cara uji ulang.** Simulasi ACK lalu timeout.

**Regression test.** Test ACK + timeout → `CLEARED_UNVERIFIED`.

**Dampak.** Status perbaikan menyesatkan.

**Status.** Resolved.

### E4. MIL tetap menyala setelah clear terverifikasi

**Gejala.** Tidak ada DTC terbaca, tetapi lampu MIL masih menyala.

**Kondisi.** Clear berhasil pada level kode.

**Penyebab.** MIL dapat memerlukan siklus kontak/uji jalan, atau gangguan pada modul lain.

**Diagnosis.** Baca ulang setelah siklus kontak; periksa prosedur servis.

**Bukti.** `docs/DTC_ENGINE.md` §7 menyatakan MIL tidak dijamin padam.

**File/fungsi.** Bukan tanggung jawab `:core`.

**Solusi.** Jelaskan batas ini kepada pengguna; jangan menjanjikan MIL padam.

**Cara uji ulang.** Siklus kontak dan uji jalan sesuai prosedur kendaraan.

**Regression test.** Tidak ada; perlu kendaraan.

**Dampak.** Ekspektasi pengguna salah.

**Status.** Hardware unverified.

### E5. Count-prefix DTC terlalu longgar

**Gejala.** Payload dengan count dan byte tambahan tetap diterima; subcode di luar
0–15 diubah menjadi 0.

**Kondisi.** Payload count-prefixed yang tidak persis sesuai panjang.

**Penyebab.** Parser memakai batas minimum (`>=`) dan melakukan coercion subcode.

**Diagnosis.** Bandingkan `parseDtcPacket` dengan aturan di `docs/DTC_ENGINE.md`.

**Bukti.** Pembacaan source `HondaDtcDatabase.parseDtcPacket`; perilaku ini juga
didokumentasikan di dokumen DTC.

**File/fungsi.** `HondaDtcDatabase.parseDtcPacket`.

**Solusi.** Perketat aturan hanya setelah pemilik menyetujui perubahan perilaku produksi,
disertai test frame valid dan tidak valid.

**Cara uji ulang.** Kirim payload count-prefixed dengan byte tambahan dan subcode 16.

**Regression test.** Tambahkan test untuk menerima/menolak bentuk ambigu.

**Dampak.** Payload rusak dapat diterima sebagai daftar kode.

**Status.** Investigating (perubahan produksi belum diizinkan).

### E6. Payload tidak dapat diparse dilaporkan `OK_EMPTY`

**Gejala.** Layar menampilkan "tidak ada DTC" untuk payload yang tidak dikenali.

**Kondisi.** Frame valid, tetapi parser tidak menghasilkan kode.

**Penyebab.** Jalur baca menetapkan `OK_EMPTY` dan status sesi `DTC_READ_CONFIRMED`
ketika payload tidak dapat di-parse; state `UNRECOGNIZED` sudah didefinisikan tetapi
belum dipakai jalur tersebut.

**Diagnosis.** Telusuri `readDtcsInternal` setelah frame valid diterima.

**Bukti.** Pembacaan source dan perbandingan dengan `docs/DTC_ENGINE.md` §5.

**File/fungsi.** `HondaProtocolEngine.readDtcsInternal`, `DtcReadState.UNRECOGNIZED`.

**Solusi.** Gunakan `UNRECOGNIZED` untuk payload yang tidak dapat dipastikan. Perubahan
ini menyentuh perilaku produksi, sehingga menunggu instruksi pemilik.

**Cara uji ulang.** Kirim frame valid dengan payload yang tidak sesuai format apa pun.

**Regression test.** Test frame valid + payload tak dikenal → `UNRECOGNIZED`.

**Dampak.** Kegagalan parse dapat tampak seperti tidak ada gangguan.

**Status.** Open (menunggu keputusan pemilik).

### E7. Kode DTC tak dikenal diubah menjadi P-code

**Gejala.** Kode yang tidak ada di database tampil seolah-olah P-code resmi.

**Kondisi.** Code/sub di luar 15 entri.

**Penyebab.** Keinginan memberi label yang "rapi".

**Diagnosis.** Periksa `isKnown` dan `standardCode`.

**Bukti.** `docs/DTC_ENGINE.md` §4: kode luar tabel memakai `DTC XXYY` dan
`isKnown = false`.

**File/fungsi.** `HondaDtcDatabase`.

**Solusi.** Tampilkan kode mentah dan label belum terverifikasi.

**Cara uji ulang.** Masukkan code/sub yang tidak dikenal.

**Regression test.** Test kode tak dikenal tidak menghasilkan P-code.

**Dampak.** Diagnosis salah.

**Status.** Resolved.

---

## Cara menambah kasus baru

1. Reproduksi dan catat environment.
2. Isi 12 field; jangan mengosongkan Bukti.
3. Pilih status yang paling jujur, bukan yang paling optimistis.
4. Tambahkan regression test bila perbaikan menyentuh kode.
5. Jika solusi memerlukan perubahan perilaku produksi, hentikan dan minta keputusan
   pemilik.
