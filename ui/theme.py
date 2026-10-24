"""OperaFast theme — Opera-like dark palette."""
from kivy.utils import get_color_from_hex

# Background layers
BG_APP         = get_color_from_hex("#1A1A1A")
BG_TOOLBAR     = get_color_from_hex("#252525")
BG_INPUT       = get_color_from_hex("#3A3A3A")
BG_INPUT_FOCUS = get_color_from_hex("#4A4A4A")

# Text
TEXT_PRIMARY   = get_color_from_hex("#FFFFFF")
TEXT_MUTED     = get_color_from_hex("#A0A0A0")

# Accent (Opera red)
ACCENT         = get_color_from_hex("#FF1B2D")
ACCENT_PRESS   = get_color_from_hex("#C41527")

# Buttons
BTN_BG         = get_color_from_hex("#333333")
BTN_PRESS      = get_color_from_hex("#444444")

# Layout
TOOLBAR_HEIGHT_DP = 52
ICON_BUTTON_WIDTH_DP = 40
URL_INPUT_HEIGHT_DP = 36
