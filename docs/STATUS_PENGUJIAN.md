# STATUS PENGUJIAN

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini mencatat status yang **dilaporkan pemilik** pada 2026-11-19. Status ini bukan hasil pengujian repository dan tidak boleh dinaikkan menjadi `VERIFIED` tanpa bukti pengujian yang sesuai.

## 1. Status fungsi aplikasi

| Fungsi | Status | Catatan |
|---|---|---|
| Dashboard | Berfungsi (dilaporkan pemilik) | Tidak ada bug yang dilaporkan |
| Live Data | Berfungsi (dilaporkan pemilik) | Menampilkan RPM, TPS, EOT, Battery Voltage, Ignition Advance, Injector Pulse, Vehicle Speed, dan O2 |
| TPS | Bug menunggu perbaikan | Pada throttle tertutup, nilai tidak mencapai 0% |
| Diagnostic | Berfungsi (dilaporkan pemilik) | Tidak ada bug yang diketahui |
| Tuning | Dalam pengembangan | Belum dinyatakan selesai |
| Settings | Tidak ada masalah dilaporkan | Bukan bukti bahwa seluruh konfigurasi teruji |
| Communication Log | Berfungsi (dilaporkan pemilik) | Tidak ada bug yang dilaporkan |

### 1.1 Prioritas perbaikan yang tercatat

| Item | Status | Bukti |
|---|---|---|
| Adapter terputus sesekali | `DALAM ANALISIS` | Prioritas pemilik; belum direproduksi dalam repository |
| Layar tidak selalu tetap menyala | `DALAM ANALISIS` | Prioritas pemilik; belum diverifikasi |
| TPS tidak 0% saat throttle tertutup | `BUG_REPORTED` | Laporan pemilik + audit formula |
| Konsistensi skala Battery/Injector | `BUG_REPORTED` (dokumentasi) | Dua skala berbeda tercatat |
| Analisis dan perbaikan Live Data | `DALAM ANALISIS` | 8 parameter tampil; tabel lain belum diuji |
| Parser DTC E5/E6 | `BUG_REPORTED` | Audit source; belum diperbaiki |
| Subkode DTC > 15 | `BUG_REPORTED` | Audit source; belum diperbaiki |

## 2. Riwayat status

- Status awal di atas dicatat sebagai laporan pemilik, bukan sebagai hasil verifikasi independen.
- Jika bug ditemukan kemudian, status awal tetap dipertahankan sebagai riwayat dan status baru dicatat terpisah.
- `berfungsi` berarti fungsi utama dapat dipakai menurut laporan pemilik. Arti itu tidak sama dengan `bebas bug`.
- `dalam pengembangan` berarti fitur belum selesai. Arti itu tidak sama dengan `gagal`.
- `tidak ada masalah dilaporkan` berarti belum ada laporan masalah. Arti itu tidak sama dengan `teruji penuh`.

## 3. Batas bukti

| Aspek | Status |
|---|---|
| Pengujian unit repository | Lulus untuk fixture sintetis |
| Pengujian APK pada perangkat | Dilaporkan pemilik; bukan verifikasi repository |
| Pengujian pada ECU fisik | Tidak dilakukan dalam repository |
| Pengujian pada kendaraan | Tidak dilakukan dalam repository |
| Kecocokan kendaraan | `UNKNOWN`; tidak ada klaim dukungan |

## 4. Kosakata status

Gunakan istilah berikut secara konsisten:

- `dilaporkan pemilik`
- `diuji perangkat lunak`
- `diamati pada hardware`
- `diuji pada kendaraan`
- `dalam pengembangan`
- `dalam analisis`
- `belum diuji`
- `tidak diketahui`
- `gagal`
- `bug menunggu perbaikan`

Jangan menyamakan `belum diuji` dengan `gagal`. Jangan menyamakan `dilaporkan pemilik` dengan `VERIFIED`.
