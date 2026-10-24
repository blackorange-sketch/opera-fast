"""Icon codepoints + Material Icons font registration for OperaFast.

If the font file is present, uses Material Icons glyphs (looks like real
Android browsers). If not, falls back to ASCII so buttons are still
distinguishable and clickable.
"""
import os

from kivy.core.text import LabelBase
from kivy.resources import resource_find

# --- Material Icons codepoints (https://fonts.google.com/icons) ---
ICON_BACK    = "\ue5c4"   # arrow_back
ICON_FORWARD = "\ue5c8"   # arrow_forward
ICON_RELOAD  = "\ue5d5"   # refresh
ICON_TABS    = "\ue3c2"   # filter_none (overlapping squares — standard tab icon)
ICON_MENU    = "\ue5d4"   # more_vert

# --- ASCII fallbacks (definitely render in any font) ---
FB_BACK    = "<"
FB_FORWARD = ">"
FB_RELOAD  = "R"
FB_TABS    = "T"
FB_MENU    = "M"

FONT_NAME = "MaterialIcons"
_FONT_REL = "assets/fonts/MaterialIcons-Regular.ttf"

_registered = False
_used_path = None

# Try several resolution methods — relative to cwd, absolute, and via
# kivy's resource_find (which knows about APK-bundled assets on Android).
_candidates = [
    _FONT_REL,
    os.path.abspath(_FONT_REL),
    resource_find(_FONT_REL) or "",
    resource_find("fonts/MaterialIcons-Regular.ttf") or "",
]

for cand in _candidates:
    if not cand:
        continue
    if not os.path.exists(cand):
        continue
    try:
        LabelBase.register(name=FONT_NAME, fn_regular=cand)
        _registered = True
        _used_path = cand
        print(f"[icons] registered Material Icons from: {cand}")
        break
    except Exception as e:
        print(f"[icons] register failed for {cand}: {e}")

if not _registered:
    print("[icons] WARNING: Material Icons not loaded, using ASCII fallback")

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
