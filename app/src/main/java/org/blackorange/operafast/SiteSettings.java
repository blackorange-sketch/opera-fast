package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;

/** Per-site choices: text size and desktop mode. */
final class SiteSettings {
    private SiteSettings() {}

    static final int[] ZOOMS = {80, 90, 100, 115, 130, 150, 175};

    private static SharedPreferences prefs;

    static void init(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("sites", Context.MODE_PRIVATE);
    }

    static int zoom(String host) {
        return prefs == null || host == null ? 100 : prefs.getInt(host + "|z", 100);
    }

    static void setZoom(String host, int percent) {
        if (prefs == null || host == null) return;
        if (percent == 100) prefs.edit().remove(host + "|z").apply();
        else prefs.edit().putInt(host + "|z", percent).apply();
    }

    /** TRUE / FALSE if the user chose for this site, null if never set. */
    static Boolean desktop(String host) {
        if (prefs == null || host == null || !prefs.contains(host + "|d")) return null;
        return Boolean.valueOf(prefs.getBoolean(host + "|d", false));
    }

    static void setDesktop(String host, boolean on) {
        if (prefs != null && host != null) prefs.edit().putBoolean(host + "|d", on).apply();
    }
}
