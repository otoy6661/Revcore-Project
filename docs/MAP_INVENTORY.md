# INVENTARIS MAP

> **STATUS: DALAM TAHAP ANALISIS DAN PENGUJIAN**
>
> Tidak seluruh informasi telah dikonfirmasi melalui pengujian hardware. Informasi diperoleh dari beberapa sumber publik dan dikembangkan lebih lanjut melalui pengujian, analisis, serta eksperimen RevCore.

Dokumen ini mencatat inventaris dan hasil analisis 162 berkas MAP. Inventaris mesin dapat dibaca di [`data/map_library/inventory.csv`](../data/map_library/inventory.csv).

Berkas MAP mentah **tidak disertakan** karena merupakan salinan aset pihak lain. Yang dipublikasikan adalah hasil analisis RevCore: inventaris, ukuran, hash, struktur kontainer, pemetaan profil, dan status. Dokumen ini **bukan** klaim kecocokan kendaraan.

## 1. Ringkasan

| Item | Nilai |
|---|---:|
| Entri MAP | 162 |
| SHA-256 unik | 161 (satu duplikat) |
| Total ukuran | 35.195.840 byte |
| Ukuran 49.168 byte | 16 berkas |
| Ukuran 65.552 byte | 47 berkas |
| Ukuran 262.160 byte | 55 berkas |
| Ukuran 393.232 byte | 43 berkas |
| Ukuran 432 byte | 1 berkas (anomali; belum dikenali) |
| Berkas mentah disertakan | Tidak (salinan aset pihak lain) |
| Berkas MAP biner hasil karya RevCore | 0 |
| Tautan profil `READY` | 159 |
| Kandidat ganda | 2 |
| Tidak dikenali | 1 |

## 2. Metode dan batas

- Setiap berkas diberi nama aman `MAP-001` sampai `MAP-162`. Nomor diurutkan berdasarkan SHA-256, bukan berdasarkan nama, profil, atau asal berkas.
- Nama berkas asli dan nama program sumber tidak dipublikasikan.
- Format diidentifikasi sebagai kontainer terenkripsi yang berisi image flash ECU, bukan definisi peta.
- 162 berkas berasal dari satu arsip aset pihak lain yang diekstrak tanpa perubahan. Tidak ada berkas yang dibuat ulang atau disintesis oleh RevCore.
- Verifikasi 2026-11-19: seluruh 162 berkas dibandingkan byte-per-byte dengan arsip sumber dan salinan aset aplikasi — **162/162 identik**, 0 berbeda. Tidak ada berkas MAP biner yang memenuhi syarat sebagai hasil karya RevCore, sehingga tidak ada biner yang dilampirkan.
- MAP-093 tetap dicantumkan sebagai `UNRECOGNIZED`; hasil analisis tidak dihapus hanya karena formatnya belum dikenali.
- Karena itu berkas mentah dipisahkan dan tidak disertakan. Kunci dan IV kontainer tidak dipublikasikan.
- Hasil analisis tetap dipublikasikan: ukuran, hash, struktur, tautan profil, dan status.
- Kolom **Berkas mentah** bernilai **Tidak disertakan** untuk seluruh entri. Alasan utamanya adalah klasifikasi salinan aset pihak lain, bukan ketidakjelasan satu berkas.
- Hak distribusi ulang tetap merupakan keputusan pemilik proyek. Akses gratis tidak otomatis berarti izin redistribusi.

## 3. Daftar berkas

| ID aman | Ukuran (byte) | SHA-256 | Identifikasi format | Status analisis | Berkas mentah |
|---|---:|---|---|---|---|
| MAP-001 | 393.232 | `0157d417572a805fe7b1a6f6f084c09b3710774924af7345eb96d01beb23c8b2` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-002 | 49.168 | `0239f8fc56dd40c40d060cf5aa5bab8d6f5e354a9aaaf777ec48114188259817` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-003 | 262.160 | `05a93f181d8e15c19613353fca4e22e3458122a1080c2f331cb2f4a9ff8968c6` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-004 | 65.552 | `07cdc1db8c98a99f735f31d4f77ed6b16b984e50c40c940f843435dd25bb1acf` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-005 | 65.552 | `081b6c9e1fc3a7459b0a567c1130ded86742a91581bfa8768a2910a1e02a3ee3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-006 | 49.168 | `0839dcb722ddc0aee467f1d2fa8319951ec12f3c5bdce9ee6eeed21e88f7ee80` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-007 | 393.232 | `08894733cafeef4866b8c005d5a4b8ea70f90c7d3bd02363a289d83565f72590` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-008 | 393.232 | `096bb5ffc3c2d68b84a973a06786c182fcfedeea0be3111d587660895b332e78` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-009 | 65.552 | `0ac77b5a0676266704945da83515a1f8433ef6774ae17ff888b308269f93614f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-010 | 49.168 | `0ddd4e450d4131f6ab0ea08b2028fbe52fd419471b3a6303ca4b29528c45ceef` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-011 | 393.232 | `1272de7a7d7cd7f001f1c1f2dcf674ace9189ed6ee339e357afce519f0a488e1` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-012 | 65.552 | `15ce511800fc5a723143f786255bb228fe4c3d11a64030629700e7edc5068c61` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-013 | 65.552 | `15f5f0d70a427c4f2b70658f6f807f23f748549118e12a2461fe93d9dee55c9f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-014 | 262.160 | `165ab10ef7a30c13f2c0aa0d6361c4ad7b76601bd8cab1ba471ca70039bc1c89` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-015 | 262.160 | `17041ea98597fe4a65f5513b311556f419a1537e091cc8746404837b9d2e7d59` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-016 | 65.552 | `177dda98a575fb8db10ced8c3214614f2022f19855a60dd9387016bb2504e7ee` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-017 | 393.232 | `18dbb5f1d950303e289e619673116d208b10c9b85ceb8cd0730c6895e5357815` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-018 | 262.160 | `19f841f681b8bfe5065bb9beadd5db62251ccdfb1559864df010b8689043e649` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-019 | 262.160 | `1f11f933db9c3460ec252d304fbb8b36e481a2a08e194b77a90a5874383b50f6` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-020 | 393.232 | `1fb918a8ac6cdfd7b4403da3c37a377c0456f37b5d364184ff30c961cd764772` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-021 | 262.160 | `2066c06c67501d5b0efd7056e55ad790aa95795d87f64b1b186595c4f189f411` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-022 | 65.552 | `2075c80a05371c437767d6b62f025670e33cb3bbcc26e9cd2fddb18dbd87d441` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-023 | 65.552 | `20b8435012ac0d3b07557bfc8b1a13614e1055710b6f5944e6e280cdb37fcfdc` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-024 | 65.552 | `20f86c87b55c75807e0bd8f6a8b076b886455bc4e202ea7d2abcb6589d6915f9` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-025 | 65.552 | `22307f1bc619df71d820be06738766f18eeaa37ad095660e54f5e218ced1f0a3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-026 | 49.168 | `258a4d09d5748068b1159f5634acf44f4fece688aebeb07c4ef4352f014854f0` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-027 | 65.552 | `275250fc37a4392f6ba974ed847a2e56a641d034cff51a68c86331c7aa84318c` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-028 | 262.160 | `27ed696c1602ece9c33c67e75aac047e711a569acc90e512fceeb817fc80f59f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-029 | 65.552 | `28fec5d559a00678e40adb9cf4ac789af017a5c7b414cc92d55a3229cc1f1189` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-030 | 49.168 | `291876264c5f62b353d1e7349a8a3a8ad4045888cdb8bc35bcdde1e7cf0d428f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-031 | 393.232 | `2b9a2c6d2ad51d169b31b0cd07ea172dd47a2a05d6b01f75a90948a72e5fd70e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-032 | 262.160 | `2cb0c0a70b55e1f103c247af89221783b8fc67ade1d33898cdb554ef07d58e8c` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-033 | 65.552 | `2cc050d1402944093b7f4c9a50f8155750df888d47f908c4356762854223a285` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-034 | 262.160 | `2dc5b96f0f63ba3d592d3ee86bf2918423d5aa369950d1bda42eea549da2c7df` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-035 | 262.160 | `2e417fe0e328769a32a9330fe9735d8db73ef0ce8bd821d17864761fd4c8cac8` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-036 | 65.552 | `301b3f1b63a315b74938836dc0a03aea33405bb104572d64cb693baece98c94e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-037 | 65.552 | `3039dca527910ba48dafbe030932b16938c58dffc0d1665126a42dacb53af6cb` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-038 | 65.552 | `320a0249985e602ff73e7fc88541f0da67dd6f66cf132ebc061a42d5fb02a1ce` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-039 | 65.552 | `328d589bff33517a5c5ba44a1d1df9e40bcc8131a445a7b4bf80f92ccbc4ee62` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-040 | 262.160 | `33d30cc99d5ed1821f8654ae435c352561b81853a98e4546cbf91b528091a613` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-041 | 262.160 | `351449cc23ebdb960bacfa0eba94bd80b9b6771b603f7321d0cd8e1080fd6c69` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-042 | 393.232 | `36baa75f87e37a948c1894b88e625ea2ee9d030992836b6cc20781824e0d2b4d` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-043 | 393.232 | `3810e0abd4fad93480ca620be30c36de99f3dd725aa35df88b0847c186c69f99` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-044 | 393.232 | `39e0ea786e73293127676277ce4904b8c989c0f0d766cb30a19c16a6278bcfb9` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-045 | 65.552 | `3a09551a9084003cbf7ad524ac5227c4646912dcc5a380c74d72b4080bf5e35f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-046 | 262.160 | `3b88bbd6676bc581deb2fa9d2cd88e29d4693f2b353637c7d64f6c32d04887fb` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-047 | 262.160 | `3c1b1856b85323e698eaca12b75c6dfbb427bd49d25ca4c3fe36e6e25ba56b3e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-048 | 65.552 | `3d6253802c0de824c0d6e581655eea944f6566470d4f1c86999eb002db349727` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-049 | 49.168 | `3de6fdd1988581ec83113f470d118cdf3e98a96e45f42efe8e8cff992d39e950` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-050 | 65.552 | `3f97cbd391ed7932ef1b87858596bcdcd89adf59c9824ebf163a7f073980c40f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-051 | 262.160 | `40977f371281ef49d4627c723a0c1b82b4b1e6b43a249d701f7654da6b29bb2d` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-052 | 262.160 | `44308e5f6e635f46d55e1d2b5febc308ff0b88f39be3e78870fdb920c8f95a46` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-053 | 393.232 | `4519ec5ea115a66acb10d11a8a8e98cdba74f51541714f09d4528e6e6a264bd0` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-054 | 262.160 | `45df50fa408fb39bef0d524117be67612bb3c215e37930394ad3142afc79e03b` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-055 | 65.552 | `4654ccf7ab4a99481136da9808c63b352c8f3c26880f5e869f6cab3de4a224a0` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-056 | 65.552 | `485d8fffe1bdd8da0c0f00b96fa6642f78b1a59a06f7d2322c3b0b95b81a3de3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-057 | 262.160 | `4a32176237ee9b73bc559a43e0bb77351d72972bb1f3b3d741af2763e1bb44ab` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-058 | 393.232 | `5073d8554776e439a5dadef9de832263c2ef3358a0f6385401b4150f7470b1c8` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-059 | 262.160 | `527e09d6f97fd7d19f6212bafb24669e9a1134e0e5fadc1ec55065fed8fc8ed6` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-060 | 65.552 | `53c35f899e56c862fb0c0555c82247b00b729f634f3f044e2bbf9f0b07139a4a` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-061 | 393.232 | `54f92fcdd4e41cb1110ec230630e41f5e494b9bcca128f9a6396e4c7f9732157` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-062 | 65.552 | `5763ef795187a4c2ec33cbaf176e0d76a2d79a312f59fa02440aaf9c13c8c610` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-063 | 262.160 | `587ffe3f1602f0c39b6a5d3c3e0d9bc245130b3b3960401069717738a57a8457` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-064 | 49.168 | `5f481f70865b86f174b1f81c1ad63247947435d39b8372c1d948d1f5657f766f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-065 | 393.232 | `5f8656b05a3647c66238ef4a015287ea6861575551e5442c4d69bec1650dbb06` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-066 | 65.552 | `6041d18fa77667967a1ac6837c70668eb43c14483efd194dbae22d8566f3d587` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-067 | 393.232 | `61331204e67cd41e722b49f6373cbf180e7e985a7276110cf542d36da9f9b180` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-068 | 393.232 | `61417493e4db38b9b6c74e2351535dce809527cfea04deb4c5eac6601ef6ef2f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-069 | 393.232 | `656fd3560c79444e2f3e178c2145d3b8a6c2d7e092e19d00d616cb6492e313ef` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-070 | 49.168 | `6791b6b411ea533b46eabd44bc3fbafa7a097ab71c9f4a8f2bb0ed4166084c46` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-071 | 393.232 | `683664cd1999496a26a6b60f0db27fad6119fee46747506352206011466a4f1d` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-072 | 262.160 | `68472a218e84b621a3f9b7eba38b0e94218e77932d1f460cbba6c612f0ada34b` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-073 | 393.232 | `6c348f5398be8348abeb22db61a975fbe42d06be03ecd59976725e6edf8a1f6e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-074 | 393.232 | `6eb740fb866073f29ebc88439f958b0ac9602174c3be5ceb9578c33f1536b694` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-075 | 262.160 | `6f6298427582647748d07556bc4a2605c201c55aefbc963a0d21d3ceff9c68b1` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-076 | 262.160 | `70342ba3719eb689aa151430435499b5524588deec3da117a54ec10a5412927e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-077 | 65.552 | `71a1ff91ab7aa25ebe1d27ceab026cfaa1fd6872689ed8ae1632ae318f5f14dd` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-078 | 393.232 | `721940615ae29317fd52e23e271090f850f7abf53056953108b37678b62ac09f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-079 | 262.160 | `7321d544fb7bc5d7ff3bb677e81713f897c097bbd5fcd9f45bfcfb735dba3278` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-080 | 393.232 | `7459c841739870c300d41259ece0c6ca321d9f50a950f3d7539e464f62467231` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-081 | 65.552 | `746dc606a07721845732c8e143dee49d985905b32047655c897677a01eea0df7` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-082 | 262.160 | `74870dcbd896d4897b5fb8e5040235a0a9dbe081546ff7c7c7b7d3eb39bb2de9` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-083 | 65.552 | `761b4634a248bbc27b740cd6083e4e0fe3b63f431894e65d1521d02feb01b182` | Kontainer terenkripsi; image flash | Dianalisis; kandidat profil ganda (`AMBIGUOUS_PROFILE`) | Tidak disertakan |
| MAP-084 | 393.232 | `76c31bc281ed393eb38df20e085b61da3670a14cf8b737792973913dce2c4be7` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-085 | 393.232 | `7aa65f2bcd1bab4fcc3eb39855ecbcbca00c7abb403c45c5f159682b1df10de8` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-086 | 262.160 | `7b10b36d60942795422b51cb146e0fea5731245a5b4f5cd58b6092ff5f11b71c` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-087 | 393.232 | `7b497bb28b14719d33517a9a0e4ce370f9a3bad6bd1aa4eb64280a5b08a3f499` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-088 | 393.232 | `7c73ddde5cce6824ce33b410a0e455215815ad64189ed51b7ea13bac2481844e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-089 | 49.168 | `7ccdc541c23a48a632d927c502a69eb088addfed3cc5dd57dc93041e17497426` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-090 | 65.552 | `81d34f6821614a0eeabeb8abb036ef4316c14e34f7b2c89ddfa44eb489f0dbae` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-091 | 65.552 | `83aca5329f551200f3ea772ac04cbe0be01d3d9e1004c5dcafb217fdf79ca336` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-092 | 262.160 | `8481b5bd25d4e225b2e220d3d572135fb1fa9a5a1222fa630d7de90f64356783` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-093 | 432 | `86016bf819625aef53cc6c039761dd16041d92f31e8230b492762bd30a27bcc5` | Kontainer terenkripsi; ukuran 432 byte | Anomali; tidak dikenali (`UNRECOGNIZED`) | Tidak disertakan |
| MAP-094 | 393.232 | `8799d755391338fcfb8c9860c0476eeae7913808e568984257918a3e6bf68bad` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-095 | 393.232 | `8d326927a60da04cd53d48dcf683032d93f5eb3204dbe25d45a6d67a8f11ad36` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-096 | 65.552 | `8d396c783868f9c70d36a95e21035ad1f0711637c7edfcde9cc322b3cbc5ad0d` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-097 | 65.552 | `8e4103defd4660838e2bd4998a1cfc39395e0c951928eb245bddb864e0857985` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-098 | 262.160 | `8ebb83e65d7d8ae4d6592e7c1d305b67b8b1bbcca738cd0212613602b9cebe76` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-099 | 262.160 | `9111872f2fa62e7d46a420381b3363502e27888e04aedc8b3238c62c016bac7d` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-100 | 393.232 | `93e41ac17e58f674fcc26687d95c2759791dd8e834722e53bfc70ccd78e9ff94` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-101 | 262.160 | `93f8b09ef42be5b2e4fc31accf709817cca3006f242056929667005e1fb1bf5a` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-102 | 65.552 | `955cdb7e617d939e980eb3fadf880fe7cfb7c2e1d923bc78b72246ad39e79dc3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-103 | 65.552 | `955cdb7e617d939e980eb3fadf880fe7cfb7c2e1d923bc78b72246ad39e79dc3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-104 | 262.160 | `95e41c5ec5c5d0b0574dd4a3ec04abe025bb55d0270a5d2a1b66d4ef41ecae43` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-105 | 262.160 | `9a2a9fdf51da54b19939f7e580d5e0575025136f44c8433872b48f5f7be653a7` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-106 | 262.160 | `9de640f0e810198b1eddcf9db7ba42d00a7634deda020fbc914f0a4a883a9315` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-107 | 262.160 | `a22a49593cf377bfdc79e0e254529c9b92781264df3d8f20489c5ca21c5be77e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-108 | 49.168 | `a73543bdff865e110c38e22931a170a96378478ad82a318048bed67b9c8a2f83` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-109 | 393.232 | `a7723ed84c8a28f54e00465e82da378db9f926fb9ac3c1d57b655ec75f1b4638` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-110 | 393.232 | `a799bd395ea3e061628214d15b14250ad09f848623f31bd659fe9e85be219fde` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-111 | 262.160 | `a863bacd7d785c4f3dc97148969d00f65f084dc0d098a2ef02810521cecab03a` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-112 | 65.552 | `aa14800d13512ee3af7cec02c848e9395c90b823d25c039397935628002adf6f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-113 | 393.232 | `aa5878bcefb030f014526cc3f5b74b31f301f8972417e7964c8cce0f450b7fca` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-114 | 262.160 | `ab415fbe00b22f4168eabc5bbcbe799223d5928ad30e0cf2978ae78ac88f07fe` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-115 | 65.552 | `ab7a12d9174d50c2399e2be2925f574720376502a8f6bee08bd8d44109c2878f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-116 | 393.232 | `acbb126060dee0f3efde9765a4f51e2606295d10df1dff8c9a40c5d87cf01e12` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-117 | 65.552 | `b18a0652e2fceef5018ba40ee8c5e9e84a5f1fb0f8558c13798df0870a8ce3fc` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-118 | 262.160 | `b229f693cc8d618217fecc2beae9cc4530ee4792722ba9c4859af3a53c1075b2` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-119 | 393.232 | `b2a854bc56872ff7d72fef81c0266f1d35bcd4e6d89ab7c1dd7e989d7341adfd` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-120 | 262.160 | `b72e7b5a1ff2bab46b5928e86694ceebce10191d081c993a878a7d5c543c0f70` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-121 | 65.552 | `b9780138e7b895561790355fe847e812c8dcf27111ab9c7e868cdca2e724df21` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-122 | 262.160 | `ba8cc4d472fabdd2432a235bf27b85366abd70b9f7acf25f51f26c2d0e065d1b` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-123 | 65.552 | `bc2f7670df7558a4f7bdd8645e988e20414b1eeed9a5ab5b37da2b7042ed32d2` | Kontainer terenkripsi; image flash | Dianalisis; kandidat profil ganda (`AMBIGUOUS_PROFILE`) | Tidak disertakan |
| MAP-124 | 393.232 | `bf9e7b7eeebc083533d6018c998d130a9e76145cdbe0f7d1bb6d9613c5b66b7c` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-125 | 49.168 | `c070d4205002df5ebd601fd0cc9cdb11b206a485dade6830e52cc4aa0165dcd3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-126 | 262.160 | `c143672847d7d15c4352d1df16d5f1b2ce22ae00b582ed02beabf944f67b663a` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-127 | 262.160 | `c2072eaae40f39ab73bf8ea8b413151a6dd0d34d6cf6a587a01f84dd9c757d89` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-128 | 65.552 | `c51f75a637b010af7b4d559ccc1225a5bd0c8bf34df58eeab771e78e810e7568` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-129 | 262.160 | `c8e485307c4113d5a01b6e8ba68ca31881bd61b2c926b3f68a3ccefc0fcb937a` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-130 | 393.232 | `c97ece810601083512ba5910d8db39d0d48d48ff959cf73de09cdfa8d859e7ea` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-131 | 262.160 | `c98a6565f112dfa0013091a9ab98765734f945ea0d3752ce299c1e0b394c2831` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-132 | 65.552 | `cc164e57e1b52f8088bdba08fb5353333e365edaffd0b80720c72a7c0aa03f29` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-133 | 262.160 | `cd9bdaa8bad341270732bce15b3df6ea4dd2a7eed93e5ae5925a76bcb6ea321f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-134 | 65.552 | `cdccea66afc4b23f46017c6f19d929698ac8a98ee16794557106efa61ac5121f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-135 | 262.160 | `d204763bb7b627993a93c4d72bd443640542f33b5bc211ce0ecccf9e4663eb40` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-136 | 393.232 | `d2eae99bcb693406f40783d5aaeb4e69338af843611d157befbb3c54edd4ffd0` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-137 | 262.160 | `d392551f04aece1d40f7c223253fb0e35e9c67d5c29de33bcc3171a21eb161de` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-138 | 49.168 | `d5a042681cfd3e4d500b8a6465e83977f1bfb30827d072a20a45853463606254` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-139 | 65.552 | `d7a83c3c9494bd1eed30abd17ec01e89fd78a65590d22a28d9324fe4207e8ca6` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-140 | 262.160 | `d9613197ad0e7236a81f20722c124b1cb8764e77e5b858e9833d7765f0797a89` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-141 | 393.232 | `d9d5d899e777a30b7310a7ecc01fb6c35a392cdd8ecdfac8d808525111aef5db` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-142 | 49.168 | `df24ad565e679504ff845fcc3984fa970e86b30addd4989f2a9e33cb3b0708ec` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-143 | 65.552 | `e13619f27cb5803461935c20cdac65c3eb226e4555b3e17b9170e66a5ba43f51` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-144 | 49.168 | `e3757fca0c123a198b74a191c67b929f16a4fde4243f906ca580bbc4d364ed81` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-145 | 262.160 | `e60a0a5fc7b8ee441bd2f4b280f052c395e50ac8c537485e039c8dcfe7a4d249` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-146 | 49.168 | `e76cdfe6834125e267e42738ad7f96d26c072db0ff8b4f43d871ff0fdfdef9f2` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-147 | 262.160 | `e786498b395afe6e1b4a4e0668c5dfb0e905cad1bab6c15499405e73357e1e0e` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-148 | 393.232 | `e91a7054227bd2698c180a77a58769f5e635ce55f6bd98996fe2d698b70b18ea` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-149 | 262.160 | `e9660da1c97012a8ee6777fd0bfa7189177c203bca956d40d2385b9379a2da08` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-150 | 65.552 | `eff82ef61cdd62fc542437d21b21997a6f3246e57929af6ade970c8edd657661` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-151 | 393.232 | `f244d2b94c50b8cc5d6f3d2ba32f8aaa08d736fa7384824471cd7d6137312dc3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-152 | 65.552 | `f2f0aea6b4fd6dbef1ad4494a8ba173b7cbba96a6ae245392158cad6209162b3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-153 | 262.160 | `f63b1f6e5b2673b67ad3fcc4c166b6998022ba52a640bb2e95d6ffa0bae983b2` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-154 | 393.232 | `f7e73aa89bd61ffb55695a3a3db8de11ea863119d62384f9d84a4c9a694578b7` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-155 | 262.160 | `fb1dad43bb7b8cc93edda02dca8df5fceded446845a2602745c0c0f388f36eb4` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-156 | 49.168 | `fb66b6382636872b00af2933747a7cc9ace83e1ae3dd3fc6d9613ef22ec7861f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-157 | 262.160 | `fbfcdad0fcf9e62ea2c9ddf155f590fb1763af2ec1cf76f8567cc1b6b4e013f3` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-158 | 65.552 | `fc03d48a6b5a95c419f8a00acfc4067367fb12242c47972a1a43a8d744f313ab` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-159 | 393.232 | `fd06f4a8d49b7eb27c00901a0b3a2220dc78fbd16b420cdb9c7b3cc7183bd15f` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-160 | 262.160 | `fd2836e5eed5e2a2db3726d372965cd046309846d208431c869f6ef5652be5f8` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-161 | 262.160 | `fe0540947ef9339709971c75d9351832201643b49f8744891672edc535e933b4` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |
| MAP-162 | 393.232 | `ff9dc6f17405d1652cfafcb23f6397d0dcffcca607046eeec3e7c2e472c7ca82` | Kontainer terenkripsi; image flash | Dianalisis; lihat `data/map_library/inventory.csv` | Tidak disertakan |

## 4. Bahan BIN/ROM eksternal

Selain berkas MAP di atas, tersedia bahan analisis eksternal berupa 700 image BIN dan 177 definisi peta (174 XDF + 3 ADX). Bahan tersebut dipakai read-only untuk perbandingan dan **bukan hasil karya RevCore**.

- Tidak ada image BIN/ROM, definisi peta, atau arsip sumber yang didistribusikan.
- Nama program, identitas bahan, nama berkas sumber, dan asal spesifik tidak diungkap.
- Tidak ada upaya menembus proteksi pihak ketiga untuk publikasi.
- Status kecocokan kendaraan: **UNKNOWN**. Tidak ada kendaraan yang boleh disebut didukung atau teruji berdasarkan bahan ini.

## 5. Temuan dan celah

| Temuan | Status |
|---|---|
| 162 entri MAP terdaftar dengan ukuran dan SHA-256 | Tercatat |
| 161 SHA-256 unik; satu pasangan duplikat | Tercatat |
| Satu berkas 432 byte berukuran tidak lazim | Belum dikenali |
| Format kontainer terenkripsi; plaintext image flash | Dianalisis; kunci tidak disertakan |
| Tautan profil | 159 `READY`, 2 `AMBIGUOUS_PROFILE`, 1 `UNRECOGNIZED` |
| Kecocokan profil ke kendaraan | UNKNOWN; tidak diuji |
| Berkas mentah | Salinan aset pihak lain; tidak disertakan |
| Pengujian pada kendaraan | Tidak dilakukan |

## 6. Aturan penggunaan

- Jangan menganggap keberadaan sebuah berkas sebagai bukti dukungan kendaraan.
- Jangan menganggap ukuran atau SHA-256 sebagai bukti isi, keaslian, atau kecocokan.
- Jangan mendistribusikan ulang berkas mentah; berkas itu bukan milik RevCore.
- Jangan meminta atau mencoba membuka kunci, menambal checksum, atau menulis ke ECU berdasarkan dokumen ini.
- Gunakan tautan profil sebagai hipotesis; `READY` bukan bukti kecocokan kendaraan.
- Setiap temuan baru harus dicatat dengan status yang jujur: berhasil, gagal, belum selesai, atau masih dipertanyakan.
