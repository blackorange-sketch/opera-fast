"""Share log via Android system share intent (no permissions needed)."""
from kivy.utils import platform
from core.log import log, read_log


def share_log():
    """Open system share chooser with the current log content."""
    if platform != "android":
        log("[share] non-android: skip")
        return

    from jnius import autoclass
    from android.runnable import run_on_ui_thread

    @run_on_ui_thread
    def _share():
        try:
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            Intent = autoclass("android.content.Intent")

            act = PythonActivity.mActivity
            if act is None:
                log("[share] no activity")
                return

            text = read_log(300) or "(empty log)"
            log(f"[share] sharing {len(text)} chars")

            intent = Intent(Intent.ACTION_SEND)
            intent.setType("text/plain")
            intent.putExtra(Intent.EXTRA_SUBJECT, "OperaFast log")
            intent.putExtra(Intent.EXTRA_TEXT, text)

            chooser = Intent.createChooser(intent, "Share log")
            act.startActivity(chooser)
            log("[share] chooser started")
        except Exception as e:
            import traceback
            log(f"[share] failed: {e}")
            log(traceback.format_exc())

    _share()
