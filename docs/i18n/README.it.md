<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Firmware Android classici — HTC Sense, TouchWiz, MIUI, AOSP — su un telefono moderno. Senza root, senza PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · **🇮🇹 Italiano** · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator avvia un vero sistema Android 2.3–4.4 direttamente da un file firmware: ZIP per recovery, archivio Odin o immagine di fabbrica Google. Il vecchio codice ARM passa da un QEMU modificato, il binder del kernel è emulato, la grafica usa la GPU del telefono e l’audio passa per lo stack audio di Android — tutto in una normale app.

## ✨ Funzionalità

- Importa quasi ogni formato: ZIP CWM/TWRP, Odin `.tar.md5` di Samsung, immagine di fabbrica Google `.tgz`, `system.img`, OTA `system.new.dat.br`
- Le interfacce dei produttori funzionano così come sono: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Grafica hardware tramite il ponte GL, audio, tocco e multitouch, rete con proxy TLS moderno
- Cartella condivisa della scheda di memoria per APK, musica e foto
- Interfaccia Material 3 Expressive in 18 lingue
- Gratuito e open source (GPL-3.0)

## 📱 Firmware supportati

Testato su Xiaomi 15 (Snapdragon 8 Elite, Android 16). Vale la pena provare qualsiasi firmware ARM a 32 bit per Android 2.3–4.4; l’elenco contiene solo immagini verificate.

| Dispositivo | Android | Interfaccia | Stato | Download |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Funziona | [archivio pronto da importare](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Funziona | [archivio pronto da importare](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Funziona — audio funzionante | [immagine di fabbrica Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Funziona — audio funzionante | [immagine di fabbrica Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Funziona — finestra di errore Bluetooth | [pacchetto Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Parziale — ancora senza audio | [pacchetto Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 In corso — si avvia fino alla configurazione, in stabilizzazione | [ZIP per recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Non ancora — il port non ufficiale va in crash | [ZIP per recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> I firmware appartengono ai produttori. I link portano ai server dei produttori o ad archivi pubblici; AEmulator non include firmware.

## 🚀 Avvio rapido

1. Scarica l’APK da [Releases](https://github.com/uxazu/aemulator/releases) e installalo.
2. Scarica sul telefono un firmware dalla tabella sopra.
3. Apri AEmulator → **Aggiungi firmware** e scegli il file. L’importazione richiede qualche minuto.
4. Premi **Avvia**. Il primo avvio è più lento: il sistema ottimizza le app.
5. Menu ⋮ per volume, tasto di accensione e registro; ⚙️ apre impostazioni e lingua.

## 📋 Requisiti

- Android 8.0+ su telefono ARM a 64 bit (arm64-v8a)
- Circa 1–3 GB liberi per firmware
- Consigliato: Snapdragon / Dimensity / Tensor recente

## ⚙️ Come funziona

Ogni processo ospite gira sotto un QEMU user-mode modificato. Un demone binder sostituisce il driver del kernel, un ponte GL inoltra le chiamate OpenGL ES alla GPU e piccole librerie ospiti (HAL audio, wrapper audio policy, shim LD_PRELOAD) adattano il codice dei produttori all’emulatore. L’importatore legge il firmware, trova gli script init nell’immagine boot e crea il piano di avvio dei servizi.

## 🛠️ Compilare dai sorgenti

Servono JDK 17, Android SDK 36 e NDK r28. Le librerie ospiti si compilano con `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Ringraziamenti

AEmulator nasce dagli emulatori HTC Desire HD e HTC One M7 dell’[autore originale](https://t.me/istratii_tech); senza il suo motore questo progetto non esisterebbe.

## 💙 Sostieni il progetto

Se AEmulator ti ha ridato un telefono amato, puoi sostenere lo sviluppo:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Link

- 🌐 Sito web: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Canale Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Autore: [uxazu](https://github.com/uxazu)
- 🧬 Autore originale: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Licenza

GPL-3.0. Android, i marchi e i firmware appartengono ai rispettivi proprietari.
