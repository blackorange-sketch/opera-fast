"""Android WebView wrapper for OperaFast (diagnostic, no WindowManager)."""
import traceback
from kivy.utils import platform

from core.log import log


class WebViewEngine:
    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)
        self._pending_url = None

    def attach(self, kivy_slot):
        if platform != "android":
            log("[engine] non-android: mock")
            return

        from jnius import autoclass
        from android.runnable import run_on_ui_thread

        WebView = autoclass("android.webkit.WebView")
        WebViewClient = autoclass("android.webkit.WebViewClient")
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        ViewGroup = autoclass("android.view.ViewGroup")
        MarginLayoutParams = autoclass("android.view.ViewGroup$MarginLayoutParams")
        Color = autoclass("android.graphics.Color")

        engine_ref = self

        @run_on_ui_thread
        def _create():
            try:
                log("[engine] step 1: get activity")
                act = PythonActivity.mActivity
                log(f"[engine] act={act}")
                if act is None:
                    return

                log("[engine] step 2: construct WebView")
                wv = WebView(act)
                log("[engine] wv created")

                log("[engine] step 3: bg color RED")
                wv.setBackgroundColor(Color.RED)

                log("[engine] step 4: settings")
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
                log("[engine] settings OK")

                log("[engine] step 5: setWebViewClient")
                wv.setWebViewClient(WebViewClient())
                log("[engine] client OK")

                log("[engine] step 6: layout params")
                params = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                params.topMargin = engine_ref._top_bar_height_px
                log("[engine] params OK")

                log("[engine] step 7: addContentView")
                act.addContentView(wv, params)
                log("[engine] addContentView OK")

                try:
                    wv.bringToFront()
                    log("[engine] bringToFront OK")
                except Exception as e:
                    log(f"[engine] bringToFront failed: {e}")

                engine_ref._wv = wv
                log("[engine] wv assigned")

                if engine_ref._pending_url:
                    url = engine_ref._pending_url
                    engine_ref._pending_url = None
                    log(f"[engine] step 8: load({url})")
                    wv.loadUrl(url)
                else:
                    log("[engine] step 8: load(about:blank)")
                    wv.loadUrl("about:blank")

                log("[engine] done")
            except Exception as e:
                log(f"[engine] EXC: {e}")
                log(traceback.format_exc())

        log("[engine] attach: calling _create()")
        _create()
        log("[engine] attach: returned")

    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

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
