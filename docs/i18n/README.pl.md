<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Klasyczne firmware Androida — HTC Sense, TouchWiz, MIUI, AOSP — na nowoczesnym telefonie. Bez roota, bez PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · **🇵🇱 Polski** · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator uruchamia prawdziwy system Android 2.3–4.4 prosto z pliku firmware: ZIP do recovery, archiwum Odin lub obraz fabryczny Google. Stary kod ARM tłumaczy zmodyfikowane QEMU, binder jądra jest emulowany, grafika korzysta z GPU telefonu, a dźwięk z systemu audio Androida — wszystko w zwykłej aplikacji.

## ✨ Funkcje

- Import niemal każdego formatu: ZIP CWM/TWRP, Odin `.tar.md5` Samsunga, obraz fabryczny Google `.tgz`, `system.img`, OTA `system.new.dat.br`
- Nakładki producentów działają bez zmian: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Sprzętowa grafika przez mostek GL, dźwięk, dotyk i multitouch, sieć z nowoczesnym proxy TLS
- Wspólny folder karty pamięci na APK, muzykę i zdjęcia
- Interfejs Material 3 Expressive w 18 językach
- Za darmo i open source (GPL-3.0)

## 📱 Obsługiwane firmware

Testowane na Xiaomi 15 (Snapdragon 8 Elite, Android 16). Warto spróbować dowolnego 32-bitowego firmware ARM dla Androida 2.3–4.4; lista zawiera tylko sprawdzone obrazy.

| Urządzenie | Android | Nakładka | Stan | Pobierz |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Działa | [archiwum gotowe do importu](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Działa | [archiwum gotowe do importu](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Działa — dźwięk działa | [obraz fabryczny Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Działa — dźwięk działa | [obraz fabryczny Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Działa — okno błędu Bluetooth | [pakiet Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Częściowo — na razie bez dźwięku | [pakiet Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 W toku — dochodzi do kreatora, stabilizacja trwa | [ZIP do recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Jeszcze nie — nieoficjalny port się wysypuje | [ZIP do recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Pliki firmware należą do producentów. Linki prowadzą do serwerów producentów lub publicznych archiwów; AEmulator nie zawiera firmware.

## 🚀 Szybki start

1. Pobierz APK z [Releases](https://github.com/uxazu/aemulator/releases) i zainstaluj.
2. Pobierz na telefon firmware z tabeli powyżej.
3. Otwórz AEmulator → **Dodaj firmware** i wybierz plik. Import trwa kilka minut.
4. Naciśnij **Uruchom**. Pierwszy start trwa dłużej: system optymalizuje aplikacje.
5. Menu ⋮ — głośność, przycisk zasilania i dziennik; ⚙️ — ustawienia i język.

## 📋 Wymagania

- Android 8.0+ na 64-bitowym telefonie ARM (arm64-v8a)
- Około 1–3 GB wolnego miejsca na firmware
- Zalecany nowy Snapdragon / Dimensity / Tensor

## ⚙️ Jak to działa

Każdy proces gościa działa pod zmodyfikowanym QEMU w trybie użytkownika. Demon binder zastępuje sterownik jądra, mostek GL przekazuje wywołania OpenGL ES do GPU, a małe biblioteki gościa (HAL audio, nakładka audio policy, shim LD_PRELOAD) dopasowują kod producentów do emulatora. Importer czyta firmware, znajduje skrypty init w obrazie boot i tworzy plan startu usług.

## 🛠️ Budowanie ze źródeł

Potrzebne są JDK 17, Android SDK 36 i NDK r28. Biblioteki gościa buduje się skryptami `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Podziękowania

AEmulator wyrósł z emulatorów HTC Desire HD i HTC One M7 [pierwotnego autora](https://t.me/istratii_tech) — bez jego silnika tego projektu by nie było.

## 💙 Wesprzyj projekt

Jeśli AEmulator przywrócił ci ulubiony telefon, możesz wesprzeć rozwój:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Linki

- 🌐 Strona: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Kanał Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Autor: [uxazu](https://github.com/uxazu)
- 🧬 Pierwotny autor: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Licencja

GPL-3.0. Android, znaki towarowe i firmware należą do ich właścicieli.
