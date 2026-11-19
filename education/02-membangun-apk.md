# 02 — Membangun APK

Dokumen ini menjawab pertanyaan yang paling sering menyesatkan pemula: **apakah
repository ini bisa menghasilkan APK?** Jawabannya harus dipisahkan menjadi dua hal.

| Yang dibangun | Hasil | Dapat dipasang? |
|---|---|---|
| Root repository (`:core`) | AAR (`core-debug.aar`) | Tidak |
| `samples/01-hello-android` | APK (`app-debug.apk`) | Ya |

Repository ini adalah **pustaka Android**, bukan aplikasi. `settings.gradle.kts` hanya
memuat `include(":core")`. Perintah `assembleDebug` di root menghasilkan AAR, dan AAR
tidak memiliki layar, ikon, atau titik masuk. Agar menjadi APK, pustaka harus dipakai
oleh sebuah modul aplikasi. `samples/01-hello-android` adalah modul aplikasi terpisah
yang membuktikan alur itu benar-benar dapat dijalankan.

## 1. Prasyarat

1. **JDK 21 atau lebih baru.** Di environment verifikasi dipakai Temurin 25.0.3.
2. **Android SDK Platform 36.** Lewat Android Studio: *Settings → Languages & Frameworks
   → Android SDK → SDK Platforms*.
3. **Android Studio** (versi yang mendukung AGP 9.1.1) atau Gradle 9.6.0.
4. **Perangkat atau emulator** dengan Android 8.0 (API 26) atau lebih baru.
5. **ADB** (ikut Android SDK Platform-Tools) untuk memasang APK.

Cek:

```bash
java -version
adb version
```

## 2. Jalur A — membangun APK contoh (sudah diverifikasi)

`samples/01-hello-android` adalah aplikasi Java minimal: satu Activity, satu tombol,
satu penghitung. Aplikasi ini **tidak** berkomunikasi dengan ECU dan bukan aplikasi
RevCore.

```bash
cd samples/01-hello-android
gradle assembleDebug             # memakai Gradle 9.6.0 yang terpasang
```

Repository ini **tidak** menyertakan `gradlew`/`gradle/wrapper` (lihat
[docs/BUILD_GUIDE.md](../docs/BUILD_GUIDE.md)). Bila memakai Android Studio, buka folder
contoh lalu pilih *Use Gradle from: 'gradle-wrapper.properties' file* agar Studio membuat
wrapper sendiri. Build pertama memerlukan internet untuk dependency Android bila belum
ada di cache; setelah itu `gradle --offline assembleDebug` dapat dipakai.

Hasil yang terverifikasi pada 2026-11-19 (perintah `gradle` dan `./gradlew` menghasilkan
APK identik):

```text
Berkas : samples/01-hello-android/app/build/outputs/apk/debug/app-debug.apk
Ukuran : 844.858 byte (828 KiB)
SHA-256: 9c3763af6378a80a030c24f2a282fe9d4116bf9fdb5af6a1c5f3ee7aaf5da01d
Waktu  : BUILD SUCCESSFUL in 1m 7s (33 task)
```

Verifikasi ulang hash:

```bash
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Pasang ke perangkat:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Buka aplikasi **Hello RevCore Sample**. Jika tombol dihitung, APK benar-benar berjalan.

### Mengimpor ke Android Studio

1. **File → Open**, pilih folder `samples/01-hello-android` (bukan root repository).
2. Tunggu **Gradle sync** selesai. Jika diminta JDK, pilih JDK 21 atau lebih baru.
3. Pilih perangkat fisik atau emulator pada toolbar.
4. Tekan **Run**. Android Studio akan membangun dan memasang APK debug.
5. Untuk melihat APK yang dihasilkan: `app/build/outputs/apk/debug/`.

Jika sync gagal karena SDK, buka **Settings → Languages & Frameworks → Android SDK** dan
pasang Platform 36.

### Mengapa APK debug dapat dipasang tanpa membuat keystore?

Android Studio/Gradle otomatis memakai **debug keystore** di direktori pengguna
(`~/.android/debug.keystore`). APK debug hanya untuk pengujian pribadi. Jangan
membagikannya sebagai rilis, dan jangan menganggapnya ditandatangani untuk distribusi.

## 3. Jalur B — memakai `:core` di aplikasi sendiri

Ini adalah tujuan jangka panjang: aplikasi Anda memiliki layar dan identitas sendiri,
sementara `:core` menangani komunikasi.

Langkah konseptual:

1. Buat proyek aplikasi Android baru dengan `applicationId` milik Anda.
2. Build `:core` menjadi AAR:

   ```bash
   export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
   gradle --offline assembleDebug
   # hasil: core/build/outputs/aar/core-debug.aar
   ```

3. Salin AAR ke `app/libs/` aplikasi Anda, lalu deklarasikan:

   ```kotlin
   dependencies {
       implementation(files("libs/core-debug.aar"))
       // dependency transitif :core tetap perlu dideklarasikan:
       implementation("com.github.mik3y:usb-serial-for-android:3.8.0")
       implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
   }
   ```

4. Minta izin USB dari aplikasi Anda sebelum memanggil `HondaProtocolEngine`.
   `:core` tidak meminta izin dan tidak mengelola lifecycle layar.
5. Tangani `NO_RESPONSE`, `UNSUPPORTED_PROFILE`, dan `null` sebagai keadaan yang berbeda
   dari "tidak ada masalah".

Langkah 2 sudah diverifikasi. Langkah 3–5 adalah `REFERENCE`: belum diuji sebagai proyek
aplikasi di repository ini. Jangan menganggapnya build-verified sampai Anda benar-benar
menjalankannya.

## 4. APK, AAB, AAR, dan library

| Istilah | Isi | Dipasang langsung? | Dipakai untuk |
|---|---|---|---|
| **APK** | Aplikasi lengkap + resource + DEX | Ya | Pengujian dan distribusi langsung |
| **AAB** | Bundel aplikasi untuk Play Store | Tidak | Unggahan ke toko aplikasi |
| **AAR** | Pustaka Android + manifest/resource | Tidak | Dipakai proyek aplikasi lain |
| **JAR** | Kelas Java/Kotlin saja | Tidak | Pustaka JVM murni |
| **Library module** | Source yang dibangun menjadi AAR | Tidak | `:core` di repository ini |

Satu repository dapat berisi library dan aplikasi sekaligus. Repository ini sengaja
hanya berisi library; contoh aplikasi diletakkan terpisah di `samples/` agar source
produksi tidak berubah.

## 5. Menandatangani APK rilis

APK rilis memerlukan keystore milik Anda. **Jangan pernah** menaruh keystore, kata sandi,
atau `signing.properties` di repository, issue, chat, atau layanan eksternal.

Membuat keystore (jalankan lokal, isi kata sandi secara interaktif):

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias revcore-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

Konfigurasi Gradle memakai variabel lingkungan atau berkas di luar repository:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("REVCORE_KEYSTORE") ?: "missing.keystore")
            storePassword = System.getenv("REVCORE_STORE_PASSWORD")
            keyAlias = System.getenv("REVCORE_KEY_ALIAS")
            keyPassword = System.getenv("REVCORE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Verifikasi hasil:

```bash
apksigner verify --verbose app-release.apk
```

Bila `apksigner` tidak ada di PATH, gunakan yang ada di
`$ANDROID_HOME/build-tools/<versi>/apksigner`.

**Keputusan distribusi APK RevCore sepenuhnya milik Otoy.** Membangun APK untuk pengujian
lokal diperbolehkan; mengunggah ke GitHub Releases, situs, atau platform lain memerlukan
persetujuan langsung, disertai versi, nama berkas, hash, lokasi, tujuan, dan informasi
yang disertakan.

## 6. Troubleshooting build

| Gejala | Penyebab | Tindakan |
|---|---|---|
| `SDK location not found` | `ANDROID_HOME` kosong | Set ke folder SDK yang memiliki Platform 36 |
| `Plugin [id: 'com.android.application' ...] was not found` | Cache plugin belum lengkap | Jalankan build online sekali |
| `Could not resolve com.github.mik3y:...` | JitPack tidak dapat diakses | Hubungkan jaringan atau isi cache Gradle |
| `Unsupported class file major version` | JDK/Gradle terlalu lama | Gunakan JDK 21+ dan Gradle 9.6.0 |
| `compileSdk 36 not found` | Platform 36 belum dipasang | Pasang lewat SDK Manager |
| `No matching toolchains found ... 21` | JDK 21 tidak tersedia | Pasang JDK 21 atau arahkan `JAVA_HOME` |
| `assembleDebug` tidak menghasilkan APK di root | Root hanya berisi library | Build `samples/01-hello-android` atau buat modul aplikasi |
| APK terpasang tetapi tidak muncul | Activity tidak dideklarasikan | Periksa `AndroidManifest.xml` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK dengan signature berbeda sudah terpasang | Uninstall versi lama lebih dulu |

## 7. Batas

- APK contoh tidak berkomunikasi dengan ECU dan tidak membuktikan protokol.
- Build berhasil membuktikan kompilasi dan pengemasan, bukan kompatibilitas kendaraan.
- Contoh memakai `compileSdk 36`, `minSdk 26`, Java 21, AGP 9.1.1, dan Gradle 9.6.0.
  Versi lain dapat memerlukan penyesuaian.
- Jangan mengunggah `build/`, `.gradle/`, APK, AAR, atau keystore ke repository.

## 8. Latihan

1. Build APK contoh dan pasang di perangkat.
2. Ubah teks pada `MainActivity.java`, build ulang, dan pastikan teks berubah.
3. Jalankan `gradle clean assembleDebug` dan bandingkan ukuran APK.
4. Buat keystore percobaan di luar repository, lalu verifikasi APK rilis dengan
   `apksigner`. Jangan gunakan keystore percobaan untuk distribusi.
5. Jelaskan dengan kata sendiri mengapa AAR tidak dapat dipasang.
