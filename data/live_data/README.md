# Tabel Live Data

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**

Berkas [`live_tables.json`](live_tables.json) memuat 7 tabel dan 84 definisi field Live Data hasil analisis RevCore.

- `payloadStart`: 4
- Urutan byte: big-endian
- Status layout: `RECONSTRUCTED`
- Pengujian kendaraan: tidak dilakukan
- Delapan parameter dilaporkan tampil oleh pemilik, bukan verifikasi independen

Penjelasan lengkap, tabel per tabel, rumus, contoh decoding, dan batas ada di [docs/LIVE_DATA_REFERENCE.md](../../docs/LIVE_DATA_REFERENCE.md).

## Catatan

- Tabel ini adalah referensi, bukan katalog yang otomatis dikirim oleh pustaka `:core`.
- Penerimaan tabel oleh ECU harus dibuktikan melalui transaksi wire.
- TPS belum dikalibrasi terhadap throttle tertutup.
- AFR tidak dihitung dari tegangan O2 narrowband.
- Field yang gagal tidak boleh ditampilkan sebagai nol.
