# Security and Integrity — Publication Boundary

Dokumen ini **bukan** dokumentasi implementasi. Ia hanya mencatat batas publikasi yang
ditetapkan Addendum A dan F: Application Layer serta Security/Integrity dianalisis
secara internal, tetapi **tidak** dipublikasikan.

## 1. Status

| Bagian | Status publikasi | Isi yang tersedia di repository |
|---|---|---|
| Application Layer | **Dikecualikan** | Tidak ada |
| Security/Integrity | **Dikecualikan** | Tidak ada, selain pernyataan batas ini |

Repository ini tidak memuat source, detail implementasi, diagram, konstanta, alur
startup, nama berkas internal, atau dokumentasi khusus dari kedua bagian tersebut.

## 2. Mengapa dikecualikan

Kedua bagian tidak termasuk prioritas publikasi pada Addendum C. Publikasi difokuskan
pada komunikasi, adapter USB/serial, K-Line, Protocol Engine, parser/checksum,
identifikasi ECU, Diagnostic Engine, DTC, Live Data, model data, dan materi developer
yang mendukungnya. Kedalaman analisis internal tidak dikurangi; hanya hasil publikasinya
yang dibatasi.

## 3. Batas integrasi yang boleh diketahui

Informasi berikut adalah batas minimum yang diperlukan untuk memahami modul publik:

- Pustaka `:core` adalah pustaka Android mandiri. Ia tidak bergantung pada layer
  aplikasi maupun layer security/integrity.
- Arah dependensi adalah aplikasi pemanggil → `:core`, bukan sebaliknya.
- Aplikasi pemanggil bertanggung jawab atas izin USB, lifecycle proses, penyimpanan
  preferensi, dan kebijakan integritasnya sendiri.
- `:core` tidak melakukan pemeriksaan tanda tangan APK, tidak membaca trust record, dan
  tidak menangani penanda instalasi.
- Tidak ada API publik di `:core` yang ditujukan untuk memverifikasi atau mengubah
  status keamanan aplikasi.

Di luar pernyataan di atas, tidak ada informasi lain tentang bagian yang dikecualikan.

## 4. Penanganan kunci dan materi rahasia

- APK referensi tidak terenkripsi dan tidak memerlukan kunci untuk dianalisis; tidak ada
  kunci yang diminta, dicetak, disimpan, atau dikirim ke layanan eksternal.
- Repository tidak memuat keystore, private key, sertifikat signing, token, kata sandi,
  atau kredensial.
- Analisis tidak membongkar proteksi pihak ketiga dan tidak mengambil materi proprietary
  yang tidak berhak dipublikasikan.
- Bila di masa depan sebuah bagian memerlukan kunci, kunci tersebut harus dimasukkan
  melalui input lokal yang aman dan tidak boleh masuk ke source, laporan, ZIP, atau
  commit.

## 5. Catatan analisis internal

Catatan analisis untuk bagian yang dikecualikan disimpan di direktori kerja privat di
luar repository ini. Catatan tersebut tidak disalin ke staging, tidak disertakan dalam
ZIP, dan tidak dipublikasikan. Repository hanya memuat kesimpulan batas seperti dokumen
ini.

## 6. Materi pihak ketiga

Firmware, tabel MAP, profil pihak ketiga, database kendaraan lengkap, dan aset lain yang
hak redistribusinya belum terbukti tidak disertakan. Konstanta Live Data yang hak
penggunaannya belum jelas telah ditarik dari repository publik dan dari katalog bawaan
`:core`; hanya parser generik dan contoh sintetis yang tersisa. Lihat
[PUBLICATION_MANIFEST.md](../PUBLICATION_MANIFEST.md) bagian 6.

## 7. Jika Anda membutuhkan bagian yang dikecualikan

Bagian tersebut berada di luar cakupan repository ini dan tidak dilisensikan oleh
[NOTICE.md](../NOTICE.md). Permintaan akses, integrasi, atau distribusi harus diajukan
kepada pemilik proyek. Jangan menganggap keberadaan dokumen ini sebagai izin untuk
meminta, merekonstruksi, atau mendistribusikan implementasi yang dikecualikan.
