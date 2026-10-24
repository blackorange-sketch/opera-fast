"""OperaFast top toolbar (Opera-style dark theme, Material Icons)."""
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.textinput import TextInput
from kivy.graphics import Color, Rectangle

from ui.theme import (
    BG_TOOLBAR, BG_INPUT, BG_INPUT_FOCUS,
    TEXT_PRIMARY, TEXT_MUTED, ACCENT,
    TOOLBAR_HEIGHT_DP, ICON_BUTTON_WIDTH_DP, URL_INPUT_HEIGHT_DP,
)
from ui.icons import (
    icon_back, icon_forward, icon_reload, icon_tabs, icon_menu,
    ICONS_AVAILABLE,
)


class _IconButton(Button):
    """Flat transparent button that renders a Material Icons glyph."""

    def __init__(self, codepoint="", font_name=None, **kw):
        super().__init__(**kw)
        self.text = codepoint
        if font_name:
            self.font_name = font_name
        self.background_normal = ""
        self.background_down = ""
        self.background_color = (0, 0, 0, 0)
        self.color = TEXT_PRIMARY
        # Material Icons look best at 24sp; ASCII fallback is smaller.
        self.font_size = "24sp" if ICONS_AVAILABLE else "18sp"
        self.size_hint_x = None
        self.width = dp(ICON_BUTTON_WIDTH_DP)


class Toolbar(BoxLayout):
    """Top toolbar with back/forward/reload/url/tabs/menu."""

    def __init__(self, callbacks=None, **kw):
        super().__init__(
            orientation="horizontal",
            size_hint_y=None,
            height=dp(TOOLBAR_HEIGHT_DP),
            spacing=dp(2),
            padding=[dp(4), dp(8), dp(4), dp(8)],
            **kw,
        )
        self.callbacks = callbacks or {}

        with self.canvas.before:
            Color(*BG_TOOLBAR)
            self._bg_rect = Rectangle(pos=self.pos, size=self.size)
        self.bind(pos=self._update_bg, size=self._update_bg)

        self._build()

    def _update_bg(self, *_):
        self._bg_rect.pos = self.pos
        self._bg_rect.size = self.size

    def _cb(self, name, default=None):
        return self.callbacks.get(name, default or (lambda *_: None))

    def _build(self):
        cp, fn = icon_back()
        self.btn_back = _IconButton(codepoint=cp, font_name=fn)
        self.btn_back.bind(on_release=self._cb("on_back"))
        self.add_widget(self.btn_back)

        cp, fn = icon_forward()
        self.btn_forward = _IconButton(codepoint=cp, font_name=fn)
        self.btn_forward.bind(on_release=self._cb("on_forward"))
        self.add_widget(self.btn_forward)

        cp, fn = icon_reload()
        self.btn_reload = _IconButton(codepoint=cp, font_name=fn)
        self.btn_reload.bind(on_release=self._cb("on_reload"))
        self.add_widget(self.btn_reload)

        self.url_input = TextInput(
            text="",
            hint_text="Search or enter address",
            multiline=False,
            size_hint=(1, None),
            height=dp(URL_INPUT_HEIGHT_DP),
            padding=[dp(12), dp(8)],
            background_normal="",
            background_active="",
            background_color=BG_INPUT,
            foreground_color=TEXT_PRIMARY,
            hint_text_color=TEXT_MUTED,
            cursor_color=ACCENT,
            font_size="14sp",
        )
        self.url_input.bind(on_text_validate=self._on_enter)
        self.url_input.bind(focus=self._on_focus_change)
        self.add_widget(self.url_input)

        cp, fn = icon_tabs()
        self.btn_tabs = _IconButton(codepoint=cp, font_name=fn)
        self.btn_tabs.bind(on_release=self._cb("on_tabs"))
        self.add_widget(self.btn_tabs)

        cp, fn = icon_menu()
        self.btn_menu = _IconButton(codepoint=cp, font_name=fn)
        self.btn_menu.bind(on_release=self._cb("on_menu"))
        self.add_widget(self.btn_menu)

    def _on_enter(self, *_):
        text = self.url_input.text.strip()
        if text:
            self._cb("on_go")(text)

    def _on_focus_change(self, _inst, focused):
        self.url_input.background_color = BG_INPUT_FOCUS if focused else BG_INPUT

    def set_url(self, url: str) -> None:
        if not self.url_input.focus:
            self.url_input.text = url or ""

    def set_tab_count(self, n: int) -> None:
        # Tab count indicator will be added in Phase 1.3 as a badge,
        # not by overwriting the icon (Material Icons has no "2" glyph).
        pass
