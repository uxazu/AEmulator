<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**Firmwares clássicos do Android — HTC Sense, TouchWiz, MIUI, AOSP — em um celular moderno. Sem root, sem PC.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · **🇧🇷 Português** · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

O AEmulator inicia um sistema Android 2.3–4.4 real direto de um arquivo de firmware: ZIP de recovery, pacote do Odin ou imagem de fábrica do Google. O código ARM antigo é traduzido por um QEMU modificado, o binder do kernel é emulado, os gráficos usam a GPU do celular e o som passa pelo áudio do Android — tudo dentro de um app comum.

## ✨ Recursos

- Importa quase qualquer formato: ZIP CWM/TWRP, Odin `.tar.md5` da Samsung, imagem de fábrica `.tgz` do Google, `system.img`, OTA `system.new.dat.br`
- Interfaces das fabricantes funcionam como vieram: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- Gráficos por hardware pela ponte GL, som, toque e multitoque, rede com proxy TLS moderno
- Pasta compartilhada de cartão de memória para APKs, músicas e fotos
- Interface Material 3 Expressive em 18 idiomas
- Gratuito e de código aberto (GPL-3.0)

## 📱 Firmwares compatíveis

Testado em um Xiaomi 15 (Snapdragon 8 Elite, Android 16). Vale tentar qualquer firmware ARM de 32 bits do Android 2.3–4.4; a lista traz só imagens verificadas.

| Aparelho | Android | Interface | Status | Download |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 Funciona | [arquivo pronto para importar](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 Funciona | [arquivo pronto para importar](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 Funciona — som funciona | [imagem de fábrica do Google](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 Funciona — som funciona | [imagem de fábrica do Google](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 Funciona — janela de erro do Bluetooth | [pacote Odin](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 Parcial — ainda sem som | [pacote Odin](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 Em andamento — inicia até a configuração, em estabilização | [ZIP de recovery](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 Ainda não — port não oficial trava | [ZIP de recovery](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> Os firmwares pertencem às fabricantes. Os links levam aos servidores delas ou a arquivos públicos; o AEmulator não inclui firmwares.

## 🚀 Início rápido

1. Baixe o APK em [Releases](https://github.com/uxazu/aemulator/releases) e instale.
2. Baixe um firmware da tabela acima no celular.
3. Abra o AEmulator → **Adicionar firmware** e escolha o arquivo. A importação leva alguns minutos.
4. Toque em **Iniciar**. A primeira inicialização é mais lenta: o sistema otimiza os apps.
5. Menu ⋮ para volume, botão liga/desliga e registro; ⚙️ abre configurações e idioma.

## 📋 Requisitos

- Android 8.0+ em celular ARM de 64 bits (arm64-v8a)
- Cerca de 1–3 GB livres por firmware
- Recomendado: Snapdragon / Dimensity / Tensor recente

## ⚙️ Como funciona

Cada processo convidado roda num QEMU de modo usuário modificado. Um daemon binder substitui o driver do kernel, uma ponte GL envia as chamadas OpenGL ES para a GPU e pequenas bibliotecas (HAL de áudio, wrapper de audio policy, shim LD_PRELOAD) adaptam o código das fabricantes ao emulador. O importador lê o firmware, acha os scripts init na imagem boot e monta o plano de início dos serviços.

## 🛠️ Compilar do código-fonte

É preciso JDK 17, Android SDK 36 e NDK r28. As bibliotecas do convidado são compiladas com `native/*/build.sh`.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 Créditos

O AEmulator nasceu dos emuladores de HTC Desire HD e HTC One M7 do [autor original](https://t.me/istratii_tech) — sem o motor dele este projeto não existiria.

## 💙 Apoie o projeto

Se o AEmulator trouxe de volta um celular querido, você pode apoiar o desenvolvimento:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 Links

- 🌐 Site: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Canal no Telegram: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 Autor: [uxazu](https://github.com/uxazu)
- 🧬 Autor original: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 Licença

GPL-3.0. Android, marcas e firmwares pertencem aos seus donos.
