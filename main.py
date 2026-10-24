from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.widget import Widget
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.utils import platform
from kivy.graphics import Color as GColor, Rectangle

from webview.engine import WebViewEngine
from core.log import clear_log, log
from ui.theme import BG_APP
from ui.toolbar import Toolbar


class BrowserRoot(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", **kw)
        clear_log()

        # Dark background for the whole app
        with self.canvas.before:
            GColor(*BG_APP)
            self._bg = Rectangle(pos=self.pos, size=self.size)
        self.bind(
            pos=lambda *a: setattr(self._bg, "pos", self.pos),
            size=lambda *a: setattr(self._bg, "size", self.size),
        )

        self.engine = WebViewEngine(top_bar_height_px=int(dp(52)))

        self.toolbar = Toolbar(callbacks={
            "on_back":    lambda *_: self.engine.go_back(),
            "on_forward": lambda *_: self.engine.go_forward(),
            "on_reload":  lambda *_: self.engine.reload(),
            "on_go":      self._navigate,
            "on_tabs":    self._on_tabs,
            "on_menu":    self._on_menu,
        })
        self.add_widget(self.toolbar)

        # Placeholder so screen is not black before WebView covers it
        self.placeholder = Widget()
        with self.placeholder.canvas.before:
            GColor(*BG_APP)
            self._ph = Rectangle(
                pos=self.placeholder.pos, size=self.placeholder.size
            )
        self.placeholder.bind(
            pos=lambda *a: setattr(self._ph, "pos", self.placeholder.pos),
            size=lambda *a: setattr(self._ph, "size", self.placeholder.size),
        )
        self.add_widget(self.placeholder)

        if platform == "android":
            Clock.schedule_once(self._boot_engine, 0.8)

    def _boot_engine(self, _):
        try:
            log("[main] booting engine")
            self.engine.attach(None)
            self.engine.load("https://duckduckgo.com")
            self.toolbar.set_url("https://duckduckgo.com")
        except Exception as e:
            import traceback
            log(f"[main] boot EXC: {e}")
            log(traceback.format_exc())

    def _on_tabs(self, *_):
        log("[main] tabs button pressed (not wired yet)")

    def _on_menu(self, *_):
        log("[main] menu button pressed (not wired yet)")

    def _navigate(self, raw):
        raw = (raw or "").strip()
        if not raw:
            return
        if raw.startswith(("http://", "https://")):
            url = raw
        elif "." in raw and " " not in raw:
            url = "https://" + raw
        else:
            url = "https://duckduckgo.com/?q=" + raw.replace(" ", "+")
        self.engine.load(url)
        self.toolbar.set_url(url)


class OperaFastApp(App):
    title = "OperaFast"

    def build(self):
        return BrowserRoot()


if __name__ == "__main__":
    OperaFastApp().run()
