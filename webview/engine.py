"""Android WebView wrapper for OperaFast.

Design:
- All WebView work happens on UI thread via run_on_ui_thread.
- load() before WebView exists is queued in _pending_url and drained
  right after creation.
- White background so empty WebView is white, not black.
- Plain WebViewClient() keeps navigation inside the app.
"""
from kivy.utils import platform


class WebViewEngine:
    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)
        self._pending_url = None

    # ---------- creation ----------
    def attach(self, kivy_slot):
        if platform != "android":
            print("[engine] non-android: mock mode")
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
                act = PythonActivity.mActivity
                print(f"[engine] activity = {act}")
                if act is None:
                    print("[engine] FATAL: mActivity is None")
                    return

                wv = WebView(act)
                try:
                    wv.setBackgroundColor(Color.WHITE)
                except Exception as e:
                    print(f"[engine] setBackgroundColor failed: {e}")

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

                # Plain client keeps navigation inside the WebView
                # and avoids the pyjnius Java-subclass crash.
                wv.setWebViewClient(WebViewClient())

                params = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                params.topMargin = engine_ref._top_bar_height_px

                act.addContentView(wv, params)
                engine_ref._wv = wv
                print("[engine] WebView attached")

                if engine_ref._pending_url:
                    url = engine_ref._pending_url
                    engine_ref._pending_url = None
                    print(f"[engine] loading queued URL: {url}")
                    wv.loadUrl(url)
                else:
                    print("[engine] loading about:blank")
                    wv.loadUrl("about:blank")
            except Exception as e:
                import traceback
                traceback.print_exc()
                print(f"[engine] create failed: {e}")

        _create()

    # ---------- helpers ----------
    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

    # ---------- navigation ----------
    def load(self, url):
        if self._wv is None:
            print(f"[engine] queueing URL (wv not ready): {url}")
            self._pending_url = url
            return
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
