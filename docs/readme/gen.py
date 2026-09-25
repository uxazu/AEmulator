#!/usr/bin/env python3
"""Собирает README.md (английский) и docs/i18n/README.<код>.md из data.py + texts.py.
Запуск из корня репозитория: python docs/readme/gen.py"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
from data import *  # noqa
from texts import T  # noqa

RTL = {"ar", "fa"}


def path_of(code, from_code):
    """Относительная ссылка на README языка code из файла языка from_code."""
    if code == "en":
        return "README.md" if from_code == "en" else "../../README.md"
    return f"docs/i18n/README.{code}.md" if from_code == "en" else f"README.{code}.md"


def render(code):
    t = T[code]
    up = "" if code == "en" else "../../"
    L = []
    if code in RTL:
        L.append('<div dir="rtl">\n')
    L.append('<div align="center">\n')
    L.append(f'<img src="{up}docs/assets/logo.png" width="128" alt="AEmulator logo"/>\n')
    L.append("# AEmulator\n")
    L.append(f"**{t['tagline']}**\n")
    L.append(
        f'[![Version](https://img.shields.io/badge/version-{VERSION}-3D5AFE?style=for-the-badge)]({REPO}/releases) '
        f'[![License](https://img.shields.io/badge/license-GPL--3.0-3D5AFE?style=for-the-badge)]({up}LICENSE) '
        f'[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)]({REPO}) '
        f'[![Telegram](https://img.shields.io/badge/Telegram-channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)]({CHANNEL}) '
        f'[![Website](https://img.shields.io/badge/site-aemulator.gt.tc-111?style=for-the-badge)]({SITE})\n')
    L.append(" · ".join(
        (f"**{flag} {name}**" if c == code else f"[{flag} {name}]({path_of(c, code)})") for c, name, flag in LANGS) + "\n")
    L.append("</div>\n")
    L.append("---\n")
    L.append(t["about"] + "\n")
    L.append(f"## ✨ {t['feat_title']}\n")
    L += [f"- {f}" for f in t["feats"]]
    L.append("")
    L.append(f"## 📱 {t['fw_title']}\n")
    L.append(t["fw_intro"] + "\n")
    L.append(f"| {t['c_device']} | {t['c_android']} | {t['c_skin']} | {t['c_status']} | {t['c_download']} |")
    L.append("|---|:---:|---|---|---|")
    for dev, ver, skin, st, note, url, kind in FIRMWARE:
        status = f"{STATUS_ICON[st]} {t['st_' + st]}" + (f" — {t['n_' + note]}" if note else "")
        L.append(f"| {dev} | {ver} | {skin} | {status} | [{t['dl_' + kind]}]({url}) |")
    L.append("")
    L.append(f"> {t['fw_legal']}\n")
    L.append(f"## 🚀 {t['start_title']}\n")
    L += [f"{i + 1}. {s.format(repo=REPO)}" for i, s in enumerate(t["steps"])]
    L.append("")
    L.append(f"## 📋 {t['req_title']}\n")
    L += [f"- {r}" for r in t["reqs"]]
    L.append("")
    L.append(f"## ⚙️ {t['how_title']}\n")
    L.append(t["how_text"] + "\n")
    L.append(f"## 🛠️ {t['build_title']}\n")
    L.append(t["build_text"] + "\n")
    L.append("```bash\ngit clone https://github.com/uxazu/aemulator.git\ncd aemulator\n./gradlew assembleRelease\n```\n")
    L.append(f"## 🙏 {t['credits_title']}\n")
    L.append(t["credits_text"].format(orig=ORIGINAL) + "\n")
    L.append(f"## 💙 {t['support_title']}\n")
    L.append(t["support_text"] + "\n")
    L.append(f"- 💳 [dalink.to/uxazu]({DONATE})")
    L.append(f"- 💵 USDT (TRC20): `{USDT}`")
    L.append(f"- 💎 TON: `{TON}`\n")
    L.append(f"## 🔗 {t['links_title']}\n")
    L.append(f"- 🌐 {t['l_site']}: [aemulator.gt.tc]({SITE})")
    L.append(f"- 📣 {t['l_channel']}: [@aemulatorofficial]({CHANNEL})")
    L.append(f"- 👤 {t['l_author']}: [uxazu]({AUTHOR})")
    L.append(f"- 🧬 {t['l_orig']}: [t.me/istratii_tech]({ORIGINAL})\n")
    L.append(f"## 📄 {t['license_title']}\n")
    L.append(t["license_text"] + "\n")
    if code in RTL:
        L.append("</div>\n")
    return "\n".join(L)


def main():
    for code, _, _ in LANGS:
        assert code in T, code
        out = os.path.join(ROOT, "README.md") if code == "en" else os.path.join(ROOT, "docs", "i18n", f"README.{code}.md")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8", newline="\n") as f:
            f.write(render(code))
    print(f"README: {len(LANGS)} языков")


if __name__ == "__main__":
    main()
