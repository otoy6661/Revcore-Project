# 01 — Dasar Android, Kotlin, Gradle, dan Git

Dokumen ini menjelaskan alat yang dipakai sebelum menyentuh protokol. Semua perintah
ditulis untuk Windows (Git Bash) dan Linux/macOS. Jalankan dari direktori proyek.

## 1. Tiga hal yang sering tertukar

| Alat | Fungsinya | Yang perlu dipasang |
|---|---|---|
| **JDK** | Mengompilasi dan menjalankan kode Java/Kotlin | JDK 21 atau lebih baru (Temurin) |
| **Android SDK** | Pustaka dan alat Android (misalnya `compileSdk 36`) | Android Studio + SDK Platform 36 |
| **Gradle** | Menjalankan proses build | Gradle 9.6.0 atau Gradle Wrapper |

JDK bukan Android SDK. Android SDK bukan Gradle. Error seperti `SDK location not found`
dan `Unsupported class file major version` muncul ketika salah satu alat ini keliru.

Cek versi:

```bash
java -version
javac -version
gradle --version
```

Repository ini memakai:

- Java toolchain 21 (dapat dijalankan dengan JDK yang lebih baru);
- Gradle 9.6.0;
- Android Gradle Plugin 9.1.1;
- Kotlin 2.2.10;
- `compileSdk` 36, `minSdk` 26.

## 2. Android dalam sepuluh menit

Aplikasi Android adalah kumpulan kode yang dibungkus menjadi APK. Tiga hal yang wajib
dipahami pemula:

1. **`AndroidManifest.xml`** mendeklarasikan identitas aplikasi, Activity yang menjadi
   layar, izin (misalnya USB), dan tema.
2. **Activity** adalah satu layar. Ia memiliki lifecycle: dibuat, tampil, berhenti,
   dan dihancurkan.
3. **Gradle** menyatukan source, resource, dan dependency menjadi APK atau AAR.

Contoh Activity minimal (Java, dari `samples/01-hello-android`):

```java
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView label = new TextView(this);
        label.setText("Hello RevCore");
        setContentView(label);
    }
}
```

Contoh yang sama di Kotlin:

```kotlin
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Hello RevCore" })
    }
}
```

## 3. Kotlin yang benar-benar dipakai RevCore

Tidak perlu menguasai seluruh Kotlin. Mulailah dari lima konsep berikut.

### 3.1 `data class`

```kotlin
data class HondaDtc(val code: Int, val subCode: Int, val standardCode: String)
```

`data class` otomatis membuat `equals`, `hashCode`, `toString`, dan `copy`. Cocok untuk
nilai yang dibawa antar lapisan.

### 3.2 `enum class`

```kotlin
enum class ClearDtcStatus {
    SUCCESS_VERIFIED,
    CLEARED_UNVERIFIED,
    REJECTED_OR_TIMEOUT
}
```

Enum mencegah string bebas seperti `"berhasil"` yang tidak dapat diperiksa compiler.

### 3.3 Nullable dan `null`

```kotlin
val model: EcmModel? = engine.identifyEcu()
if (model == null) {
    // tidak ada identitas yang cocok; jangan mengarang model
}
```

Tanda `?` berarti nilai boleh kosong. RevCore memakai `null` untuk "tidak tersedia",
bukan angka palsu seperti `0`.

### 3.4 `suspend` dan coroutine

```kotlin
lifecycleScope.launch {
    val dtcs = engine.readDtcs()
    textView.text = dtcs.toString()
}
```

Operasi yang menunggu perangkat keras tidak boleh memblokir layar. `suspend` menandai
fungsi yang menunggu tanpa mengunci thread.

### 3.5 `when`

```kotlin
when (result.status) {
    ClearDtcStatus.SUCCESS_VERIFIED -> showVerified()
    ClearDtcStatus.CLEARED_UNVERIFIED -> showUnverified()
    else -> showFailure(result.status)
}
```

`when` memaksa semua kemungkinan dipikirkan. Gunakan `else` hanya bila memang ada
perilaku umum yang benar.

## 4. Struktur repository

```text
Revcore-Project/
├── core/          # pustaka Android :core (satu-satunya modul produksi)
├── docs/          # dokumentasi developer
├── examples/      # data sintetis
├── tools/         # validator Python
├── audit/         # inventaris dan audit
├── education/     # pusat pembelajaran ini
└── samples/       # proyek contoh terisolasi
```

`settings.gradle.kts` hanya memuat `include(":core")`. Artinya build root menghasilkan
**AAR**, bukan APK. Ini bukan kekurangan; ini memang pustaka.

## 5. Gradle: task yang perlu dikenal

| Perintah | Arti |
|---|---|
| `gradle --offline clean test` | Menghapus output lama dan menjalankan unit test tanpa jaringan |
| `gradle --offline assembleDebug` | Membangun AAR debug `:core` |
| `gradle --offline test --tests "*Honda*"` | Menjalankan test tertentu |
| `gradle tasks` | Melihat daftar task |

Prasyarat build di environment yang sudah diverifikasi:

```bash
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"   # Windows Git Bash
gradle --offline clean test
```

Jangan membuat `local.properties` berisi path pribadi di dalam repository. Gunakan
variabel lingkungan. Hasil verifikasi terakhir: 153 test, 0 gagal, 0 error, 0 dilewati
(2026-11-19). Angka itu membuktikan logika perangkat lunak, bukan komunikasi ECU.

Jika Gradle menolak karena cache plugin belum lengkap, jalankan sekali tanpa `--offline`
dengan koneksi internet, lalu kembali ke mode offline.

## 6. Git tanpa merusak riwayat

Git menyimpan riwayat, bukan hanya file terbaru. Aturan minimum:

```bash
git status                 # apa yang berubah?
git diff                   # isi perubahan
git log --oneline -10      # riwayat terakhir
git switch -c belajar/frame # cabang kerja baru
git add education/         # pilih berkas tertentu
git commit -m "Jelaskan mengapa perubahan ini perlu"
```

Hal yang **tidak** boleh dilakukan tanpa instruksi pemilik:

- `git push --force` atau `git reset --hard` pada riwayat bersama;
- menghapus commit lama;
- mengubah kredit commit;
- menimpa pekerjaan yang belum disimpan;
- mengubah pemilik repository.

Backup sebelum eksperimen besar:

```bash
git branch backup/sebelum-eksperimen
```

Branch adalah backup murah. Manfaatkan.

## 7. Membaca source secara efisien

1. Cari nama fungsi dengan pencarian teks.
2. Buka test yang menyebut fungsi itu.
3. Baca fungsi publik lebih dulu, lalu fungsi privat satu per satu.
4. Catat tiga hal: input, output, dan kondisi gagal.
5. Jika menemukan angka tanpa asal, beri catatan `UNVERIFIED` di catatan belajar Anda.
   Jangan langsung mengubah kode produksi.

## 8. Latihan

1. Jalankan `samples/01-hello-android` dan pasang APK debug-nya.
2. Ubah teks tombol, build ulang, dan pastikan perubahan terlihat.
3. Jalankan `gradle --offline test` di root repository dan catat jumlah test.
4. Buat branch `belajar/nama-anda`, tambahkan satu catatan belajar, lalu commit.
5. Bandingkan `git diff` sebelum dan sesudah commit.

Setelah nyaman dengan alur ini, lanjut ke [02 — Membangun APK](02-membangun-apk.md).
