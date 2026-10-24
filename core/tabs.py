"""Tab model and manager for OperaFast."""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import List, Optional


DEFAULT_URL = "about:blank"
DEFAULT_TITLE = "New Tab"


@dataclass
class Tab:
    """A single browser tab (minimal, JSON-serializable)."""

    id: str
    url: str = DEFAULT_URL
    title: str = DEFAULT_TITLE

    def to_dict(self) -> dict:
        return {"id": self.id, "url": self.url, "title": self.title}

    @classmethod
    def from_dict(cls, d: dict) -> "Tab":
        return cls(
            id=d.get("id") or str(uuid.uuid4()),
            url=d.get("url") or DEFAULT_URL,
            title=d.get("title") or DEFAULT_TITLE,
        )


class TabManager:
    """Holds the list of tabs and tracks the active one."""

    def __init__(self) -> None:
        self._tabs: List[Tab] = []
        self._active_id: Optional[str] = None
        # Start with a single blank tab so UI always has something to show.
        self.add_tab(activate=True)

    # ---- accessors ----
    @property
    def tabs(self) -> List[Tab]:
        return list(self._tabs)

    @property
    def active(self) -> Optional[Tab]:
        for t in self._tabs:
            if t.id == self._active_id:
                return t
        return None

    @property
    def active_id(self) -> Optional[str]:
        return self._active_id

    def count(self) -> int:
        return len(self._tabs)

    def get(self, tab_id: str) -> Optional[Tab]:
        for t in self._tabs:
            if t.id == tab_id:
                return t
        return None

    # ---- mutations ----
    def add_tab(self, url: str = DEFAULT_URL, title: str = DEFAULT_TITLE,
                activate: bool = True) -> Tab:
        tab = Tab(id=str(uuid.uuid4()), url=url, title=title)
        self._tabs.append(tab)
        if activate or self._active_id is None:
            self._active_id = tab.id
        return tab

    def close_tab(self, tab_id: str) -> Optional[Tab]:
        tab = self.get(tab_id)
        if tab is None:
            return None
        was_active = (tab_id == self._active_id)
        self._tabs.remove(tab)
        if was_active:
            if self._tabs:
                self._active_id = self._tabs[0].id
            else:
                self._active_id = None
        return tab

    def activate(self, tab_id: str) -> Optional[Tab]:
        if self.get(tab_id) is None:
            return None
        self._active_id = tab_id
        return self.active

    def update_tab(self, tab_id: str, url: Optional[str] = None,
                   title: Optional[str] = None) -> None:
        tab = self.get(tab_id)
        if tab is None:
            return
        if url is not None:
            tab.url = url
        if title is not None:
            tab.title = title

    # ---- persistence helpers ----
    def to_dict(self) -> dict:
        return {
            "tabs": [t.to_dict() for t in self._tabs],
            "active_id": self._active_id,
        }

    def load_from_dict(self, d: dict) -> None:
        tabs_data = d.get("tabs") or []
        if not tabs_data:
            self.add_tab(activate=True)
            return
        self._tabs = [Tab.from_dict(x) for x in tabs_data]
        active = d.get("active_id")
        if active and self.get(active):
            self._active_id = active
        else:
            self._active_id = self._tabs[0].id
