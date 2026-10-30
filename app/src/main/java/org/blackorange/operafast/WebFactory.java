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
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
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

        void onDownload(Tab tab, String url, String userAgent, String contentDisposition, String mime);

        /** Non-web scheme (mailto:, tel:, intent:, market: ...) after a user gesture. */
        void onExternalUrl(String url);

        /** The renderer process died (usually out of memory). */
        void onRenderGone(Tab tab);

        /** Long press on a link. */
        void onLinkLongPress(Tab tab, String url);

        /** A login form was submitted (called on the main thread). */
        void onPasswordSubmit(Tab tab, String user, String pass);

        /** A saved login exists for this page and it shows a password field. */
        void onLoginAvailable(Tab tab, String user);

        /** Main-frame certificate problem; the host must call handler.proceed() or cancel(). */
        void onSslError(Tab tab, android.webkit.SslErrorHandler handler, android.net.http.SslError error);

        /** A page asks for camera / microphone. */
        void onSitePermission(Tab tab, android.webkit.PermissionRequest request);

        /** The page scrolled (dy > 0 = content moved up). */
        void onScrolled(Tab tab, int dy, int y);

        /** A page asks for the location. */
        void onGeoPrompt(Tab tab, String origin, android.webkit.GeolocationPermissions.Callback cb);
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
        applyUserAgent(wv, tab.desktop);
        applyDisplayPrefs(wv);
        s.setDatabaseEnabled(true);
        if (android.os.Build.VERSION.SDK_INT >= 23) s.setOffscreenPreRaster(true);   // smoother scrolling
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        if (tab.priv) {
            s.setSaveFormData(false);
        } else {
            wv.addJavascriptInterface(new PassBridge(tab, host), "OFPass");
        }

        wv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                host.onScrolled(tab, sy - oy, sy);
            }
        });

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
                host.onDownload(tab, url, userAgent, contentDisposition, mimetype);
            }
        });
        return wv;
    }

    /** JS -> Java: only lets a page *offer* a login to be saved; nothing can be read back. */
    private static final class PassBridge {
        private final Tab tab;
        private final Host host;

        PassBridge(Tab tab, Host host) {
            this.tab = tab;
            this.host = host;
        }

        @android.webkit.JavascriptInterface
        public void save(String token, final String user, final String pass) {
            // frames other than the top one never see the token, so they cannot fake a login
            if (token == null || !token.equals(tab.bridgeToken)) return;
            if (pass == null || pass.isEmpty() || pass.length() > 256) return;
            final String u = user == null ? "" : user;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    host.onPasswordSubmit(tab, u.length() > 256 ? u.substring(0, 256) : u, pass);
                }
            });
        }
    }

    private static String captureJs(String token) {
        return "(function(){if(window.__ofp)return;window.__ofp=1;var T='" + token + "';"
                + CAPTURE_BODY;
    }

    private static final String CAPTURE_BODY = ""
            + "function grab(root){var ps=(root||document).querySelectorAll('input[type=password]');"
            + "var p=null;for(var i=0;i<ps.length;i++){if(ps[i].value){p=ps[i];break;}}if(!p)return;"
            + "var scope=p.form||document,ins=scope.querySelectorAll('input'),prev=null;"
            + "for(var j=0;j<ins.length;j++){if(ins[j]===p)break;var t=(ins[j].type||'text').toLowerCase();"
            + "if((t=='text'||t=='email'||t=='tel')&&ins[j].value)prev=ins[j];}"
            + "try{OFPass.save(T,prev?prev.value:'',p.value);}catch(e){}}"
            + "document.addEventListener('submit',function(e){grab(e.target)},true);"
            + "document.addEventListener('click',function(e){var t=e.target&&e.target.closest?"
            + "e.target.closest('button,input[type=submit]'):null;if(t&&t.type!='button'&&t.type!='reset')grab(document)},true);"
            + "document.addEventListener('keydown',function(e){if(e.key=='Enter')grab(document)},true);})();";

    private static String fillJs(String user, String pass) {
        return "(function(u,p){var n=0;function set(e,v){var d=Object.getOwnPropertyDescriptor("
                + "HTMLInputElement.prototype,'value');d.set.call(e,v);"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "function f(){var ps=document.querySelectorAll('input[type=password]'),done=false;"
                + "for(var i=0;i<ps.length;i++){var e=ps[i];if(e.offsetParent===null||e.value)continue;"
                + "var scope=e.form||document,ins=scope.querySelectorAll('input'),prev=null;"
                + "for(var j=0;j<ins.length;j++){if(ins[j]===e)break;var t=(ins[j].type||'text').toLowerCase();"
                + "if((t=='text'||t=='email'||t=='tel')&&ins[j].offsetParent!==null)prev=ins[j];}"
                + "if(prev&&!prev.value)set(prev,u);set(e,p);done=true;}"
                + "if(!done&&++n<12)setTimeout(f,500);}f();})("
                + org.json.JSONObject.quote(user) + "," + org.json.JSONObject.quote(pass) + ");";
    }

    /** Installs the form-submit watcher and tells the host when a saved login fits this page. */
    private static void passwordHooks(final WebView view, final Tab tab, final Host host) {
        if (tab.priv || !Settings.savePasswords()) return;
        tab.bridgeToken = Long.toHexString(new java.security.SecureRandom().nextLong())
                + Long.toHexString(new java.security.SecureRandom().nextLong());
        view.evaluateJavascript(captureJs(tab.bridgeToken), null);
        final String url = tab.url;
        if (url == null || !url.startsWith("https://")) return;
        final PasswordStore.Item it = PasswordStore.find(hostOf(url), null);
        if (it == null || it.pass.isEmpty()) return;
        pollForm(view, tab, host, it.user, url, 0);
    }

    /** Forms often appear after load; look a few times, then offer the saved login. */
    private static void pollForm(final WebView view, final Tab tab, final Host host,
                                 final String user, final String url, final int n) {
        view.evaluateJavascript("!!document.querySelector('input[type=password]')",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        if (!url.equals(tab.url)) return; // navigated away
                        if ("true".equals(v)) {
                            host.onLoginAvailable(tab, user);
                        } else if (n < 6) {
                            view.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    pollForm(view, tab, host, user, url, n + 1);
                                }
                            }, 700);
                        }
                    }
                });
    }

    /** Fills the saved login into the page (called when the user taps "Fill"). */
    static void fillLogin(WebView view, Tab tab) {
        if (tab.priv || tab.url == null || !tab.url.startsWith("https://")) return;
        PasswordStore.Item it = PasswordStore.find(hostOf(tab.url), null);
        if (it != null && !it.pass.isEmpty()) view.evaluateJavascript(fillJs(it.user, it.pass), null);
    }

    static boolean isLocalHost(String h) {
        return h == null || h.equals("localhost") || h.endsWith(".local")
                || h.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }

    /** HTTPS-only mode: the https:// twin of a plain http:// address, or null to leave it alone. */
    static String upgrade(Tab tab, String url) {
        if (!Settings.httpsOnly() || url == null || !url.startsWith("http://")) return null;
        String h = hostOf(url);
        if (isLocalHost(h) || h.equals(tab.noUpgradeHost)) return null;
        tab.fallbackHttp = url;
        return "https://" + url.substring(7);
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

    /** Per-site text size, and desktop mode if the user chose it for this site. */
    private static void applySitePrefs(WebView view, Tab tab) {
        String h = hostOf(tab.url);
        if (h == null) return;
        view.getSettings().setTextZoom(SiteSettings.zoom(h));
        final Boolean d = SiteSettings.desktop(h);
        if (d != null && d.booleanValue() != tab.desktop) {
            tab.desktop = d.booleanValue();
            applyUserAgent(view, tab.desktop);
            final WebView v = view;
            view.post(new Runnable() {
                @Override
                public void run() {
                    v.reload();
                }
            });
        }
    }

    private static boolean online(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm == null ? null : cm.getActiveNetwork();
            android.net.NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            return caps != null
                    && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    /** Night mode (dark pages) and text-only mode; called on creation and when toggled. */
    @SuppressWarnings("deprecation")
    static void applyDisplayPrefs(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setLoadsImagesAutomatically(!Settings.noImages());
        s.setMixedContentMode(Settings.httpsOnly() ? WebSettings.MIXED_CONTENT_NEVER_ALLOW
                : WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        Object tag = wv.getTag();
        if (tag instanceof Tab && ((Tab) tag).priv) {
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        } else if (Settings.cacheFirst() || !online(wv.getContext())) {
            // faster repeat visits, and pages still open without a connection
            s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        } else {
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(Settings.night());
        } else if (android.os.Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(Settings.night() ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }
    }

    /**
     * Hybrid cache: the page was shown from the cache (fast load); when the reader hasn't
     * scrolled yet, quietly revalidate it with the server so it is up to date.
     */
    /**
     * Newly exposed WebView area (when the bars slide away) is painted with the WebView's own
     * background for a frame or two: make that the page's background colour, not white.
     */
    static void syncBackground(final WebView view) {
        view.evaluateJavascript("(function(){function c(e){if(!e)return null;var x=getComputedStyle(e)"
                + ".backgroundColor;return x&&x!=='rgba(0, 0, 0, 0)'&&x!=='transparent'?x:null}"
                + "return c(document.body)||c(document.documentElement)||''})()",
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("(\\d+)\\D+(\\d+)\\D+(\\d+)").matcher(v == null ? "" : v);
                            int color = 0xFFFFFFFF;
                            if (m.find()) {
                                color = 0xFF000000 | (Integer.parseInt(m.group(1)) << 16)
                                        | (Integer.parseInt(m.group(2)) << 8) | Integer.parseInt(m.group(3));
                            }
                            view.setBackgroundColor(color);
                        } catch (Exception ignored) {
                        }
                    }
                });
    }

    /** The bottom bar floats over the page: leave room at the very end so the footer stays reachable. */
    static void addBottomSpacer(WebView view) {
        view.evaluateJavascript("(function(){if(document.getElementById('__of_sp'))return;"
                + "var d=document.createElement('div');d.id='__of_sp';"
                + "d.style.cssText='height:56px;width:1px;pointer-events:none';"
                + "(document.body||document.documentElement).appendChild(d)})()", null);
    }

    private static void hybridRefresh(WebView view) {
        Object tag = view.getTag();
        if (!(tag instanceof Tab)) return;
        Tab t = (Tab) tag;
        if (t.priv || !Settings.cacheFirst() || t.url == null || !t.url.startsWith("http")) return;
        if (t.revalidated != null) {
            // this navigation was already refreshed once (or the user reloaded): never again
            applyDisplayPrefs(view);
            return;
        }
        final boolean fast = System.currentTimeMillis() - t.startedAt < 1500;   // loaded quickly = from cache
        if (!online(view.getContext())) return;
        final String url = t.url;
        t.revalidated = url;   // one check per navigation
        final WebView v = view;
        final Tab tab = t;
        BgRefresh.check(view.getContext(), url, view.getSettings().getUserAgentString(),
                new BgRefresh.Result() {
                    @Override
                    public void onChecked(boolean changed) {
                        // the page text really changed on the server: only then touch the shown page
                        if (changed && fast && tab.wv == v && url.equals(tab.url) && v.getScrollY() == 0) {
                            freshReload(v);
                        }
                    }
                });
    }

    /** Reload that revalidates with the server, once per navigation. */
    static void freshReload(WebView view) {
        Object tag = view.getTag();
        if (tag instanceof Tab) {
            Tab t = (Tab) tag;
            t.selfReload = true;
            t.revalidated = t.url == null ? "" : t.url;
        }
        view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        view.reload();
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
            if (scheme.equals("http") && req.isForMainFrame()) {
                String up = upgrade(tab, u.toString());
                if (up != null) {
                    view.loadUrl(up);
                    return true;
                }
            }
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")
                    || scheme.equals("data") || scheme.equals("blob")
                    || scheme.equals("javascript") || scheme.equals("file")) {
                return false;
            }
            if (req.hasGesture()) host.onExternalUrl(u.toString());
            return true;
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            // soft fade-in instead of a hard cut when the new page first paints
            syncBackground(view);
            view.setAlpha(0.55f);
            view.animate().alpha(1f).setDuration(180).start();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null) tab.url = url;
            tab.loading = true;
            tab.startedAt = System.currentTimeMillis();
            if (tab.selfReload) tab.selfReload = false;
            else tab.revalidated = null;
            tab.progress = 0;
            tab.blocked.set(0);
            tab.favicon = favicon;
            if (tab.priv) PrivateData.note(view.getContext(), hostOf(tab.url));
            applySitePrefs(view, tab);
            host.onTabState(tab);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url != null) tab.url = url;
            tab.loading = false;
            tab.progress = 100;
            String title = view.getTitle();
            if (title != null && !title.isEmpty()) tab.title = title;
            if (!tab.priv) HistoryDb.get(view.getContext()).visit(tab.url, tab.title);
            if (tab.fallbackHttp != null && tab.url != null && tab.url.startsWith("https://")) {
                tab.fallbackHttp = null; // the upgrade worked
            }
            passwordHooks(view, tab, host);
            syncBackground(view);
            addBottomSpacer(view);
            hybridRefresh(view);
            host.onTabState(tab);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url != null) tab.url = url;
            host.onTabState(tab);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req,
                                    android.webkit.WebResourceError err) {
            if (req.isForMainFrame() && tab.fallbackHttp != null) {
                // the https twin does not work: go back to plain http once, for this host
                String http = tab.fallbackHttp;
                tab.fallbackHttp = null;
                tab.noUpgradeHost = hostOf(http);
                view.loadUrl(http);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                       android.net.http.SslError error) {
            String shown = tab.url == null ? "" : tab.url;
            String failing = error.getUrl() == null ? "" : error.getUrl();
            if (hostOf(shown) != null && hostOf(shown).equals(hostOf(failing))) {
                host.onSslError(tab, handler, error);
            } else {
                handler.cancel();
            }
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
            if (!tab.priv) HistoryDb.get(view.getContext()).setTitle(tab.url, title);
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
        public void onPermissionRequest(PermissionRequest request) {
            host.onSitePermission(tab, request);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback cb) {
            host.onGeoPrompt(tab, origin, cb);
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
