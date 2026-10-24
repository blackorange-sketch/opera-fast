"""Android WebView wrapper for OperaFast.

Uses plain WebViewClient() (autoclass) — NOT a PythonJavaClass
subclass. Subclassing WebViewClient via PythonJavaClass is unreliable
because WebViewClient is an abstract Java class, not an interface,
and pyjnius's dynamic proxy doesn't handle that case well (WebView
can stop loading pages). URL/title are read by polling getUrl()/
getTitle() from a Kivy Clock timer instead.
"""
import traceback
from kivy.utils import platform

from core.log import log


class WebViewEngine:
    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)
        self._pending_url = None
        self._pending_visible = True

    def attach(self, kivy_slot):
        if platform != "android":
            log("[engine] non-android: mock")
            return

        from jnius import autoclass
        from android.runnable import run_on_ui_thread

        WebView = autoclass("android.webkit.WebView")
        WebViewClient = autoclass("android.webkit.WebViewClient")
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        LayoutParams = autoclass("android.view.ViewGroup$LayoutParams")
        MarginLayoutParams = autoclass("android.view.ViewGroup$MarginLayoutParams")
        Color = autoclass("android.graphics.Color")
        View = autoclass("android.view.View")

        engine_ref = self

        @run_on_ui_thread
        def _create():
            try:
                log("[engine] step 1: get activity")
                act = PythonActivity.mActivity
                log(f"[engine] act={act}")
                if act is None:
                    return

                wv = WebView(act)
                wv.setBackgroundColor(Color.WHITE)

                s = wv.getSettings()
                s.setJavaScriptEnabled(True)
                s.setDomStorageEnabled(True)
                s.setDatabaseEnabled(True)
                s.setSupportZoom(False)
                s.setBuiltInZoomControls(False)
                s.setDisplayZoomControls(False)
                s.setMediaPlaybackRequiresUserGesture(False)
                s.setLoadWithOverviewMode(True)
                s.setUseWideViewPort(True)

                # Use native Java AdBlockClient (extends WebViewClient).
                # If class is missing (e.g. during local dev), fall back
                # to plain WebViewClient so the app still works.
                try:
                    AdBlockClient = autoclass(
                        "org.blackorange.operafast.AdBlockClient"
                    )
                    wv.setWebViewClient(AdBlockClient())
                    log("[engine] AdBlockClient installed")
                except Exception as ex:
                    log(f"[engine] AdBlockClient missing, fallback: {ex}")
                    wv.setWebViewClient(WebViewClient())
                    log("[engine] plain WebViewClient installed")

                params = MarginLayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                )
                params.topMargin = engine_ref._top_bar_height_px

                act.addContentView(wv, params)
                log("[engine] addContentView OK")

                engine_ref._wv = wv

                if not engine_ref._pending_visible:
                    wv.setVisibility(View.GONE)
                    log("[engine] initial visibility: GONE")

                if engine_ref._pending_url:
                    url = engine_ref._pending_url
                    engine_ref._pending_url = None
                    log(f"[engine] load({url})")
                    wv.loadUrl(url)

                log("[engine] done")
            except Exception as e:
                log(f"[engine] EXC: {e}")
                log(traceback.format_exc())

        _create()

    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

    def set_visible(self, visible: bool):
        self._pending_visible = visible
        if self._wv is None:
            log(f"[engine] set_visible({visible}) — queued")
            return
        def _toggle():
            try:
                from jnius import autoclass
                View = autoclass("android.view.View")
                self._wv.setVisibility(View.VISIBLE if visible else View.GONE)
                log(f"[engine] visibility -> {'VISIBLE' if visible else 'GONE'}")
            except Exception as e:
                log(f"[engine] set_visible EXC: {e}")
        self._ui(_toggle)

    def load(self, url):
        if self._wv is None:
            log(f"[engine] queue URL: {url}")
            self._pending_url = url
            return
        log(f"[engine] load({url})")
        self._ui(lambda: self._wv.loadUrl(url))

    def reload(self):
        if self._wv:
            self._ui(lambda: self._wv.reload())

    def go_back(self):
        if self._wv:
            def _go():
                if self._wv.canGoBack():
                    self._wv.goBack()
            self._ui(_go)

    def go_forward(self):
        if self._wv:
            def _go():
                if self._wv.canGoForward():
                    self._wv.goForward()
            self._ui(_go)

    # ---------- polling helpers (called from Kivy Clock) ----------
    def get_url(self):
        """Thread-safe read of current URL. Returns str or None."""
        if self._wv is None:
            return None
        try:
            u = self._wv.getUrl()
            return str(u) if u else None
        except Exception as e:
            log(f"[engine] get_url EXC: {e}")
            return None

    def get_title(self):
        """Thread-safe read of current title. Returns str or None."""
        if self._wv is None:
            return None
        try:
            t = self._wv.getTitle()
            return str(t) if t else None
        except Exception as e:
            log(f"[engine] get_title EXC: {e}")
            return None
