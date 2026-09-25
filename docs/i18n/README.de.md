<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Klassische Android-Firmware — HTC Sense, TouchWiz, MIUI, AOSP — auf einem modernen Handy. Ohne Root, ohne PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · **🇩🇪 Deutsch** · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator startet ein echtes Android-2.3–4.4-System direkt aus einer Firmware-Datei: Recovery-ZIP, Odin-Archiv oder Google-Factory-Image. Der alte ARM-Code läuft über ein angepasstes QEMU, der Binder des Kernels wird emuliert, die Grafik läuft über die GPU des Handys und der Ton über Androids Audiosystem — alles in einer normalen App.

## ✨ Funktionen

- Import fast aller Formate: CWM/TWRP-ZIP, Samsung Odin `.tar.md5`, Google Factory `.tgz`, `system.img`, OTA `system.new.dat.br`
- Hersteller-Oberflächen laufen unverändert: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Hardwaregrafik über die GL-Brücke, Ton, Touch und Multitouch, Netzwerk mit modernem TLS-Proxy
- Gemeinsamer Speicherkarten-Ordner für APKs, Musik und Fotos
- Material-3-Expressive-Oberfläche in 18 Sprachen
- Kostenlos und quelloffen (GPL-3.0)

## 📱 Unterstützte Firmware

Getestet auf einem Xiaomi 15 (Snapdragon 8 Elite, Android 16). Jede 32-Bit-ARM-Firmware für Android 2.3–4.4 ist einen Versuch wert — die Liste enthält nur von uns geprüfte Images.

| Gerät | Android | Oberfläche | Status | Download |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Läuft | [importfertiges Archiv](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Läuft | [importfertiges Archiv](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Läuft — Ton funktioniert | [Google-Factory-Image](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Läuft — Ton funktioniert | [Google-Factory-Image](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Läuft — Bluetooth-Fehlerdialog | [Odin-Paket](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Teilweise — noch kein Ton | [Odin-Paket](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 In Arbeit — startet bis zur Einrichtung, wird stabilisiert | [Recovery-ZIP](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Noch nicht — inoffizieller Port stürzt ab | [Recovery-ZIP](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Firmware-Dateien gehören ihren Herstellern. Links führen zu Herstellerservern oder öffentlichen Archiven; AEmulator enthält keine Firmware.

## 🚀 Schnellstart

1. APK unter [Releases](https://github.com/uxazu/aemulator/releases) herunterladen und installieren.
2. Eine Firmware aus der Tabelle oben aufs Handy laden.
3. AEmulator öffnen → **Firmware hinzufügen** und die Datei wählen. Der Import dauert einige Minuten.
4. **Starten** drücken. Der erste Start dauert länger: Das System optimiert Apps.
5. Menü ⋮ für Lautstärke, Ein/Aus und Protokoll; ⚙️ öffnet Einstellungen und Sprache.

## 📋 Voraussetzungen

- Android 8.0+ auf einem 64-Bit-ARM-Handy (arm64-v8a)
- Etwa 1–3 GB freier Speicher pro Firmware
- Empfohlen: aktueller Snapdragon / Dimensity / Tensor

## ⚙️ So funktioniert es

Jeder Gastprozess läuft unter einem angepassten User-Mode-QEMU. Ein Binder-Daemon ersetzt den Kernel-Treiber, eine GL-Brücke leitet OpenGL-ES-Aufrufe an die GPU weiter, und kleine Gast-Bibliotheken (Audio-HAL, Audio-Policy-Wrapper, LD_PRELOAD-Shim) passen Herstellercode an den Emulator an. Der Importer liest die Firmware, findet die Init-Skripte im Boot-Image und erstellt einen Startplan für die Systemdienste.

## 🛠️ Aus dem Quellcode bauen

Benötigt werden JDK 17, Android SDK 36 und NDK r28. Die Gast-Bibliotheken werden mit `native/*/build.sh` gebaut.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Dank

AEmulator ist aus den Emulatoren für HTC Desire HD und HTC One M7 [des Originalautors](https://t.me/istratii_tech) hervorgegangen — ohne seine Engine gäbe es dieses Projekt nicht.

## 💙 Projekt unterstützen

Wenn AEmulator dir ein geliebtes Handy zurückgebracht hat, kannst du die Entwicklung unterstützen:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Links

- 🌐 Website: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Telegram-Kanal: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Autor: [uxazu](https://github.com/uxazu)
- 🧬 Originalautor: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Lizenz

GPL-3.0. Android, Marken und Firmware gehören ihren Inhabern.
