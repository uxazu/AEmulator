<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Klasik Android yazılımları — HTC Sense, TouchWiz, MIUI, AOSP — modern bir telefonda. Root ve PC gerekmez.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · **🇹🇷 Türkçe** · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator gerçek bir Android 2.3–4.4 sistemini doğrudan yazılım dosyasından başlatır: recovery ZIP, Odin arşivi veya Google fabrika imajı. Eski ARM kodu değiştirilmiş QEMU ile çevrilir, çekirdeğin binder’ı taklit edilir, grafikler telefonun GPU’sunu, ses Android’in ses altyapısını kullanır — hepsi sıradan bir uygulamanın içinde.

## ✨ Özellikler

- Neredeyse her biçimi içe aktarma: CWM/TWRP ZIP, Samsung Odin `.tar.md5`, Google fabrika `.tgz`, `system.img`, OTA `system.new.dat.br`
- Üretici arayüzleri olduğu gibi çalışır: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- GL köprüsüyle donanım grafikleri, ses, dokunma ve çoklu dokunma, modern TLS vekilli ağ
- APK, müzik ve fotoğraflar için ortak hafıza kartı klasörü
- 18 dilde Material 3 Expressive arayüz
- Ücretsiz ve açık kaynak (GPL-3.0)

## 📱 Desteklenen yazılımlar

Xiaomi 15 (Snapdragon 8 Elite, Android 16) üzerinde test edildi. Android 2.3–4.4 için her 32 bit ARM yazılımı denemeye değer; listede yalnızca doğrulanan imajlar var.

| Cihaz | Android | Arayüz | Durum | İndir |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Çalışıyor | [içe aktarmaya hazır arşiv](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Çalışıyor | [içe aktarmaya hazır arşiv](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Çalışıyor — ses çalışıyor | [Google fabrika imajı](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Çalışıyor — ses çalışıyor | [Google fabrika imajı](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Çalışıyor — Bluetooth hata penceresi | [Odin paketi](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Kısmen — henüz ses yok | [Odin paketi](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 Üzerinde çalışılıyor — kurulum sihirbazına kadar açılıyor, kararlı hâle getiriliyor | [recovery ZIP](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Henüz değil — resmî olmayan port çöküyor | [recovery ZIP](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Yazılım dosyaları üreticilerine aittir. Bağlantılar üretici sunucularına veya herkese açık arşivlere gider; AEmulator hiçbir yazılım içermez.

## 🚀 Hızlı başlangıç

1. APK’yı [Releases](https://github.com/uxazu/aemulator/releases) sayfasından indirip kurun.
2. Yukarıdaki tablodan bir yazılımı telefona indirin.
3. AEmulator’ı açın → **Yazılım ekle** ve dosyayı seçin. İçe aktarma birkaç dakika sürer.
4. **Başlat**’a basın. İlk açılış daha uzundur: sistem uygulamaları optimize eder.
5. ⋮ menüsü: ses, güç düğmesi ve günlük; ⚙️ ayarları ve dili açar.

## 📋 Gereksinimler

- 64 bit ARM telefonda Android 8.0+ (arm64-v8a)
- Yazılım başına yaklaşık 1–3 GB boş alan
- Önerilen: yeni bir Snapdragon / Dimensity / Tensor

## ⚙️ Nasıl çalışır

Her misafir süreç değiştirilmiş kullanıcı kipi QEMU altında çalışır. Bir binder arka plan programı çekirdek sürücüsünün yerini alır, GL köprüsü OpenGL ES çağrılarını GPU’ya iletir, küçük misafir kütüphaneleri (ses HAL’i, audio policy sarmalayıcısı, LD_PRELOAD katmanı) üretici kodunu emülatöre uyarlar. İçe aktarıcı yazılımı okur, boot imajındaki init betiklerini bulur ve servislerin başlama planını kurar.

## 🛠️ Kaynaktan derleme

JDK 17, Android SDK 36 ve NDK r28 gerekir. Misafir kütüphaneleri `native/*/build.sh` betikleriyle derlenir.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Teşekkürler

AEmulator, [ilk geliştiricinin](https://t.me/istratii_tech) HTC Desire HD ve HTC One M7 emülatörlerinden doğdu — onun motoru olmadan bu proje olmazdı.

## 💙 Projeyi destekleyin

AEmulator sevdiğiniz bir telefonu geri getirdiyse geliştirmeyi destekleyebilirsiniz:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Bağlantılar

- 🌐 Web sitesi: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Telegram kanalı: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Geliştirici: [uxazu](https://github.com/uxazu)
- 🧬 İlk geliştirici: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Lisans

GPL-3.0. Android, markalar ve yazılımlar sahiplerine aittir.
