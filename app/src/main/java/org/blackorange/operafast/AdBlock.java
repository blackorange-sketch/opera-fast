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
        hosts = set;
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

    static int totalBlocked() {
        return totalBlocked.get();
    }
}
