# 01 — Hello Android

Aplikasi Android minimal untuk membuktikan bahwa sebuah proyek aplikasi benar-benar
dapat dibangun menjadi APK dan dipasang. Aplikasi ini **bukan** aplikasi RevCore dan
**tidak** berkomunikasi dengan ECU.

## Tujuan

- Memahami perbedaan modul library dan modul aplikasi.
- Menghasilkan APK debug yang dapat dipasang.
- Mengenal `AndroidManifest.xml`, Activity, dan resource dasar.

## Isi

```text
01-hello-android/
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/example/hellorevcore/MainActivity.java
```

- `applicationId`: `com.example.hellorevcore` (bukan package RevCore).
- `compileSdk` 36, `minSdk` 26, `targetSdk` 36.
- Java 21 toolchain.
- Satu Activity yang membuat tampilan secara programatik.
- Tidak ada dependency jaringan, iklan, atau telemetri.

## Prasyarat

- JDK 21 atau lebih baru.
- Android SDK Platform 36.
- Gradle 9.6.0 terpasang (`gradle` ada di PATH). Repository ini tidak membawa Gradle
  Wrapper; Android Studio dapat membuatkannya saat folder ini diimpor.
- ADB untuk memasang ke perangkat.

Set environment SDK:

```bash
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"   # Windows Git Bash
```

## Cara build

```bash
cd samples/01-hello-android
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
gradle assembleDebug
```

Build pertama memerlukan internet untuk mengunduh dependency Android bila belum ada di
cache. Setelah itu:

```bash
gradle --offline assembleDebug
```

## Output terverifikasi

Dibangun pada 2026-11-19 dengan Gradle 9.6.0, AGP 9.1.1, JDK 25.0.3 (toolchain 21),
dan Android SDK Platform 36:

```text
app/build/outputs/apk/debug/app-debug.apk
Ukuran : 844.858 byte (828 KiB)
SHA-256: 9c3763af6378a80a030c24f2a282fe9d4116bf9fdb5af6a1c5f3ee7aaf5da01d
Hasil  : BUILD SUCCESSFUL
```

## Input dan output

Input: tidak ada. Aplikasi hanya menampilkan tombol dan penghitung.

Output: teks `Hello RevCore Sample` dan angka yang bertambah setiap kali tombol ditekan.

## Memasang

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Jika muncul `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall versi lama lebih dulu:

```bash
adb uninstall com.example.hellorevcore
```

## Batasan

- APK debug ditandatangani dengan debug keystore; bukan untuk distribusi.
- Aplikasi tidak memakai `:core`, tidak meminta izin USB, dan tidak membuka adapter.
- Aplikasi tidak membuktikan protokol atau kompatibilitas kendaraan.
- Tampilan sengaja minimal agar fokus pada proses build, bukan desain UI.

## Status bukti

Build APK: **Verified** pada environment yang tercatat di atas. Perilaku aplikasi:
trivial dan tidak terkait ECU.

## Latihan

1. Ubah teks tombol, build ulang, dan pasang.
2. Tambahkan tombol kedua yang mengurangi penghitung.
3. Ganti warna latar belakang.
4. Buat keystore percobaan di luar repository dan build APK rilis bertanda tangan.
5. Jelaskan mengapa APK ini tidak boleh disebut "aplikasi RevCore".
