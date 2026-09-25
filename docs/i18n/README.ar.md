<div dir="rtl">

<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**شغّل برامج أندرويد الكلاسيكية — HTC Sense وTouchWiz وMIUI وAOSP — على هاتف حديث. بلا روت وبلا حاسوب.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · **🇸🇦 العربية** · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

يقلع AEmulator نظام أندرويد 2.3–4.4 حقيقيًا مباشرة من ملف البرنامج الثابت: ملف ZIP للريكفري أو أرشيف Odin أو صورة مصنع Google. تُترجم شفرة ARM القديمة عبر QEMU معدّل، ويُحاكى binder النواة، وتُرسم الواجهة بمعالج رسومات الهاتف ويمر الصوت عبر نظام صوت أندرويد — كل ذلك داخل تطبيق عادي.

## ✨ المزايا

- استيراد كل الصيغ تقريبًا: ‏ZIP لـ CWM/TWRP، و‏Odin ‏`.tar.md5` من سامسونج، وصورة مصنع Google ‏`.tgz`، و`system.img`، وOTA ‏`system.new.dat.br`
- واجهات الشركات تعمل كما هي: HTC Sense وSamsung TouchWiz وMIUI وAOSP
- رسوميات عتادية عبر جسر GL، وصوت، ولمس ولمس متعدد، وشبكة مع وكيل TLS حديث
- مجلد بطاقة ذاكرة مشترك لملفات APK والموسيقى والصور
- واجهة Material 3 Expressive بـ 18 لغة
- مجاني ومفتوح المصدر (GPL-3.0)

## 📱 البرامج الثابتة المدعومة

اختُبر على Xiaomi 15 ‏(Snapdragon 8 Elite، أندرويد 16). تستحق أي برامج ARM بمعمارية 32 بت لأندرويد 2.3–4.4 التجربة؛ القائمة تضم ما تحققنا منه فقط.

| الجهاز | أندرويد | الواجهة | الحالة | تنزيل |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 يعمل | [أرشيف جاهز للاستيراد](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 يعمل | [أرشيف جاهز للاستيراد](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 يعمل — الصوت يعمل | [صورة مصنع Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 يعمل — الصوت يعمل | [صورة مصنع Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 يعمل — نافذة خطأ Bluetooth | [حزمة Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 جزئيًا — بلا صوت حاليًا | [حزمة Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 قيد العمل — يصل إلى معالج الإعداد، قيد التحسين | [ZIP للريكفري](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 ليس بعد — المنفذ غير الرسمي ينهار | [ZIP للريكفري](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> ملفات البرامج الثابتة ملك لشركاتها. الروابط تشير إلى خوادم الشركات أو أرشيفات عامة؛ لا يتضمن AEmulator أي برامج ثابتة.

## 🚀 البدء السريع

1. نزّل ملف APK من [Releases](https://github.com/uxazu/aemulator/releases) وثبّته.
2. نزّل برنامجًا ثابتًا من الجدول أعلاه إلى هاتفك.
3. افتح AEmulator ← **إضافة برنامج ثابت** واختر الملف. يستغرق الاستيراد بضع دقائق.
4. اضغط **تشغيل**. الإقلاع الأول أبطأ: النظام يحسّن التطبيقات.
5. القائمة ⋮ للصوت وزر التشغيل والسجل؛ وزر ⚙️ للإعدادات واللغة.

## 📋 المتطلبات

- أندرويد 8.0+ على هاتف ARM بمعمارية 64 بت (arm64-v8a)
- نحو 1–3 غيغابايت لكل برنامج ثابت
- يُنصح بمعالج Snapdragon / Dimensity / Tensor حديث

## ⚙️ كيف يعمل

تعمل كل عملية ضيف تحت QEMU معدّل بوضع المستخدم. يحل خادم binder محل برنامج تشغيل النواة، وينقل جسر GL استدعاءات OpenGL ES إلى معالج الرسومات، وتكيّف مكتبات صغيرة (HAL للصوت، وغلاف audio policy، وطبقة LD_PRELOAD) شفرة الشركات مع المحاكي. يقرأ المستورد البرنامج الثابت ويجد سكربتات init في صورة boot ويبني خطة تشغيل الخدمات.

## 🛠️ البناء من المصدر

تحتاج JDK 17 وAndroid SDK 36 وNDK r28. تُبنى مكتبات الضيف عبر `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 شكر وتقدير

نشأ AEmulator من محاكيات HTC Desire HD وHTC One M7 التي صنعها [المطوّر الأصلي](https://t.me/istratii_tech)، ولولا محركه ما وُجد هذا المشروع.

## 💙 ادعم المشروع

إن أعاد إليك AEmulator هاتفًا أحببته، يمكنك دعم التطوير:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 روابط

- 🌐 الموقع: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 قناة تيليجرام: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 المطوّر: [uxazu](https://github.com/uxazu)
- 🧬 المطوّر الأصلي: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 الترخيص

GPL-3.0. أندرويد والعلامات التجارية والبرامج الثابتة ملك لأصحابها.

</div>
