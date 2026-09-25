# Данные README берутся из site/config.js — один источник для сайта и GitHub.
# Тексты на языках — в texts.py.
import json, os

_cfg_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "site", "config.js")
_src = open(_cfg_path, encoding="utf-8").read()
_cfg = json.loads(_src[_src.index("window.AEMU_CONFIG =") + len("window.AEMU_CONFIG ="):].strip().rstrip(";"))

VERSION = _cfg["version"]
_L = _cfg["links"]
REPO, SITE, CHANNEL, AUTHOR, ORIGINAL = _L["repo"], _L["site"], _L["channel"], _L["author"], _L["original"]
DONATE, USDT, TON = _L["donate"], _L["usdt"], _L["ton"]
FIRMWARE = [(f["device"], f["android"], f["skin"], f["status"], f.get("note"), f["url"], f["kind"]) for f in _cfg["firmware"]]

STATUS_ICON = {"ok": "🟢", "partial": "🟡", "wip": "🟠", "no": "🔴"}

LANGS = [  # код, самоназвание, флаг
    ("en", "English", "🇬🇧"), ("ru", "Русский", "🇷🇺"), ("uk", "Українська", "🇺🇦"), ("de", "Deutsch", "🇩🇪"),
    ("fr", "Français", "🇫🇷"), ("es", "Español", "🇪🇸"), ("pt-BR", "Português", "🇧🇷"), ("it", "Italiano", "🇮🇹"),
    ("pl", "Polski", "🇵🇱"), ("tr", "Türkçe", "🇹🇷"), ("ar", "العربية", "🇸🇦"), ("fa", "فارسی", "🇮🇷"),
    ("hi", "हिन्दी", "🇮🇳"), ("id", "Indonesia", "🇮🇩"), ("vi", "Tiếng Việt", "🇻🇳"), ("zh-CN", "简体中文", "🇨🇳"),
    ("ja", "日本語", "🇯🇵"), ("ko", "한국어", "🇰🇷"),
]
