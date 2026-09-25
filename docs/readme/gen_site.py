#!/usr/bin/env python3
"""Собирает site/i18n.js (переводы сайта) из docs/readme/texts.py. Прошивки и ссылки — в site/config.js.
Запуск из корня репозитория: python docs/readme/gen_site.py"""
import json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import data  # noqa
from texts import T  # noqa


def md(s):
    """Простейший markdown → html для строк README (ссылки, жирный, код)."""
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2" target="_blank" rel="noopener">\1</a>', s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    return s


def main():
    out = {}
    for code, name, flag in data.LANGS:
        t = dict(T[code])
        t["steps"] = [md(x.format(repo=data.REPO)) for x in t["steps"]]
        t["feats"] = [md(x) for x in t["feats"]]
        t["credits_text"] = md(t["credits_text"].format(orig=data.ORIGINAL))
        t["build_text"] = md(t["build_text"])
        out[code] = t
    payload = {"langs": [{"code": c, "name": n, "flag": f} for c, n, f in data.LANGS], "rtl": ["ar", "fa"], "t": out}
    os.makedirs(os.path.join(ROOT, "site"), exist_ok=True)
    with open(os.path.join(ROOT, "site", "i18n.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write("// создано docs/readme/gen_site.py — не править вручную\nwindow.AEMU_I18N = ")
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write(";\n")
    print("site/i18n.js готов")


if __name__ == "__main__":
    main()
