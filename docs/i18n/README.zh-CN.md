<div align="center">

<img src="../../docs/assets/logo.png" width="128" alt="AEmulator logo"/>

# AEmulator

**在现代手机上运行经典 Android 固件——HTC Sense、TouchWiz、MIUI、AOSP。无需 root，无需电脑。**

[![Version](https://img.shields.io/badge/version-0.0.0.1-3D5AFE?style=for-the-badge)](https://github.com/uxazu/aemulator/releases) [![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)](../../LICENSE) [![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/uxazu/aemulator) [![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/aemulatorofficial) [![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)](https://aemulator.gt.tc)

[🇬🇧 English](../../README.md) · [🇷🇺 Русский](README.ru.md) · [🇺🇦 Українська](README.uk.md) · [🇩🇪 Deutsch](README.de.md) · [🇫🇷 Français](README.fr.md) · [🇪🇸 Español](README.es.md) · [🇧🇷 Português](README.pt-BR.md) · [🇮🇹 Italiano](README.it.md) · [🇵🇱 Polski](README.pl.md) · [🇹🇷 Türkçe](README.tr.md) · [🇸🇦 العربية](README.ar.md) · [🇮🇷 فارسی](README.fa.md) · [🇮🇳 हिन्दी](README.hi.md) · [🇮🇩 Indonesia](README.id.md) · [🇻🇳 Tiếng Việt](README.vi.md) · **🇨🇳 简体中文** · [🇯🇵 日本語](README.ja.md) · [🇰🇷 한국어](README.ko.md)

</div>

---

AEmulator 直接从固件文件启动真实的 Android 2.3–4.4 系统：Recovery 卡刷包、Odin 包或 Google 出厂镜像。旧的 ARM 代码由改进的 QEMU 转译，内核 binder 被模拟，图形使用手机 GPU 绘制，声音经由 Android 音频系统播放——一切都在一个普通应用里完成。

## ✨ 功能

- 导入几乎所有格式：CWM/TWRP 卡刷 ZIP、三星 Odin `.tar.md5`、Google 出厂 `.tgz`、`system.img`、OTA `system.new.dat.br`
- 厂商系统原样运行：HTC Sense、三星 TouchWiz、MIUI、AOSP
- 经 GL 桥的硬件图形、声音、触控与多点触控，带现代 TLS 代理的网络
- 共享存储卡文件夹，方便放 APK、音乐和照片
- Material 3 Expressive 界面，支持 18 种语言
- 免费开源（GPL-3.0）

## 📱 支持的固件

在 Xiaomi 15（骁龙 8 Elite，Android 16）上测试。任何适用于 Android 2.3–4.4 的 32 位 ARM 固件都值得一试；列表仅包含我们验证过的镜像。

| 设备 | Android | 系统界面 | 状态 | 下载 |
|---|:---:|---|---|---|
| HTC Desire HD | 2.3.3 | HTC Sense | 🟢 可用 | [可直接导入的压缩包](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_DesireHD_2.3.3_Sense_rootfs.tar.gz) |
| HTC One M7 | 4.4.2 | Sense 5.5 | 🟢 可用 | [可直接导入的压缩包](https://github.com/uxazu/aemulator/releases/download/v0.0.0.1/HTC_One_M7_4.4.2_Sense5.5_rootfs.tar.gz) |
| Google Galaxy Nexus (takju) | 4.3 | AOSP | 🟢 可用 — 声音正常 | [Google 出厂镜像](https://dl.google.com/dl/android/aosp/takju-jwr66y-factory-5104ab1d.tgz) |
| Google Nexus 4 (occam) | 4.4.4 | AOSP | 🟢 可用 — 声音正常 | [Google 出厂镜像](https://dl.google.com/dl/android/aosp/occam-ktu84p-factory-b6ac3ad6.tgz) |
| Samsung Galaxy S II (GT-I9100) | 2.3.3 | TouchWiz | 🟢 可用 — 蓝牙错误弹窗 | [Odin 包](https://archive.org/download/i9100xexe/I9100XEKE1.zip) |
| Samsung Galaxy S III (GT-I9300) | 4.3 | TouchWiz | 🟡 部分可用 — 暂无声音 | [Odin 包](https://archive.org/download/i-9300-xxugnj-2-i-9300-oxxgnj-1-xef/I9300XXUGNJ2_I9300OXXGNJ1_XEF.zip) |
| Xiaomi Redmi 1S (HM2014011) | 4.4.2 | MIUI 8 | 🟠 进行中 — 可进入开机向导，正在完善 | [卡刷 ZIP](https://archive.org/download/HM2014011/multirom_HM1STD_V8.5.1.0.KHFCNED_v4.4.2_b10_3f73d79027.zip) |
| Samsung Galaxy S II (port) | 4.4 | MIUI 7 | 🔴 暂不支持 — 非官方移植会崩溃 | [卡刷 ZIP](https://archive.org/download/FullotaMiui7I9100Initial.release/Fullota_Miui7-I9100-Initial.release.zip) |

> 固件归各厂商所有。链接指向厂商服务器或公共存档；AEmulator 不附带任何固件。

## 🚀 快速开始

1. 从 [Releases](https://github.com/uxazu/aemulator/releases) 下载并安装 APK。
2. 把上表中的固件下载到手机。
3. 打开 AEmulator →**添加固件**并选择文件。导入需要几分钟。
4. 点击**启动**。首次启动较慢：系统正在优化应用。
5. ⋮ 菜单可调音量、电源键和日志；⚙️ 打开设置与语言。

## 📋 要求

- 64 位 ARM 手机（arm64-v8a）上的 Android 8.0+
- 每个固件约需 1–3 GB 空间
- 推荐较新的骁龙 / 天玑 / Tensor 芯片

## ⚙️ 工作原理

每个客户机进程运行在改进的用户态 QEMU 中。binder 守护进程取代内核驱动，GL 桥把 OpenGL ES 调用转发给 GPU，小型客户机库（音频 HAL、audio policy 包装、LD_PRELOAD 垫片）让厂商代码适配模拟器。导入器读取固件，在 boot 镜像中找到 init 脚本，并生成系统服务的启动计划。

## 🛠️ 从源码构建

需要 JDK 17、Android SDK 36 和 NDK r28。客户机库通过 `native/*/build.sh` 构建。

```bash
git clone https://github.com/uxazu/aemulator.git
cd aemulator
./gradlew assembleRelease
```

## 🙏 致谢

AEmulator 源自[原作者](https://t.me/istratii_tech)的 HTC Desire HD 与 HTC One M7 模拟器——没有他的引擎就没有本项目。

## 💙 支持项目

如果 AEmulator 让你心爱的手机重获新生，欢迎支持开发：

- 💳 [dalink.to/uxazu](https://dalink.to/uxazu)
- 💵 USDT (TRC20): `TN5cZFQ6BKPKCJZiUqEQKaifaEdtNUqaBF`
- 💎 TON: `UQCDtAs_DWUUKStpnHBOBo72VA7C044PPo1asfpq6vQHAF-V`

## 🔗 链接

- 🌐 网站: [aemulator.gt.tc](https://aemulator.gt.tc)
- 📣 Telegram 频道: [@aemulatorofficial](https://t.me/aemulatorofficial)
- 👤 作者: [uxazu](https://github.com/uxazu)
- 🧬 原作者: [t.me/istratii_tech](https://t.me/istratii_tech)

## 📄 许可证

GPL-3.0。Android、商标及固件归其所有者所有。
