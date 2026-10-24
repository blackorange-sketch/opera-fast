"""Ad blocker bridge: loads hosts list into AdBlockClient (Java)."""
import os
from kivy.utils import platform

from core.log import log


def _hosts_path() -> str:
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    return os.path.join(root, "assets", "adblock", "hosts.txt")


def _load_hosts_text() -> str:
    path = _hosts_path()
    if not os.path.exists(path):
        log(f"[adblock] hosts.txt not found at {path}")
        return ""
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            # Already pre-parsed: one hostname per line
            lines = [ln.strip() for ln in f if ln.strip() and not ln.startswith("#")]
        log(f"[adblock] loaded {len(lines)} hosts from {path}")
        return "\n".join(lines)
    except Exception as e:
        log(f"[adblock] failed to read hosts: {e}")
        return ""


def install() -> bool:
    """Push the hosts list into AdBlockClient (Java). Returns True on success."""
    if platform != "android":
        log("[adblock] non-android: skip install")
        return False

    try:
        from jnius import autoclass
        AdBlockClient = autoclass("org.blackorange.operafast.AdBlockClient")
    except Exception as e:
        log(f"[adblock] cannot load AdBlockClient: {e}")
        return False

    text = _load_hosts_text()
    if not text:
        log("[adblock] empty hosts, skipping")
        return False

    try:
        AdBlockClient.setBlockedHostsFromString(text)
        n = AdBlockClient.getBlockedHostCount()
        log(f"[adblock] installed, {n} rules active")
        return True
    except Exception as e:
        log(f"[adblock] install failed: {e}")
        return False


def get_stats():
    """Return (blocked_count, total_rules) or (0, 0) on error."""
    if platform != "android":
        return (0, 0)
    try:
        from jnius import autoclass
        AdBlockClient = autoclass("org.blackorange.operafast.AdBlockClient")
        return (int(AdBlockClient.getBlockedCount()),
                int(AdBlockClient.getBlockedHostCount()))
    except Exception:
        return (0, 0)
