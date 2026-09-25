<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Les anciennes ROM Android — HTC Sense, TouchWiz, MIUI, AOSP — sur un téléphone moderne. Sans root, sans PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · **🇫🇷 Français** · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator démarre un vrai système Android 2.3–4.4 directement depuis un fichier de ROM : ZIP recovery, archive Odin ou image d’usine Google. L’ancien code ARM passe par un QEMU modifié, le binder du noyau est émulé, les graphismes utilisent le GPU du téléphone et le son passe par la pile audio d’Android — le tout dans une application ordinaire.

## ✨ Fonctionnalités

- Import de presque tous les formats : ZIP CWM/TWRP, Odin `.tar.md5` de Samsung, image d’usine Google `.tgz`, `system.img`, OTA `system.new.dat.br`
- Les surcouches constructeur fonctionnent telles quelles : HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Graphismes matériels via le pont GL, son, tactile et multitouch, réseau avec proxy TLS moderne
- Dossier de carte mémoire partagé pour les APK, la musique et les photos
- Interface Material 3 Expressive en 18 langues
- Gratuit et open source (GPL-3.0)

## 📱 ROM prises en charge

Testé sur un Xiaomi 15 (Snapdragon 8 Elite, Android 16). Toute ROM ARM 32 bits pour Android 2.3–4.4 mérite un essai — la liste ne contient que les images vérifiées.

| Appareil | Android | Surcouche | État | Téléchargement |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Fonctionne | [archive prête à importer](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Fonctionne | [archive prête à importer](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Fonctionne — le son fonctionne | [image d’usine Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Fonctionne — le son fonctionne | [image d’usine Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Fonctionne — boîte d’erreur Bluetooth | [paquet Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Partiel — pas encore de son | [paquet Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 En cours — démarre jusqu’à l’assistant, en stabilisation | [ZIP recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Pas encore — le portage non officiel plante | [ZIP recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Les ROM appartiennent à leurs constructeurs. Les liens mènent aux serveurs des constructeurs ou à des archives publiques ; AEmulator ne contient aucune ROM.

## 🚀 Démarrage rapide

1. Téléchargez l’APK depuis [Releases](https://github.com/uxazu/aemulator/releases) et installez-le.
2. Téléchargez une ROM du tableau ci-dessus sur le téléphone.
3. Ouvrez AEmulator → **Ajouter une ROM** et choisissez le fichier. L’import prend quelques minutes.
4. Appuyez sur **Démarrer**. Le premier démarrage est plus long : le système optimise les applis.
5. Menu ⋮ pour le volume, le bouton marche et le journal ; ⚙️ ouvre les paramètres et la langue.

## 📋 Configuration requise

- Android 8.0+ sur un téléphone ARM 64 bits (arm64-v8a)
- Environ 1–3 Go libres par ROM
- Puce récente conseillée : Snapdragon / Dimensity / Tensor

## ⚙️ Fonctionnement

Chaque processus invité tourne sous un QEMU en mode utilisateur modifié. Un démon binder remplace le pilote du noyau, un pont GL transmet les appels OpenGL ES au GPU, et de petites bibliothèques invitées (HAL audio, enveloppe audio policy, shim LD_PRELOAD) adaptent le code constructeur à l’émulateur. L’importateur lit la ROM, trouve les scripts init dans l’image boot et construit le plan de démarrage des services.

## 🛠️ Compiler depuis les sources

Il faut JDK 17, Android SDK 36 et NDK r28. Les bibliothèques invitées se compilent avec `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Remerciements

AEmulator est né des émulateurs HTC Desire HD et HTC One M7 de [l’auteur d’origine](https://t.me/istratii_tech) — sans son moteur, ce projet n’existerait pas.

## 💙 Soutenir le projet

Si AEmulator vous a rendu un téléphone que vous aimiez, vous pouvez soutenir le développement :

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Liens

- 🌐 Site web: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Chaîne Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Auteur: [uxazu](https://github.com/uxazu)
- 🧬 Auteur d’origine: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Licence

GPL-3.0. Android, les marques et les ROM appartiennent à leurs propriétaires.
