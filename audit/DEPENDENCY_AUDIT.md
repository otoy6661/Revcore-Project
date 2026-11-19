# AUDIT DEPENDENSI

Dokumen ini mencatat pustaka pihak ketiga yang dipakai repository, dasar verifikasi lisensinya, dan kewajiban distribusi. Ini bukan lisensi untuk source RevCore; ketentuan RevCore ada di [NOTICE.md](../NOTICE.md) dan ringkasan dependensi ada di [LICENSE-DEPENDENCIES.md](../LICENSE-DEPENDENCIES.md).

## 1. Prinsip

- Repository tidak menyalin source pustaka pihak ketiga. Dependensi diambil dari repositori Maven pada saat build.
- Lisensi diverifikasi sedapat mungkin dari metadata POM di cache Gradle lokal. Metadata bukan teks lisensi lengkap; teks resmi tetap menjadi acuan.
- Tidak ada pustaka yang ditambahkan hanya karena nyaman. Setiap dependensi harus dipakai oleh modul publik.
- Dependensi test tidak ikut didistribusikan dalam AAR.
- Tidak ada dependensi yang memuat database kendaraan, firmware, atau materi pihak ketiga lain.

## 2. Dependensi runtime

Dideklarasikan di `core/build.gradle.kts`.

| Dependency | Versi | Lisensi | Penggunaan | Dasar verifikasi |
|---|---|---|---|---|
| `com.github.mik3y:usb-serial-for-android` | 3.8.0 | MIT | Driver USB serial FTDI, CH340, CP210x, Prolific, CDC-ACM | POM menyatakan `MIT License` |
| `androidx.annotation:annotation` | 1.8.1 | Apache License 2.0 | Anotasi AndroidX | Metadata AndroidX; dideklarasikan eksplisit karena versi transitif adalah 1.8.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache License 2.0 | Coroutine dan `StateFlow` | POM menyatakan `The Apache Software License, Version 2.0` |

### 2.1 Catatan `usb-serial-for-android`

- POM `3.8.0` mendeklarasikan satu dependensi runtime: `androidx.annotation:annotation:1.8.0`.
- Repository memilih `1.8.1` secara eksplisit. Ini adalah versi AndroidX yang lebih baru pada jalur yang sama, bukan penggantian fungsional.
- Library ini menyediakan driver chipset USB serial. Kode driver FTDI native RevCore di `core/ftdi` adalah jalur terpisah dan tidak menyalin source library.
- Diambil melalui JitPack. Ketersediaan jangka panjang JitPack bukan jaminan; pemilik perlu menyimpan artefak atau mengunci sumber bila repository dipublikasikan.

### 2.2 Dependensi transitif runtime

Diperiksa dengan `gradle --offline :core:dependencies --configuration debugRuntimeClasspath`.

| Dependency transitif | Versi | Lisensi | Ditarik oleh |
|---|---|---|---|
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.10 | Apache License 2.0 | Kotlin/AGP |
| `org.jetbrains:annotations` | 23.0.0 | Apache License 2.0 | kotlin-stdlib / coroutines |
| `androidx.annotation:annotation-jvm` | 1.8.1 | Apache License 2.0 | `androidx.annotation` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.9.0 | Apache License 2.0 | `kotlinx-coroutines-android` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.9.0 | Apache License 2.0 | `kotlinx-coroutines-core` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.9.0 | Apache License 2.0 | `kotlinx-coroutines-android` (constraint) |

## 3. Dependensi test

Tidak didistribusikan dalam AAR.

| Dependency | Versi | Lisensi | Penggunaan |
|---|---|---|---|
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 | Unit test JVM |
| `org.hamcrest:hamcrest-core` | 1.3 | BSD 3-Clause | Transitif dari JUnit |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache License 2.0 | Test coroutine dan waktu virtual |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm` | 1.9.0 | Apache License 2.0 | Transitif dari coroutines-test |

## 4. Perkakas build

Tidak disertakan dalam repository dan tidak didistribusikan sebagai bagian pustaka.

| Perkakas | Versi | Lisensi | Catatan |
|---|---|---|---|
| Android Gradle Plugin | 9.1.1 | Apache License 2.0 | Plugin build |
| Gradle | 9.6.0 | Apache License 2.0 | Build tool; wrapper tidak disertakan |
| Kotlin | 2.2.10 (plugin build) | Apache License 2.0 | Plugin dideklarasikan `apply false`; AGP menyediakan Kotlin bawaan |
| Android SDK Platform | 36 | Android SDK License | Tidak disertakan |
| JDK | 21 target; 25 runtime saat verifikasi | GPLv2 + Classpath Exception (Temurin) | Tidak disertakan |

## 5. Platform dan API sistem

- Android Framework API 26–36 disediakan oleh perangkat/aplikasi pemanggil.
- `UsbManager`, `UsbDevice`, `UsbDeviceConnection`, `UsbEndpoint`, `UsbInterface`, `AssetManager`, dan `StateFlow` berasal dari platform atau dependensi yang dideklarasikan.
- Tidak ada API tersembunyi atau refleksi ke internal platform yang dipakai untuk komunikasi.

## 6. Materi pihak ketiga dan keputusan distribusi

| Materi | Status | Alasan |
|---|---|---|
| Library protokol native pihak ketiga | Tidak disertakan | Hanya referensi analisis konstanta |
| Profil ECM hasil rekonstruksi RevCore | Dipublikasikan di `data/vehicle_database/` | Hasil pengembangan, bukan salinan |
| Tabel Live Data hasil rekonstruksi RevCore | Dipublikasikan di `data/live_data/` | Hasil pengembangan; layout program lain tidak disertakan |
| Berkas mentah 162 `.map` | Tidak disertakan | Salinan aset pihak lain; inventaris dan analisis dipublikasikan |
| Firmware ECU, BIN/ROM | Tidak disertakan | Salinan aset pihak lain |
| `mapping.txt`, `seeds.txt` | Tidak disertakan | Memuat nama internal Application/Security |
| Teks lisensi pihak ketiga | Tidak disalin | Repository hanya mencatat metadata; teks resmi menyertai artefak masing-masing |

## 7. Kewajiban jika didistribusikan

Jika AAR atau aplikasi yang memuat `:core` didistribusikan:

1. Sertakan notice hak cipta MIT untuk `usb-serial-for-android`.
2. Sertakan notice Apache License 2.0 untuk AndroidX annotation dan kotlinx-coroutines.
3. Jangan menghapus notice dari file pihak ketiga.
4. Sertakan atribusi yang diwajibkan lisensi masing-masing.
5. JUnit dan coroutines-test tidak perlu disertakan karena hanya test, tetapi jangan mengklaim lisensinya sebagai lisensi RevCore.

Teks notice lengkap ada di [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md). Dokumen ini bukan nasihat hukum. Pemilik perlu meninjau kewajiban distribusi sebelum publikasi.

## 8. Batas verifikasi

- Lisensi diverifikasi dari metadata POM dan dependency report Gradle, bukan audit hukum penuh.
- Dependensi transitif yang tercatat adalah hasil resolusi Gradle untuk konfigurasi runtime dan unit test; konfigurasi lain dapat menarik artefak tambahan.
- Tidak ada klaim bahwa kombinasi lisensi ini cocok untuk model distribusi komersial tertentu.
- Perubahan versi dependensi harus memperbarui dokumen ini dan `LICENSE-DEPENDENCIES.md`.
