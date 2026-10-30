package org.blackorange.operafast;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebView;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** One browser tab. The WebView is created lazily and may be released to save memory. */
final class Tab {
    final String id;
    volatile String url;
    String title = "";
    boolean desktop;
    String openerId;          // tab that opened this one (back closes it and returns there)

    WebView wv;               // null until the tab is shown for the first time / after eviction
    Bundle state;             // WebView back-forward state saved when wv was released
    Bitmap favicon;
    Bitmap thumb;             // small screenshot for the tab switcher (taken when it opens)
    boolean loading;
    int progress;
    long lastUsed;
    int crashes;              // renderer crashes since the user last loaded something here
    final AtomicInteger blocked = new AtomicInteger();

    Tab(String url) {
        this(UUID.randomUUID().toString(), url);
    }

    Tab(String id, String url) {
        this.id = id;
        this.url = url == null ? "" : url;
        this.lastUsed = System.currentTimeMillis();
    }

    boolean isBlank() {
        String u = url;
        return u == null || u.isEmpty() || u.equals("about:blank");
    }
}
