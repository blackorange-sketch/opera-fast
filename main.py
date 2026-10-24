from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.widget import Widget
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.utils import platform
from kivy.graphics import Color as GColor, Rectangle

from webview.engine import WebViewEngine
from core.log import clear_log, log

TOP_BAR_H = dp(52)


class BrowserRoot(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", **kw)

        clear_log()

        top = BoxLayout(
            size_hint_y=None, height=TOP_BAR_H,
            spacing=dp(4), padding=[dp(4), dp(4)],
        )
        btn_back = Button(text="\u2190", size_hint_x=None, width=dp(40))
        btn_back.bind(on_press=lambda *_: self.engine.go_back())
        btn_fwd = Button(text="\u2192", size_hint_x=None, width=dp(40))
        btn_fwd.bind(on_press=lambda *_: self.engine.go_forward())
        btn_reload = Button(text="\u27f3", size_hint_x=None, width=dp(40))
        btn_reload.bind(on_press=lambda *_: self.engine.reload())

        self.url = TextInput(
            text="https://duckduckgo.com", multiline=False,
            size_hint_y=None, height=dp(40),
        )
        self.url.bind(on_text_validate=self._navigate)

        btn_go = Button(text="\u2192", size_hint_x=None, width=dp(40))
        btn_go.bind(on_press=self._navigate)

        top.add_widget(btn_back)
        top.add_widget(btn_fwd)
        top.add_widget(btn_reload)
        top.add_widget(self.url)
        top.add_widget(btn_go)
        self.add_widget(top)

        # White placeholder; WebView covers it once loaded.
        self.placeholder = Widget()
        with self.placeholder.canvas.before:
            GColor(1, 1, 1, 1)
            self._rect = Rectangle(
                pos=self.placeholder.pos, size=self.placeholder.size
            )
        self.placeholder.bind(
            pos=lambda *a: setattr(self._rect, "pos", self.placeholder.pos),
            size=lambda *a: setattr(self._rect, "size", self.placeholder.size),
        )
        self.add_widget(self.placeholder)

        self.engine = WebViewEngine(top_bar_height_px=int(TOP_BAR_H))

        if platform == "android":
            Clock.schedule_once(self._boot_engine, 0.8)

    def _boot_engine(self, _):
        try:
            log("[main] booting engine")
            self.engine.attach(None)
            self.engine.load("https://duckduckgo.com")
        except Exception as e:
            import traceback
            log(f"[main] boot EXC: {e}")
            log(traceback.format_exc())

    def _navigate(self, *_):
        raw = self.url.text.strip()
        if not raw:
            return
        if raw.startswith(("http://", "https://")):
            url = raw
        elif "." in raw and " " not in raw:
            url = "https://" + raw
        else:
            url = "https://duckduckgo.com/?q=" + raw.replace(" ", "+")
        self.engine.load(url)


class OperaFastApp(App):
    title = "OperaFast"

    def build(self):
        return BrowserRoot()


if __name__ == "__main__":
    OperaFastApp().run()
