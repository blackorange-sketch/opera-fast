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

        /** A login form was submitted (called on the main thread). */
        void onPasswordSubmit(Tab tab, String user, String pass);
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
        if (tab.priv) {
            s.setSaveFormData(false);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        } else {
            wv.addJavascriptInterface(new PassBridge(tab, host), "OFPass");
        }

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

    /** JS -> Java: only lets a page *offer* a login to be saved; nothing can be read back. */
    private static final class PassBridge {
        private final Tab tab;
        private final Host host;

        PassBridge(Tab tab, Host host) {
            this.tab = tab;
            this.host = host;
        }

        @android.webkit.JavascriptInterface
        public void save(final String user, final String pass) {
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

    private static final String CAPTURE_JS = "(function(){if(window.__ofp)return;window.__ofp=1;"
            + "function grab(root){var ps=(root||document).querySelectorAll('input[type=password]');"
            + "var p=null;for(var i=0;i<ps.length;i++){if(ps[i].value){p=ps[i];break;}}if(!p)return;"
            + "var scope=p.form||document,ins=scope.querySelectorAll('input'),prev=null;"
            + "for(var j=0;j<ins.length;j++){if(ins[j]===p)break;var t=(ins[j].type||'text').toLowerCase();"
            + "if((t=='text'||t=='email'||t=='tel')&&ins[j].value)prev=ins[j];}"
            + "try{OFPass.save(prev?prev.value:'',p.value);}catch(e){}}"
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

    /** Offers saved login for https pages and installs the form-submit watcher. */
    private static void passwordHooks(WebView view, Tab tab) {
        if (tab.priv || !Settings.savePasswords()) return;
        view.evaluateJavascript(CAPTURE_JS, null);
        String url = tab.url;
        if (url == null || !url.startsWith("https://")) return;
        PasswordStore.Item it = PasswordStore.find(hostOf(url), null);
        if (it != null && !it.pass.isEmpty()) view.evaluateJavascript(fillJs(it.user, it.pass), null);
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
            if (tab.priv) PrivateData.note(view.getContext(), hostOf(tab.url));
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
            passwordHooks(view, tab);
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
