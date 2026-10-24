"""OperaFast speed dial — grid of shortcut icons shown on blank tabs."""
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.graphics import Color, Rectangle, Ellipse
from kivy.metrics import dp
from kivy.utils import get_color_from_hex

from ui.theme import BG_APP, TEXT_PRIMARY, TEXT_MUTED, ACCENT


SHORTCUTS = [
    ("DuckDuckGo", "https://duckduckgo.com", "#DE5833", "D"),
    ("Google",     "https://google.com",     "#4285F4", "G"),
    ("YouTube",    "https://youtube.com",    "#FF0000", "Y"),
    ("Wikipedia",  "https://wikipedia.org",  "#555555", "W"),
    ("GitHub",     "https://github.com",     "#181717", "G"),
    ("Reddit",     "https://reddit.com",     "#FF4500", "R"),
    ("Telegram",   "https://web.telegram.org", "#229ED9", "T"),
    ("X",          "https://x.com",          "#000000", "X"),
]


class _Tile(FloatLayout):
    """A circular shortcut icon with a letter."""

    def __init__(self, name, url, color_hex, letter, on_open, **kw):
        super().__init__(**kw)
        self._color = get_color_from_hex(color_hex)

        # Colored circle centered
        self._circle_color = Color(*self._color)
        self._circle = Ellipse()
        self.canvas.before.add(self._circle_color)
        self.canvas.before.add(self._circle)
        self.bind(pos=self._layout, size=self._layout)

        # Letter in the middle
        self.add_widget(Label(
            text=letter,
            font_size="24sp",
            bold=True,
            color=(1, 1, 1, 1),
        ))

        # Invisible button on top for touch handling
        btn = Button(
            text="",
            background_normal="",
            background_color=(0, 0, 0, 0),
        )
        btn.bind(on_release=lambda *_: on_open(url))
        self.add_widget(btn)

        self._name = name
        self._on_open = on_open
        self._url = url

    def _layout(self, *_):
        d = min(self.width, self.height) * 0.9
        self._circle.size = (d, d)
        self._circle.pos = (
            self.center_x - d / 2,
            self.center_y - d / 2,
        )


class SpeedDial(FloatLayout):
    """Grid of quick-access shortcuts, dark themed."""

    def __init__(self, on_open=None, **kw):
        super().__init__(**kw)
        self.on_open = on_open or (lambda url: None)

        with self.canvas.before:
            Color(*BG_APP)
            self._bg = Rectangle(pos=self.pos, size=self.size)
        self.bind(
            pos=lambda *a: setattr(self._bg, "pos", self.pos),
            size=lambda *a: setattr(self._bg, "size", self.size),
        )

        main = BoxLayout(
            orientation="vertical",
            padding=[dp(20), dp(30), dp(20), dp(20)],
            spacing=dp(20),
        )

        # Title
        main.add_widget(Label(
            text="[b]OperaFast[/b]",
            markup=True,
            size_hint_y=None,
            height=dp(50),
            font_size="26sp",
            color=ACCENT,
        ))

        # Subtitle
        main.add_widget(Label(
            text="Quick links",
            size_hint_y=None,
            height=dp(24),
            font_size="12sp",
            color=TEXT_MUTED,
        ))

        # Grid of shortcut tiles
        grid = GridLayout(
            cols=4,
            spacing=[dp(8), dp(20)],
            size_hint_y=None,
        )
        grid.bind(minimum_height=grid.setter("height"))

        for name, url, color_hex, letter in SHORTCUTS:
            tile_wrap = BoxLayout(
                orientation="vertical",
                size_hint_y=None,
                height=dp(96),
            )
            tile = _Tile(
                name=name, url=url, color_hex=color_hex, letter=letter,
                on_open=self._open,
                size_hint=(1, None),
                height=dp(64),
            )
            tile_wrap.add_widget(tile)
            tile_wrap.add_widget(Label(
                text=name,
                size_hint_y=None,
                height=dp(24),
                font_size="11sp",
                color=TEXT_PRIMARY,
            ))
            grid.add_widget(tile_wrap)

        main.add_widget(grid)
        main.add_widget(BoxLayout())  # spacer

        self.add_widget(main)

    def _open(self, url):
        self.on_open(url)
