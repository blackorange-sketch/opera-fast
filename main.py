from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.utils import platform
from kivy.graphics import Color as GColor, Rectangle

from webview.engine import WebViewEngine
from core.log import read_log, clear_log, log, log_path

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

        self.placeholder = BoxLayout()
        with self.placeholder.canvas.before:
            GColor(1, 1, 1, 1)
            self._rect = Rectangle(pos=self.placeholder.pos, size=self.placeholder.size)
        self.placeholder.bind(
            pos=lambda *a: setattr(self._rect, "pos", self.placeholder.pos),
            size=lambda *a: setattr(self._rect, "size", self.placeholder.size),
        )

        self.log_label = Label(
            text="[boot]", size_hint=(1, None),
            halign="left", valign="top",
            font_size="9sp", color=(0, 0, 0, 1),
            text_size=(None, None),
        )
        self.log_label.bind(
            width=lambda *a: setattr(
                self.log_label, "text_size", (self.log_label.width, None)
            )
        )

        sv = ScrollView(size_hint=(1, 1))
        sv.add_widget(self.log_label)
        self.placeholder.add_widget(sv)
        self.add_widget(self.placeholder)

        # Write log file path so it is visible on screen before any crash
        try:
            log(f"[main] log file: {log_path()}")
        except Exception:
            pass

        self.engine = WebViewEngine(top_bar_height_px=int(TOP_BAR_H))

        if platform == "android":
            Clock.schedule_once(self._boot_engine, 0.8)
            Clock.schedule_interval(self._refresh_log, 0.3)

    def _boot_engine(self, _):
        try:
            log("[main] booting engine")
            self.engine.attach(None)
            self.engine.load("https://duckduckgo.com")
        except Exception as e:
            import traceback
            log(f"[main] boot engine EXC: {e}")
            log(traceback.format_exc())

    def _refresh_log(self, _dt):
        try:
            text = read_log(200)
            if text != self.log_label.text:
                self.log_label.text = text
        except Exception as e:
            self.log_label.text = f"(log read error: {e})"
        return True

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
