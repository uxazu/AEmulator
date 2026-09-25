<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Старые прошивки Android — HTC Sense, TouchWiz, MIUI, AOSP — на современном телефоне. Без root и без ПК.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · **🇷🇺 Русский** · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator загружает настоящую систему Android 2.3–4.4 прямо из файла прошивки: ZIP для рекавери, архива Odin или factory-образа Google. Старый ARM-код транслирует доработанный QEMU, binder ядра эмулируется, графика идёт через GPU телефона, звук — через аудиосистему Android. Всё работает внутри обычного приложения.

## ✨ Возможности

- Импорт почти любых форматов: ZIP для CWM/TWRP, Odin `.tar.md5` от Samsung, factory `.tgz` от Google, `system.img`, OTA `system.new.dat.br`
- Оболочки производителей работают как есть: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Аппаратная графика через GL-мост, звук, касания и мультитач, сеть с современным TLS-прокси
- Общая папка «карты памяти» для APK, музыки и фото
- Интерфейс Material 3 Expressive на 18 языках
- Бесплатно и с открытым кодом (GPL-3.0)

## 📱 Поддерживаемые прошивки

Проверено на Xiaomi 15 (Snapdragon 8 Elite, Android 16). Пробовать стоит любую 32-битную ARM-прошивку Android 2.3–4.4 — в списке только проверенные нами.

| Устройство | Android | Оболочка | Статус | Скачать |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Работает | [готовый архив для импорта](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Работает | [готовый архив для импорта](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Работает — звук работает | [factory-образ Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Работает — звук работает | [factory-образ Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Работает — окно ошибки Bluetooth | [пакет Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Частично — пока без звука | [пакет Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 В работе — доходит до мастера настройки, дорабатывается | [ZIP для рекавери](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Пока нет — неофициальный порт падает | [ZIP для рекавери](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Файлы прошивок принадлежат производителям. Ссылки ведут на серверы производителей или публичные архивы; AEmulator не содержит прошивок.

## 🚀 Быстрый старт

1. Скачайте APK из [Releases](https://github.com/uxazu/aemulator/releases) и установите.
2. Скачайте прошивку из таблицы выше на телефон.
3. Откройте AEmulator → **Добавить прошивку** и выберите файл. Импорт занимает несколько минут.
4. Нажмите **Запустить**. Первая загрузка дольше: система оптимизирует приложения.
5. Меню ⋮ — громкость, кнопка питания и журнал; кнопка ⚙️ — настройки приложения и язык.

## 📋 Требования

- Android 8.0+ на 64-битном ARM-телефоне (arm64-v8a)
- Около 1–3 ГБ свободного места на прошивку
- Рекомендуется современный Snapdragon / Dimensity / Tensor

## ⚙️ Как это устроено

Каждый процесс гостя работает под доработанным QEMU в режиме пользователя. Демон binder заменяет драйвер ядра, GL-мост передаёт вызовы OpenGL ES на GPU телефона, а небольшие гостевые библиотеки (звуковой HAL, обёртка audio policy, прослойка LD_PRELOAD) подгоняют код производителей под эмулятор. Импортёр читает прошивку, находит init-скрипты в boot-образе и строит план запуска системных служб.

## 🛠️ Сборка из исходников

Нужны JDK 17, Android SDK 36 и NDK r28. Гостевые библиотеки собираются скриптами `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Благодарности

AEmulator вырос из эмуляторов HTC Desire HD и HTC One M7 [первого автора](https://t.me/istratii_tech) — без его движка проекта бы не было.

## 💙 Поддержать проект

Если AEmulator вернул вам любимый телефон, можно поддержать разработку:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Ссылки

- 🌐 Сайт: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Telegram-канал: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Автор: [uxazu](https://github.com/uxazu)
- 🧬 Первый автор: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Лицензия

GPL-3.0. Android, товарные знаки и прошивки принадлежат их владельцам.
