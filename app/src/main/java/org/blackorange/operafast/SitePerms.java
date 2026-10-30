package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembered answers to "allow this site to use the camera / microphone / location?". */
final class SitePerms {
    private SitePerms() {}

    static final String CAMERA = "camera";
    static final String MIC = "mic";
    static final String LOCATION = "location";

    private static SharedPreferences prefs;

    static void init(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("siteperms", Context.MODE_PRIVATE);
    }

    /** TRUE = allowed, FALSE = denied, null = never asked. */
    static Boolean get(String host, String type) {
        if (prefs == null || host == null) return null;
        String v = prefs.getString(host + "|" + type, null);
        return v == null ? null : Boolean.valueOf(v.equals("1"));
    }

    static void set(String host, String type, boolean allow) {
        if (prefs != null && host != null) {
            prefs.edit().putString(host + "|" + type, allow ? "1" : "0").apply();
        }
    }

    static void clear() {
        if (prefs != null) prefs.edit().clear().apply();
    }
}
