# AUDIT KEAMANAN

Dokumen ini mencatat pemeriksaan keamanan dan kebersihan materi pada staging repository. Isinya **hanya** pernyataan cakupan dan hasil pemeriksaan. Mekanisme, konfigurasi, format data, kunci, dan detail implementasi Security/Integrity tidak dijelaskan di sini.

## 1. Cakupan

| Area | Termasuk audit | Dipublikasikan |
|---|---|---|
| Communication Engine, USB/serial, K-Line, Protocol Engine, DTC, Live Data | Ya | Ya |
| Application Layer (UI, navigasi, state, lifecycle aplikasi) | Hanya batas integrasi | Tidak |
| Komponen keamanan internal | Hanya batas integrasi dan pemeriksaan kebocoran | Tidak |
| Database kendaraan, profil ECM, inventaris MAP, tabel Live Data hasil pengembangan | Ya | Ya; berkas mentah pihak lain tidak |
| Berkas MAP mentah, firmware, BIN/ROM pihak lain, kunci kontainer | Hanya pemeriksaan ketidakhadiran | Tidak |
| Kredensial, keystore, token, sertifikat signing | Hanya pemeriksaan ketidakhadiran | Tidak |

Application Layer dan Security/Integrity dianalisis secara internal seperlunya, tetapi source, diagram, catatan implementasi, dan dokumentasi khususnya berada di luar repository ini. Catatan internal disimpan di direktori kerja privat yang terpisah.

## 2. Kunci dan proteksi

- APK referensi tidak dienkripsi. DEX bermagic `dex\n038`, manifest dan aset dapat dibaca, dan tidak ada packer atau loader yang menyembunyikan bytecode.
- Tidak ada key yang diperlukan. Tidak ada key yang diminta, diterima, dicetak, disimpan, atau dikirim.
- Tidak ada upaya menembus proteksi pihak ketiga. Yang dilakukan adalah pembacaan artefak yang diberikan owner dan korelasi dengan source proyek.
- Jika di masa depan analisis benar-benar memerlukan kunci, prosedurnya: hentikan pekerjaan, minta owner memasukkan kunci melalui input lokal aman di Qoder, jangan pernah mencetak atau menyimpan kunci, jangan memasukkannya ke source/repository/ZIP/commit, dan jangan mengirimkannya ke layanan eksternal. Kunci tidak menjadi izin untuk mengambil materi proprietary pihak ketiga.

## 3. Pemeriksaan kebocoran

Pemeriksaan dijalankan pada seluruh berkas repository, tidak termasuk direktori cache/build yang di-ignore.

| Pemeriksaan | Metode | Hasil |
|---|---|---|
| Private key, keystore, sertifikat | Pola `BEGIN ... PRIVATE KEY`, `keystore`, `.jks`, `.p12`, `.pfx`, `.pepk` | Tidak ada nilai rahasia; hanya teks larangan di dokumentasi |
| Password, token, API key, secret | Pola `password=`, `token`, `api_key`, `secret=` | Tidak ada kredensial; hanya teks larangan |
| Path pengguna | Pola `C:\Users`, `/Users/` | Tidak ada |
| Package terlarang | Package internal Application Layer dan Security | Tidak ada source; hanya pernyataan batas di dokumen |
| Artefak biner | `.apk`, `.aab`, `.aar`, `.jar`, `.map`, `.so`, `.dex`, `.bin` | Tidak ada di luar cache/build |
| Database kendaraan | Isi `ecm_database.json` | Hanya `SAMPLE-ECM-001/002/003` |
| Data pribadi | Nama, nomor telepon, email, ID perangkat | Tidak ada |
| Akses jaringan | `java.net`, socket, HTTP client | Tidak ada |
| API identitas perangkat | IMEI, Android ID, subscriber ID, serial perangkat | Tidak ada; `deviceId` yang muncul adalah ID USB |
| APK/analisis diunggah | Riwayat pekerjaan | Tidak ada unggahan ke layanan eksternal |

Pola pencarian yang dipakai:

```bash
grep -rniE "BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|keystore|password[[:space:]]*=|api[_-]?key|bearer |secret[[:space:]]*=" \
  --include="*.kt" --include="*.md" --include="*.json" --include="*.kts" --include="*.py" \
  --exclude-dir=build .

grep -rn "C:\\\\Users\|/Users/" --include="*.kt" --include="*.md" --include="*.json" --exclude-dir=build .

grep -rn "com\.revcore\.android\.\(security\|ui\|tuning\|storage\)" \
  --include="*.kt" --include="*.md" --include="*.kts" --exclude-dir=build .
```

Hasil yang diharapkan dan diperoleh: tidak ada nilai rahasia, tidak ada path pengguna, dan tidak ada source package terlarang.

## 4. Batas dependensi

- `:core` hanya mengimpor `android.*`, `com.hoho.android.usbserial.*`, `kotlinx.coroutines.*`, `org.json.*`, `java.*`, dan package `com.revcore.android.core.*` / `data.ecu`.
- Tidak ada import ke package Application Layer atau Security/Integrity.
- `EcmDatabase.kt` tidak lagi memanggil hook Security/Integrity; pustaka dapat berdiri sendiri.
- Tidak ada akses jaringan dari `:core`.
- Dependensi diambil dari Maven/JitPack dengan versi tetap, bukan rentang dinamis.

## 5. Materi pihak ketiga

| Materi | Status | Penanganan |
|---|---|---|
| APK dan isinya | Tidak disalin | Hanya dianalisis lokal |
| Library protokol native pihak ketiga | Tidak disertakan | Alamat/konstanta hasil analisis ditandai `RECONSTRUCTED`/`REFERENCE` |
| Profil ECM hasil rekonstruksi RevCore | Dipublikasikan | `data/vehicle_database/ecm_profiles.json`; tanpa resource mentah |
| Berkas mentah 162 `.map` | Tidak disertakan | Salinan aset pihak lain; inventaris, hash, dan analisis dipublikasikan |
| Kunci dan IV kontainer MAP | Tidak dipublikasikan | Mekanisme kripto internal |
| Tabel Live Data hasil rekonstruksi RevCore | Dipublikasikan | `data/live_data/live_tables.json`; layout program lain tidak |
| `ecm_database.json` bawaan APK | Tidak disalin | Build memakai data sintetis; database kompilasi dipublikasikan di `data/` |
| `mapping.txt`, `seeds.txt` | Tidak disertakan | Memuat nama internal |
| Teks lisensi pihak ketiga | Tidak disalin | Kewajiban notice dicatat di [DEPENDENCY_AUDIT.md](DEPENDENCY_AUDIT.md) |

## 6. Risiko residual

| Risiko | Tingkat | Tindakan |
|---|---|---|
| Layout Live Data dari program lain | Rendah | Hanya tabel hasil rekonstruksi RevCore; layout asing tidak disertakan |
| Alamat binary dan nama modul di `Evidence.kt` | Rendah–menengah | Pemilik memutuskan apakah alamat perlu disanitasi |
| Ketergantungan pada JitPack untuk `usb-serial-for-android` | Rendah | Versi dikunci; pemilik dapat menyimpan artefak |
| Log komunikasi dapat memuat data adapter/frame | Rendah | Jangan mengekspor log mentah tanpa peninjauan; tidak ada data pribadi yang dikumpulkan kode |
| Cache `.gradle/` dan `core/build/` ada di mesin lokal | Rendah | Di-ignore; wajib dikecualikan dari ZIP final |
| Belum ada uji hardware/ECU | Tinggi untuk klaim kompatibilitas | Jangan mengklaim kompatibilitas; dokumentasi menyatakan `UNVERIFIED` |

## 7. Kewajiban pengguna dan kontributor

- Jangan menambahkan keystore, private key, token, password, atau kredensial ke issue, test, contoh, log, atau commit.
- Jangan mengunggah APK atau materi analisis ke layanan eksternal.
- Jangan menambahkan berkas MAP/firmware/BIN mentah milik pihak lain tanpa hak yang terbukti.
- Laporkan dugaan kebocoran atau pelanggaran hak kepada pemilik dan jangan mempublikasikannya.
- Hasil analisis, database, profil, inventaris, dan tabel Live Data hasil pengembangan RevCore boleh dipublikasikan sesuai Work Order 19 November 2026.

## 8. Kesimpulan

Tidak ditemukan kredensial, path pribadi, source Application/Security, atau artefak terlarang di repository. Dataset hasil pengembangan RevCore ada di `data/`; layout tabel dari program lain, berkas mentah pihak lain, dan kunci kontainer tidak disertakan. Satu hal masih memerlukan keputusan pemilik: sanitasi alamat binary dan nama modul di `Evidence.kt`. Revisi 2026-11-19 belum di-commit, belum di-ZIP, dan belum di-push sampai instruksi eksekusi pemilik.
