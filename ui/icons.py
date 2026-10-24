"""Icon codepoints + Material Icons font registration for OperaFast."""
import os

from kivy.core.text import LabelBase
from kivy.resources import resource_find

ICON_BACK    = "\ue5c4"
ICON_FORWARD = "\ue5c8"
ICON_RELOAD  = "\ue5d5"
ICON_TABS    = "\ue3c2"
ICON_MENU    = "\ue5d4"

FB_BACK    = "<"
FB_FORWARD = ">"
FB_RELOAD  = "R"
FB_TABS    = "T"
FB_MENU    = "M"

FONT_NAME = "MaterialIcons"

# Build absolute path from this file's location so it works regardless
# of cwd (which differs between desktop, buildozer staging, and APK).
_here = os.path.dirname(os.path.abspath(__file__))     # .../ui/
_root = os.path.dirname(_here)                         # app root
_font_abs = os.path.join(_root, "assets", "fonts", "MaterialIcons-Regular.ttf")

_candidates = [
    _font_abs,
    os.path.join("assets", "fonts", "MaterialIcons-Regular.ttf"),
    resource_find("assets/fonts/MaterialIcons-Regular.ttf") or "",
    resource_find("fonts/MaterialIcons-Regular.ttf") or "",
]

_registered = False
_used_path = None

print(f"[icons] looking for font. __file__={__file__}")
print(f"[icons] root dir: {_root}")
print(f"[icons] candidates: {_candidates}")

for cand in _candidates:
    if not cand:
        continue
    exists = os.path.exists(cand)
    print(f"[icons]   try {cand!r} exists={exists}")
    if not exists:
        continue
    try:
        LabelBase.register(name=FONT_NAME, fn_regular=cand)
        _registered = True
        _used_path = cand
        print(f"[icons] REGISTERED Material Icons from: {cand}")
        break
    except Exception as e:
        print(f"[icons] register failed for {cand}: {e}")

if not _registered:
    print("[icons] WARNING: Material Icons NOT loaded, using ASCII fallback")

ICONS_AVAILABLE = _registered


def _pick(codepoint: str, fallback: str):
    return (codepoint, FONT_NAME) if _registered else (fallback, None)


def icon_back():
    return _pick(ICON_BACK, FB_BACK)


def icon_forward():
    return _pick(ICON_FORWARD, FB_FORWARD)


def icon_reload():
    return _pick(ICON_RELOAD, FB_RELOAD)


def icon_tabs():
    return _pick(ICON_TABS, FB_TABS)


def icon_menu():
    return _pick(ICON_MENU, FB_MENU)
