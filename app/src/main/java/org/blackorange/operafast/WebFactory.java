package org.blackorange.operafast;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;

/** Creates and configures WebViews; routes their events to the host activity. */
final class WebFactory {
    private WebFactory() {}

    interface Host {
        /** URL / title / loading state of a tab changed. */
        void onTabState(Tab tab);

        void onProgress(Tab tab, int progress);

        /** A page called window.open(): return a tab that already has a live WebView. */
        Tab onOpenWindow(Tab opener);

        void onShowCustomView(View view, WebChromeClient.CustomViewCallback cb);

        void onHideCustomView();

        boolean onFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params);

        void onDownload(String url, String userAgent, String contentDisposition, String mime);

        /** Non-web scheme (mailto:, tel:, intent:, market: ...) after a user gesture. */
        void onExternalUrl(String url);

        /** The renderer process died (usually out of memory). */
        void onRenderGone(Tab tab);

        /** Long press on a link. */
        void onLinkLongPress(Tab tab, String url);
    }

    static WebView create(Context ctx, final Tab tab, final Host host) {
        WebView wv = new WebView(ctx);
        wv.setTag(tab);
        wv.setBackgroundColor(0xFFFFFFFF);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        applyUserAgent(wv, tab.desktop);
        applyDisplayPrefs(wv);

        wv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult r = ((WebView) v).getHitTestResult();
                if (r != null && r.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    String link = r.getExtra();
                    if (link != null) {
                        host.onLinkLongPress(tab, link);
                        return true;
                    }
                }
                return false;
            }
        });

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        if ((ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        wv.setWebViewClient(new Client(tab, host));
        wv.setWebChromeClient(new Chrome(tab, host));
        wv.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                host.onDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
        return wv;
    }

    /**
     * Looks like Chrome instead of "an embedded WebView" (drops the "; wv" and "Version/4.0"
     * markers) so sites such as Google sign-in do not refuse the browser. Desktop mode also
     * swaps the platform token and drops "Mobile".
     */
    static void applyUserAgent(WebView wv, boolean desktop) {
        String ua = WebSettings.getDefaultUserAgent(wv.getContext())
                .replace("; wv", "")
                .replace(" Version/4.0", "");
        if (desktop) {
            ua = ua.replaceAll("\\(Linux; Android[^)]*\\)", "(X11; Linux x86_64)")
                    .replace(" Mobile", "");
        }
        WebSettings s = wv.getSettings();
        s.setUserAgentString(ua);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
    }

    /** Night mode (dark pages) and text-only mode; called on creation and when toggled. */
    @SuppressWarnings("deprecation")
    static void applyDisplayPrefs(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setLoadsImagesAutomatically(!Settings.noImages());
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(Settings.night());
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(Settings.night() ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
    }

    private static final class Client extends WebViewClient {
        private final Tab tab;
        private final Host host;

        Client(Tab tab, Host host) {
            this.tab = tab;
            this.host = host;
        }

        // Runs on a WebView network thread: must not touch WebView methods (getUrl() would throw).
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            try {
                if (!req.isForMainFrame()) {
                    Uri u = req.getUrl();
                    String pageHost = hostOf(tab.url);
                    if (AdBlock.shouldBlock(u.getHost(), pageHost)) {
                        tab.blocked.incrementAndGet();
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
            } catch (Exception ignored) {
                // never let the interceptor crash the renderer
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase(java.util.Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")
                    || scheme.equals("data") || scheme.equals("blob")
                    || scheme.equals("javascript") || scheme.equals("file")) {
                return false;
            }
            if (req.hasGesture()) host.onExternalUrl(u.toString());
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null) tab.url = url;
            tab.loading = true;
            tab.progress = 0;
            tab.blocked.set(0);
            tab.favicon = favicon;
            host.onTabState(tab);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url != null) tab.url = url;
            tab.loading = false;
            tab.progress = 100;
            String title = view.getTitle();
            if (title != null && !title.isEmpty()) tab.title = title;
            HistoryDb.get(view.getContext()).visit(tab.url, tab.title);
            host.onTabState(tab);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url != null) tab.url = url;
            host.onTabState(tab);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            host.onRenderGone(tab);
            return true;
        }
    }

    private static final class Chrome extends WebChromeClient {
        private final Tab tab;
        private final Host host;

        Chrome(Tab tab, Host host) {
            this.tab = tab;
            this.host = host;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            tab.progress = newProgress;
            host.onProgress(tab, newProgress);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (title == null) return;
            tab.title = title;
            HistoryDb.get(view.getContext()).setTitle(tab.url, title);
            host.onTabState(tab);
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            tab.favicon = icon;
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            if (!isUserGesture) return false; // popup blocker
            Tab nt = host.onOpenWindow(tab);
            if (nt == null || nt.wv == null) return false;
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(nt.wv);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            host.onShowCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            host.onHideCustomView();
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams params) {
            return host.onFileChooser(filePathCallback, params);
        }
    }

    static String hostOf(String url) {
        if (url == null) return null;
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
