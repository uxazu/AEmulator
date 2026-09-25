<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Firmware clásico de Android — HTC Sense, TouchWiz, MIUI, AOSP — en un teléfono moderno. Sin root, sin PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · **🇪🇸 Español** · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator arranca un sistema Android 2.3–4.4 real directamente desde un archivo de firmware: ZIP de recovery, archivo de Odin o imagen de fábrica de Google. El código ARM antiguo se traduce con un QEMU modificado, el binder del kernel se emula, los gráficos usan la GPU del teléfono y el sonido pasa por la pila de audio de Android — todo dentro de una app normal.

## ✨ Características

- Importa casi cualquier formato: ZIP CWM/TWRP, Odin `.tar.md5` de Samsung, imagen de fábrica `.tgz` de Google, `system.img`, OTA `system.new.dat.br`
- Las capas de los fabricantes funcionan tal cual: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Gráficos por hardware con el puente GL, sonido, táctil y multitáctil, red con proxy TLS moderno
- Carpeta compartida de tarjeta de memoria para APK, música y fotos
- Interfaz Material 3 Expressive en 18 idiomas
- Gratis y de código abierto (GPL-3.0)

## 📱 Firmware compatible

Probado en un Xiaomi 15 (Snapdragon 8 Elite, Android 16). Vale la pena probar cualquier firmware ARM de 32 bits para Android 2.3–4.4; la lista solo incluye imágenes comprobadas.

| Dispositivo | Android | Capa | Estado | Descarga |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Funciona | [archivo listo para importar](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Funciona | [archivo listo para importar](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Funciona — el sonido funciona | [imagen de fábrica de Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Funciona — el sonido funciona | [imagen de fábrica de Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Funciona — diálogo de error de Bluetooth | [paquete Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Parcial — aún sin sonido | [paquete Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 En progreso — arranca hasta el asistente, en estabilización | [ZIP de recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Aún no — el port no oficial falla | [ZIP de recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Los archivos de firmware pertenecen a sus fabricantes. Los enlaces llevan a servidores de fabricantes o archivos públicos; AEmulator no incluye firmware.

## 🚀 Inicio rápido

1. Descarga el APK desde [Releases](https://github.com/uxazu/aemulator/releases) e instálalo.
2. Descarga un firmware de la tabla anterior en el teléfono.
3. Abre AEmulator → **Añadir firmware** y elige el archivo. La importación tarda unos minutos.
4. Pulsa **Iniciar**. El primer arranque es más lento: el sistema optimiza las apps.
5. Menú ⋮ para volumen, botón de encendido y registro; ⚙️ abre ajustes e idioma.

## 📋 Requisitos

- Android 8.0+ en un teléfono ARM de 64 bits (arm64-v8a)
- Unos 1–3 GB libres por firmware
- Se recomienda un Snapdragon / Dimensity / Tensor reciente

## ⚙️ Cómo funciona

Cada proceso invitado se ejecuta con un QEMU de modo usuario modificado. Un demonio binder sustituye al controlador del kernel, un puente GL envía las llamadas OpenGL ES a la GPU y pequeñas bibliotecas invitadas (HAL de audio, envoltorio de audio policy, shim LD_PRELOAD) adaptan el código del fabricante al emulador. El importador lee el firmware, encuentra los scripts init en la imagen boot y crea el plan de arranque de los servicios.

## 🛠️ Compilar desde el código

Necesitas JDK 17, Android SDK 36 y NDK r28. Las bibliotecas del invitado se compilan con `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Créditos

AEmulator nació de los emuladores de HTC Desire HD y HTC One M7 del [autor original](https://t.me/istratii_tech); sin su motor este proyecto no existiría.

## 💙 Apoya el proyecto

Si AEmulator te devolvió un teléfono querido, puedes apoyar el desarrollo:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Enlaces

- 🌐 Sitio web: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Canal de Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Autor: [uxazu](https://github.com/uxazu)
- 🧬 Autor original: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Licencia

GPL-3.0. Android, las marcas y el firmware pertenecen a sus dueños.
