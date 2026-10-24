"""pyjnius WebViewClient implementation via PythonJavaClass.

Correct pattern for subclassing Java interfaces in pyjnius:
  - inherit PythonJavaClass (not the autoclass)
  - declare __javainterfaces__
  - decorate methods with @java_method(signature)
  - keep a strong reference to the instance (caller's responsibility)

Signature notes:
  onPageStarted(WebView, String) -> void
  onPageFinished(WebView, String) -> void
  onReceivedTitle(WebView, String) -> void
"""
from jnius import PythonJavaClass, java_method


class PyWebViewClient(PythonJavaClass):
    __javainterfaces__ = ["android/webkit/WebViewClient"]
    __javacontext__ = "app"

    def __init__(self, on_started=None, on_finished=None, on_title=None):
        super().__init__()
        self.on_started = on_started
        self.on_finished = on_finished
        self.on_title = on_title

    @java_method("(Landroid/webkit/WebView;Ljava/lang/String;)V")
    def onPageStarted(self, view, url):
        try:
            if self.on_started:
                self.on_started(str(url))
        except Exception as e:
            print(f"[client] onPageStarted error: {e}")

    @java_method("(Landroid/webkit/WebView;Ljava/lang/String;)V")
    def onPageFinished(self, view, url):
        try:
            title = None
            try:
                title = str(view.getTitle())
            except Exception:
                pass
            if self.on_finished:
                self.on_finished(str(url), title)
        except Exception as e:
            print(f"[client] onPageFinished error: {e}")

    @java_method("(Landroid/webkit/WebView;Ljava/lang/String;)V")
    def onReceivedTitle(self, view, title):
        try:
            if self.on_title:
                self.on_title(str(title))
        except Exception as e:
            print(f"[client] onReceivedTitle error: {e}")
