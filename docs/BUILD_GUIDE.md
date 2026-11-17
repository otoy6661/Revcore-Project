# Build Guide

Panduan membangun pustaka `:core` dari repository ini. Repository hanya berisi satu
modul Android library; tidak ada modul aplikasi, konfigurasi signing, atau artefak rilis.

## 1. Prasyarat

| Komponen | Versi yang diuji | Catatan |
|---|---|---|
| JDK | Temurin 25.0.3 (runtime), target Java 21 | JDK 21 atau lebih baru; proyek menargetkan Java 21 |
| Gradle | 9.6.0 | Wrapper tidak disertakan; gunakan instalasi Gradle 9.6.0 |
| Android Gradle Plugin | 9.1.1 | Dideklarasikan di `build.gradle.kts` root |
| Kotlin | 2.2.10 (plugin), built-in Kotlin pada AGP | Tidak ada plugin Kotlin terpisah di modul |
| Android SDK Platform | 36 | `compileSdk = 36`, `minSdk = 26` |
| Android Build Tools | Sesuai Platform 36 | Dipasang bersama SDK Platform |
| Koneksi jaringan | Diperlukan saat build pertama | Untuk AGP, AndroidX, kotlinx-coroutines, usb-serial-for-android |

Pustaka `:core` tidak memerlukan perangkat Android, kabel, adapter, atau ECU untuk
dibangun dan diuji. Semua unit test berjalan di JVM.

## 2. Menyiapkan environment

### 2.1 Android SDK

Arahkan `ANDROID_HOME` ke direktori SDK. Jangan membuat `local.properties` berisi path
pribadi di dalam repository.

PowerShell:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
```

Git Bash:

```bash
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
```

Pastikan `$ANDROID_HOME/platforms/android-36` ada.

### 2.2 Gradle

Jika `gradle` belum ada di `PATH`, panggil binary Gradle 9.6.0 secara langsung:

```bash
export GRADLE_BIN="$HOME/.gradle/wrapper/dists/gradle-9.6.0-bin/<hash>/gradle-9.6.0/bin/gradle"
"$GRADLE_BIN" --version
```

`<hash>` berbeda di setiap mesin. Cari dengan:

```bash
find "$HOME/.gradle/wrapper/dists" -path "*gradle-9.6.0*/bin/gradle" -type f
```

Repository sengaja tidak menyertakan `gradlew`/`gradle/wrapper`. Jika Anda menambahkan
wrapper, jangan mengubah versi Gradle tanpa menguji ulang seluruh build.

### 2.3 JDK

Proyek memakai `compileOptions` Java 21 dan `kotlin { jvmToolchain(21) }`. Bila mesin
hanya memiliki JDK 25, Gradle dapat memakai JDK tersebut sebagai launcher, tetapi
toolchain 21 harus tersedia atau di-provision. Bila muncul
`No matching toolchains found for requested specification: {languageVersion=21}`,
pasang JDK 21 dan/atau arahkan Gradle ke instalasi tersebut:

```bash
export JAVA_HOME="/path/to/jdk-21"
```

## 3. Perintah build

Dari root repository:

```bash
"$GRADLE_BIN" clean test
"$GRADLE_BIN" assembleDebug
```

Jika cache dependensi sudah terisi dan Anda ingin memastikan tidak ada unduhan:

```bash
"$GRADLE_BIN" --offline clean test
"$GRADLE_BIN" --offline assembleDebug
```

Artefak:

- Laporan test: `core/build/reports/tests/testDebugUnitTest/index.html`
- Hasil XML: `core/build/test-results/testDebugUnitTest/`
- AAR debug: `core/build/outputs/aar/core-debug.aar`

AAR tersebut adalah artefak build lokal, bukan produk rilis, dan tidak disertakan dalam
repository.

Perintah test yang lebih sempit:

```bash
"$GRADLE_BIN" :core:testDebugUnitTest --tests "com.revcore.android.core.comm.HondaFrameCodecTest"
"$GRADLE_BIN" :core:testDebugUnitTest --tests "com.revcore.android.core.protocol.*"
```

## 4. Struktur konfigurasi

| Berkas | Isi |
|---|---|
| `settings.gradle.kts` | `rootProject.name = "RevCore"`, `include(":core")`, repositori Google/Maven Central/JitPack |
| `build.gradle.kts` | Versi plugin AGP dan Kotlin |
| `gradle.properties` | Heap 2048 MB, metaspace 512 MB, `android.useAndroidX=true` |
| `core/build.gradle.kts` | Namespace, SDK, Java 21, dependency |
| `.gitignore` | `.gradle/`, `build/`, `local.properties`, keystore, APK/AAR/JAR |

`settings.gradle.kts` memuat `resolutionStrategy` yang memetakan plugin
`com.android.library` ke modul `com.android.tools.build:gradle:<versi>`. Ini diperlukan
karena penanda plugin untuk modul library tidak selalu tersedia di cache lokal. Jangan
menghapus blok tersebut tanpa menguji build bersih.

## 5. Dependensi

| Dependency | Versi | Lisensi | Kegunaan |
|---|---|---|---|
| `androidx.annotation:annotation` | 1.8.1 | Apache 2.0 | Anotasi AndroidX |
| `com.github.mik3y:usb-serial-for-android` | 3.8.0 | MIT | Transport USB serial |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache 2.0 | Coroutine dan `StateFlow` |
| `junit:junit` (test) | 4.13.2 | EPL 1.0 | Unit test JVM |
| `kotlinx-coroutines-test` (test) | 1.9.0 | Apache 2.0 | Kontrol waktu pada test |

Rincian lisensi ada di [LICENSE-DEPENDENCIES.md](../LICENSE-DEPENDENCIES.md).
`usb-serial-for-android` diambil dari JitPack; build pertama memerlukan akses ke
`https://jitpack.io`.

## 6. Catatan tentang unit test

`core/build.gradle.kts` menetapkan:

```kotlin
testOptions {
    unitTests.isReturnDefaultValues = true
}
```

Artinya pemanggilan API Android yang tidak tersedia di JVM mengembalikan nilai default,
bukan melempar exception. Test tidak boleh bergantung pada perilaku Android nyata dan
tidak boleh mengklaim uji perangkat keras. Baca [TESTING_GUIDE.md](TESTING_GUIDE.md).

## 7. Troubleshooting

| Gejala | Penyebab umum | Tindakan |
|---|---|---|
| `SDK location not found` | `ANDROID_HOME` kosong | Set `ANDROID_HOME` ke SDK yang memiliki Platform 36 |
| `No matching toolchains found ... 21` | JDK 21 tidak terpasang | Pasang JDK 21 atau arahkan `JAVA_HOME` |
| `Plugin [id: 'com.android.library', version: '9.1.1'] was not found` | Cache plugin tidak lengkap | Jalankan build online sekali; jangan hapus `resolutionStrategy` |
| `Could not resolve com.github.mik3y:usb-serial-for-android:3.8.0` | JitPack tidak dapat diakses | Hubungkan jaringan atau isi cache Gradle |
| `Unsupported class file major version` | Gradle/JDK terlalu lama | Gunakan Gradle 9.6.0 dan JDK 21+ |
| `compileSdk 36 not found` | Platform 36 belum dipasang | Pasang SDK Platform 36 |
| Test gagal karena method Android bernilai default | Asumsi test salah | Perbaiki test; jangan mengaktifkan mock framework tanpa alasan |
| Build gagal karena path pengguna | `local.properties` ikut terbawa | Hapus dari repository; gunakan environment variable |

Jika build gagal karena unduhan dependensi, jangan menyalin JAR/AAR ke repository
sebagai solusi. Perbaiki resolusi dependensi atau gunakan cache Gradle lokal.

## 8. Batas build

- Tidak ada signing, `keystore`, atau `signingConfig` di repository ini.
- Tidak ada build varian rilis, ProGuard/R8, atau APK aplikasi.
- `assembleDebug` hanya membuktikan pustaka dapat dikompilasi, bukan bahwa protokol
  berfungsi pada kendaraan.
- Jangan mengunggah `build/`, `.gradle/`, atau AAR ke repository.
