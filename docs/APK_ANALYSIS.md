# ANALISIS APK

> **Catatan status (2026-11-19).** Dokumen ini adalah catatan analisis yang ditulis
> sebelum rilis `v0.3.0`. Repository sudah dipublikasikan; pernyataan tentang "staging"
> atau "belum dipublikasikan" di bagian akhir dokumen merujuk kondisi saat audit.

Dokumen ini merangkum analisis APK RevCore yang menjadi input reverse engineering. Isinya dibatasi pada metadata, struktur arsip, dan temuan yang diperlukan untuk menjelaskan modul publik. Source dan detail implementasi Application Layer serta Security/Integrity **tidak** didokumentasikan di sini.

## 1. Input analisis

| Item | Nilai |
|---|---|
| Nama file | `RevCore build 161126.apk` |
| Ukuran | 39.689.642 byte |
| SHA-256 | `b2b6c5bd68cc9c727c2babe0c510b928a6135fa7ef5e9d8847275887c8647432` |
| Package | `com.revcore.android` |
| versionCode / versionName | `1` / `1.0` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Waktu build (timestamp file) | 2026-11-16 |

APK ini adalah artefak build terakhir yang disediakan owner. File asli tidak diubah, tidak di-repack, dan tidak diunggah ke layanan mana pun. Seluruh analisis dilakukan lokal.

## 2. Metode dan tingkat bukti

| Temuan | Metode | Tingkat bukti |
|---|---|---|
| Hash, ukuran, daftar entry ZIP | `sha256sum`, pembacaan central directory | TERUKUR |
| Jumlah class/method/field DEX | Pembacaan header `classes.dex` | TERUKUR |
| Daftar permission dan komponen manifest | `aapt2 dump badging` / `xmltree` | TERUKUR |
| Kategori aset | Pembacaan nama dan ukuran entry ZIP | TERUKUR |
| Tidak ada enkripsi/packer DEX | Magic `dex\n038`, header valid, class dapat dibaca | TERVERIFIKASI |
| Pemetaan nama class R8 | `mapping.txt` dari output build release lokal | REFERENSI |
| Perilaku protokol | Source staging + pengujian unit | TERVERIFIKASI (lihat dokumen terkait) |

`mapping.txt` berasal dari direktori output build lokal, bukan diekstrak dari APK. File ini dipakai sebagai alat bantu korelasi; klaim identitas kode tetap harus disertai bukti DEX atau perilaku yang dapat diuji.

## 3. Triase proteksi

- DEX menggunakan magic `dex\n038` dan header standar. Tidak ditemukan indikasi enkripsi DEX, packer, atau loader dinamis yang menyembunyikan bytecode.
- Nama class aplikasi sebagian besar dipertahankan (identity mapping) karena konfigurasi R8 build; sebagian nama di-obfuscate.
- Tidak ada native library milik aplikasi. Empat file `.so` yang ada seluruhnya `libandroidx.graphics.path.so` untuk empat ABI.
- **Tidak diperlukan key** untuk membaca DEX, manifest, aset, atau resource. Karena itu prosedur permintaan key pada addendum B tidak dijalankan.
- Sertifikat signing tervalidasi secara struktur, tetapi detail sertifikat, fingerprint, dan rantai sertifikat tidak dipublikasikan.

Kesimpulan triase: proteksi yang teramati adalah obfuscation R8 dan resource shrinking, bukan proteksi kriptografis yang memblokir analisis.

## 4. Inventaris arsip

Total 253 entry:

| Kategori | Jumlah | Keterangan |
|---|---:|---|
| `assets/<MAP pihak ketiga>/` | 163 | 162 file `.map` + 1 manifest `.tsv` |
| `META-INF/` | 51 | Metadata signing dan manifest |
| `res/` | 17 | Resource terkompilasi |
| `lib/` | 4 | `libandroidx.graphics.path.so` (4 ABI) |
| `assets/` lain | 6 | Lihat tabel aset |
| Root | 4 | `classes.dex`, `AndroidManifest.xml`, `resources.arsc`, `DebugProbesKt.bin` |
| `kotlin/` | 8 | Metadata `*.kotlin_builtins` bawaan Kotlin |

Tidak ada secondary DEX, tidak ada split APK, dan tidak ada file firmware di dalam APK.

## 5. DEX

| Properti | Nilai |
|---|---|
| Jumlah file DEX | 1 (`classes.dex`) |
| Magic / versi | `dex\n038` |
| String IDs | 17.075 |
| Type IDs | 3.849 |
| Prototype IDs | 5.049 |
| Field IDs | 11.945 |
| Method IDs | 18.722 |
| Class definitions | 3.006 |

Angka di atas adalah isi header DEX, bukan jumlah class yang seluruhnya milik aplikasi. Sebagian besar berasal dari AndroidX, Kotlin, dan library pendukung.

## 6. Native library

| ABI | File | Ukuran |
|---|---|---:|
| arm64-v8a | `libandroidx.graphics.path.so` | lihat arsip |
| armeabi-v7a | `libandroidx.graphics.path.so` | lihat arsip |
| x86 | `libandroidx.graphics.path.so` | lihat arsip |
| x86_64 | `libandroidx.graphics.path.so` | lihat arsip |

Tidak ada library protokol native pihak ketiga pada build ini. Implementasi K-Line seluruhnya berada di bytecode.

## 7. Manifest

### 7.1 Permission

| Permission | Catatan |
|---|---|
| `android.permission.INTERNET` | Tidak dipakai oleh modul komunikasi publik |
| `android.permission.ACCESS_NETWORK_STATE` | Tidak dipakai oleh modul komunikasi publik |
| `android.permission.WRITE_EXTERNAL_STORAGE` | `maxSdkVersion=29`; berkaitan dengan penyimpanan aplikasi |
| `android.permission.READ_EXTERNAL_STORAGE` | `maxSdkVersion=32`; berkaitan dengan penyimpanan aplikasi |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | Berkaitan dengan penyimpanan aplikasi |
| `com.revcore.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Permission internal AndroidX |

Modul `:core` tidak meminta permission sendiri. Izin USB dan storage merupakan tanggung jawab aplikasi pemanggil.

### 7.2 Feature

- `android.hardware.usb.host` dengan `required=false`.

### 7.3 Komponen

| Tipe | Nama | Relevansi publik |
|---|---|---|
| Activity | `com.revcore.android.MainActivity` | Launcher; memiliki intent filter `USB_DEVICE_ATTACHED` yang menjadi titik masuk penanganan adapter |
| Provider | `androidx.startup.InitializationProvider` | Inisialisasi AndroidX (Emoji2, Lifecycle, ProfileInstaller) |
| Receiver | `androidx.profileinstaller.ProfileInstallReceiver` | Komponen bawaan AndroidX, bukan bagian protokol |

Tidak ada `Service` pada manifest. Komunikasi USB berjalan di dalam proses aplikasi dan lifecycle pemanggil.

Komponen Application Layer lain tidak dirinci. Yang dicantumkan hanya minimum yang diperlukan untuk menjelaskan jalur masuk USB dan dependensi framework.

## 8. Aset

| Aset | Ukuran (byte) | Keputusan publikasi |
|---|---:|---|
| `assets/ecm_database.json` | 748.580 | **Tidak dipublikasikan.** Diganti 3 record sintetis di staging |
| `assets/<profil pihak ketiga>.json` | 127.269 | **Tidak dipublikasikan.** Hak redistribusi belum terbukti |
| `assets/<MAP pihak ketiga>/` | 162 `.map` + manifest | **Tidak dipublikasikan.** Materi pihak ketiga |
| `assets/integrity_policy.json` | 37.630 | **Tidak dipublikasikan.** Security/Integrity |
| `assets/ui_integrity_manifest.json` | 12.550 | **Tidak dipublikasikan.** Security/Integrity |
| `assets/dexopt/baseline.prof` | 6.405 | Tidak relevan untuk repo |
| `assets/dexopt/baseline.profm` | 761 | Tidak relevan untuk repo |

Repo staging hanya membawa `ecm_database.json` sintetis dan contoh frame/sensor yang dibuat sendiri. Tidak ada aset APK asli yang disalin ke repo publik.

## 9. Obfuscation dan pemetaan

- `mapping.txt` berukuran 34.569.827 byte dan `seeds.txt` 703.986 byte tersedia di output build lokal.
- Sebagian besar class di package `core.comm`, `core.protocol`, `core.ftdi`, `core.usb`, dan `data.ecu` tetap memakai nama aslinya.
- Nama yang di-obfuscate tidak "ditebak" menjadi API publik. Rekonstruksi sumber di staging memakai nama yang dapat dipertanggungjawabkan dan diberi status sesuai tingkat buktinya.
- Pemetaan lengkap tidak disalin ke repo publik karena memuat nama internal Application Layer dan Security/Integrity.

## 10. Batas publikasi

Sesuai addendum:

1. **Application Layer** dianalisis secukupnya untuk memahami titik integrasi. Source, diagram internal, dan detail implementasinya tidak masuk repo.
2. **Security/Integrity** dianalisis untuk memastikan tidak ada materi yang salah dipublikasikan. Mekanisme, konfigurasi, dan asetnya tidak dijelaskan di repo.
3. Analisis internal yang lebih rinci disimpan di direktori kerja privat, terpisah dari staging publik.
4. Kedalaman analisis tidak dikurangi; yang dibatasi adalah apa yang dipublikasikan.

## 11. Keterbatasan

- Analisis ini tidak membuktikan kompatibilitas dengan ECU atau kendaraan nyata.
- Jumlah class DEX bukan ukuran kualitas atau kelengkapan rekonstruksi.
- Status signing diverifikasi secara struktur, bukan sebagai jaminan keaslian di luar konteks build owner.
- Pemetaan R8 hanya berlaku untuk build ini dan tidak boleh diasumsikan untuk APK lain.
