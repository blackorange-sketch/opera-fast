"""Shared file logger for OperaFast (works without ADB)."""
import os


def _storage_dir() -> str:
    try:
        from android.storage import app_storage_path
        return app_storage_path()
    except Exception:
        return os.path.expanduser("~/.operafast")


def log_path() -> str:
    d = _storage_dir()
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return os.path.join(d, "operafast.log")


def log(msg: str) -> None:
    print(msg)
    try:
        with open(log_path(), "a", encoding="utf-8") as f:
            f.write(msg + "\n")
    except Exception:
        pass


def read_log(max_lines: int = 100) -> str:
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
