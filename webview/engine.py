from kivy.utils import platform


class WebViewEngine:
    """Thin wrapper over Android WebView via pyjnius."""

    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)

    def attach(self, kivy_slot):
        if platform != "android":
            print("[engine] non-android: mock mode")
            return

        from jnius import autoclass
        from android.runnable import run_on_ui_thread

        WebView = autoclass("android.webkit.WebView")
        WebViewClient = autoclass("android.webkit.WebViewClient")
        Activity = autoclass("org.kivy.android.PythonActivity").mActivity
        ViewGroup = autoclass("android.view.ViewGroup")
        MarginLayoutParams = autoclass("android.view.ViewGroup$MarginLayoutParams")

        @run_on_ui_thread
        def _create():
            wv = WebView(Activity)
            s = wv.getSettings()
            s.setJavaScriptEnabled(True)
            s.setDomStorageEnabled(True)
            s.setDatabaseEnabled(True)
            s.setSupportZoom(False)
            s.setBuiltInZoomControls(False)
            s.setMediaPlaybackRequiresUserGesture(False)
            wv.setWebViewClient(WebViewClient())

            params = MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            params.topMargin = self._top_bar_height_px
            Activity.addContentView(wv, params)
            self._wv = wv

        _create()

    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

    def load(self, url):
        if self._wv is None:
            print("[engine] not ready, would load:", url)
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
