package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;

/** Small global switches (applied to every WebView). */
final class Settings {
    private Settings() {}

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
}
