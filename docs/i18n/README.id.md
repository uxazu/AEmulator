<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Firmware Android klasik — HTC Sense, TouchWiz, MIUI, AOSP — di ponsel modern. Tanpa root, tanpa PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · **🇮🇩 Indonesia** · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator mem-boot sistem Android 2.3–4.4 asli langsung dari file firmware: ZIP recovery, arsip Odin, atau factory image Google. Kode ARM lama diterjemahkan oleh QEMU yang dimodifikasi, binder kernel diemulasikan, grafis memakai GPU ponsel, dan suara lewat sistem audio Android — semuanya di dalam aplikasi biasa.

## ✨ Fitur

- Impor hampir semua format: ZIP CWM/TWRP, Odin `.tar.md5` Samsung, factory image Google `.tgz`, `system.img`, OTA `system.new.dat.br`
- Tampilan pabrikan berjalan apa adanya: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Grafis hardware lewat jembatan GL, suara, sentuh dan multisentuh, jaringan dengan proxy TLS modern
- Folder kartu memori bersama untuk APK, musik, dan foto
- Antarmuka Material 3 Expressive dalam 18 bahasa
- Gratis dan sumber terbuka (GPL-3.0)

## 📱 Firmware yang didukung

Diuji di Xiaomi 15 (Snapdragon 8 Elite, Android 16). Firmware ARM 32-bit apa pun untuk Android 2.3–4.4 layak dicoba; daftar ini hanya memuat image yang sudah kami periksa.

| Perangkat | Android | Tampilan | Status | Unduh |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Berjalan | [arsip siap impor](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Berjalan | [arsip siap impor](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Berjalan — suara berfungsi | [factory image Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Berjalan — suara berfungsi | [factory image Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Berjalan — dialog galat Bluetooth | [paket Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Sebagian — belum ada suara | [paket Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 Dikerjakan — boot sampai penyiapan, sedang distabilkan | [ZIP recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Belum — port tidak resmi crash | [ZIP recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> File firmware milik pabrikannya. Tautan mengarah ke server pabrikan atau arsip publik; AEmulator tidak menyertakan firmware.

## 🚀 Mulai cepat

1. Unduh APK dari [Releases](https://github.com/uxazu/aemulator/releases) lalu pasang.
2. Unduh firmware dari tabel di atas ke ponsel.
3. Buka AEmulator → **Tambah firmware** lalu pilih file. Impor butuh beberapa menit.
4. Tekan **Mulai**. Boot pertama lebih lambat: sistem mengoptimalkan aplikasi.
5. Menu ⋮ untuk volume, tombol daya, dan log; ⚙️ membuka setelan dan bahasa.

## 📋 Persyaratan

- Android 8.0+ di ponsel ARM 64-bit (arm64-v8a)
- Sekitar 1–3 GB ruang kosong per firmware
- Disarankan Snapdragon / Dimensity / Tensor terbaru

## ⚙️ Cara kerja

Setiap proses tamu berjalan di QEMU mode pengguna yang dimodifikasi. Daemon binder menggantikan driver kernel, jembatan GL meneruskan panggilan OpenGL ES ke GPU, dan pustaka tamu kecil (HAL audio, pembungkus audio policy, shim LD_PRELOAD) menyesuaikan kode pabrikan dengan emulator. Importir membaca firmware, menemukan skrip init di image boot, dan menyusun rencana mulai layanan.

## 🛠️ Membangun dari sumber

Butuh JDK 17, Android SDK 36, dan NDK r28. Pustaka tamu dibangun dengan `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Kredit

AEmulator tumbuh dari emulator HTC Desire HD dan HTC One M7 karya [pembuat asli](https://t.me/istratii_tech) — tanpa mesinnya proyek ini tidak akan ada.

## 💙 Dukung proyek

Jika AEmulator mengembalikan ponsel kesayangan Anda, Anda bisa mendukung pengembangan:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Tautan

- 🌐 Situs web: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Kanal Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Pembuat: [uxazu](https://github.com/uxazu)
- 🧬 Pembuat asli: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Lisensi

GPL-3.0. Android, merek dagang, dan firmware milik pemiliknya.
