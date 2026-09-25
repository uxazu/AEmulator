/*
 * AEmulator — настройки сайта (без бэкенда). Правьте этот файл и обновите страницу.
 * Его же читает генератор README (docs/readme/gen.py), так что таблица на GitHub совпадёт с сайтом.
 *
 * firmware — список ПРОВЕРЕННЫХ прошивок, по одной записи:
 *   device  — название устройства (как есть, без перевода)
 *   android — версия Android, например "4.4.2"
 *   skin    — оболочка: "AOSP", "MIUI 8", "TouchWiz", "Sense 5.5"…
 *   status  — "ok" (работает) | "partial" (частично) | "wip" (в работе) | "no" (пока нет)
 *   note    — примечание или null: "sound" | "bt" | "nosound" | "wip" | "crash" | "lollipop" | "arm64"
 *             (переводы примечаний — в i18n.js, ключи n_*)
 *   url     — ссылка на скачивание
 *   kind    — подпись кнопки: "rootfs" | "factory" | "odin" | "zip" | "ftf"
 *
 * Внутри AEMU_CONFIG должен оставаться чистый JSON (двойные кавычки, без комментариев и хвостовых запятых).
 */
window.AEMU_CONFIG = {
  "version": "0.0.0.1",
  "links": {
    "repo": "https://github.com/uxazu/aemulator",
    "site": "https://aemulator.gt.tc",
    "channel": "https://t.me/aemulatorofficial",
    "author": "https://github.com/uxazu",
    "original": "https://t.me/istratii_tech",
    "donate": "https://dalink.to/uxazu",
    "usdt": "TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF",
    "ton": "UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V"
  },
  "firmware": [
    {
      "device": "HTC Desire HD",
      "android": "2.3.3",
      "skin": "HTC Sense",
      "status": "ok",
      "note": null,
      "url": "https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing",
      "kind": "rootfs"
    },
    {
      "device": "HTC One M7",
      "android": "4.4.2",
      "skin": "Sense 5.5",
      "status": "ok",
      "note": null,
      "url": "https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link",
      "kind": "rootfs"
    },
    {
      "device": "Google Galaxy Nexus (takju)",
      "android": "4.3",
      "skin": "AOSP",
      "status": "ok",
      "note": "sound",
      "url": "https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz",
      "kind": "factory"
    },
    {
      "device": "Google Nexus 4 (occam)",
      "android": "4.4.4",
      "skin": "AOSP",
      "status": "ok",
      "note": "sound",
      "url": "https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz",
      "kind": "factory"
    },
    {
      "device": "Samsung Galaxy S II (GT-I9100)",
      "android": "2.3.3",
      "skin": "TouchWiz",
      "status": "ok",
      "note": "bt",
      "url": "https://archive.org/download/i9100xexe/I9100XEKE1.zip",
      "kind": "odin"
    },
    {
      "device": "Samsung Galaxy S III (GT-I9300)",
      "android": "4.3",
      "skin": "TouchWiz",
      "status": "partial",
      "note": "nosound",
      "url": "https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip",
      "kind": "odin"
    },
    {
      "device": "Xiaomi Redmi 1S (HM2014011)",
      "android": "4.4.2",
      "skin": "MIUI 8",
      "status": "wip",
      "note": "wip",
      "url": "https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip",
      "kind": "zip"
    },
    {
      "device": "Samsung Galaxy S II (port)",
      "android": "4.4",
      "skin": "MIUI 7",
      "status": "no",
      "note": "crash",
      "url": "https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip",
      "kind": "zip"
    }
  ]
};
