package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import java.util.HashSet;
import java.util.Set;

/**
 * WebView has one shared cookie jar, so private tabs cannot be truly isolated. Instead we remember
 * which sites a private tab touched and delete their cookies when the last private tab closes
 * (or on next start if the app was killed first).
 */
final class PrivateData {
    private PrivateData() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("private", Context.MODE_PRIVATE);
    }

    static void note(Context c, String host) {
        if (host == null || host.isEmpty()) return;
        SharedPreferences p = prefs(c);
        Set<String> s = new HashSet<String>(p.getStringSet("hosts", new HashSet<String>()));
        if (s.add(host)) p.edit().putStringSet("hosts", s).apply();
    }

    static void wipe(Context c) {
        SharedPreferences p = prefs(c);
        Set<String> s = p.getStringSet("hosts", null);
        if (s == null || s.isEmpty()) return;
        CookieManager cm = CookieManager.getInstance();
        for (String host : s) {
            String h = host;
            while (h.indexOf('.') > 0) {
                expire(cm, h);
                h = h.substring(h.indexOf('.') + 1);
            }
        }
        cm.flush();
        p.edit().remove("hosts").apply();
    }

    private static void expire(CookieManager cm, String domain) {
        String url = "https://" + domain + "/";
        String all = cm.getCookie(url);
        if (all == null) return;
        for (String part : all.split(";")) {
            int eq = part.indexOf('=');
            String name = (eq > 0 ? part.substring(0, eq) : part).trim();
            if (name.isEmpty()) continue;
            String dead = name + "=; Max-Age=0; Path=/";
            cm.setCookie(url, dead);
            cm.setCookie(url, dead + "; Domain=" + domain);
            cm.setCookie(url, dead + "; Domain=." + domain);
        }
    }
}
