package org.blackorange.operafast;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * WebViewClient with ad blocking via shouldInterceptRequest.
 *
 * Design:
 *  - Static state so Python (jnius) can configure the client and read stats.
 *  - setBlockedHostsFromString() accepts newline-separated hostnames
 *    (avoids pushing 80k items via jnius one at a time).
 *  - Blocked requests return an empty 200 OK; the browser treats them
 *    as loaded but empty, so no error UI.
 *  - Per-site whitelist: if the top-level page host is whitelisted,
 *    nothing is blocked for that page.
 */
public class AdBlockClient extends WebViewClient {

    private static final Set<String> blockedHosts = new HashSet<>();
    private static final Set<String> whitelist = new HashSet<>();
    private static volatile int blockedCount = 0;
    private static volatile boolean enabled = true;

    public static void setBlockedHostsFromString(String newlineSeparated) {
        blockedHosts.clear();
        if (newlineSeparated == null || newlineSeparated.isEmpty()) {
            return;
        }
        for (String raw : newlineSeparated.split("\n")) {
            String host = raw.trim().toLowerCase();
            if (host.isEmpty() || host.startsWith("#")) {
                continue;
            }
            blockedHosts.add(host);
        }
    }

    public static int getBlockedHostCount() {
        return blockedHosts.size();
    }

    public static int getBlockedCount() {
        return blockedCount;
    }

    public static void resetBlockedCount() {
        blockedCount = 0;
    }

    public static void setEnabled(boolean e) {
        enabled = e;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void whitelistSite(String host) {
        if (host != null && !host.isEmpty()) {
            whitelist.add(host.toLowerCase());
        }
    }

    public static void unwhitelistSite(String host) {
        if (host != null) {
            whitelist.remove(host.toLowerCase());
        }
    }

    private static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (!enabled) {
            return super.shouldInterceptRequest(view, request);
        }
        try {
            String reqHost = hostOf(request.getUrl().toString());
            if (reqHost == null) {
                return super.shouldInterceptRequest(view, request);
            }
            reqHost = reqHost.toLowerCase();

            // Per-site whitelist: if the page the user is on is whitelisted,
            // do not block anything on that page.
            String pageHost = hostOf(view.getUrl());
            if (pageHost != null && whitelist.contains(pageHost.toLowerCase())) {
                return super.shouldInterceptRequest(view, request);
            }

            // Exact match against blocked set.
            if (blockedHosts.contains(reqHost)) {
                blockedCount++;
                return new WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        new ByteArrayInputStream(new byte[0])
                );
            }
        } catch (Exception e) {
            // Never propagate to WebView — could crash the renderer.
        }
        return super.shouldInterceptRequest(view, request);
    }
}
