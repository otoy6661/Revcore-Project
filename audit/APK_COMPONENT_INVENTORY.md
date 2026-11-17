# INVENTARIS KOMPONEN APK

Dokumen ini mencatat inventaris terukur dari APK referensi. Isinya dibatasi pada metadata, struktur arsip, dan komponen minimum yang diperlukan untuk menjelaskan modul publik. Source, diagram internal, dan detail implementasi Application Layer serta Security/Integrity **tidak** dicatat di sini.

## 1. Identitas input

| Item | Nilai | Cara ukur |
|---|---|---|
| Berkas | `RevCore build 161126.apk` | Nama file yang disediakan owner |
| Ukuran | 39.689.642 byte | `stat` / `ls -l` |
| SHA-256 | `b2b6c5bd68cc9c727c2babe0c510b928a6135fa7ef5e9d8847275887c8647432` | `sha256sum` |
| Package | `com.revcore.android` | Manifest terkompilasi |
| versionCode / versionName | `1` / `1.0` | Manifest terkompilasi |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 | Manifest dan metadata build |
| Waktu build (timestamp file) | 2026-11-16 | Timestamp arsip |

File asli tidak diubah, tidak di-repack, dan tidak diunggah ke layanan eksternal. Seluruh pengukuran dilakukan lokal.

## 2. Triase proteksi

| Pemeriksaan | Hasil | Tingkat bukti |
|---|---|---|
| Magic DEX | `dex\n038`, header standar | TERVERIFIKASI |
| Enkripsi DEX / packer / loader dinamis | Tidak ditemukan | TERVERIFIKASI |
| Nama class | Sebagian dipertahankan, sebagian di-obfuscate R8 | TERUKUR |
| Native library aplikasi | Tidak ada | TERUKUR |
| Kebutuhan key | Tidak ada | TERVERIFIKASI |

Karena DEX, manifest, aset, dan resource dapat dibaca tanpa kunci, prosedur permintaan key pada addendum B tidak dijalankan. Tidak ada key yang diminta, dicetak, disimpan, atau dikirim.

## 3. Struktur arsip

Total **253 entry**. Ukuran terkompresi dan tidak terkompresi diukur dari central directory.

| Kategori | Entry | Terkompresi (byte) | Tidak terkompresi (byte) | Keterangan |
|---|---:|---:|---:|---|
| `assets/<MAP pihak ketiga>/` | 163 | 35.216.243 | 35.225.468 | 162 file `.map` + 1 `manifest.tsv` |
| `res/` | 17 | 2.632.811 | 2.634.145 | Resource terkompilasi |
| `kotlin/` | 8 | 12.053 | 51.125 | Metadata `*.kotlin_builtins` |
| `META-INF/` | 51 | 540 | 454 | Metadata signing dan manifest |
| `lib/` | 4 | 37.392 | 37.392 | `libandroidx.graphics.path.so` (4 ABI) |
| `assets/` data lain | 4 | 56.595 | 926.029 | Lihat §6 |
| `assets/dexopt/` | 2 | 7.166 | 7.166 | `baseline.prof`, `baseline.profm` |
| Root | 4 | 1.652.574 | 3.301.216 | `classes.dex`, `AndroidManifest.xml`, `resources.arsc`, `DebugProbesKt.bin` |
| **Total** | **253** | **39.615.374** | **42.182.995** | Selisih dengan ukuran file berasal dari struktur ZIP |

Tidak ada secondary DEX, split APK, atau berkas firmware di dalam APK.

## 4. DEX

| Properti | Nilai |
|---|---:|
| Jumlah file DEX | 1 (`classes.dex`) |
| Magic / versi | `dex\n038` |
| Ukuran `classes.dex` | 3.171.576 byte |
| String IDs | 17.075 |
| Type IDs | 3.849 |
| Prototype IDs | 5.049 |
| Field IDs | 11.945 |
| Method IDs | 18.722 |
| Class definitions | 3.006 |

Angka ini adalah isi header DEX, bukan jumlah class milik aplikasi. Sebagian besar berasal dari AndroidX, Kotlin, dan library pendukung. Jumlah class tidak dipakai sebagai ukuran kelengkapan rekonstruksi.

## 5. Native library

| ABI | Berkas | Ukuran tidak terkompresi (byte) |
|---|---|---:|
| arm64-v8a | `libandroidx.graphics.path.so` | 10.096 |
| armeabi-v7a | `libandroidx.graphics.path.so` | 7.252 |
| x86 | `libandroidx.graphics.path.so` | 9.284 |
| x86_64 | `libandroidx.graphics.path.so` | 10.760 |

Tidak ada library protokol native pihak ketiga pada build ini. Implementasi K-Line berada di bytecode. Referensi library native yang pernah dianalisis tidak disertakan dan hanya dipakai sebagai pembanding konstanta, ditandai `RECONSTRUCTED`/`REFERENCE`.

## 6. Aset

| Aset | Ukuran (byte) | Keputusan publikasi |
|---|---:|---|
| `assets/ecm_database.json` | 748.580 | Tidak dipublikasikan; diganti 3 record sintetis |
| `assets/<profil pihak ketiga>.json` | 127.269 | Tidak dipublikasikan; hak redistribusi belum terbukti |
| `assets/<MAP pihak ketiga>/` | 162 `.map` + `manifest.tsv` | Tidak dipublikasikan; materi pihak ketiga |
| `assets/integrity_policy.json` | 37.630 | Tidak dipublikasikan; Security/Integrity |
| `assets/ui_integrity_manifest.json` | 12.550 | Tidak dipublikasikan; Security/Integrity |
| `assets/dexopt/baseline.prof` | 6.405 | Tidak relevan untuk repository |
| `assets/dexopt/baseline.profm` | 761 | Tidak relevan untuk repository |

Repo staging hanya membawa `core/src/main/assets/ecm_database.json` sintetis (`SAMPLE-ECM-001` sampai `SAMPLE-ECM-003`) dan fixture yang dibuat sendiri. Tidak ada aset APK asli yang disalin ke repository.

## 7. Manifest: minimum untuk modul publik

### 7.1 Permission

| Permission | Catatan |
|---|---|
| `android.permission.INTERNET` | Tidak dipakai modul komunikasi publik |
| `android.permission.ACCESS_NETWORK_STATE` | Tidak dipakai modul komunikasi publik |
| `android.permission.WRITE_EXTERNAL_STORAGE` | `maxSdkVersion=29`; penyimpanan aplikasi |
| `android.permission.READ_EXTERNAL_STORAGE` | `maxSdkVersion=32`; penyimpanan aplikasi |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | Penyimpanan aplikasi |
| `com.revcore.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Permission internal AndroidX |

Pustaka `:core` tidak mendeklarasikan permission sendiri. Izin USB dan storage adalah tanggung jawab aplikasi pemanggil.

### 7.2 Feature

- `android.hardware.usb.host` dengan `required=false`.

### 7.3 Komponen yang relevan

| Tipe | Nama | Relevansi publik |
|---|---|---|
| Activity | `com.revcore.android.MainActivity` | Launcher dan intent filter `USB_DEVICE_ATTACHED`; titik masuk penanganan adapter |
| Provider | `androidx.startup.InitializationProvider` | Inisialisasi AndroidX, bukan bagian protokol |
| Receiver | `androidx.profileinstaller.ProfileInstallReceiver` | Komponen bawaan AndroidX |

Tidak ada `Service` pada manifest. Komunikasi USB berjalan dalam proses dan lifecycle aplikasi pemanggil. Komponen Application Layer lain tidak dirinci karena di luar cakupan publikasi.

## 8. Pemetaan R8

| Item | Nilai |
|---|---|
| `mapping.txt` | 34.569.827 byte, dari output build lokal (bukan diekstrak dari APK) |
| `seeds.txt` | 703.986 byte |
| Pemetaan disalin ke repository | Tidak |

`mapping.txt` dipakai hanya untuk korelasi nama class. Isinya memuat nama internal Application Layer dan Security/Integrity sehingga tidak boleh dipublikasikan. Pemetaan berlaku untuk build ini dan tidak boleh diasumsikan untuk APK lain.

## 9. Batas dan keterbatasan

- Inventaris ini tidak membuktikan kompatibilitas dengan ECU atau kendaraan nyata.
- Keberadaan class, aset, atau string di APK tidak membuktikan bahwa jalur komunikasi telah diuji pada hardware.
- Detail sertifikat, fingerprint, trust record, dan konfigurasi integritas tidak dicatat.
- Angka ukuran dapat berbeda bila alat ZIP menghitung header tambahan; angka di atas berasal dari central directory.

## 10. Cara memverifikasi ulang

```bash
sha256sum "RevCore build 161126.apk"
unzip -l "RevCore build 161126.apk" | tail -n 1
unzip -l "RevCore build 161126.apk" | awk '{print $4}' | grep -c '^assets/.*\.map$'
```

Hasil yang diharapkan: SHA-256 sama, 253 entry, dan 162 file `.map` di direktori MAP pihak ketiga. Nama direktori asli tidak dicetak di dokumen publik.
