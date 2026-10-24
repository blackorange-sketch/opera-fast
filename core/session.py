"""Session persistence for OperaFast (tabs + active tab)."""
from __future__ import annotations

import json
import os

from core.tabs import TabManager


SESSION_FILENAME = "session.json"


def _storage_dir() -> str:
    """Return the app private storage directory (Android) or a desktop fallback."""
    try:
        from android.storage import app_storage_path  # type: ignore
        return app_storage_path()
    except Exception:
        base = os.path.expanduser("~")
        return os.path.join(base, ".operafast")


def _session_path() -> str:
    d = _storage_dir()
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return os.path.join(d, SESSION_FILENAME)


def save_session(tabs: TabManager) -> bool:
    """Persist the current tabs + active tab. Returns True on success."""
    try:
        path = _session_path()
        with open(path, "w", encoding="utf-8") as f:
            json.dump(tabs.to_dict(), f)
        return True
    except Exception as e:
        print(f"[session] save failed: {e}")
        return False


def load_session(tabs: TabManager) -> bool:
    """Restore tabs from disk into the given TabManager. Returns True if restored."""
    try:
        path = _session_path()
        if not os.path.isfile(path):
            return False
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        tabs.load_from_dict(data)
        return True
    except Exception as e:
        print(f"[session] load failed: {e}")
        return False
