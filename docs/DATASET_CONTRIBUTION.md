# KONTRIBUSI DATASET

Dokumen ini menjelaskan cara menambahkan data kendaraan, profil ECU, profil Live Data, dan
MAP ke repository tanpa melanggar hak pihak ketiga dan tanpa mengarang bukti.

## 1. Prinsip

- Kontribusi harus berasal dari data yang Anda miliki atau yang boleh Anda distribusikan.
- Jangan menyalin database, MAP, profil, atau firmware pihak ketiga, termasuk dengan
  mengganti nama field atau mengubah format.
- Jangan mengarang offset, frame, nilai RPM, atau hasil pengujian.
- Label bukti harus jujur: `VERIFIED`, `RECONSTRUCTED`, `REFERENCE`, `HYPOTHESIS`, atau
  `UNVERIFIED`.
- Data sintetis harus ditandai `SAMPLE-` dan `synthetic`.

## 2. Jenis kontribusi

| Kontribusi | Lokasi | Status minimum |
|---|---|---|
| Record kendaraan/ECM | `ecm_database.json` atau template | `SAMPLE-` untuk data sintetis |
| Profil identifikasi ECU | kode profil yang Anda buat | `RECONSTRUCTED` atau `VERIFIED` |
| Profil Live Data | `LiveTableProfile` milik Anda | `REFERENCE`/`RECONSTRUCTED`/`VERIFIED` |
| MAP sintetis | `examples/maps/` | `REFERENCE` |
| Catatan protokol | `docs/` atau issue | Sesuai bukti |

## 3. Kontribusi kendaraan/ECM

1. Salin `examples/vehicle_db/vehicle_template.json`.
2. Isi field wajib: `id`, `partNumber`, `bikeName`, `statusNotes`, `source`.
3. Gunakan `ecmId` kanonik 10/12 digit hex bila tersedia. Jangan memotong ID.
4. Isi offset hanya jika Anda memiliki bukti. Selain itu gunakan `"0x0"`.
5. Set `isVerifiedDiagnostic` dan `isVerifiedRemap` ke `false` kecuali ada uji hardware.
6. Jelaskan sumber dan batas bukti di `statusNotes`.
7. Jangan memasukkan VIN, nomor plat, nama pemilik, atau data pribadi.
8. Jalankan `py tools/validate_datasets.py --verbose`.

## 4. Kontribusi profil Live Data

1. Buat `LiveTableProfile` dengan request frame lengkap termasuk checksum.
2. Tentukan `payloadStart`, `minPayload`, dan `minFrameSize`.
3. Definisikan setiap field: offset, panjang, skala, bias, rentang, satuan.
4. Tandai `verification` sesuai bukti.
5. Tambahkan unit test dengan payload sintetis dan kasus negatif.
6. Jangan menambahkan profil ke katalog bawaan tanpa persetujuan pemilik dan bukti
   tangkapan perangkat keras.

## 5. Kontribusi MAP

1. Gunakan `examples/maps/synthetic_map.json` sebagai template.
2. Sumbu harus menaik ketat dan sesuai unit.
3. Nilai sel harus dalam satuan teknik, bukan raw tanpa penjelasan.
4. Jelaskan rumus skala di `notes` bila data berasal dari raw.
5. Jangan mengunggah `.map`, `.bin`, ROM, atau firmware.
6. Jalankan validator dan lampirkan hasilnya.

## 6. Materi yang dilarang

- Berkas MAP, BIN, ROM, atau firmware yang merupakan salinan aset pihak lain tanpa hak distribusi.
- Kunci, IV, dan mekanisme kripto kontainer MAP.
- Keystore, private key, token, password, atau API key.
- Data pribadi atau data kendaraan yang dapat diidentifikasi.
- Source Application Layer atau Security/Integrity.
- Layout tabel Live Data dari program lain.

Database, profil, inventaris MAP, tabel Live Data, rumus, dan temuan hasil pengembangan RevCore **boleh** dikontribusikan. Tandai status bukti dengan jujur; jangan menahan hasil analisis sendiri hanya karena hak pihak ketiga belum jelas.

## 7. Proses peninjauan

Setiap kontribusi ditinjau berdasarkan:

1. kebutuhan teknis;
2. keamanan dan privasi;
3. hak penggunaan dan redistribusi;
4. provenance dan kualitas bukti;
5. persetujuan pemilik.

Kontribusi dapat diterima, diterima dengan redaksi, ditolak, atau ditahan sampai bukti
tambahan tersedia. Tidak ada jaminan bahwa kontribusi diterima.

## 8. Format pengajuan

Sertakan:

- tujuan kontribusi;
- berkas yang diubah/ditambahkan;
- sumber data dan dasar hak penggunaan;
- metode verifikasi;
- hasil validator;
- hal yang belum terbukti.

Jangan melampirkan materi proprietary di issue publik. Jelaskan jenis materi dan tunggu
instruksi pemilik.
