"""OperaFast top toolbar (Opera-style dark theme)."""
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


class _IconButton(Button):
    """Flat transparent button that behaves like a browser icon."""

    def __init__(self, text="", **kw):
        super().__init__(**kw)
        self.text = text
        self.background_normal = ""
        self.background_down = ""
        self.background_color = (0, 0, 0, 0)
        self.color = TEXT_PRIMARY
        self.font_size = "20sp"
        self.size_hint_x = None
        self.width = dp(ICON_BUTTON_WIDTH_DP)


class Toolbar(BoxLayout):
    """Top toolbar with back/forward/reload/url/tabs/menu.

    callbacks: dict with optional keys
        'on_back', 'on_forward', 'on_reload', 'on_go'(url), 'on_tabs', 'on_menu'
    """

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
        self.btn_back = _IconButton(text="\u2190")
        self.btn_back.bind(on_release=self._cb("on_back"))
        self.add_widget(self.btn_back)

        self.btn_forward = _IconButton(text="\u2192")
        self.btn_forward.bind(on_release=self._cb("on_forward"))
        self.add_widget(self.btn_forward)

        self.btn_reload = _IconButton(text="\u27f3")
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

        self.btn_tabs = _IconButton(text="\u29c9")  # ⧉
        self.btn_tabs.bind(on_release=self._cb("on_tabs"))
        self.add_widget(self.btn_tabs)

        self.btn_menu = _IconButton(text="\u22ee")  # ⋮
        self.btn_menu.bind(on_release=self._cb("on_menu"))
        self.add_widget(self.btn_menu)

    def _on_enter(self, *_):
        text = self.url_input.text.strip()
        if text:
            self._cb("on_go")(text)

    def _on_focus_change(self, _inst, focused):
        self.url_input.background_color = BG_INPUT_FOCUS if focused else BG_INPUT

    def set_url(self, url: str) -> None:
        """Update URL text unless user is actively typing."""
        if not self.url_input.focus:
            self.url_input.text = url or ""

    def set_tab_count(self, n: int) -> None:
        """Display tab count on the tabs icon (Opera style: number in brackets)."""
        self.btn_tabs.text = f"\u29c9 {n}" if n > 1 else "\u29c9"
