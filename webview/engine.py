"""Android WebView wrapper for OperaFast (diagnostic version)."""
import os
import traceback
from kivy.utils import platform


def _log_path():
    try:
        from android.storage import app_storage_path
        d = app_storage_path()
    except Exception:
        d = os.path.expanduser("~/.operafast")
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return os.path.join(d, "operafast.log")


def _log(msg):
    print(msg)
    try:
        with open(_log_path(), "a", encoding="utf-8") as f:
            f.write(msg + "\n")
    except Exception:
        pass


class WebViewEngine:
    def __init__(self, top_bar_height_px=0):
        self._wv = None
        self._top_bar_height_px = int(top_bar_height_px)
        self._pending_url = None

    def attach(self, kivy_slot):
        if platform != "android":
            _log("[engine] non-android: mock mode")
            return

        from jnius import autoclass
        from android.runnable import run_on_ui_thread

        WebView = autoclass("android.webkit.WebView")
        WebViewClient = autoclass("android.webkit.WebViewClient")
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        ViewGroup = autoclass("android.view.ViewGroup")
        FrameLayout = autoclass("android.widget.FrameLayout")
        FLParams = autoclass("android.widget.FrameLayout$LayoutParams")
        Color = autoclass("android.graphics.Color")
        Gravity = autoclass("android.view.Gravity")

        engine_ref = self

        @run_on_ui_thread
        def _create():
            try:
                act = PythonActivity.mActivity
                _log(f"[engine] activity = {act}")
                if act is None:
                    _log("[engine] FATAL: mActivity is None")
                    return

                wv = WebView(act)
                # DIAGNOSTIC: red background so we can see if WebView is visible
                wv.setBackgroundColor(Color.RED)

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

                wv.setWebViewClient(WebViewClient())

                params = FLParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.TOP,
                )
                params.topMargin = engine_ref._top_bar_height_px

                # Find the content view (FrameLayout) and add WebView there
                res_id = act.getResources().getIdentifier(
                    "content", "id", "android"
                )
                _log(f"[engine] android.R.id.content = {res_id}")
                content = act.findViewById(res_id)
                _log(f"[engine] content view = {content}")

                if content is not None:
                    content.addView(wv, params)
                    _log("[engine] added to content view")
                else:
                    act.addContentView(wv, params)
                    _log("[engine] added via addContentView (fallback)")

                wv.bringToFront()
                engine_ref._wv = wv
                _log("[engine] WebView attached")

                # Report visibility & size for diagnosis
                _log(
                    f"[engine] wv vis={wv.getVisibility()} "
                    f"size={wv.getWidth()}x{wv.getHeight()} "
                    f"top={wv.getTop()}"
                )

                if engine_ref._pending_url:
                    url = engine_ref._pending_url
                    engine_ref._pending_url = None
                    _log(f"[engine] loading queued URL: {url}")
                    wv.loadUrl(url)
                else:
                    _log("[engine] loading about:blank")
                    wv.loadUrl("about:blank")
            except Exception as e:
                _log(f"[engine] create failed: {e}")
                _log(traceback.format_exc())

        _create()

    def _ui(self, fn):
        from android.runnable import run_on_ui_thread
        run_on_ui_thread(fn)()

    def load(self, url):
        if self._wv is None:
            _log(f"[engine] queueing URL (wv not ready): {url}")
            self._pending_url = url
            return
        _log(f"[engine] load({url})")
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
