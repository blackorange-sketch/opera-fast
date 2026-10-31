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

        /** An ad-like pop-up tab was opened; close it. */
        void onPopupBlocked(Tab tab);

        void onShowCustomView(View view, WebChromeClient.CustomViewCallback cb);

        void onHideCustomView();

        boolean onFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params);

        void onDownload(Tab tab, String url, String userAgent, String contentDisposition, String mime,
                        long length);

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
        wv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                tab.lastTouch = System.currentTimeMillis();
                return false;
            }
        });
        // neutral until the page paints (then syncBackground() takes the page's own colour)
        wv.setBackgroundColor(tab.priv ? 0xFF1C1428 : UI.BG_APP);

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
                host.onDownload(tab, url, userAgent, contentDisposition, mimetype, contentLength);
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
     * WebView adds "X-Requested-With: <package>" to every request. Some servers take that for an
     * AJAX call and answer with a bare page fragment (no head, no scripts). An empty value in the
     * extra headers replaces it for the navigation itself.
     */
    static void go(WebView v, String url) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            java.util.HashMap<String, String> h = new java.util.HashMap<String, String>();
            h.put("X-Requested-With", "");
            v.loadUrl(url, h);
        } else {
            v.loadUrl(url);
        }
    }

    static void again(WebView v) {
        String u = v.getUrl();
        if (u != null && u.startsWith("http")) go(v, u); else v.reload();
    }

    /**
     * Looks like Chrome instead of "an embedded WebView" (drops the "; wv" and "Version/4.0"
     * markers) so sites such as Google sign-in do not refuse the browser. Desktop mode also
     * swaps the platform token and drops "Mobile".
     */
    static void applyUserAgent(WebView wv, boolean desktop) {
        String ua = WebSettings.getDefaultUserAgent(wv.getContext());
        if (!Settings.compat()) ua = ua.replace("; wv", "").replace(" Version/4.0", "");
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
                    again(v);
                }
            });
        }
    }

    static boolean online(Context c) {
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
    static void syncBackground(final WebView view, final Host host) {
        view.evaluateJavascript("(function(){function c(e){if(!e)return '';var x=getComputedStyle(e)"
                + ".backgroundColor;return x&&x!=='rgba(0, 0, 0, 0)'&&x!=='transparent'?x:''}"
                + "var bg=c(document.body)||c(document.documentElement);"
                + "var m=document.querySelector('meta[name=theme-color]'),th='';"
                + "if(m&&m.content){var i=document.createElement('i');i.style.color=m.content;"
                + "document.documentElement.appendChild(i);th=getComputedStyle(i).color;i.remove()}"
                + "return bg+'|'+th})()",
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        try {
                            String s = v == null ? "" : v.replace("\"", "");
                            int bar = s.indexOf('|');
                            Integer bg = parseRgb(bar < 0 ? s : s.substring(0, bar));
                            Integer th = bar < 0 ? null : parseRgb(s.substring(bar + 1));
                            view.setBackgroundColor(bg != null ? bg : 0xFFFFFFFF);
                            Object tag = view.getTag();
                            if (tag instanceof Tab) {
                                Tab t = (Tab) tag;
                                int pc = th != null ? th : (bg != null ? bg : 0);
                                if (pc != t.pageColor) {
                                    t.pageColor = pc;
                                    host.onTabState(t);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
    }

    private static Integer parseRgb(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\D+(\\d+)\\D+(\\d+)").matcher(s == null ? "" : s);
        if (!m.find()) return null;
        return 0xFF000000 | (Integer.parseInt(m.group(1)) << 16)
                | (Integer.parseInt(m.group(2)) << 8) | Integer.parseInt(m.group(3));
    }

    /** Room (CSS px) kept free under the floating top / bottom bars. */
    static final int INSET_TOP = 54, INSET_BOTTOM = 56;

    /** The bars float over the page: leave room at its start and end so nothing is hidden under them. */
    static void addBottomSpacer(WebView view) {
        // CSS only: never insert elements into the page (that can break frameworks that
        // hydrate the DOM and rules like body > :first-child)
        view.evaluateJavascript("(function(){var h=document.documentElement;if(!h)return;"
                + "var s=h.style;s.setProperty('padding-top','" + INSET_TOP + "px','important');"
                + "s.setProperty('padding-bottom','" + INSET_BOTTOM + "px','important');"
                + "s.setProperty('scroll-padding-top','" + INSET_TOP + "px')})()", null);
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
        again(view);
    }

    private static int typeOf(String url, String accept) {
        if (accept != null) {
            if (accept.startsWith("image/")) return Filters.IMAGE;
            if (accept.startsWith("text/css")) return Filters.CSS;
        }
        String p = url;
        int q = p.indexOf('?');
        if (q > 0) p = p.substring(0, q);
        p = p.toLowerCase(java.util.Locale.ROOT);
        if (p.endsWith(".js") || p.endsWith(".mjs")) return Filters.SCRIPT;
        if (p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".gif")
                || p.endsWith(".webp") || p.endsWith(".svg")) return Filters.IMAGE;
        if (p.endsWith(".css")) return Filters.CSS;
        if (p.endsWith(".woff") || p.endsWith(".woff2") || p.endsWith(".ttf")) return Filters.FONT;
        if (p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mp3")) return Filters.MEDIA;
        return Filters.OTHER | Filters.XHR | Filters.SUBDOC | Filters.SCRIPT;
    }

    /** Hides ad elements known for this page with an injected stylesheet. */
    static void cosmetic(WebView view, Tab tab) {
        if (!AdBlock.isEnabled() || tab.url == null) return;
        String h = hostOf(tab.url);
        if (h == null || AdBlock.isAllowed(h)) return;
        String css = Filters.cssFor(h);
        if (css.isEmpty()) return;
        view.evaluateJavascript("(function(){if(document.getElementById('__of_hide'))return;"
                + "var s=document.createElement('style');s.id='__of_hide';s.textContent="
                + org.json.JSONObject.quote(css)
                + ";(document.head||document.documentElement).appendChild(s)})()", null);
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
                if (req.isForMainFrame() && tab.openerId != null && Settings.blockPopups()
                        && !tab.popupOk) {
                    String h = req.getUrl().getHost();
                    if (h != null && (AdBlock.shouldBlock(h, null)
                            || (AdBlock.isEnabled() && Filters.popupBlocked(req.getUrl().toString(),
                            hostOf(tab.openerUrl))))) {
                        tab.issue("POPUP blocked (ad host) " + req.getUrl());
                        host.onPopupBlocked(tab);
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                if (!req.isForMainFrame()) {
                    Uri u = req.getUrl();
                    String pageHost = hostOf(tab.url);
                    // never block stylesheets: a blocked CSS file leaves the whole page unstyled
                    String accept = req.getRequestHeaders() == null ? null
                            : req.getRequestHeaders().get("Accept");
                    boolean css = accept != null && accept.startsWith("text/css");
                    if (accept == null || !accept.startsWith("image/")) {
                        tab.issue("REQ " + (css ? "css " : "") + u);
                    }
                    boolean fb = !css && AdBlock.isEnabled() && !AdBlock.isAllowed(pageHost)
                            && Filters.blocks(u.toString(), pageHost, typeOf(u.toString(), accept));
                    if (fb) AdBlock.countBlocked();
                    if (fb || (!css && AdBlock.shouldBlock(u.getHost(), pageHost))) {
                        tab.blocked.incrementAndGet();
                        tab.issue("BLOCKED " + u);
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
                    go(view, up);
                    return true;
                }
            }
            if ((scheme.equals("http") || scheme.equals("https")) && req.isForMainFrame()
                    && Settings.blockPopups() && tab.openerId == null && !tab.loading) {
                String nh = u.getHost();
                String ph = hostOf(tab.url);
                boolean ad = nh != null && AdBlock.shouldBlock(nh, ph);
                boolean auto = System.currentTimeMillis() - tab.lastTouch > 1500
                        && nh != null && ph != null && !related(nh, ph) && !looksLikeAuth(u.toString())
                        && !looksLikeFile(u.toString());
                if (ad || auto) {
                    tab.issue("REDIRECT blocked " + u);
                    host.onPopupBlocked(null);
                    return true;
                }
            }
            if ((scheme.equals("http") || scheme.equals("https")) && req.isForMainFrame()
                    && !req.isRedirect() && "GET".equals(req.getMethod())
                    && tab.openerId == null) {
                final WebView wv0 = view;
                final String target = u.toString();
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            go(wv0, target);
                        } catch (Exception ignored) {
                            // view may already be destroyed
                        }
                    }
                });
                return true;
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
            if (Settings.compat()) return;
            cosmetic(view, tab);
            syncBackground(view, host);
            addBottomSpacer(view);
            view.setAlpha(0.55f);
            view.animate().alpha(1f).setDuration(180).start();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null) tab.url = url;
            tab.loading = true;
            tab.startedAt = System.currentTimeMillis();
            if (tab.selfReload) {
                tab.selfReload = false;
                tab.issue("---- background refresh (reload) ----");
            } else {
                tab.revalidated = null;
                tab.clearIssues();
            }
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
            applyDisplayPrefs(view); // back to the normal cache mode after a forced network load
            if (!Settings.compat()) {
                passwordHooks(view, tab, host);
                cosmetic(view, tab);
                syncBackground(view, host);
                addBottomSpacer(view);
                hybridRefresh(view);
            }
            host.onTabState(tab);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url != null) tab.url = url;
            host.onTabState(tab);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest req,
                                        WebResourceResponse resp) {
            tab.issue("HTTP " + resp.getStatusCode() + " " + req.getUrl());
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req,
                                    android.webkit.WebResourceError err) {
            tab.issue((req.isForMainFrame() ? "MAIN ERR " : "ERR ") + err.getErrorCode() + " "
                    + err.getDescription() + " " + req.getUrl());
            if (req.isForMainFrame() && tab.fallbackHttp != null) {
                // the https twin does not work: go back to plain http once, for this host
                String http = tab.fallbackHttp;
                tab.fallbackHttp = null;
                tab.noUpgradeHost = hostOf(http);
                go(view, http);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                       android.net.http.SslError error) {
            tab.issue("SSL " + error.getPrimaryError() + " " + error.getUrl());
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
        public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
            if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR
                    || m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                tab.issue("JS " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
            }
            return false;
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

        private boolean noTouch() {
            return Settings.blockPopups() && System.currentTimeMillis() - tab.lastTouch > 1500;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult r) {
            if (noTouch()) {
                tab.issue("DIALOG blocked: " + message);
                r.cancel();
                return true;
            }
            return false;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult r) {
            if (noTouch()) {
                tab.issue("DIALOG blocked: " + message);
                r.cancel();
                return true;
            }
            return false;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String def,
                                  android.webkit.JsPromptResult r) {
            if (noTouch()) {
                tab.issue("DIALOG blocked: " + message);
                r.cancel();
                return true;
            }
            return false;
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

    /** Registrable-ish domain: "a.b.example.com" -> "example.com", "x.co.uk" -> "x.co.uk". */
    static String siteOf(String host) {
        if (host == null) return "";
        String[] p = host.toLowerCase(java.util.Locale.ROOT).split("\\.");
        int n = p.length;
        if (n <= 2) return host.toLowerCase(java.util.Locale.ROOT);
        boolean cc = p[n - 1].length() == 2 && p[n - 2].length() <= 3;
        int k = cc ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, n - k); i < n; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(p[i]);
        }
        return sb.toString();
    }

    static boolean sameSite(String a, String b) {
        return siteOf(a).equals(siteOf(b));
    }

    /** Same brand on another domain (github.com / githubusercontent.com, google.com / gstatic...). */
    static boolean related(String a, String b) {
        if (a == null || b == null) return false;
        String x = siteOf(a), y = siteOf(b);
        if (x.equals(y)) return true;
        String bx = x.substring(0, x.indexOf('.') < 0 ? x.length() : x.indexOf('.'));
        String by = y.substring(0, y.indexOf('.') < 0 ? y.length() : y.indexOf('.'));
        if (bx.length() < 4 || by.length() < 4) return false;
        return bx.startsWith(by) || by.startsWith(bx);
    }

    static boolean looksLikeFile(String url) {
        if (url == null) return false;
        String p = url;
        int q = p.indexOf('?');
        if (q > 0) p = p.substring(0, q);
        p = p.toLowerCase(java.util.Locale.ROOT);
        String[] ext = {".zip", ".apk", ".apks", ".xapk", ".obb", ".exe", ".msi", ".dmg", ".iso",
                ".7z", ".rar", ".gz", ".tgz", ".xz", ".bz2", ".pdf", ".deb", ".rpm", ".jar", ".bin"};
        for (String e : ext) if (p.endsWith(e)) return true;
        return p.contains("/releases/download/") || p.contains("/archive/");
    }

    /** Sign-in / payment pop-ups are legitimate even when they point to another site. */
    static boolean looksLikeAuth(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return u.contains("login") || u.contains("signin") || u.contains("sign-in")
                || u.contains("oauth") || u.contains("auth") || u.contains("sso")
                || u.contains("accounts.") || u.contains("pay") || u.contains("checkout")
                || u.contains("captcha") || u.contains("recaptcha") || u.contains("consent");
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
