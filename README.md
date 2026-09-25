<div align="center">

<img src="docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Run classic Android firmware — HTC Sense, TouchWiz, MIUI, AOSP — on a modern phone. No root, no PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

**🇬🇧 English** · [🇷🇺 Русский](docs/i18n/README.ru.md) · [🇺🇦 Українська](docs/i18n/README.uk.md) · [🇩🇪 Deutsch](docs/i18n/README.de.md) · [🇫🇷 Français](docs/i18n/README.fr.md) · [🇪🇸 Español](docs/i18n/README.es.md) · [🇧🇷 Português](docs/i18n/README.pt-BR.md) · [🇮🇹 Italiano](docs/i18n/README.it.md) · [🇵🇱 Polski](docs/i18n/README.pl.md) · [🇹🇷 Türkçe](docs/i18n/README.tr.md) · [🇸🇦 العربية](docs/i18n/README.ar.md) · [🇮🇷 فارسی](docs/i18n/README.fa.md) · [🇮🇳 हिन्दी](docs/i18n/README.hi.md) · [🇮🇩 Indonesia](docs/i18n/README.id.md) · [🇻🇳 Tiếng Việt](docs/i18n/README.vi.md) · [🇨🇳 简体中文](docs/i18n/README.zh-CN.md) · [🇯🇵 日本語](docs/i18n/README.ja.md) · [🇰🇷 한국어](docs/i18n/README.ko.md)

</div>

---

AEmulator boots a real Android 2.3–4.4 system image straight from a firmware file you already have: a recovery ZIP, an Odin archive or a Google factory image. It translates the old ARM code with a patched QEMU, emulates the kernel’s binder, draws with your phone’s GPU and plays sound through Android’s audio stack — everything runs inside a normal app.

## ✨ Features

- Import almost any firmware format: CWM/TWRP ZIP, Samsung Odin `.tar.md5`, Google factory `.tgz`, `system.img`, OTA `system.new.dat.br`
- Vendor skins work as shipped: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Hardware graphics through the GL bridge, sound, touch and multitouch, network with a modern TLS proxy
- Shared memory-card folder for APKs, music and photos
- Material 3 Expressive interface in 18 languages
- Free and open source (GPL-3.0)

## 📱 Supported firmware

Tested on a Xiaomi 15 (Snapdragon 8 Elite, Android 16). Any 32-bit ARM firmware for Android 2.3–4.4 is worth trying — this list only covers images we have checked.

| Device | Android | Skin | Status | Download |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Works | [ready-to-import archive](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Works | [ready-to-import archive](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Works — sound works | [Google factory image](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Works — sound works | [Google factory image](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Works — Bluetooth app dialog | [Odin package](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Partly — no sound yet | [Odin package](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 In progress — boots to setup, being stabilised | [recovery ZIP](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Not yet — unofficial port crashes | [recovery ZIP](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Firmware files belong to their manufacturers. Links point to the manufacturers’ servers or public archives; AEmulator does not ship any firmware.

## 🚀 Quick start

1. Download the APK from [Releases](https://github.com/uxazu/aemulator/releases) and install it.
2. Download a firmware from the table above to your phone.
3. Open AEmulator → **Add firmware** and pick the file. Import takes a few minutes.
4. Press **Start**. The first boot is slower: the system optimises its apps.
5. Use the ⋮ menu for volume, power button and logs; the ⚙️ button opens app settings and language.

## 📋 Requirements

- Android 8.0+ on a 64-bit ARM phone (arm64-v8a)
- About 1–3 GB of free space per firmware
- A recent Snapdragon / Dimensity / Tensor chip is recommended

## ⚙️ How it works

Each guest process runs under a patched user-mode QEMU. A binder daemon replaces the kernel driver, a GL bridge forwards OpenGL ES calls to the phone’s GPU, and small guest libraries (audio HAL, audio policy wrapper, LD_PRELOAD shim) adapt vendor code to the emulator. The importer reads the firmware, finds the init scripts in its boot image and builds a start plan for system services.

## 🛠️ Build from source

You need JDK 17, Android SDK 36 and NDK r28. Native guest parts are built with the scripts in `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Credits

AEmulator grew out of the HTC Desire HD and HTC One M7 emulators by [the original author](https://t.me/istratii_tech) — their engine made this project possible.

## 💙 Support the project

If AEmulator brought back a phone you loved, you can support development:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Links

- 🌐 Website: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Telegram channel: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Author: [uxazu](https://github.com/uxazu)
- 🧬 Original author: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 License

GPL-3.0. Android, trademarks and firmware belong to their owners.
