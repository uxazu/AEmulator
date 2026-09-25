<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**클래식 Android 펌웨어 — HTC Sense, TouchWiz, MIUI, AOSP — 를 최신 휴대폰에서. 루팅도 PC도 필요 없습니다.**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · [🇨🇳 简体中文](README.zh-CN.md) · [🇯🇵 日本語](README.ja.md) · **🇰🇷 한국어**

</div>

---

AEmulator는 펌웨어 파일에서 진짜 Android 2.3–4.4 시스템을 바로 부팅합니다: 리커버리 ZIP, Odin 아카이브, Google 팩토리 이미지. 오래된 ARM 코드는 수정된 QEMU가 변환하고, 커널 binder를 에뮬레이션하며, 그래픽은 휴대폰 GPU로, 소리는 Android 오디오로 처리합니다. 모두 평범한 앱 안에서 동작합니다.

## ✨ 기능

- 거의 모든 형식 가져오기: CWM/TWRP ZIP, 삼성 Odin `.tar.md5`, Google 팩토리 `.tgz`, `system.img`, OTA `system.new.dat.br`
- 제조사 UI가 그대로 동작: HTC Sense, Samsung TouchWiz, MIUI, AOSP
- GL 브리지 하드웨어 그래픽, 소리, 터치와 멀티터치, 최신 TLS 프록시 네트워크
- APK·음악·사진을 위한 공유 메모리 카드 폴더
- 18개 언어의 Material 3 Expressive 인터페이스
- 무료 오픈 소스(GPL-3.0)

## 📱 지원 펌웨어

Xiaomi 15(Snapdragon 8 Elite, Android 16)에서 테스트했습니다. Android 2.3–4.4용 32비트 ARM 펌웨어라면 시도해 볼 만합니다. 목록은 확인된 이미지만 담고 있습니다.

| 기기 | Android | UI | 상태 | 다운로드 |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 동작 | [바로 가져올 수 있는 아카이브](https://drive.google.com/file/d/1GGOOw60JLXXA5yFbWu9SubTZ_1W1vxjE/view?usp=sharing) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 동작 | [바로 가져올 수 있는 아카이브](https://drive.google.com/file/d/1Gcz8uD6fAXNt93kqorGHA38TW_bQ12_d/view?usp=drive_link) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 동작 — 소리 동작 | [Google 팩토리 이미지](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 동작 — 소리 동작 | [Google 팩토리 이미지](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 동작 — Bluetooth 오류 창 | [Odin 패키지](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 일부 — 아직 소리 없음 | [Odin 패키지](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 작업 중 — 설정 마법사까지 부팅, 안정화 중 | [리커버리 ZIP](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 아직 — 비공식 포팅은 충돌 | [리커버리 ZIP](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> 펌웨어 파일은 각 제조사의 것입니다. 링크는 제조사 서버나 공개 아카이브로 연결되며 AEmulator에는 펌웨어가 포함되지 않습니다.

## 🚀 빠른 시작

1. [Releases](https://github.com/uxazu/aemulator/releases)에서 APK를 받아 설치합니다.
2. 위 표에서 펌웨어를 휴대폰으로 받습니다.
3. AEmulator → **펌웨어 추가**에서 파일을 고릅니다. 가져오기는 몇 분 걸립니다.
4. **시작**을 누릅니다. 첫 부팅은 앱 최적화로 더 오래 걸립니다.
5. ⋮ 메뉴에서 볼륨·전원 버튼·로그, ⚙️에서 설정과 언어.

## 📋 요구 사항

- 64비트 ARM 휴대폰(arm64-v8a)의 Android 8.0+
- 펌웨어당 약 1–3GB 여유 공간
- 최신 Snapdragon / Dimensity / Tensor 권장

## ⚙️ 작동 방식

각 게스트 프로세스는 수정된 사용자 모드 QEMU에서 실행됩니다. binder 데몬이 커널 드라이버를 대신하고, GL 브리지가 OpenGL ES 호출을 GPU로 전달하며, 작은 게스트 라이브러리(오디오 HAL, audio policy 래퍼, LD_PRELOAD 심)가 제조사 코드를 에뮬레이터에 맞춥니다. 가져오기 도구는 펌웨어를 읽고 boot 이미지의 init 스크립트로 서비스 시작 계획을 만듭니다.

## 🛠️ 소스에서 빌드

JDK 17, Android SDK 36, NDK r28이 필요합니다. 게스트 라이브러리는 `native/*/build.sh`로 빌드합니다.

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 감사의 말

AEmulator는 [원작자](https://t.me/istratii_tech)의 HTC Desire HD·HTC One M7 에뮬레이터에서 시작되었습니다. 그의 엔진이 없었다면 이 프로젝트도 없었습니다.

## 💙 프로젝트 후원

AEmulator로 추억의 휴대폰을 되살렸다면 개발을 후원할 수 있습니다:

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 링크

- 🌐 웹사이트: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 텔레그램 채널: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 제작자: [uxazu](https://github.com/uxazu)
- 🧬 원작자: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 라이선스

GPL-3.0. Android, 상표, 펌웨어는 각 소유자에게 있습니다.
