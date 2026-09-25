#!/usr/bin/env python3
"""Генерирует res/values*/strings.xml и res/xml/locales_config.xml из langs/*.py.
Запуск: python app/l10n/gen.py  (английский — язык по умолчанию, values/)."""
import os, runpy, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "res")
sys.path.insert(0, HERE)
from keys import KEYS  # noqa: E402

# код языка → каталог ресурсов Android
DIRS = {
    "en": "values", "ru": "values-ru", "uk": "values-uk", "de": "values-de", "fr": "values-fr",
    "es": "values-es", "pt-BR": "values-pt-rBR", "it": "values-it", "pl": "values-pl", "tr": "values-tr",
    "ar": "values-ar", "fa": "values-fa", "hi": "values-hi", "id": "values-in", "vi": "values-vi",
    "zh-CN": "values-zh-rCN", "ja": "values-ja", "ko": "values-ko",
}


def esc(s: str) -> str:
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def load(code):
    return runpy.run_path(os.path.join(HERE, "langs", code + ".py"))["S"]


def main():
    en = load("en")
    missing = [k for k in KEYS if k not in en]
    assert not missing, f"en: нет ключей {missing}"
    for code, d in DIRS.items():
        s = load(code)
        lack = [k for k in KEYS if k not in s]
        extra = [k for k in s if k not in KEYS]
        if lack or extra:
            print(f"{code}: нет {lack} лишние {extra}")
        out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- создано app/l10n/gen.py, правьте langs/*.py -->", "<resources>"]
        if code == "en":
            out.append('    <string name="app_name" translatable="false">AEmulator</string>')
        for k in KEYS:
            if k in s:
                out.append(f'    <string name="{k}">{esc(s[k])}</string>')
        out.append("</resources>\n")
        path = os.path.join(RES, d)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(out))
    tags = [c for c in DIRS]
    xml = ['<?xml version="1.0" encoding="utf-8"?>', '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">']
    xml += [f'    <locale android:name="{t}" />' for t in tags]
    xml.append("</locale-config>\n")
    os.makedirs(os.path.join(RES, "xml"), exist_ok=True)
    with open(os.path.join(RES, "xml", "locales_config.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(xml))
    print(f"готово: {len(DIRS)} языков, {len(KEYS)} строк")


if __name__ == "__main__":
    main()
