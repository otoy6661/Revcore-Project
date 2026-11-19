# 07 — Kontributor dan Kemandirian

Ada dua jalur yang sama sahnya:

1. **Berkontribusi ke repository RevCore** untuk memperbaiki atau memperluas materi
   publik.
2. **Membangun aplikasi sendiri** memakai pengetahuan dan pustaka yang tersedia, dengan
   identitas sendiri.

Keduanya memerlukan bukti, bukan klaim. Dokumen ini menjelaskan langkah praktisnya.

## 1. Fork, clone, dan branch

```bash
# Di GitHub: tekan Fork pada repository otoy6661/Revcore-Project
git clone https://github.com/<akun-anda>/Revcore-Project.git
cd Revcore-Project
git remote add upstream https://github.com/otoy6661/Revcore-Project.git
git switch -c kontribusi/deskripsi-singkat
```

Alur kerja:

```bash
git fetch upstream
git switch main
git merge --ff-only upstream/main
git switch kontribusi/deskripsi-singkat
# kerjakan perubahan
git diff
git add <berkas-yang-berubah>
git commit -m "Jelaskan mengapa perubahan ini perlu"
git push origin kontribusi/deskripsi-singkat
```

Jangan memakai `--force`, `reset --hard`, atau menghapus riwayat pada repository bersama.
Jangan mengubah kredit commit.

## 2. Melaporkan masalah

Format laporan yang berguna:

```text
Judul: <gejala singkat>

Versi/commit:
Modul dan fungsi:
Langkah reproduksi:
1.
2.
3.

Hasil yang diharapkan:
Hasil yang terjadi:
Log TX/RX (sudah disanitasi):
Adapter (chip, VID:PID):
Kendaraan/ECU (bila relevan):
Kondisi (kontak, mesin, tegangan):
Status pengujian: unit test / simulasi / hardware
```

Jangan menyertakan rahasia, data pribadi, atau data kendaraan yang dapat diidentifikasi.
Jangan menyebut hasil simulasi sebagai hasil hardware.

## 3. Pull request yang baik

Satu PR untuk satu tujuan. Sertakan:

- **Masalah:** apa yang salah dan mengapa penting.
- **Bukti:** file/baris, log, atau test yang menunjukkan masalah.
- **Perubahan:** apa yang diubah dan apa yang sengaja tidak diubah.
- **Test:** perintah dan hasil.
- **Risiko:** apa yang dapat rusak.
- **Batas:** bagian yang belum diverifikasi.
- **Lisensi:** asal data dan hak penggunaannya.

PR akan ditolak bila mengarang frame, mengklaim kompatibilitas tanpa bukti, memasukkan
materi proprietary, melemahkan test, atau menyentuh bagian yang dikecualikan.

## 4. Menambah sensor atau profil Live Data

1. **Kumpulkan bukti.** Documentation, tangkapan perangkat, atau referensi yang sah.
   Catat sumber dan tanggal.
2. **Tentukan field.** Nama sensor, offset relatif payload, panjang 1–2 byte, urutan
   byte, skala, bias, satuan, dan rentang valid.
3. **Buat `LiveTableProfile`.** Sertakan `requestFrame`, `payloadStart`, `minPayload`,
   `minFrameSize`, dan daftar `LiveFieldSpec`.
4. **Beri label.** `REFERENCE` atau `HYPOTHESIS` bila belum diuji. Jangan memakai
   `VERIFIED` tanpa pengujian.
5. **Tambahkan test sintetis.** Payload valid, payload pendek, field `0xFF`, nilai di
   luar rentang.
6. **Dokumentasikan batas.** Family, kendaraan, kondisi mesin, dan apa yang belum
   diketahui.
7. **Jangan memakai konstanta proprietary** yang hak penggunaannya belum jelas.

Pemanggil dapat menyediakan profil sendiri tanpa mengubah katalog bawaan. Ini cara yang
paling aman untuk bereksperimen.

## 5. Menambah parser atau family ECU

1. Tentukan format frame dan checksum dari bukti, bukan dari kebiasaan.
2. Jangan memakai header Honda bila protokolnya berbeda.
3. Implementasikan transport dan parser secara terpisah.
4. Tambahkan test: checksum valid/tidak valid, frame terpotong, echo, respons negatif,
   payload pendek, dan nilai batas.
5. Kembalikan state kegagalan yang jelas; jangan mengembalikan daftar kosong.
6. Tandai family yang belum terbukti dengan katalog kosong dan alasan tertulis.

## 6. Menambah dokumentasi

Dokumentasi yang baik:

- menyebut file dan fungsi yang dirujuk;
- membedakan fakta, rekonstruksi, dan dugaan;
- mencantumkan batas dan kondisi pengujian;
- tidak menambah nama berkas internal atau mekanisme yang dikecualikan;
- tidak mengklaim lebih dari bukti.

Jika dokumen dan source berbeda, perbaiki dokumen atau source berdasarkan bukti. Jangan
menafsirkan ulang source agar cocok dengan klaim.

## 7. Membangun aplikasi sendiri

Anda bebas membuat aplikasi diagnostik dengan:

- nama aplikasi sendiri;
- `applicationId` sendiri;
- UI, navigasi, dan arsitektur sendiri;
- target pengguna dan fitur sendiri;
- identitas visual sendiri.

Yang perlu diperhatikan:

1. **Jangan memakai identitas RevCore** untuk produk Anda, dan jangan menyiratkan
   dukungan atau afiliasi.
2. **Beri atribusi yang jujur** bila memakai pustaka atau pengetahuan dari repository,
   misalnya "Menggunakan RevCore Communication Core" dengan tautan.
3. **Jangan mengklaim sebagai pencipta RevCore** atau mengambil kredit Otoy.
4. **Hormati lisensi.** `NOTICE.md` menyatakan repository bukan proyek OSI, melarang
   klaim kepemilikan, penjualan ulang komersial tanpa izin, dan distribusi modifikasi
   tanpa izin pemilik. Untuk penggunaan pribadi dan belajar, ikuti ketentuan tersebut;
   untuk distribusi, minta izin pemilik.
5. **Jangan memakai materi yang tidak disertakan** (UI RevCore, Security/Integrity,
   katalog proprietary, firmware, MAP nyata).
6. **Jangan menambahkan operasi tulis** ke ECU tanpa prosedur keselamatan dan izin.

Kemandirian bukan berarti mengklaim karya orang lain. Kemandirian berarti Anda dapat
membangun, menguji, dan mempertanggungjawabkan aplikasi Anda sendiri.

## 8. Alur bukti pengujian hardware

Bila Anda menguji dengan perangkat nyata:

1. Catat tanggal, perangkat, versi Android, adapter, chip, VID:PID, kabel, dan wiring.
2. Catat kendaraan/ECU hanya bila Anda berhak dan tanpa data pribadi.
3. Simpan log TX/RX mentah; sanitasi sebelum dibagikan.
4. Bedakan `Hardware-observed` (komunikasi teramati) dari `Vehicle-tested` (diuji pada
   kendaraan yang disebut).
5. Jangan menaikkan label menjadi `Verified` tanpa bukti yang dapat diperiksa ulang.
6. Jangan mengunggah APK atau log ke layanan eksternal tanpa keputusan pemilik.

## 9. Yang akan ditolak

- Frame atau respons yang dikarang.
- Klaim kompatibilitas atau hasil uji tanpa bukti.
- Source Application Layer atau Security/Integrity.
- Dataset kendaraan, MAP, firmware, atau aset pihak ketiga tanpa izin.
- Kredensial dalam bentuk apa pun.
- Perubahan yang mematikan validasi atau menyembunyikan kegagalan.
- Test yang dilunakkan agar lulus.
- Perubahan identitas, pemilik, kredit, atau nama UI.

## 10. Checklist sebelum mengirim

- [ ] Perubahan sesuai tujuan dan tidak melebar.
- [ ] Test berjalan dan hasilnya dicatat.
- [ ] Validator dijalankan bila data berubah.
- [ ] Tidak ada rahasia atau data pribadi.
- [ ] Label bukti benar.
- [ ] Dokumentasi diperbarui.
- [ ] Diff dibaca ulang.
- [ ] Riwayat Git tidak ditulis ulang.
- [ ] Batas yang belum terselesaikan disebutkan.

Kontribusi yang jujur lebih berharga daripada kontribusi yang terlihat lengkap.
