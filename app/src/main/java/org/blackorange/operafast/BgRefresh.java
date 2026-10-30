package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Invisible freshness check: downloads only the page's HTML in the background, reduces it to
 * its visible text and compares a hash with the one stored last time. The shown page is touched
 * only when the text really changed.
 */
final class BgRefresh {
    private BgRefresh() {}

    interface Result {
        /** Called on the main thread. */
        void onChecked(boolean changed);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void check(final Context c, final String url, final String ua, final Result r) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean changed = false;
                try {
                    String html = fetch(url, ua);
                    if (html != null) {
                        int h = fingerprint(html);
                        SharedPreferences p = app.getSharedPreferences("pagehash", Context.MODE_PRIVATE);
                        String key = "h" + url.hashCode();
                        if (p.contains(key) && p.getInt(key, 0) != h) changed = true;
                        SharedPreferences.Editor e = p.edit();
                        if (p.getAll().size() > 600) e.clear();
                        e.putInt(key, h).apply();
                    }
                } catch (Exception ignored) {
                }
                final boolean res = changed;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        r.onChecked(res);
                    }
                });
            }
        }, "bg-refresh").start();
    }

    private static String fetch(String url, String ua) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            con.setInstanceFollowRedirects(true);
            if (ua != null) con.setRequestProperty("User-Agent", ua);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) con.setRequestProperty("Cookie", cookie);
            con.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            if (con.getResponseCode() != 200) return null;
            String type = con.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.US).contains("html")) return null;
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 1500000) break;
            }
            in.close();
            return bo.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    /** Visible-ish text only: no scripts, styles, tags, digits (counters, nonces, timestamps). */
    static int fingerprint(String html) {
        String s = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("[0-9]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return s.hashCode() * 31 + s.length();
    }
}
