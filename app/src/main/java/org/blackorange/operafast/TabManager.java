package org.blackorange.operafast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Ordered list of tabs, the active one, and JSON (de)serialisation for the session. */
final class TabManager {
    private final ArrayList<Tab> tabs = new ArrayList<Tab>();
    private Tab active;

    List<Tab> all() {
        return tabs;
    }

    int privateCount() {
        int n = 0;
        for (Tab t : tabs) {
            if (t.priv) n++;
        }
        return n;
    }

    int size() {
        return tabs.size();
    }

    Tab active() {
        return active;
    }

    Tab byId(String id) {
        if (id == null) return null;
        for (Tab t : tabs) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    Tab add(String url, Tab opener) {
        return add(url, opener, false);
    }

    Tab add(String url, Tab opener, boolean priv) {
        Tab t = new Tab(url);
        t.priv = priv;
        if (opener != null) t.openerId = opener.id;
        tabs.add(t);
        return t;
    }

    void activate(Tab t) {
        active = t;
        t.lastUsed = System.currentTimeMillis();
    }

    /** Removes the tab and returns the tab that should be active afterwards (never null). */
    Tab close(Tab t) {
        int idx = tabs.indexOf(t);
        tabs.remove(t);
        if (tabs.isEmpty()) {
            Tab n = add(null, null);
            active = n;
            return n;
        }
        if (active == t || active == null) {
            Tab next = byId(t.openerId);
            if (next == null) next = tabs.get(Math.min(Math.max(idx, 0), tabs.size() - 1));
            active = next;
        }
        return active;
    }

    String toJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Tab t : tabs) {
                if (t.priv) continue;
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("url", t.url == null ? "" : t.url);
                o.put("title", t.title == null ? "" : t.title);
                o.put("desktop", t.desktop);
                if (t.openerId != null) o.put("opener", t.openerId);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("tabs", arr);
            root.put("active", active == null || active.priv ? "" : active.id);
            return root.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns true if at least one tab was restored. */
    boolean restore(String json) {
        if (json == null || json.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("tabs");
            if (arr == null || arr.length() == 0) return false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab t = new Tab(o.optString("id", null), o.optString("url", ""));
                if (t.id == null) continue;
                t.title = o.optString("title", "");
                t.desktop = o.optBoolean("desktop", false);
                String opener = o.optString("opener", "");
                if (!opener.isEmpty()) t.openerId = opener;
                tabs.add(t);
            }
            if (tabs.isEmpty()) return false;
            active = byId(root.optString("active", ""));
            if (active == null) active = tabs.get(0);
            return true;
        } catch (Exception e) {
            tabs.clear();
            active = null;
            return false;
        }
    }
}
