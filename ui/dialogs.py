"""Native Android AlertDialog wrappers for OperaFast.

Why native: Kivy modals render in SDL2 layer, which WebView covers.
Native AlertDialog is a system window on top of everything.

Click listeners are PythonJavaClass over
android.content.DialogInterface$OnClickListener — an *interface*,
so pyjnius's dynamic proxy handles it correctly (unlike
WebViewClient which is an abstract class).
"""
from kivy.utils import platform
from core.log import log


def _make_listener(callback):
    """Create a DialogInterface.OnClickListener bound to callback(int which)."""
    from jnius import PythonJavaClass, java_method

    class _Listener(PythonJavaClass):
        __javainterfaces__ = ["android/content/DialogInterface$OnClickListener"]
        __javacontext__ = "app"

        def __init__(self, cb):
            super().__init__()
            self.cb = cb

        @java_method("(Landroid/content/DialogInterface;I)V")
        def onClick(self, dialog, which):
            try:
                self.cb(int(which))
            except Exception as e:
                log(f"[dialog] onClick error: {e}")

    return _Listener(callback)


def show_adblock_dialog(blocked, total_rules, enabled, page_host,
                        on_toggle_enabled, on_whitelist):
    """Show native AlertDialog with ad-blocker stats + actions."""
    if platform != "android":
        log(f"[dialog] adblock: blocked={blocked} total={total_rules} enabled={enabled}")
        return

    from jnius import autoclass
    from android.runnable import run_on_ui_thread

    @run_on_ui_thread
    def _show():
        try:
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            Builder = autoclass("android.app.AlertDialog$Builder")
            AlertDialog = autoclass("android.app.AlertDialog")

            act = PythonActivity.mActivity
            if act is None:
                return

            status = "ON" if enabled else "OFF"
            body = (
                f"Blocked on this page: {blocked}\n"
                f"Total rules: {total_rules}\n"
                f"Status: {status}\n"
            )
            if page_host:
                body += f"\nSite: {page_host}"

            builder = Builder(act)
            builder.setTitle("Ad Blocker")
            builder.setMessage(body)

            # Positive: Toggle ON/OFF
            positive = _make_listener(lambda w: on_toggle_enabled())
            builder.setPositiveButton("Toggle", positive)

            # Neutral: Whitelist / un-whitelist current site
            neutral = _make_listener(lambda w: on_whitelist(page_host))
            builder.setNeutralButton("Whitelist site", neutral)

            # Negative: Close
            builder.setNegativeButton("Close", None)

            dialog = builder.create()
            dialog.show()

            # Keep references alive — Python GC would kill the listeners
            # while the dialog is open, causing NullPointerException on tap.
            _show._keepalive = (builder, positive, neutral, dialog)

        except Exception as e:
            import traceback
            log(f"[dialog] show failed: {e}")
            log(traceback.format_exc())

    _show()
