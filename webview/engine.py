"""Android WebView wrapper for OperaFast.

Key trick: Kivy on Android draws via SDL2 SurfaceView, which is drawn
above any regular View added via Activity.addContentView. To show
WebView above SDL2, we add it to WindowManager as TYPE_APPLICATION_PANEL,
a separate window-layer attached to the Activity via token.
"""
import traceback
from kivy.utils import platform

from core.log import log


class WebViewEngine:
    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)
        self._pending_url = None
        self._wm = None
        self._params = None

    def attach(self, kivy_slot):
        if platform != "android":
            log("[engine] non-android: mock mode")
            return

        from jnius import autoclass
        from android.runnable import run_on_ui_thread

        WebView = autoclass("android.webkit.WebView")
        WebViewClient = autoclass("android.webkit.WebViewClient")
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        Context = autoclass("android.content.Context")
        WindowManager = autoclass("android.view.WindowManager")
        WMLayoutParams = autoclass("android.view.WindowManager$LayoutParams")
        Gravity = autoclass("android.view.Gravity")
        Color = autoclass("android.graphics.Color")
        PixelFormat = autoclass("android.graphics.PixelFormat")

        engine_ref = self

        @run_on_ui_thread
        def _create():
            try:
                act = PythonActivity.mActivity
                log(f"[engine] activity = {act}")
                if act is None:
                    log("[engine] FATAL: mActivity is None")
                    return

                wv = WebView(act)
                try:
                    wv.setBackgroundColor(Color.RED)  # diagnostic
                except Exception as e:
                    log(f"[engine] setBackgroundColor failed: {e}")

                try:
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
                except Exception as e:
                    log(f"[engine] settings failed: {e}")

                try:
                    wv.setWebViewClient(WebViewClient())
                except Exception as e:
                    log(f"[engine] setWebViewClient failed: {e}")

                # --- WindowManager path ---
                log("[engine] getting WindowManager...")
                wm = act.getSystemService(Context.WINDOW_SERVICE)
                log(f"[engine] wm = {wm}")

                params = WMLayoutParams(
                    WMLayoutParams.MATCH_PARENT,
                    WMLayoutParams.MATCH_PARENT,
                    WMLayoutParams.TYPE_APPLICATION_PANEL,
                    WMLayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )
                params.gravity = Gravity.TOP
                params.y = engine_ref._top_bar_height_px

                try:
                    token = act.getWindow().getDecorView().getWindowToken()
                    log(f"[engine] token = {token}")
                    params.token = token
                except Exception as e:
                    log(f"[engine] token set failed: {e}")

                log("[engine] calling wm.addView...")
                wm.addView(wv, params)
                log("[engine] wm.addView OK")

                engine_ref._wv = wv
                engine_ref._wm = wm
                engine_ref._params = params

                if engine_ref._pending_url:
                    url = engine_ref._pending_url
                    engine_ref._pending_url = None
                    log(f"[engine] loading queued URL: {url}")
                    wv.loadUrl(url)
                else:
                    log("[engine] loading about:blank")
                    wv.loadUrl("about:blank")
            except Exception as e:
                log(f"[engine] create failed: {e}")
                log(traceback.format_exc())

        _create()

    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

    def load(self, url):
        if self._wv is None:
            log(f"[engine] queueing URL (wv not ready): {url}")
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
