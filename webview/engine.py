"""Android WebView wrapper for OperaFast.

Key design points:
- All WebView work happens on the UI thread via run_on_ui_thread.
- WebView creation is async (queued to UI thread), so load() before
  the view exists is queued in _pending_url and drained right after
  creation finishes.
- White background so an empty WebView is visibly white, not black.
- WebViewClient logs page lifecycle so failures are visible in logcat.
"""
from kivy.utils import platform


class WebViewEngine:
    """Thin wrapper over Android WebView via pyjnius."""

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
        WebViewClientBase = autoclass("android.webkit.WebViewClient")
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        ViewGroup = autoclass("android.view.ViewGroup")
        MarginLayoutParams = autoclass("android.view.ViewGroup$MarginLayoutParams")
        Color = autoclass("android.graphics.Color")

        engine_ref = self

        # Custom WebViewClient for logging (subclassing a Java class in pyjnius)
        class _Client(WebViewClientBase):
            def onPageStarted(self, view, url, favicon):
                print(f"[webview] page started: {url}")

            def onPageFinished(self, view, url):
                print(f"[webview] page finished: {url}")

            def onReceivedError(self, view, errorCode, description, failingUrl):
                print(f"[webview] error {errorCode}: {description} @ {failingUrl}")

        @run_on_ui_thread
        def _create():
            try:
                act = PythonActivity.mActivity
                print(f"[engine] activity = {act}")
                if act is None:
                    print("[engine] FATAL: mActivity is None")
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

                wv.setWebViewClient(_Client())

                params = MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                params.topMargin = engine_ref._top_bar_height_px

                act.addContentView(wv, params)
                engine_ref._wv = wv
                print("[engine] WebView attached")

                # Drain pending URL or show about:blank
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

    # ---------- navigation API ----------
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
