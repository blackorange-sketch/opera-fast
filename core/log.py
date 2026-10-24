"""Shared file logger for OperaFast — writes to externally-readable path."""
import os


def log_path() -> str:
    # Prefer external app-specific dir: /sdcard/Android/data/<pkg>/files/operafast.log
    # Visible in any file manager on Android 13 without permissions.
    try:
        from jnius import autoclass
        PythonActivity = autoclass("org.kivy.android.PythonActivity")
        act = PythonActivity.mActivity
        if act is not None:
            ext = act.getExternalFilesDir(None)
            if ext is not None:
                p = ext.getAbsolutePath()
                os.makedirs(p, exist_ok=True)
                return os.path.join(p, "operafast.log")
    except Exception:
        pass
    # Fallback: internal storage
    try:
        from android.storage import app_storage_path
        d = app_storage_path()
        os.makedirs(d, exist_ok=True)
        return os.path.join(d, "operafast.log")
    except Exception:
        d = os.path.expanduser("~/.operafast")
        os.makedirs(d, exist_ok=True)
        return os.path.join(d, "operafast.log")


def log(msg: str) -> None:
    print(msg)
    try:
        with open(log_path(), "a", encoding="utf-8") as f:
            f.write(msg + "\n")
    except Exception:
        pass


def read_log(max_lines: int = 200) -> str:
    try:
        with open(log_path(), "r", encoding="utf-8") as f:
            lines = f.readlines()
        return "".join(lines[-max_lines:])
    except Exception:
        return "(no log yet)"


def clear_log() -> None:
    try:
        open(log_path(), "w").close()
    except Exception:
        pass
