# STATUS REKONSTRUKSI

Dokumen ini menjawab tiga pertanyaan terpisah untuk setiap modul:

1. **Asal source** — apakah berkas berasal dari proyek pemilik, diubah, atau ditulis ulang.
2. **Tingkat bukti protokol** — seberapa kuat elemen komunikasi dibuktikan oleh bytecode, referensi, atau hanya hipotesis.
3. **Status pengujian** — apakah perilaku diuji dengan unit test, dan apakah pernah diuji pada ECU fisik.

Ketiganya tidak boleh digabung. Source berstatus `ORIGINAL` tidak otomatis berarti protokolnya terbukti pada kendaraan, dan test yang lulus tidak membuktikan kompatibilitas hardware.

## 1. Definisi label

| Label | Arti |
|---|---|
| `ORIGINAL` | Source proyek pemilik, tidak diubah |
| `RECONSTRUCTED` | Ditulis ulang atau disesuaikan berdasarkan bytecode, resource, perilaku, atau referensi |
| `REFERENCE` | Materi contoh/penjelas, bukan rekaman ECU |
| `UNVERIFIED` | Belum dapat dipastikan |
| `BINARY_EVIDENCE` | Terbukti langsung dari bytecode/konstanta di binary referensi |
| `XREF_MAPPED` | Modul/fungsi ditemukan dan dipetakan, tetapi isi frame belum terbukti |
| `HYPOTHESIS` | Dugaan yang belum didukung bukti kuat |
| `UNRESOLVED` | Belum ada bukti; sengaja tidak diisi |

## 2. Matriks bukti elemen komunikasi

Lokasi bukti biner dan identitas referensi tidak dipublikasikan. Tabel ini hanya mencatat status, ringkasan bukti, dan batasnya.

| Elemen | Status bukti | Bukti | Batas |
|---|---|---|---|
| Checksum aditif 8-bit komplemen dua | `BINARY_EVIDENCE` | Rutin menjumlah byte lalu `neg` 8-bit; finalizer menulis byte terakhir | Tidak membuktikan ECU menerima semua frame |
| Batas body frame 254 byte (`0xFE`) | `BINARY_EVIDENCE` | Guard panjang dan string error di sekitar konstanta | Batas implementasi host, bukan batas ECU |
| Pemilihan header request `0x7D`/`0x7B` | `BINARY_EVIDENCE` | Helper memilih header dari flag lalu menambahkan `0x01 0x03` | Bukan katalog lengkap semua request |
| Fallback fast-init ke 10400 bps | `BINARY_EVIDENCE` | Perbandingan state dan konstanta `0x28A0` sebelum probe ulang | Toleransi timing ECU tidak diukur |
| Wake pulse LOW 70 ms / HIGH 130 ms | `RECONSTRUCTED` | Dipertahankan dari baseline RevCore; timing host-side | Referensi tidak memuat pasangan konstanta 70/130 |
| Probe sesi H99B generik | `XREF_MAPPED` | Modul probe sesi KWP bertahap 0..3, reply minimal 4 byte, payload pertama `0x07` | Byte request sesi belum terbukti |
| Modul Keihin KWP-over-K-Line | `XREF_MAPPED` | Modul terpisah ditemukan | Frame dan key sesi belum direkonstruksi |
| Implementasi Shindengen | `HYPOTHESIS` | Enum family ada; tidak ada modul khusus Shindengen | Profil sengaja kosong |
| FTDI bulk transfer/claim | `BINARY_EVIDENCE` | Signature JNI `bulkTransfer(UsbEndpoint, byte[], int, int)` dan penemuan endpoint bulk | Tidak membuktikan semua adapter/ chipset |
| Katalog request PGM-FI baseline (`0x72`) | `RECONSTRUCTED` | Berasal dari baseline RevCore, bukan literal di referensi eksternal | Probe family-neutral, belum diuji ECU |
| Frame identifikasi/live/DTC Shindengen | `UNRESOLVED` | Tidak ada bukti | Tidak ada frame yang dikarang |
| Frame Keihin spesifik dari referensi | `UNRESOLVED` | Tidak ditemukan frame Honda literal | Modul ada, byte belum |

Sumber registry: `core/src/main/java/com/revcore/android/core/comm/Evidence.kt`. Registry ini ikut dibangun dan dapat dibaca aplikasi.

## 3. Status per kelompok modul

| Kelompok | Source | Bukti protokol | Test | Hardware |
|---|---|---|---|---|
| Framing dan checksum (`HondaFrameCodec`, `HondaChecksum`) | `ORIGINAL` (`HondaChecksum`), `RECONSTRUCTED` (`HondaFrameCodec`, komentar dianonimkan) | `BINARY_EVIDENCE` untuk checksum dan batas panjang | Lulus | Belum |
| Wire engine K-Line (`KlineWireEngine`) | `ORIGINAL` | Campuran `BINARY_EVIDENCE` dan `RECONSTRUCTED` | Lulus | Belum |
| Sesi dan single-flight (`EcuSessionManager`, `SingleFlightGate`) | `ORIGINAL` | Perilaku host-side | Lulus | Belum |
| Transport USB serial (`UsbSerialLibraryTransport`) | `ORIGINAL` | API library pihak ketiga + logika host | Lulus (tanpa perangkat) | Belum |
| Driver FTDI native (`FtdiSerialDriver`) | `ORIGINAL` | `BINARY_EVIDENCE` untuk jalur transfer | Lulus (tanpa perangkat) | Belum |
| Protocol engine baseline (`HondaProtocolEngine`) | `RECONSTRUCTED` | `RECONSTRUCTED` untuk katalog request; probe Live Data dihapus | Lulus | Belum |
| Identifikasi ECU (`EcuIdCanonicalizer`, `EcmDatabase`) | `ORIGINAL` kecuali `EcmDatabase` `RECONSTRUCTED` | Aturan exact/partial dapat diuji; dataset publik sintetis | Lulus | Belum |
| DTC (`HondaDtcDatabase`) | `ORIGINAL` | 15 kode referensi; parser diuji | Lulus | Belum |
| Live Data (`LiveDataProfile`, `HondaLiveDataProfiles`) | `RECONSTRUCTED` | Parser generik dan contoh sintetis `0x7A` (`REFERENCE`); katalog bawaan kosong | Lulus | Belum |
| Log dan sniffer (`CommunicationSniffer`) | `ORIGINAL` | Perilaku host-side | Lulus | Belum |
| Profil Keihin KWP / Shindengen / Denso / Keihin H99B | `RECONSTRUCTED` (`HondaEcuProfiles`) | `UNRESOLVED`/`HYPOTHESIS`; katalog kosong | Test memastikan katalog kosong tidak mengirim frame | Belum |
| Legacy engine | `RECONSTRUCTED` | Referensi rollback; query Live Data dihapus | Tidak dipakai jalur utama | Belum |

## 4. Yang benar-benar terbukti

- Struktur frame, checksum, batas panjang, dan pemilihan header tertentu didukung bytecode referensi.
- Perilaku host (single-flight, timeout, retry, echo filter, state mesin) diuji dengan unit test dan fixture sintetis.
- Identitas ECU tidak pernah dipilih dari hasil ambigu atau terpotong; aturan ini diuji.
- Live Data tidak menurunkan AFR di host; AFR dilaporkan `UNSUPPORTED`.
- Pembacaan DTC yang gagal tidak disajikan sebagai daftar kosong; clear DTC memerlukan verifikasi ulang.
- Katalog family yang belum terbukti kosong, sehingga tidak ada frame spekulatif yang dikirim.

## 5. Yang belum terbukti

| Hal | Status | Alasan |
|---|---|---|
| Kompatibilitas dengan ECU fisik | `UNVERIFIED` | Tidak ada hardware dalam pengujian repository |
| Toleransi timing wake/init pada ECU nyata | `UNVERIFIED` | Hanya simulasi dan referensi host |
| Byte request untuk Keihin KWP, Shindengen, Denso, Keihin H99B | `UNRESOLVED` | Frame tidak ditemukan/dibuktikan |
| Tabel Live Data hasil rekonstruksi | `RECONSTRUCTED` untuk layout; `OWNER_REPORTED`/`UNVERIFIED` untuk respons ECU | Dipublikasikan di `data/live_data/`; belum ada capture perangkat keras independen |
| Dukungan semua adapter USB | `UNVERIFIED` | Diuji tanpa perangkat; bergantung library dan chipset |
| Perilaku pada ignition cycle dan tegangan rendah | `UNVERIFIED` | Tidak diuji di kendaraan |
| Layout tabel Live Data dari program lain | Ditahan | Bukan hasil RevCore; hanya tabel rekonstruksi sendiri yang dipublikasikan |

## 6. Batas klaim

- Repository ini tidak boleh disebut "kompatibel" atau "teruji" pada kendaraan tertentu.
- Test yang lulus hanya membuktikan perilaku kode terhadap fixture sintetis.
- Keberadaan class di APK membuktikan class itu ada pada build tersebut, bukan bahwa komunikasinya berhasil pada ECU.
- Perubahan status harus disertai bukti baru; status tidak boleh dinaikkan karena source "terlihat benar".

## 7. Riwayat verifikasi terakhir

| Pemeriksaan | Hasil | Tanggal |
|---|---|---|
| `gradle --offline clean test` | 153 test, 0 gagal, 0 error, 0 dilewati | 2026-11-17 |
| `gradle --offline assembleDebug` | AAR debug terbangun | 2026-11-17 |
| Validator fixture | 14 frame lulus | 2026-11-17 |
| Validator dataset | 5 pemeriksaan lulus; demo bilinear 1,34 | 2026-11-17 |
| Uji hardware ECU | Tidak dilakukan | — |
| Uji signing/instalasi aplikasi | Di luar cakupan repository | — |
