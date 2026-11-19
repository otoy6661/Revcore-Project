# LICENSE-DEPENDENCIES

Dokumen ini mencatat pustaka pihak ketiga yang dipakai repository developer RevCore. Dokumen ini **bukan** lisensi untuk source RevCore. Ketentuan penggunaan RevCore ada di [NOTICE.md](NOTICE.md).

## 1. Dependensi runtime

| Dependency | Versi | Lisensi | Penggunaan | Catatan |
|---|---|---|---|---|
| `com.github.mik3y:usb-serial-for-android` | 3.8.0 | MIT | Driver USB serial untuk FTDI, CH340, CP210x, Prolific, CDC-ACM | Diakses melalui JitPack. Lisensi terverifikasi dari metadata POM. |
| `androidx.annotation:annotation` | 1.8.1 | Apache License 2.0 | Anotasi AndroidX | Dideklarasikan eksplisit karena versi transitif `usb-serial-for-android` adalah 1.8.0. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache License 2.0 | Coroutine dan `StateFlow` | Terverifikasi dari metadata POM. |

Transitif runtime: `kotlin-stdlib` 2.2.10, `org.jetbrains:annotations` 23.0.0,
`annotation-jvm` 1.8.1, `kotlinx-coroutines-core`/`-core-jvm` 1.9.0, dan
`kotlinx-coroutines-bom` 1.9.0, semuanya Apache License 2.0.

## 2. Dependensi test

| Dependency | Versi | Lisensi | Penggunaan |
|---|---|---|---|
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 | Unit test JVM |
| `org.hamcrest:hamcrest-core` | 1.3 | BSD 3-Clause | Transitif JUnit |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache License 2.0 | Test coroutine dan waktu virtual |

Teks notice lengkap ada di [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## 3. Perkakas build (tidak didistribusikan)

| Perkakas | Versi | Lisensi | Catatan |
|---|---|---|---|
| Android Gradle Plugin | 9.1.1 | Apache License 2.0 | Plugin build, tidak disertakan dalam AAR |
| Gradle | 9.6.0 | Apache License 2.0 | Build tool, tidak disertakan |
| Kotlin | 2.2.10 (build environment) | Apache License 2.0 | Compiler/plugin, tidak disertakan |
| Android SDK Platform | 36 | Android SDK License | Tidak disertakan |

## 4. Platform dan runtime

- Android Framework API 26–36 disediakan oleh perangkat/aplikasi pemanggil, bukan didistribusikan repository.
- `UsbManager`, `UsbDevice`, `UsbDeviceConnection`, `UsbEndpoint`, dan `UsbInterface` adalah API platform Android.

## 5. Materi referensi yang tidak disertakan

Analisis menggunakan referensi internal proyek dan pembanding eksternal. **Tidak ada** berkas MAP, firmware, BIN/ROM, atau kunci kontainer pihak ketiga yang disertakan. Yang disertakan adalah hasil pengembangan RevCore: database kendaraan, profil ECM, inventaris dan analisis MAP, tabel Live Data, rumus, dan dokumentasi. Bagian hasil rekonstruksi ditandai `RECONSTRUCTED`/`REFERENCE` dan tidak diklaim sebagai hasil pengujian kendaraan.

## 6. Kewajiban pengguna

Jika menggunakan atau mendistribusikan ulang dependency di atas, patuhi lisensi masing-masing, termasuk mempertahankan notice hak cipta. Dokumen ini tidak menggantikan teks lisensi resmi.
