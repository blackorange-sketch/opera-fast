from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.clock import Clock
from kivy.metrics import dp
from kivy.utils import platform
from kivy.graphics import Color as GColor, Rectangle

from webview.engine import WebViewEngine
from core.log import clear_log, log
from core.tabs import TabManager, DEFAULT_URL
from core.session import save_session, load_session
from core import adblock
from ui.theme import BG_APP, TOOLBAR_HEIGHT_DP
from ui.toolbar import Toolbar
from ui.speed_dial import SpeedDial
from ui.sidebar import Sidebar
from ui.dialogs import show_adblock_dialog


def _is_blank(url: str) -> bool:
    return (not url) or url == DEFAULT_URL or url == "about:blank"


class BrowserRoot(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", **kw)
        clear_log()

        with self.canvas.before:
            GColor(*BG_APP)
            self._bg = Rectangle(pos=self.pos, size=self.size)
        self.bind(
            pos=lambda *a: setattr(self._bg, "pos", self.pos),
            size=lambda *a: setattr(self._bg, "size", self.size),
        )

        self.tabs = TabManager()
        if load_session(self.tabs):
            log("[main] session restored")
        else:
            log("[main] new session")

        self.toolbar = Toolbar(callbacks={
            "on_back":    lambda *_: self.engine.go_back(),
            "on_forward": lambda *_: self.engine.go_forward(),
            "on_reload":  lambda *_: self.engine.reload(),
            "on_go":      self._navigate,
            "on_tabs":    self._show_sidebar,
            "on_shield":  self._on_shield_tap,
            "on_menu":    self._on_menu_tap,
        })
        self.add_widget(self.toolbar)

        self.content = FloatLayout()
        with self.content.canvas.before:
            GColor(*BG_APP)
            self._content_bg = Rectangle(
                pos=self.content.pos, size=self.content.size
            )
        self.content.bind(
            pos=lambda *a: setattr(self._content_bg, "pos", self.content.pos),
            size=lambda *a: setattr(self._content_bg, "size", self.content.size),
        )
        self.add_widget(self.content)

        self.speed_dial = SpeedDial(on_open=self._navigate)
        self.content.add_widget(self.speed_dial)

        self.sidebar = None

        self.engine = WebViewEngine(
            top_bar_height_px=int(dp(TOOLBAR_HEIGHT_DP)),
        )

        # Track last-polled values to avoid churning UI every 0.5s
        self._last_polled_url = None
        self._last_polled_title = None
        self._last_blocked_count = 0

        if platform == "android":
            Clock.schedule_once(self._boot_engine, 0.8)
            Clock.schedule_interval(self._poll_engine, 0.5)

    # ---------- boot ----------
    def _boot_engine(self, _):
        try:
            log("[main] booting engine")
            adblock.install()
            self.engine.attach(None)
            self._sync_to_active_tab(initial=True)
        except Exception as e:
            import traceback
            log(f"[main] boot EXC: {e}")
            log(traceback.format_exc())

    # ---------- engine polling ----------
    def _poll_engine(self, _dt):
        if self.engine._wv is None:
            return True
        url = self.engine.get_url()
        title = self.engine.get_title()

        if url and url != self._last_polled_url:
            self._last_polled_url = url
            tab = self.tabs.active
            if tab is not None and tab.url != url:
                self.tabs.update_tab(tab.id, url=url)
            self.toolbar.set_url(url)
            # If user landed on a page from about:blank, hide speed dial
            if self.speed_dial.opacity > 0:
                self.speed_dial.opacity = 0
                self.speed_dial.disabled = True

        if title and title != self._last_polled_title:
            self._last_polled_title = title
            tab = self.tabs.active
            if tab is not None:
                self.tabs.update_tab(tab.id, title=title)

        # Adblock stats: update shield badge + log on change
        blocked, total = adblock.get_stats()
        if blocked != self._last_blocked_count:
            self._last_blocked_count = blocked
            self.toolbar.set_blocked_count(blocked)
            log(f"[adblock] blocked total: {blocked}/{total} rules")
        return True

    # ---------- state sync ----------
    def _sync_to_active_tab(self, initial=False):
        tab = self.tabs.active
        if tab is None:
            self.tabs.add_tab(activate=True)
            tab = self.tabs.active

        if _is_blank(tab.url):
            self._show_speed_dial()
            self.toolbar.set_url("")
        else:
            self.toolbar.set_url(tab.url)
            self._show_webview(tab.url if initial else None)

    def _show_speed_dial(self):
        self.engine.set_visible(False)
        self.speed_dial.opacity = 1
        self.speed_dial.disabled = False

    def _show_webview(self, url=None):
        self.speed_dial.opacity = 0
        self.speed_dial.disabled = True
        self.engine.set_visible(True)
        if url:
            self.engine.load(url)
        # Reset polling cache so next poll picks up fresh URL
        self._last_polled_url = None
        self._last_polled_title = None
        self._last_blocked_count = 0

    # ---------- navigation ----------
    def _parse_url(self, raw: str) -> str:
        raw = (raw or "").strip()
        if not raw:
            return ""
        if raw.startswith(("http://", "https://")):
            return raw
        if "." in raw and " " not in raw:
            return "https://" + raw
        return "https://duckduckgo.com/?q=" + raw.replace(" ", "+")

    def _on_shield_tap(self, *_):
        try:
            blocked, total = adblock.get_stats()
            enabled = adblock.is_enabled()
            page_url = self.engine.get_url() or ""
            page_host = adblock.host_of(page_url)
            log(f"[main] shield tapped: blocked={blocked} total={total} "
                f"enabled={enabled} host={page_host}")
            show_adblock_dialog(
                blocked=blocked,
                total_rules=total,
                enabled=enabled,
                page_host=page_host,
                on_toggle_enabled=self._toggle_adblock,
                on_whitelist=self._whitelist_current,
            )
        except Exception as e:
            import traceback
            log(f"[main] shield tap EXC: {e}")
            log(traceback.format_exc())

    def _on_menu_tap(self, *_):
        log("[main] menu tapped — sharing log")
        try:
            from core.share import share_log
            share_log()
        except Exception as e:
            import traceback
            log(f"[main] share EXC: {e}")
            log(traceback.format_exc())

    def _toggle_adblock(self):
        new_state = not adblock.is_enabled()
        adblock.set_enabled(new_state)
        log(f"[main] adblock enabled -> {new_state}")
        # Reload page so the change takes effect immediately
        self.engine.reload()

    def _whitelist_current(self, host):
        if not host:
            return
        adblock.whitelist_site(host)
        log(f"[main] whitelisted: {host}")
        self.engine.reload()

    def _navigate(self, raw: str):
        url = self._parse_url(raw)
        if not url:
            return
        tab = self.tabs.active
        if tab:
            self.tabs.update_tab(tab.id, url=url, title=raw)
        if self.sidebar is not None:
            self.content.remove_widget(self.sidebar)
            self.sidebar = None
        self.toolbar.set_url(url)
        self._show_webview(url)
        save_session(self.tabs)

    # ---------- sidebar ----------
    def _show_sidebar(self, *_):
        log("[main] opening sidebar")
        self.engine.set_visible(False)
        self.speed_dial.opacity = 0
        self.speed_dial.disabled = True

        if self.sidebar is not None:
            self.content.remove_widget(self.sidebar)

        self.sidebar = Sidebar(
            tabs=self.tabs.tabs,
            callbacks={
                "active_id": self.tabs.active_id,
                "on_close": self._hide_sidebar,
                "on_select": self._select_tab,
                "on_new_tab": self._new_tab,
                "on_close_tab": self._close_tab,
            },
        )
        self.content.add_widget(self.sidebar)

    def _hide_sidebar(self, *_):
        if self.sidebar is not None:
            self.content.remove_widget(self.sidebar)
            self.sidebar = None
        self._sync_to_active_tab()

    def _select_tab(self, tab_id):
        self.tabs.activate(tab_id)
        self._hide_sidebar()
        save_session(self.tabs)

    def _new_tab(self, *_):
        self.tabs.add_tab(activate=True)
        self._hide_sidebar()
        save_session(self.tabs)

    def _close_tab(self, tab_id):
        self.tabs.close_tab(tab_id)
        if self.tabs.count() == 0:
            self.tabs.add_tab(activate=True)
        if self.sidebar is not None:
            self.content.remove_widget(self.sidebar)
            self.sidebar = None
            self._show_sidebar()
        else:
            self._sync_to_active_tab()
        save_session(self.tabs)


class OperaFastApp(App):
    title = "OperaFast"

    def build(self):
        return BrowserRoot()


if __name__ == "__main__":
    OperaFastApp().run()
