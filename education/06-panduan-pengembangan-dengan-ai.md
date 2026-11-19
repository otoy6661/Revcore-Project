# 06 — Panduan Pengembangan dengan AI

AI dapat mempercepat membaca source, menulis test, dan menjelaskan error. AI juga dapat
menghasilkan kode yang terlihat benar tetapi salah, atau mengklaim pengujian yang tidak
pernah dijalankan. Panduan ini menjaga agar AI menjadi alat bantu, bukan sumber
kebenaran.

## 1. Tiga aturan dasar

1. **Jangan menyerahkan keputusan.** Perubahan perilaku produksi, identitas, lisensi, dan
   distribusi tetap milik pemilik.
2. **Jangan memberi rahasia.** Jangan tempel keystore, kata sandi, token, data pribadi,
   atau materi proprietary ke chat AI atau layanan eksternal.
3. **Jangan percaya klaim tanpa bukti.** Minta file, baris, perintah, dan output. Jalankan
   sendiri test dan build.

AI tidak boleh diminta mengubah Application Layer, komponen keamanan internal, package
name, nama UI, atau kredit pemilik. Materi itu berada di luar cakupan repository.

## 2. Alur kerja yang aman

```text
1. Jelaskan tujuan dan batas
2. Minta diagnosis tanpa perubahan kode
3. Minta patch minimal
4. Baca diff baris demi baris
5. Jalankan test/build/validator
6. Periksa dampak keamanan dan data
7. Commit dengan pesan yang menjelaskan mengapa
8. Simpan riwayat Git sebagai backup
```

Jangan melompat dari langkah 1 ke "terapkan semua". Patch besar yang tidak dipahami
adalah utang, bukan kemajuan.

## 3. Prompt siap pakai

### 3.1 Diagnosis tanpa mengubah kode

```text
Analisis masalah berikut. Jangan mengubah source code.

Gejala:
<tempel gejala dan pesan error>

Konteks:
- Repository: RevCore public developer repo
- Modul: <core/comm | core/protocol | core/usb | ...>
- File yang dicurigai: <path>
- Yang sudah dicoba: <daftar>

Tugas:
1. Sebutkan kemungkinan penyebab, diurutkan dari yang paling didukung bukti.
2. Untuk setiap penyebab, sebutkan file dan baris yang mendukung.
3. Bedakan fakta dari dugaan.
4. Usulkan langkah diagnosis berikutnya, bukan solusi yang belum terbukti.
```

### 3.2 Menjelaskan kode

```text
Jelaskan fungsi <nama fungsi> di <path>:<baris>.

Untuk setiap langkah:
- input dan output,
- kondisi gagal,
- efek samping,
- asal setiap angka atau frame (dokumen/test/source).

Jika asal tidak ditemukan, tulis "UNVERIFIED". Jangan mengarang asal.
```

### 3.3 Patch minimal

```text
Usulkan patch sekecil mungkin untuk <tujuan>.

Batasan:
- Jangan mengubah perilaku di luar tujuan.
- Jangan mengubah identitas, package name, UI, atau komponen keamanan internal.
- Jangan melemahkan assertion atau menonaktifkan validasi.
- Sertakan diff dan alasan setiap baris.
- Sebutkan test yang harus ditambahkan.
- Jika tujuan memerlukan perubahan perilaku produksi, hentikan dan jelaskan mengapa
  persetujuan pemilik diperlukan.
```

### 3.4 Review diff

```text
Tinjau diff berikut sebagai reviewer yang skeptis.

Cari:
1. Perubahan perilaku yang tidak diminta.
2. Data palsu, nilai cadangan, atau klaim tanpa bukti.
3. Penanganan error yang menelan kegagalan.
4. Test yang melemah atau tidak menguji jalur gagal.
5. Rahasia, path pribadi, atau data kendaraan nyata.
6. Dampak ke kompatibilitas dan keamanan.

Untuk setiap temuan, beri file, baris, alasan, dan tingkat keyakinan.
```

### 3.5 Menulis test

```text
Tulis unit test untuk <perilaku> di <path>.

Syarat:
- Gunakan data sintetis.
- Uji jalur sukses, jalur gagal, dan batas.
- Jangan mengubah kode produksi agar test lulus.
- Jangan melemahkan assertion yang sudah ada.
- Jelaskan mengapa setiap kasus penting.
```

### 3.6 Mencari asal frame, offset, atau konstanta

```text
Cari asal <nilai/frame/offset> di repository.

Jawab dengan:
- file dan baris,
- dokumen yang menjelaskannya,
- test yang memakainya,
- label bukti (Synthetic/Reconstructed/Source-analyzed/Hardware-observed/
  Vehicle-tested/Verified).

Jika tidak ada asal yang jelas, katakan "tidak ditemukan". Jangan menebak.
```

### 3.7 Memperbarui dokumentasi

```text
Sesuaikan dokumentasi berikut dengan source saat ini: <path>.

Aturan:
- Jangan mengubah klaim menjadi lebih kuat dari bukti.
- Pertahankan label bukti.
- Tandai bagian yang belum diverifikasi.
- Jangan menambah nama file internal, mekanisme keamanan, atau data rahasia.
- Tunjukkan bagian yang berubah dan alasannya.
```

### 3.8 Belajar, bukan disuapi

```text
Saya ingin memahami <topik>, bukan hanya mendapat jawaban.

Buat:
1. Penjelasan singkat dengan analogi.
2. Satu contoh kecil yang dapat saya jalankan.
3. Tiga pertanyaan untuk menguji pemahaman saya.
4. Kesalahan umum dan cara mengenalinya.

Jangan menulis kode produksi untuk saya.
```

## 4. Disiplin review

Sebelum menerima perubahan dari AI:

- [ ] Saya memahami setiap baris yang berubah.
- [ ] Diff tidak menyentuh file di luar tujuan.
- [ ] Tidak ada identitas, package name, UI, atau komponen keamanan internal yang
      berubah.
- [ ] Tidak ada nilai palsu, fallback diam-diam, atau error yang ditelan.
- [ ] Test baru menguji jalur gagal, bukan hanya jalur sukses.
- [ ] `gradle --offline clean test` dijalankan dan hasilnya dibaca.
- [ ] Validator dijalankan bila data berubah.
- [ ] Tidak ada rahasia, path pribadi, atau data kendaraan nyata.
- [ ] Commit dipisahkan per tujuan dan pesannya menjelaskan mengapa.
- [ ] Branch backup tersedia bila perubahan besar.

## 5. Fakta, dugaan, dan klaim

| Jenis | Ciri | Cara memperlakukan |
|---|---|---|
| Fakta source | Ada file, baris, dan kode yang dapat dibuka | Boleh dipakai, tetap sebut lokasi |
| Fakta test | Ada test dan output yang dijalankan | Sebut perintah dan hasil |
| Rekonstruksi | Ada analisis, tetapi bukan source asli | Label Reconstructed |
| Dugaan | "Sepertinya", "mungkin", tanpa bukti | Jangan jadikan dasar perubahan |
| Karangan | Angka/frame tanpa asal | Tolak |

AI sering mencampur keempatnya dalam satu paragraf. Tugas Anda adalah memisahkannya.

## 6. Contoh respons yang baik dan buruk

**Buruk:**

> "Frame ini pasti bekerja di semua Honda. Saya sudah memperbaiki checksum dan
> menambahkan offset AFR 14.7."

Masalah: klaim kompatibilitas tanpa bukti, AFR dikarang, "memperbaiki" checksum.

**Baik:**

> "Frame `72 05 74 01 14` muncul di `docs/DTC_ENGINE.md` sebagai kandidat query.
> Checksum-nya valid menurut `HondaChecksum`. Ini Source-analyzed, bukan bukti hardware.
> Saya tidak menemukan bukti bahwa frame ini berlaku untuk family selain baseline."

## 7. Batas

- AI tidak dapat menguji perangkat keras untuk Anda.
- AI tidak mengetahui kondisi kendaraan, adapter, atau wiring Anda.
- AI dapat menghasilkan API yang tidak ada. Periksa signature di source.
- AI dapat "memperbaiki" test agar lulus. Bandingkan assertion sebelum dan sesudah.
- AI tidak berwenang memberi izin distribusi atau mengubah lisensi.

Gunakan AI untuk mempercepat pemahaman dan pekerjaan mekanis. Keputusan akhir tetap
berada pada manusia yang membaca bukti.
