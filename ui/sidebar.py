"""OperaFast tab sidebar — right-side panel listing open tabs."""
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.widget import Widget
from kivy.graphics import Color, Rectangle
from kivy.metrics import dp

from ui.icons import FONT_NAME
from ui.theme import (
    BG_TOOLBAR, BG_INPUT, TEXT_PRIMARY, TEXT_MUTED, ACCENT,
)


class Sidebar(FloatLayout):
    def __init__(self, tabs, callbacks, **kw):
        super().__init__(**kw)
        self.callbacks = callbacks

        # Dim overlay
        with self.canvas.before:
            Color(0, 0, 0, 0.55)
            self._dim = Rectangle(pos=self.pos, size=self.size)
        self.bind(
            pos=lambda *a: setattr(self._dim, "pos", self.pos),
            size=lambda *a: setattr(self._dim, "size", self.size),
        )

        # Right panel — 70% width, not too wide
        panel = BoxLayout(
            orientation="vertical",
            size_hint=(0.70, 1),
            pos_hint={"right": 1},
            padding=[dp(12), dp(12)],
            spacing=dp(8),
        )
        with panel.canvas.before:
            Color(*BG_TOOLBAR)
            self._panel_bg = Rectangle(pos=panel.pos, size=panel.size)
        panel.bind(
            pos=lambda *a: setattr(self._panel_bg, "pos", panel.pos),
            size=lambda *a: setattr(self._panel_bg, "size", panel.size),
        )

        # Header
        header = BoxLayout(size_hint_y=None, height=dp(48))
        header.add_widget(Label(
            text=f"Tabs ({len(tabs)})",
            color=TEXT_PRIMARY,
            font_size="18sp",
            bold=True,
            halign="left",
            valign="middle",
        ))
        btn_close = Button(
            text="\ue5cd",  # ✕
            size_hint_x=None,
            width=dp(48),
            background_normal="",
            background_color=(0, 0, 0, 0),
            color=TEXT_PRIMARY,
            font_size="20sp",
        )
        btn_close.bind(on_release=lambda *_: callbacks["on_close"]())
        header.add_widget(btn_close)
        panel.add_widget(header)

        # + New tab
        btn_new = Button(
            text="+ New tab",
            size_hint_y=None,
            height=dp(48),
            background_normal="",
            background_color=ACCENT,
            color=(1, 1, 1, 1),
            font_size="15sp",
            bold=True,
        )
        btn_new.bind(on_release=lambda *_: callbacks["on_new_tab"]())
        panel.add_widget(btn_new)

        # Scrollable tab list
        sv = ScrollView(size_hint=(1, 1))
        list_box = BoxLayout(
            orientation="vertical",
            size_hint_y=None,
            spacing=dp(4),
        )
        list_box.bind(minimum_height=list_box.setter("height"))

        active_id = callbacks.get("active_id")
        for tab in tabs:
            list_box.add_widget(self._make_row(tab, tab.id == active_id))

        sv.add_widget(list_box)
        panel.add_widget(sv)
        self.add_widget(panel)

    def _make_row(self, tab, is_active):
        row = BoxLayout(
            orientation="horizontal",
            size_hint_y=None,
            height=dp(56),
            spacing=0,
        )

        # Subtle background: active tab a bit lighter
        with row.canvas.before:
            Color(*(BG_INPUT if not is_active else (0.28, 0.28, 0.28, 1)))
            row._bg = Rectangle(pos=row.pos, size=row.size)
        row.bind(
            pos=lambda *a: setattr(row._bg, "pos", row.pos),
            size=lambda *a: setattr(row._bg, "size", row.size),
        )

        # Thin accent strip on the left for the active tab
        if is_active:
            strip = Widget(size_hint_x=None, width=dp(4))
            with strip.canvas.before:
                Color(*ACCENT)
                strip._bg = Rectangle(pos=strip.pos, size=strip.size)
            strip.bind(
                pos=lambda *a: setattr(strip._bg, "pos", strip.pos),
                size=lambda *a: setattr(strip._bg, "size", strip.size),
            )
            row.add_widget(strip)

        title_text = tab.title or tab.url or "New Tab"
        btn_title = Button(
            text=title_text,
            background_normal="",
            background_color=(0, 0, 0, 0),
            color=TEXT_PRIMARY,
            font_size="14sp",
            halign="left",
            valign="middle",
        )
        btn_title.bind(
            width=lambda inst, w: setattr(
                inst, "text_size", (w - dp(8), None)
            )
        )
        btn_title.bind(
            on_release=lambda *_: self.callbacks["on_select"](tab.id)
        )
        row.add_widget(btn_title)

        btn_x = Button(
            text="\ue5cd",
            font_name=FONT_NAME,
            size_hint_x=None,
            width=dp(48),
            background_normal="",
            background_color=(0, 0, 0, 0),
            color=TEXT_MUTED,
            font_size="20sp",
        )
        btn_x.bind(
            on_release=lambda *_: self.callbacks["on_close_tab"](tab.id)
        )
        row.add_widget(btn_x)
        return row
