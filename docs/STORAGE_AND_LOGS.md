# PENYIMPANAN DAN SESSION LOG

Dokumen ini menjelaskan dua utilitas pendukung: `RevCoreStorage` untuk layout berkas dan
`TuningLogStore` untuk log sesi dengan progress yang terukur.

## 1. RevCoreStorage

`com.revcore.android.core.storage.RevCoreStorage` membuat satu root yang terlihat pengguna
dan subfolder tetap:

```text
Internal Storage/Revcore/
├── Logs/
├── Backup/
├── Maps/
├── Exports/
├── Database/
└── Config/
```

### 1.1 Izin

| API | Kebutuhan |
|---|---|
| 26–29 | izin runtime `WRITE_EXTERNAL_STORAGE` |
| 30+ | akses khusus "semua file" (`MANAGE_EXTERNAL_STORAGE`) |

`ensure(context)` tidak pernah diam-diam beralih ke penyimpanan privat aplikasi. Hasilnya
salah satu dari:

- `Ready(root)` — semua folder ada dan dapat ditulis;
- `NeedsPermission(reason)` — pemanggil harus menampilkan alasan dan membuka pengaturan;
- `Failed(reason)` — folder tidak dapat dibuat atau ditulis.

`permissionIntent(context)` dan `openFolderIntent(folder)` membantu aplikasi membuka layar
sistem yang tepat. Pustaka `:core` tidak menampilkan UI sendiri.

### 1.2 Penamaan berkas

`sanitizeName` menghapus isi dalam tanda kurung, mengganti karakter non-alfanumerik dengan
`_`, dan mempertahankan huruf/angka. `uniqueFile` menambahkan sufiks angka dan tidak pernah
menimpa berkas yang sudah ada.

Contoh:

```kotlin
val name = RevCoreStorage.buildBackupFileName("Honda Beat", "1234567890")
// Honda_Beat_1234567890_Backup_YYYY-MM-DD.bin
```

## 2. TuningLogStore

`com.revcore.android.core.tuning.TuningLogStore` adalah log proses untuk operasi tuning.
Log ini terpisah dari `CommunicationSniffer`: trafik USB, Live Data, dan DTC tidak
dicampur ke log sesi.

### 2.1 Model event

Setiap event memiliki `category`, `stage`, `status`, `message`, dan opsional `detail`,
`errorCause`, serta `nextAction`. Status terminal tidak dapat ditulis ulang; terminal kedua
untuk operasi yang sama ditolak.

### 2.2 Progress

`TuningProgressMath.percent(completed, total)` mengembalikan `null` bila total tidak
diketahui. Tidak ada persentase palsu dari timer. Nilai 100 hanya dilaporkan bila
`completed >= total`.

```kotlin
val percent = TuningProgressMath.percent(512, 1024) // 50
val unknown = TuningProgressMath.percent(10, 0)     // null
```

### 2.3 Ekspor

`exportSessionText()` menghasilkan teks yang memuat event dan ringkasan progress. Riwayat
lengkap tetap disimpan di memori setelah tampilan dibersihkan; batas penyimpanan adalah
2.000 event dan 50 operasi terakhir.

## 3. Batas

- Log tidak membuktikan bahwa operasi ECU berhasil.
- `SUCCESS` hanya boleh dicatat setelah respons atau bukti yang sesuai.
- Jangan menulis kredensial, token, atau data pribadi ke log.
