package org.blackorange.operafast;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host-based ad blocker. The hosts list is parsed on a background thread so it never
 * delays startup; until it is ready nothing is blocked. Safe to call from WebView's
 * network threads (the set is published once via a volatile reference).
 */
final class AdBlock {
    private AdBlock() {}

    private static volatile Set<String> hosts = new HashSet<String>();
    private static final Set<String> allowedSites =
            Collections.synchronizedSet(new HashSet<String>());
    private static final AtomicInteger totalBlocked = new AtomicInteger();
    private static volatile boolean enabled = true;
    private static SharedPreferences prefs;

    static void init(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences("adblock", Context.MODE_PRIVATE);
        enabled = prefs.getBoolean("enabled", true);
        Set<String> saved = prefs.getStringSet("allowed", null);
        if (saved != null) allowedSites.addAll(saved);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loadHosts(app);
                Filters.load(app);
                if (Filters.stale(app) && WebFactory.online(app)) {
                    Filters.download(app);
                }
            }
        }, "adblock-load");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void loadHosts(Context app) {
        HashSet<String> set = new HashSet<String>(1 << 17);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    app.getAssets().open("adblock/hosts.txt"), "UTF-8"), 1 << 16);
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    set.add(line.toLowerCase(Locale.ROOT));
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
            // No list -> no blocking, browsing still works.
        }
        try {
            java.io.File extra = new java.io.File(app.getFilesDir(), "adblock-hosts.txt");
            if (extra.exists()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        new java.io.FileInputStream(extra), "UTF-8"), 1 << 16);
                try {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) set.add(line.toLowerCase(Locale.ROOT));
                    }
                } finally {
                    r.close();
                }
            }
        } catch (Exception ignored) {
        }
        hosts = set;
    }

    interface UpdateCallback {
        /** rules >= 0 on success, -1 on failure; runs on the main thread. */
        void done(int rules);
    }

    /** Downloads a fresh hosts list (StevenBlack unified, ads + malware) and reloads the rules. */
    static void update(Context ctx, final UpdateCallback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int result = -1;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                            .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    if (c.getResponseCode() == 200) {
                        java.io.File tmp = new java.io.File(app.getFilesDir(), "adblock-hosts.tmp");
                        java.io.PrintWriter w = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                                new java.io.FileOutputStream(tmp), "UTF-8"));
                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(c.getInputStream(), "UTF-8"), 1 << 16);
                        int n = 0;
                        String line;
                        while ((line = r.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.charAt(0) == '#') continue;
                            String[] p = line.split("\\s+");
                            if (p.length >= 2 && (p[0].equals("0.0.0.0") || p[0].equals("127.0.0.1"))
                                    && p[1].indexOf('.') > 0 && !p[1].equals("0.0.0.0")) {
                                w.println(p[1].toLowerCase(Locale.ROOT));
                                n++;
                            }
                        }
                        w.close();
                        r.close();
                        if (n > 1000) {
                            java.io.File dst = new java.io.File(app.getFilesDir(), "adblock-hosts.txt");
                            dst.delete();
                            tmp.renameTo(dst);
                            loadHosts(app);
                            result = hosts.size();
                        } else {
                            tmp.delete();
                        }
                    }
                    c.disconnect();
                } catch (Exception ignored) {
                }
                boolean lists = Filters.download(app);
                if (lists && result < 0) result = hosts.size();
                if (result >= 0) result += Filters.ruleCount();
                final int res = result;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(res);
                    }
                });
            }
        }, "adblock-update").start();
    }

    /** reqHost: host of the sub-resource; pageHost: host of the page that requested it. */
    static boolean shouldBlock(String reqHost, String pageHost) {
        if (!enabled || reqHost == null) return false;
        if (pageHost != null && allowedSites.contains(pageHost)) return false;
        if (hosts.contains(reqHost.toLowerCase(Locale.ROOT))) {
            totalBlocked.incrementAndGet();
            return true;
        }
        return false;
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void setEnabled(boolean on) {
        enabled = on;
        if (prefs != null) prefs.edit().putBoolean("enabled", on).apply();
    }

    static boolean isAllowed(String host) {
        return host != null && allowedSites.contains(host);
    }

    static void setAllowed(String host, boolean allow) {
        if (host == null || host.isEmpty()) return;
        if (allow) allowedSites.add(host); else allowedSites.remove(host);
        if (prefs != null) {
            prefs.edit().putStringSet("allowed", new HashSet<String>(allowedSites)).apply();
        }
    }

    static int ruleCount() {
        return hosts.size();
    }

    static void countBlocked() {
        totalBlocked.incrementAndGet();
    }

    static int totalBlocked() {
        return totalBlocked.get();
    }
}
