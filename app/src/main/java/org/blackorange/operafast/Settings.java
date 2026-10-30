package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small global settings (applied to every WebView / screen). */
final class Settings {
    private Settings() {}

    static final String[] ENGINE_NAMES = {"Google", "DuckDuckGo", "Bing", "Brave"};
    private static final String[] ENGINE_URLS = {
            "https://www.google.com/search?q=",
            "https://duckduckgo.com/?q=",
            "https://www.bing.com/search?q=",
            "https://search.brave.com/search?q="};

    private static final String[][] DEFAULT_DIAL = {
            {"DuckDuckGo", "https://duckduckgo.com"},
            {"Google", "https://google.com"},
            {"YouTube", "https://youtube.com"},
            {"Wikipedia", "https://wikipedia.org"},
            {"GitHub", "https://github.com"},
            {"Reddit", "https://reddit.com"},
            {"Telegram", "https://web.telegram.org"},
            {"X", "https://x.com"},
    };

    private static SharedPreferences prefs;
    private static boolean night;
    private static boolean noImages;

    static void init(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        night = prefs.getBoolean("night", false);
        noImages = prefs.getBoolean("noImages", false);
    }

    static boolean night() {
        return night;
    }

    static void setNight(boolean on) {
        night = on;
        if (prefs != null) prefs.edit().putBoolean("night", on).apply();
    }

    static boolean noImages() {
        return noImages;
    }

    static void setNoImages(boolean on) {
        noImages = on;
        if (prefs != null) prefs.edit().putBoolean("noImages", on).apply();
    }

    // ---- language: "auto" | "en" | "uk"

    static String lang() {
        return prefs == null ? "auto" : prefs.getString("lang", "auto");
    }

    static void setLang(String l) {
        if (prefs != null) prefs.edit().putString("lang", l).apply();
    }

    static boolean uk() {
        String l = lang();
        if (l.equals("uk")) return true;
        if (l.equals("en")) return false;
        return Locale.getDefault().getLanguage().equals("uk");
    }

    // ---- search engine

    static int engine() {
        int e = prefs == null ? 1 : prefs.getInt("engine", 1);
        return e >= 0 && e < ENGINE_URLS.length ? e : 1;
    }

    static void setEngine(int e) {
        if (prefs != null) prefs.edit().putInt("engine", e).apply();
    }

    static String searchUrl() {
        return ENGINE_URLS[engine()];
    }

    // ---- start page: "" = speed dial, otherwise an address new tabs open

    static String homeUrl() {
        return prefs == null ? "" : prefs.getString("home", "");
    }

    static void setHomeUrl(String u) {
        if (prefs != null) prefs.edit().putString("home", u == null ? "" : u).apply();
    }

    /** Tree URI (content://...) of the folder chosen for downloads; "" = the Downloads folder. */
    static String dlFolder() {
        return prefs == null ? "" : prefs.getString("dlFolder", "");
    }

    static void setDlFolder(String f) {
        if (prefs != null) prefs.edit().putString("dlFolder", f == null ? "" : f).apply();
    }

    // ---- address-bar suggestions

    static boolean suggestNet() {
        return prefs == null || prefs.getBoolean("sugNet", true);
    }

    static void setSuggestNet(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("sugNet", on).apply();
    }

    static boolean suggestLocal() {
        return prefs == null || prefs.getBoolean("sugLocal", true);
    }

    static void setSuggestLocal(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("sugLocal", on).apply();
    }

    // ---- cache: prefer stored copies of pages (faster repeat visits, may show older content)

    static boolean cacheFirst() {
        return prefs != null && prefs.getBoolean("cacheFirst", false);
    }

    static void setCacheFirst(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("cacheFirst", on).apply();
    }

    static boolean blockPopups() {
        return prefs == null || prefs.getBoolean("blockPopups", true);
    }

    static void setBlockPopups(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("blockPopups", on).apply();
    }

    static boolean compat() {
        return prefs != null && prefs.getBoolean("compat", false);
    }

    static void setCompat(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("compat", on).apply();
    }

    // ---- security / behaviour switches

    static boolean lockSensitive() {
        return prefs == null || prefs.getBoolean("lock", true);
    }

    static void setLockSensitive(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("lock", on).apply();
    }

    static boolean httpsOnly() {
        return prefs != null && prefs.getBoolean("httpsOnly", false);
    }

    static void setHttpsOnly(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("httpsOnly", on).apply();
    }

    static boolean tintBars() {
        return prefs == null || prefs.getBoolean("tintBars", true);
    }

    static void setTintBars(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("tintBars", on).apply();
    }

    static boolean hideBars() {
        return prefs == null || prefs.getBoolean("hideBars", true);
    }

    static void setHideBars(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("hideBars", on).apply();
    }

    static boolean showTitle() {
        return prefs != null && prefs.getBoolean("showTitle", false);
    }

    static void setShowTitle(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("showTitle", on).apply();
    }

    /** 0 = dark, 1 = light, 2 = follow the system. */
    static int theme() {
        return prefs == null ? 0 : prefs.getInt("theme", 0);
    }

    static void setTheme(int i) {
        if (prefs != null) prefs.edit().putInt("theme", i).apply();
    }

    static int accent() {
        return prefs == null ? 0 : prefs.getInt("accent", 0);
    }

    static void setAccent(int i) {
        if (prefs != null) prefs.edit().putInt("accent", i).apply();
    }

    // ---- passwords

    static boolean savePasswords() {
        return prefs == null || prefs.getBoolean("savePw", true);
    }

    static void setSavePasswords(boolean on) {
        if (prefs != null) prefs.edit().putBoolean("savePw", on).apply();
    }

    // ---- speed dial: list of {name, url}

    static List<String[]> dial() {
        List<String[]> out = new ArrayList<String[]>();
        String raw = prefs == null ? null : prefs.getString("dial", null);
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    JSONArray p = a.getJSONArray(i);
                    out.add(new String[]{p.getString(0), p.getString(1)});
                }
                return out;
            } catch (Exception ignored) {
                out.clear();
            }
        }
        for (String[] d : DEFAULT_DIAL) out.add(d);
        return out;
    }

    static void setDial(List<String[]> list) {
        if (prefs == null) return;
        JSONArray a = new JSONArray();
        for (String[] p : list) {
            JSONArray e = new JSONArray();
            e.put(p[0]);
            e.put(p[1]);
            a.put(e);
        }
        prefs.edit().putString("dial", a.toString()).apply();
    }

    static void resetDial() {
        if (prefs != null) prefs.edit().remove("dial").apply();
    }
}
