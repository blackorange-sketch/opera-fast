package org.blackorange.operafast;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Single-activity browser: address bar on top, page in the middle, Opera-style bottom bar.
 *
 * Fast-start design:
 *  - no third-party libraries, UI is built in code (no inflation, no AppCompat);
 *  - the first frame is the dark UI; the WebView (the slow part) is created right after it;
 *  - tabs without a page (start page) never create a WebView;
 *  - hidden tabs keep at most MAX_LIVE WebViews, the rest are released and restored on demand;
 *  - Back at the root of a tab moves the task to background instead of finishing, so the next
 *    launch from the launcher is a warm start.
 */
public class MainActivity extends Activity implements WebFactory.Host {

    private static final String SEARCH_URL = "https://duckduckgo.com/?q=";
    private static final int REQ_FILE = 1;
    private static final int REQ_STORAGE = 2;
    private static final int REQ_NOTIF = 3;
    private static final int REQ_AUTH = 4;
    private static final int REQ_SITE = 5;
    private static final int REQ_WALL = 7;
    private static final int REQ_FOLDER = 8;
    private static final String READER_JS = ""
            + "(function(){"
            + "function txt(e){return (e.innerText||'').trim();}"
            + "var cands=document.querySelectorAll('article,main,[role=main],section,div'),best=null,bs=0;"
            + "for(var i=0;i<cands.length;i++){var c=cands[i],s=0,k=c.children;"
            + "for(var j=0;j<k.length;j++){if(k[j].tagName==='P'){var l=txt(k[j]).length;if(l>50)s+=l;}}"
            + "if(s>bs){bs=s;best=c;}}"
            + "if(!best||bs<200)return '';"
            + "function esc(t){return t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}"
            + "var out='',els=best.querySelectorAll('h1,h2,h3,p,img,blockquote,li,pre');"
            + "for(var i=0;i<els.length;i++){var e=els[i];"
            + "if(e.closest('nav,aside,footer,form'))continue;"
            + "var t=e.tagName;"
            + "if(t==='IMG'){var u=e.currentSrc||e.src;if(u&&e.naturalWidth>200)out+='<img src=\"'+u.replace(/\"/g,'')+'\">';}"
            + "else{var x=txt(e);if(!x||(t==='P'&&x.length<2))continue;"
            + "var tag=t==='LI'?'p':t.toLowerCase();out+='<'+tag+'>'+esc(x)+'</'+tag+'>';}}"
            + "return JSON.stringify({t:document.title,h:out});"
            + "})()";

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int maxLive = 4;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TabManager tabs = new TabManager();
    private Tab shown;

    private FrameLayout screen;
    private LinearLayout rootLayout;
    private FrameLayout webHost;
    private SpeedDialView speedDial;
    private LinearLayout urlPill;
    private EditText urlField;
    private TextView shieldBtn;
    private TextView reloadBtn;
    private TextView backBtn;
    private TextView fwdBtn;
    private UI.TabCount tabCount;
    private UI.ProgressLine progress;

    private LinearLayout topBar;
    private ScrollView suggestScroll;
    private LinearLayout suggestList;
    private boolean programmatic;
    private int suggestGen;
    private final java.util.concurrent.ExecutorService netIo =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private LinearLayout bottomBar;
    private boolean passwordsFromSettings;
    private TextView lockIcon;
    private TextView pullSpinner;
    private LinearLayout snackBar;
    private TextView snackText;
    private Runnable snackUndo;
    private final java.util.ArrayList<String[]> closedTabs = new java.util.ArrayList<String[]>();
    private int scrollAcc;
    private long barsFreeze;
    private LinearLayout topHolder;
    private FrameLayout bottomHolder;
    private TextView toolsBtn;
    private android.widget.PopupWindow toolsPopup;
    private android.animation.ValueAnimator barsAnim;
    private View contentView;
    private int barsTh, barsBh;
    private float barsFrac;   // 0 = shown, 1 = hidden
    private boolean barsHidden;
    private LinearLayout loginBar;
    private TextView loginText;
    private Runnable pendingAuth;
    private long lastAuth;
    private android.webkit.PermissionRequest pendingSiteReq;
    private String[] pendingSiteGrant;
    private String pendingGeoOrigin;
    private android.webkit.GeolocationPermissions.Callback pendingGeoCb;
    private boolean pwPromptOpen;
    private View overlay;
    private LinearLayout findBar;
    private EditText findField;
    private TextView findCount;
    private FrameLayout customHolder;
    private WebChromeClient.CustomViewCallback customCb;
    private ValueCallback<Uri[]> fileCb;
    private String[] pendingDownload;
    private boolean askedNotifications;

    private final Runnable saveRunnable = new Runnable() {
        @Override
        public void run() {
            saveSession();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AdBlock.init(this);
        Settings.init(this);
        android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) {
            maxLive = am.isLowRamDevice() ? 2 : am.getMemoryClass() >= 384 ? 7
                    : am.getMemoryClass() >= 256 ? 5 : 4;
        }
        PasswordStore.init(this);
        SitePerms.init(this);
        Downloads.init(this);
        SiteSettings.init(this);
        int th = Settings.theme();
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        UI.applyTheme(th == 1 || (th == 2 && !night));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(UI.BG_APP));
        UI.applyAccent(Settings.accent());
        getWindow().setStatusBarColor(UI.BG_BAR);
        getWindow().setNavigationBarColor(UI.BG_BAR);
        if (UI.light && android.os.Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        PrivateData.wipe(this); // leftovers of a private session that was killed

        boolean restored = tabs.restore(
                getSharedPreferences("session", MODE_PRIVATE).getString("data", null));
        if (!restored) tabs.activate(tabs.add(null, null));

        buildUi();
        setContentView(screen);

        // A launch from recents re-delivers the old intent; only act on fresh ones.
        if (savedInstanceState == null) handleIntent(getIntent(), false);

        // Let the first frame (dark UI + bars) reach the screen, then create the WebView.
        screen.post(new Runnable() {
            @Override
            public void run() {
                showTab(tabs.active());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            for (Tab t : tabs.all()) {
                if (t != shown) {
                    release(t);
                    t.thumb = null;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (fileCb != null) {
            fileCb.onReceiveValue(null);
            fileCb = null;
        }
        for (Tab t : tabs.all()) destroyWebView(t);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI construction

    private int dp(float v) {
        return UI.dp(this, v);
    }

    private void buildUi() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(UI.BG_APP);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setFocusableInTouchMode(true);

        // ---- top: address pill
        final LinearLayout top = new LinearLayout(this);
        topBar = top;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(UI.BG_BAR);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));

        urlPill = new LinearLayout(this);
        urlPill.setOrientation(LinearLayout.HORIZONTAL);
        urlPill.setGravity(Gravity.CENTER_VERTICAL);
        urlPill.setBackground(UI.rounded(UI.BG_INPUT, dp(20)));

        shieldBtn = UI.iconButton(this, UI.SHIELD, 20);
        shieldBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShieldDialog();
            }
        });
        urlPill.addView(shieldBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        lockIcon = UI.icon(this, UI.LOCK, 14, UI.TEXT_MUTED);
        lockIcon.setVisibility(View.GONE);
        urlPill.addView(lockIcon, new LinearLayout.LayoutParams(dp(18), dp(40)));

        urlField = new EditText(this);
        urlField.setBackground(null);
        urlField.setTextColor(UI.TEXT);
        urlField.setHintTextColor(UI.TEXT_MUTED);
        urlField.setHint(S.t("Search or enter address"));
        urlField.setTextSize(14);
        urlField.setPadding(dp(4), 0, dp(4), 0);
        urlField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setSingleLine(true);
        urlField.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        urlField.setSelectAllOnFocus(true);
        urlField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (programmatic || !urlField.hasFocus()) return;
                showSuggestions(e.toString().trim());
            }
        });
        urlField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    navigate(v.getText().toString());
                    return true;
                }
                return false;
            }
        });
        urlField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                urlPill.setBackground(UI.rounded(
                        hasFocus ? UI.BG_INPUT_FOCUS : UI.BG_INPUT, dp(20)));
                lockIcon.setVisibility(hasFocus ? View.GONE : lockIcon.getVisibility());
                if (hasFocus) toolsBtn.setVisibility(View.GONE);
                if (hasFocus) {
                    Tab t = shown;
                    urlField.setTextColor(UI.TEXT);
                    programmatic = true;
                    urlField.setText(t == null || t.isBlank() ? "" : t.url);
                    programmatic = false;
                    urlField.selectAll();
                } else {
                    hideSuggestions();
                    hideKeyboard();
                    updateUrlField();
                }
            }
        });
        urlPill.addView(urlField, new LinearLayout.LayoutParams(0, MATCH, 1f));

        reloadBtn = UI.iconButton(this, UI.REFRESH, 20);
        reloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tab t = shown;
                if (t == null || t.wv == null) return;
                if (t.loading) {
                    t.wv.stopLoading();
                } else {
                    WebFactory.applyDisplayPrefs(t.wv);
                    WebFactory.freshReload(t.wv);
                }
            }
        });
        urlPill.addView(reloadBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        toolsBtn = UI.iconButton(this, UI.MORE_V, 20);
        toolsBtn.setVisibility(View.GONE);
        toolsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPageTools();
            }
        });
        urlPill.addView(toolsBtn, new LinearLayout.LayoutParams(dp(36), dp(40)));

        top.addView(urlPill, new LinearLayout.LayoutParams(MATCH, dp(40)));
        // the top bar, progress line and find bar float over the page as one block
        topHolder = new LinearLayout(this);
        topHolder.setOrientation(LinearLayout.VERTICAL);
        topHolder.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        progress = new UI.ProgressLine(this);
        topHolder.addView(progress, new LinearLayout.LayoutParams(MATCH, dp(2)));

        // ---- find-in-page bar (hidden until used)
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackgroundColor(UI.BG_BAR);
        findBar.setPadding(dp(12), 0, dp(4), 0);
        findBar.setVisibility(View.GONE);
        findField = new EditText(this);
        findField.setBackground(null);
        findField.setTextColor(UI.TEXT);
        findField.setHintTextColor(UI.TEXT_MUTED);
        findField.setHint(S.t("Find in page"));
        findField.setTextSize(15);
        findField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        findField.setSingleLine(true);
        findField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        findField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                runFind(e.toString());
            }
        });
        findField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    findStep(true);
                    return true;
                }
                return false;
            }
        });
        findBar.addView(findField, new LinearLayout.LayoutParams(0, MATCH, 1f));
        findCount = UI.label(this, "", 13, UI.TEXT_MUTED);
        findBar.addView(findCount, new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView findPrev = UI.iconButton(this, UI.UP, 22);
        findPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(false);
            }
        });
        findBar.addView(findPrev, new LinearLayout.LayoutParams(dp(40), MATCH));
        TextView findNext = UI.iconButton(this, UI.DOWN, 22);
        findNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(true);
            }
        });
        findBar.addView(findNext, new LinearLayout.LayoutParams(dp(40), MATCH));
        TextView findClose = UI.iconButton(this, UI.CLOSE, 20);
        findClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFindBar();
            }
        });
        findBar.addView(findClose, new LinearLayout.LayoutParams(dp(40), MATCH));
        topHolder.addView(findBar, new LinearLayout.LayoutParams(MATCH, dp(46)));

        // ---- middle: start page or WebView of the active tab
        FrameLayout content = new FrameLayout(this);
        contentView = content;
        speedDial = new SpeedDialView(this, new SpeedDialView.Listener() {
            @Override
            public void onOpen(String url) {
                loadUrl(shown, url);
            }
        });
        content.addView(speedDial, new FrameLayout.LayoutParams(MATCH, MATCH));
        pullSpinner = UI.icon(this, UI.REFRESH, 20, UI.TEXT);
        pullSpinner.setBackground(UI.rounded(UI.POP_BG, dp(20)));
        pullSpinner.setElevation(dp(4));
        pullSpinner.setAlpha(0f);
        webHost = new PullLayout(this, pullSpinner, new PullLayout.Listener() {
            @Override
            public void onRefresh() {
                reloadActive();
            }
        });
        content.addView(webHost, new FrameLayout.LayoutParams(MATCH, MATCH));
        suggestScroll = new ScrollView(this);
        suggestScroll.setBackgroundColor(UI.BG_APP);
        suggestScroll.setVisibility(View.GONE);
        suggestScroll.setKeepScreenOn(false);
        suggestList = new LinearLayout(this);
        suggestList.setOrientation(LinearLayout.VERTICAL);
        suggestScroll.addView(suggestList, new ScrollView.LayoutParams(MATCH, WRAP));
        content.addView(suggestScroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams spl = new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        spl.topMargin = dp(WebFactory.INSET_TOP);   // below the floating top bar
        content.addView(pullSpinner, spl);
        rootLayout.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // ---- bottom: back / forward / new tab / tabs / menu
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setBackgroundColor(UI.BG_BAR);
        bottomBar = bottom;

        backBtn = UI.iconButton(this, UI.BACK, 24);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        fwdBtn = UI.iconButton(this, UI.FORWARD, 24);
        fwdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tab t = shown;
                if (t != null && t.wv != null && t.wv.canGoForward()) t.wv.goForward();
            }
        });
        TextView plus = UI.iconButton(this, UI.ADD, 26);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newTab();
            }
        });
        FrameLayout tabsBtn = new FrameLayout(this);
        tabsBtn.setBackground(UI.ripple());
        tabsBtn.setClickable(true);
        tabCount = new UI.TabCount(this);
        tabsBtn.addView(tabCount, new FrameLayout.LayoutParams(MATCH, MATCH));
        tabsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSwitcher();
            }
        });
        TextView more = UI.iconButton(this, UI.MORE, 24);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        View[] items = {backBtn, fwdBtn, plus, tabsBtn, more};
        for (View v : items) {
            bottom.addView(v, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }
        bottomHolder = new FrameLayout(this);
        bottomHolder.addView(bottom, new FrameLayout.LayoutParams(MATCH, dp(52)));

        screen.addView(rootLayout, new FrameLayout.LayoutParams(MATCH, MATCH));
        // the bars float over the page; the page gets spacers at its start and end instead
        screen.addView(topHolder, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));
        topHolder.setTranslationZ(dp(3));
        screen.addView(bottomHolder, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        bottomHolder.setTranslationZ(dp(3));
        speedDial.setPadding(0, dp(WebFactory.INSET_TOP), 0, dp(WebFactory.INSET_BOTTOM));
        suggestScroll.setPadding(0, dp(WebFactory.INSET_TOP), 0, 0);
        speedDial.setClipToPadding(false);

        // "Fill saved login" pill above the bottom bar
        loginBar = new LinearLayout(this);
        loginBar.setOrientation(LinearLayout.HORIZONTAL);
        loginBar.setGravity(Gravity.CENTER_VERTICAL);
        loginBar.setBackground(UI.rounded(UI.POP_BG, dp(24)));
        loginBar.setElevation(dp(6));
        loginBar.setPadding(dp(16), 0, dp(4), 0);
        loginBar.setVisibility(View.GONE);
        loginText = UI.label(this, "", 14, UI.TEXT);
        loginBar.addView(loginText, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView fill = UI.label(this, "Fill", 14, UI.ACCENT);
        fill.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fill.setGravity(Gravity.CENTER);
        fill.setBackground(UI.ripple());
        fill.setClickable(true);
        fill.setPadding(dp(14), 0, dp(14), 0);
        fill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tab t = shown;
                if (t != null && t.wv != null) WebFactory.fillLogin(t.wv, t);
                hideLoginBar();
            }
        });
        loginBar.addView(fill, new LinearLayout.LayoutParams(WRAP, MATCH));
        TextView no = UI.iconButton(this, UI.CLOSE, 18);
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideLoginBar();
            }
        });
        loginBar.addView(no, new LinearLayout.LayoutParams(dp(40), MATCH));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(MATCH, dp(48), Gravity.BOTTOM);
        llp.setMargins(dp(12), 0, dp(12), dp(60));
        screen.addView(loginBar, llp);

        // "Tab closed - Undo" pill
        snackBar = new LinearLayout(this);
        snackBar.setOrientation(LinearLayout.HORIZONTAL);
        snackBar.setGravity(Gravity.CENTER_VERTICAL);
        snackBar.setBackground(UI.rounded(UI.POP_BG, dp(24)));
        snackBar.setElevation(dp(8));
        snackBar.setPadding(dp(18), 0, dp(4), 0);
        snackBar.setVisibility(View.GONE);
        snackText = UI.label(this, "", 14, UI.TEXT);
        snackBar.addView(snackText, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView undo = UI.label(this, "Undo", 14, UI.ACCENT);
        undo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        undo.setGravity(Gravity.CENTER);
        undo.setBackground(UI.ripple());
        undo.setClickable(true);
        undo.setPadding(dp(16), 0, dp(16), 0);
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Runnable r = snackUndo;
                hideSnack();
                if (r != null) r.run();
            }
        });
        snackBar.addView(undo, new LinearLayout.LayoutParams(WRAP, MATCH));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(MATCH, dp(48), Gravity.BOTTOM);
        slp.setMargins(dp(16), 0, dp(16), dp(84));
        screen.addView(snackBar, slp);
    }

    // ------------------------------------------------------------------ tabs

    private void showTab(Tab t) {
        if (t == null) return;
        Tab prev = shown;
        hideLoginBar();
        setBars(true);
        if (prev != null && prev != t) hideFindBar();
        if (prev != null && prev != t && prev.wv != null) {
            webHost.removeView(prev.wv);
            prev.wv.onPause();
        }
        shown = t;
        tabs.activate(t);
        speedDial.setPrivateMode(t.priv);

        if (t.isBlank() && t.wv == null && t.state == null) {
            webHost.setVisibility(View.GONE);
            speedDial.setVisibility(View.VISIBLE);
        } else {
            ensureWebView(t);
            if (t.wv.getParent() != webHost) {
                webHost.removeAllViews();
                webHost.addView(t.wv, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
            t.wv.onResume();
            speedDial.setVisibility(View.GONE);
            webHost.setVisibility(View.VISIBLE);
        }
        syncBars();
        trimLive();
        scheduleSave();
        if (prev != t) {
            // soft cross-fade into the newly shown tab
            View enter = speedDial.getVisibility() == View.VISIBLE ? speedDial : webHost;
            enter.setAlpha(0.6f);
            enter.animate().alpha(1f).setDuration(110).start();
        }
    }

    private void ensureWebView(Tab t) {
        if (t.wv != null) return;
        t.wv = WebFactory.create(this, t, this);
        if (t.state != null) {
            Bundle st = t.state;
            t.state = null;
            if (t.wv.restoreState(st) == null && !t.isBlank()) t.wv.loadUrl(t.url);
        } else if (!t.isBlank()) {
            t.wv.loadUrl(t.url);
        }
    }

    /** Loads a URL in the given tab (typed address, speed dial, history). */
    private void loadUrl(Tab t, String url) {
        if (t == null || url == null) return;
        boolean had = t.wv != null;
        String up = WebFactory.upgrade(t, url);
        if (up != null) url = up;
        t.url = url;
        t.title = "";
        t.state = null;
        t.crashes = 0;
        showTab(t);               // creates the WebView and loads t.url if there was none
        if (had) {
            WebFactory.applyDisplayPrefs(t.wv);
            t.wv.loadUrl(url);
        }
    }

    private void newTab() {
        newTab(shown != null && shown.priv);
    }

    private void newTab(final boolean priv) {
        if (priv && (shown == null || !shown.priv)) {
            requireAuth(new Runnable() {
                @Override
                public void run() {
                    openNewTab(true);
                }
            });
            return;
        }
        openNewTab(priv);
    }

    private void openNewTab(final boolean priv) {
        // grab a preview of the page we are leaving, then open the new tab
        captureThumb(shown, new Runnable() {
            @Override
            public void run() {
                String home = priv ? "" : Settings.homeUrl();
                Tab t = tabs.add(home.isEmpty() ? null : home, null, priv);
                showTab(t);
                if (!home.isEmpty()) return;
                urlField.requestFocus();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager im =
                                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (im != null) {
                            im.showSoftInput(urlField, InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                });
            }
        });
    }

    /** Screenshot of the visible page (small), for the tab switcher. Always calls done once. */
    private void captureThumb(final Tab t, final Runnable done) {
        if (t == null || t.wv == null || t.isBlank() || overlay != null
                || webHost.getVisibility() != View.VISIBLE
                || webHost.getWidth() <= 0 || webHost.getHeight() <= 0) {
            done.run();
            return;
        }
        try {
            int[] loc = new int[2];
            webHost.getLocationInWindow(loc);
            int w = webHost.getWidth();
            int h = webHost.getHeight();
            int tw = 360;
            int th = Math.max(1, tw * h / w);
            final Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Rect r = new Rect(loc[0], loc[1], loc[0] + w, loc[1] + h);
            PixelCopy.request(getWindow(), r, bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override
                public void onPixelCopyFinished(int result) {
                    if (result == PixelCopy.SUCCESS) t.thumb = bmp;
                    done.run();
                }
            }, ui);
        } catch (Exception e) {
            done.run();
        }
    }

    private void showSnack(String text, Runnable undo) {
        snackText.setText(text);
        snackUndo = undo;
        snackBar.setVisibility(View.VISIBLE);
        snackBar.bringToFront();
        snackBar.setAlpha(0f);
        snackBar.animate().alpha(1f).setDuration(150).start();
        ui.removeCallbacks(hideSnackRunnable);
        ui.postDelayed(hideSnackRunnable, 4500);
    }

    private final Runnable hideSnackRunnable = new Runnable() {
        @Override
        public void run() {
            hideSnack();
        }
    };

    private void hideSnack() {
        ui.removeCallbacks(hideSnackRunnable);
        if (snackBar != null) snackBar.setVisibility(View.GONE);
        snackUndo = null;
    }

    private void reopenClosed(int index) {
        if (index < 0 || index >= closedTabs.size()) return;
        String[] c = closedTabs.remove(index);
        Tab nt = tabs.add(c[0], null, false);
        nt.title = c[1];
        if (overlay instanceof TabSwitcherView) {
            ((TabSwitcherView) overlay).reload();
        } else {
            showTab(nt);
        }
        scheduleSave();
    }

    private void closeTab(Tab t) {
        if (!t.priv && !t.isBlank()) {
            closedTabs.add(0, new String[]{t.url, t.title == null ? "" : t.title});
            while (closedTabs.size() > 12) closedTabs.remove(closedTabs.size() - 1);
            showSnack(S.t("Tab closed"), new Runnable() {
                @Override
                public void run() {
                    reopenClosed(0);
                }
            });
        }
        boolean wasShown = t == shown;
        destroyWebView(t);
        Tab next = tabs.close(t);
        if (t.priv && tabs.privateCount() == 0) PrivateData.wipe(this);
        if (wasShown) {
            shown = null;
            showTab(next);
        } else {
            syncBars();
            scheduleSave();
        }
    }

    /** Keeps at most MAX_LIVE WebViews: the least recently used hidden ones are released. */
    private void trimLive() {
        int live = 0;
        for (Tab t : tabs.all()) {
            if (t.wv != null) live++;
        }
        while (live > maxLive) {
            Tab victim = null;
            for (Tab t : tabs.all()) {
                if (t == shown || t.wv == null) continue;
                if (victim == null || t.lastUsed < victim.lastUsed) victim = t;
            }
            if (victim == null) return;
            release(victim);
            live--;
        }
    }

    /** Frees a hidden tab's WebView but remembers its history for later. */
    private void release(Tab t) {
        if (t.wv == null) return;
        try {
            Bundle st = new Bundle();
            t.wv.saveState(st);
            t.state = st;
        } catch (Exception ignored) {
        }
        t.loading = false;
        t.progress = 100;
        destroyWebView(t);
    }

    private void destroyWebView(Tab t) {
        WebView w = t.wv;
        if (w == null) return;
        t.wv = null;
        ViewParent p = w.getParent();
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(w);
        try {
            w.stopLoading();
            if (t.priv) {
                w.clearCache(true);
                w.clearFormData();
                w.clearHistory();
            }
            w.destroy();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ bars

    private void syncBars() {
        Tab t = shown;
        if (t == null) return;
        updateUrlField();
        boolean back = t.wv != null && t.wv.canGoBack();
        boolean fwd = t.wv != null && t.wv.canGoForward();
        backBtn.setAlpha(back || canCloseToOpener(t) ? 1f : 0.35f);
        fwdBtn.setAlpha(fwd ? 1f : 0.35f);
        reloadBtn.setText(t.loading ? UI.CLOSE : UI.REFRESH);
        tabCount.setCount(tabs.size());
        int bar = t.priv ? UI.PRIV_BAR : UI.BG_BAR;
        topBar.setBackgroundColor(bar);
        bottomBar.setBackgroundColor(bar);
        updateSecure();
        progress.setProgress(t.loading ? t.progress : 100);
        updateShield();
    }

    /** No screenshots / recents thumbnails while any private tab exists. */
    private void updateSecure() {
        if (tabs.privateCount() > 0) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void updateUrlField() {
        if (urlField.hasFocus()) return;
        Tab t = shown;
        String text = "";
        int color = UI.TEXT;
        if (t != null && !t.isBlank()) {
            text = Settings.showTitle() && t.title != null && !t.title.isEmpty()
                    ? t.title : UI.hostOf(t.url);
            if (t.url.startsWith("http://")) color = UI.ORANGE;
        }
        boolean secure = t != null && t.url != null && t.url.startsWith("https://");
        boolean plain = t != null && t.url != null && t.url.startsWith("http://");
        lockIcon.setVisibility(secure || plain ? View.VISIBLE : View.GONE);
        toolsBtn.setVisibility(t != null && !t.isBlank() ? View.VISIBLE : View.GONE);
        lockIcon.setText(plain ? UI.LOCK_OPEN : UI.LOCK);
        lockIcon.setTextColor(plain ? UI.ORANGE : UI.TEXT_MUTED);
        if (!text.contentEquals(urlField.getText())) {
            programmatic = true;
            urlField.setText(text);
            programmatic = false;
        }
        urlField.setTextColor(color);
    }

    private void updateShield() {
        Tab t = shown;
        int color = UI.TEXT;
        if (!AdBlock.isEnabled() || (t != null && AdBlock.isAllowed(WebFactory.hostOf(t.url)))) {
            color = UI.TEXT_MUTED;
        } else if (t != null && t.blocked.get() > 0) {
            color = UI.GREEN;
        }
        shieldBtn.setTextColor(color);
    }

    private void showShieldDialog() {
        final Tab t = shown;
        if (t == null) return;
        final String host = WebFactory.hostOf(t.url);
        String msg = S.t("Blocked on this page: ") + t.blocked.get()
                + "\n" + S.t("Blocked in total: ") + AdBlock.totalBlocked()
                + "\n" + S.t("Rules loaded: ") + AdBlock.ruleCount();
        AlertDialog.Builder b = new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t("Ad blocker"))
                .setMessage(msg)
                .setPositiveButton(S.t(AdBlock.isEnabled() ? "Turn off" : "Turn on"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                AdBlock.setEnabled(!AdBlock.isEnabled());
                                reloadActive();
                                updateShield();
                            }
                        })
                .setNegativeButton(S.t("Close"), null);
        if (host != null && AdBlock.isEnabled()) {
            final boolean allowed = AdBlock.isAllowed(host);
            b.setNeutralButton(S.t(allowed ? "Block on this site" : "Allow on this site"),
                    new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            AdBlock.setAllowed(host, !allowed);
                            reloadActive();
                            updateShield();
                        }
                    });
        }
        b.show();
    }

    private void reloadActive() {
        Tab t = shown;
        if (t != null && t.wv != null) {
            WebFactory.applyDisplayPrefs(t.wv);
            WebFactory.freshReload(t.wv);
        }
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(urlField.getWindowToken(), 0);
    }

    private void clearUrlFocus() {
        hideSuggestions();
        urlField.clearFocus();
        rootLayout.requestFocus();
    }

    // ------------------------------------------------------------------ navigation

    private void navigate(String raw) {
        String url = toUrl(raw);
        if (url == null) return;
        clearUrlFocus();
        loadUrl(shown, url);
    }

    /** Address-bar text -> URL: explicit http(s), bare host/IP, or a web search. */
    static String toUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return s;
        if (s.indexOf(' ') < 0) {
            if (lower.matches("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")) {
                return "http://" + s;
            }
            if (s.indexOf('.') > 0 && !s.endsWith(".")) return "https://" + s;
        }
        return Settings.searchUrl() + Uri.encode(s);
    }

    private boolean canCloseToOpener(Tab t) {
        return t.openerId != null && tabs.byId(t.openerId) != null;
    }

    /** Back inside the tab, or close a popup/link tab and return to its opener. */
    private boolean goBack() {
        Tab t = shown;
        if (t == null) return false;
        if (t.wv != null && t.wv.canGoBack()) {
            t.wv.goBack();
            return true;
        }
        if (canCloseToOpener(t)) {
            closeTab(t);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (customHolder != null) {
            onHideCustomView();
            return;
        }
        if (overlay != null) {
            closeOverlay();
            return;
        }
        if (findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
            return;
        }
        if (urlField.hasFocus()) {
            clearUrlFocus();
            return;
        }
        if (goBack()) return;
        moveTaskToBack(true); // keep the process warm for the next launch
    }

    private void handleIntent(Intent in, boolean show) {
        if (in == null) return;
        if ((in.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return;
        String action = in.getAction();
        if ("org.blackorange.operafast.DOWNLOADS".equals(action)) {
            if (show) showDownloads();
            return;
        }
        String target = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri d = in.getData();
            if (d != null) {
                String sc = d.getScheme();
                if ("http".equalsIgnoreCase(sc) || "https".equalsIgnoreCase(sc)) {
                    target = d.toString();
                }
            }
        } else if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            target = toUrl(in.getStringExtra(SearchManager.QUERY));
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = in.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                Matcher m = Patterns.WEB_URL.matcher(text);
                target = m.find() ? toUrl(m.group()) : toUrl(text);
            }
        }
        if (target == null) return;

        Tab t = tabs.active();
        if (t == null || !t.isBlank() || t.wv != null || t.state != null) {
            t = tabs.add(null, null);
        }
        t.url = target;
        t.title = "";
        tabs.activate(t);
        if (show) {
            closeOverlay();
            onHideCustomView();
            showTab(t);
        }
    }

    // ------------------------------------------------------------------ overlays & menu

    private void showOverlay(View v) {
        showOverlay(v, true);
    }

    private void showOverlay(View v, boolean animate) {
        clearUrlFocus();
        closeOverlay();
        overlay = v;
        screen.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationY(dp(28));
            v.animate().alpha(1f).translationY(0f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).withLayer().start();
        }
    }

    private void closeOverlay() {
        final View v = overlay;
        if (v == null) return;
        overlay = null;
        if (v == downloadSheet) downloadSheet = null;
        Runnable remove = new Runnable() {
            @Override
            public void run() {
                screen.removeView(v);
            }
        };
        if (v instanceof MenuSheet) {
            ((MenuSheet) v).animateOut(remove);
        } else if (v instanceof DownloadSheet) {
            ((DownloadSheet) v).animateOut(remove);
        } else {
            v.animate().alpha(0f).translationY(dp(28)).setDuration(150)
                    .setInterpolator(new AccelerateInterpolator()).withEndAction(remove).start();
        }
    }

    private void openSwitcher() {
        final float aspect = webHost.getWidth() > 0
                ? (float) webHost.getHeight() / webHost.getWidth() : 1.6f;
        captureThumb(shown, new Runnable() {
            @Override
            public void run() {
                showOverlay(new TabSwitcherView(MainActivity.this, tabs, aspect,
                        new TabSwitcherView.Listener() {
                            @Override
                            public void onSelect(Tab tab) {
                                closeOverlay();
                                showTab(tab);
                            }

                            @Override
                            public void onClose(Tab tab) {
                                closeTab(tab);
                                if (overlay instanceof TabSwitcherView) {
                                    ((TabSwitcherView) overlay).reload();
                                }
                            }

                            @Override
                            public void onNew(boolean priv) {
                                closeOverlay();
                                newTab(priv);
                            }

                            @Override
                            public void onDone() {
                                closeOverlay();
                            }

                            @Override
                            public void onGate(Runnable proceed) {
                                requireAuth(proceed);
                            }

                            @Override
                            public void onHistory() {
                                showHistory();
                            }

                            @Override
                            public void onDownloads() {
                                showDownloads();
                            }

                            @Override
                            public void onMenu() {
                                showMenu();
                            }
                        }));
            }
        });
    }

    private void showHistory() {
        showOverlay(new HistoryView(this, false, new HistoryView.Listener() {
            @Override
            public void onOpen(String url) {
                closeOverlay();
                loadUrl(shown, url);
            }

            @Override
            public void onClose() {
                closeOverlay();
            }
        }));
    }

    private void showBookmarks() {
        showOverlay(new HistoryView(this, true, new HistoryView.Listener() {
            @Override
            public void onOpen(String url) {
                closeOverlay();
                loadUrl(shown, url);
            }

            @Override
            public void onClose() {
                closeOverlay();
            }
        }));
    }

    private void showDownloads() {
        showOverlay(new DownloadsView(this, new DownloadsView.Listener() {
            @Override
            public void onClose() {
                closeOverlay();
            }
        }));
    }

    private void showMenu() {
        final Tab t = shown;
        boolean page = t != null && !t.isBlank();
        boolean[] toggles = {
                AdBlock.isEnabled(), Settings.night(), t != null && t.desktop, Settings.noImages()};
        boolean bookmarked = page && HistoryDb.get(this).isBookmarked(t.url);
        showOverlay(new MenuSheet(this, toggles, page, bookmarked, new MenuSheet.Listener() {
            @Override
            public boolean onToggle(int id) {
                switch (id) {
                    case MenuSheet.ADBLOCK:
                        AdBlock.setEnabled(!AdBlock.isEnabled());
                        reloadActive();
                        updateShield();
                        return AdBlock.isEnabled();
                    case MenuSheet.NIGHT:
                        Settings.setNight(!Settings.night());
                        applyDisplayPrefs(false);
                        return Settings.night();
                    case MenuSheet.DESKTOP:
                        if (t == null) return false;
                        t.desktop = !t.desktop;
                        SiteSettings.setDesktop(WebFactory.hostOf(t.url), t.desktop);
                        if (t.wv != null) {
                            WebFactory.applyUserAgent(t.wv, t.desktop);
                            t.wv.reload();
                        }
                        scheduleSave();
                        return t.desktop;
                    case MenuSheet.IMAGES:
                        Settings.setNoImages(!Settings.noImages());
                        applyDisplayPrefs(true);
                        return Settings.noImages();
                    default:
                        return false;
                }
            }

            @Override
            public void onAction(int id) {
                closeOverlay();
                runAction(id);
            }

            @Override
            public void onDismiss() {
                closeOverlay();
            }
        }), false);
    }

    /** Executes a menu / page-tools action by id. */
    private void runAction(int id) {
        switch (id) {
                    case MenuSheet.BOOKMARKS:
                        showBookmarks();
                        break;
                    case MenuSheet.DOWNLOADS:
                        showDownloads();
                        break;
                    case MenuSheet.HISTORY:
                        showHistory();
                        break;
                    case MenuSheet.BOOKMARK_PAGE:
                        toggleBookmark();
                        break;
                    case MenuSheet.FIND:
                        showFindBar();
                        break;
                    case MenuSheet.QR:
                        showQr();
                        break;
                    case MenuSheet.COPY_LINK:
                        copyLink();
                        break;
                    case MenuSheet.SHARE:
                        sharePage();
                        break;
                    case MenuSheet.CLEAR:
                        confirmClearData();
                        break;
                    case MenuSheet.RECENT:
                        showRecentlyClosed();
                        break;
                    case MenuSheet.READER:
                        startReader();
                        break;
                    case MenuSheet.TRANSLATE:
                        translatePage();
                        break;
                    case MenuSheet.TEXT_SIZE:
                        pickTextSize();
                        break;
                    case MenuSheet.ADD_HOME:
                        addToHomeScreen();
                        break;
                    case MenuSheet.NEW_PRIVATE:
                        newTab(true);
                        break;
                    case MenuSheet.PASSWORDS:
                        passwordsFromSettings = false;
                        showPasswords();
                        break;
                    case MenuSheet.SETTINGS:
                        showSettings();
                        break;
                    default:
                        break;
                }
    }

    private void applyDisplayPrefs(boolean reloadShown) {
        for (Tab t : tabs.all()) {
            if (t.wv != null) WebFactory.applyDisplayPrefs(t.wv);
        }
        if (reloadShown) reloadActive();
    }

    // ------------------------------------------------------------------ scrolling bars

    private void setBars(boolean show) {
        if (show != barsHidden) return;       // already in that state
        barsHidden = !show;
        scrollAcc = 0;
        if (barsAnim != null) barsAnim.cancel();
        if (topHolder.getHeight() > 0) barsTh = topHolder.getHeight();
        if (bottomBar.getHeight() > 0) barsBh = bottomBar.getHeight();
        if (barsTh == 0) barsTh = dp(WebFactory.INSET_TOP);
        if (barsBh == 0) barsBh = dp(52);
        // Nothing is resized: the bars just slide (GPU translation) over the page.
        barsAnim = android.animation.ValueAnimator.ofFloat(barsFrac, show ? 0f : 1f);
        barsAnim.setDuration(200);
        barsAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        barsAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                barsFrac = (Float) a.getAnimatedValue();
                topHolder.setTranslationY(-barsTh * barsFrac);
                bottomBar.setTranslationY(barsBh * barsFrac);
            }
        });
        barsAnim.start();
    }

    @Override
    public void onScrolled(Tab tab, int dy, int y) {
        if (tab != shown || !Settings.hideBars() || overlay != null || urlField.hasFocus()
                || findBar.getVisibility() == View.VISIBLE) return;
        if (System.currentTimeMillis() < barsFreeze) return;
        if (y <= dp(4)) {
            setBars(true);
            return;
        }
        scrollAcc = (dy > 0) == (scrollAcc > 0) ? scrollAcc + dy : dy;
        if (scrollAcc > dp(48)) setBars(false);
        else if (scrollAcc < -dp(32)) setBars(true);
    }

    // ------------------------------------------------------------------ extra page tools

    private void startReader() {
        final Tab t = shown;
        if (t == null || t.wv == null) return;
        t.wv.evaluateJavascript(READER_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                try {
                    Object o = new org.json.JSONTokener(value).nextValue();
                    if (!(o instanceof String) || ((String) o).isEmpty()) {
                        toast(S.t("Reader view isn't available for this page"));
                        return;
                    }
                    org.json.JSONObject j = new org.json.JSONObject((String) o);
                    String page = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                            + "<style>body{background:#1a1a1a;color:#e6e6e6;font:19px/1.6 Georgia,serif;"
                            + "max-width:680px;margin:0 auto;padding:18px}h1,h2,h3{font-family:sans-serif;line-height:1.25}"
                            + "img{max-width:100%;height:auto;border-radius:8px}pre{white-space:pre-wrap}"
                            + "blockquote{border-left:3px solid #666;margin:0;padding-left:14px;color:#bbb}</style></head><body>"
                            + "<h1>" + android.text.TextUtils.htmlEncode(j.optString("t")) + "</h1>"
                            + j.optString("h") + "</body></html>";
                    t.wv.loadDataWithBaseURL(t.url, page, "text/html", "utf-8", t.url);
                } catch (Exception e) {
                    toast(S.t("Reader view isn't available for this page"));
                }
            }
        });
    }

    private void translatePage() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        String lang = Settings.uk() ? "uk" : "en";
        loadUrl(t, "https://translate.google.com/translate?sl=auto&tl=" + lang + "&u=" + Uri.encode(t.url));
    }

    private void pickTextSize() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        final String host = WebFactory.hostOf(t.url);
        int cur = SiteSettings.zoom(host);
        String[] names = new String[SiteSettings.ZOOMS.length];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = SiteSettings.ZOOMS[i] + "%";
            if (SiteSettings.ZOOMS[i] == cur) checked = i;
        }
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t("Text size") + " · " + host)
                .setSingleChoiceItems(names, checked, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        SiteSettings.setZoom(host, SiteSettings.ZOOMS[which]);
                        Tab s = shown;
                        if (s != null && s.wv != null) {
                            s.wv.getSettings().setTextZoom(SiteSettings.ZOOMS[which]);
                        }
                        d.dismiss();
                    }
                }).show();
    }

    private void showRecentlyClosed() {
        if (closedTabs.isEmpty()) {
            toast(S.t("Nothing closed yet"));
            return;
        }
        CharSequence[] items = new CharSequence[closedTabs.size()];
        for (int i = 0; i < items.length; i++) {
            String[] c = closedTabs.get(i);
            items[i] = c[1].isEmpty() ? UI.hostOf(c[0]) : c[1];
        }
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t("Recently closed"))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        reopenClosed(which);
                    }
                }).show();
    }

    private void addToHomeScreen() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        try {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) return;
            String host = UI.hostOf(t.url);
            String label = t.title == null || t.title.isEmpty() ? host : t.title;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(t.url)).setClass(this, MainActivity.class);
            android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(
                    this, "page" + Integer.toHexString(t.url.hashCode()))
                    .setShortLabel(label.length() > 24 ? label.substring(0, 24) : label)
                    .setLongLabel(label)
                    .setIcon(android.graphics.drawable.Icon.createWithBitmap(shortcutIcon(t, host)))
                    .setIntent(open)
                    .build();
            sm.requestPinShortcut(info, null);
            toast(S.t("Shortcut requested"));
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
    }

    private Bitmap shortcutIcon(Tab t, String host) {
        int size = 192;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF2E2E36);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        if (t.favicon != null) {
            c.drawBitmap(t.favicon, null, new Rect(48, 48, size - 48, size - 48), p);
        } else {
            p.setColor(UI.ACCENT);
            p.setTextSize(96);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            p.setFakeBoldText(true);
            String l = host.isEmpty() ? "O" : host.substring(0, 1).toUpperCase();
            c.drawText(l, size / 2f, size / 2f + 34, p);
        }
        return b;
    }

    private void pickWallpaper() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_WALL);
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
    }

    private void saveWallpaper(Uri uri) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            int target = 1280, sample = 1;
            while (o.outWidth / (sample * 2) >= target) sample *= 2;
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (b == null) return;
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(getFilesDir(), "wallpaper.jpg"));
            b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.close();
            speedDial.refresh();
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ security helpers

    private void hideLoginBar() {
        if (loginBar != null) loginBar.setVisibility(View.GONE);
    }

    @Override
    public void onLoginAvailable(Tab tab, String user) {
        if (tab != shown || tab.priv) return;
        loginText.setText(S.t("Saved login") + (user.isEmpty() ? "" : ": " + user));
        loginBar.setVisibility(View.VISIBLE);
        loginBar.setAlpha(0f);
        loginBar.animate().alpha(1f).setDuration(160).start();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                hideLoginBar();
            }
        }, 15000);
    }

    /** Asks for the phone's screen lock (PIN / pattern / fingerprint) before sensitive screens. */
    private void requireAuth(Runnable action) {
        if (!Settings.lockSensitive() || System.currentTimeMillis() - lastAuth < 60000) {
            action.run();
            return;
        }
        android.app.KeyguardManager km =
                (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        Intent i = km != null && km.isDeviceSecure()
                ? km.createConfirmDeviceCredentialIntent("OperaFast", S.t("Confirm it's you")) : null;
        if (i == null) {
            action.run();      // no screen lock set on the phone: nothing to ask
            return;
        }
        pendingAuth = action;
        try {
            startActivityForResult(i, REQ_AUTH);
        } catch (Exception e) {
            pendingAuth = null;
            action.run();
        }
    }

    @Override
    public void onSslError(Tab tab, final android.webkit.SslErrorHandler handler,
                           android.net.http.SslError error) {
        final String host = WebFactory.hostOf(tab.url);
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t("Connection is not private"))
                .setMessage(host + "\n" + S.t("The site's security certificate cannot be trusted."))
                .setPositiveButton(S.t("Go back"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        handler.cancel();
                    }
                })
                .setNegativeButton(S.t("Proceed anyway"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        handler.proceed();
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        handler.cancel();
                    }
                })
                .show();
    }

    @Override
    public void onSitePermission(Tab tab, final android.webkit.PermissionRequest req) {
        final String host = req.getOrigin() == null ? "" : req.getOrigin().getHost();
        final java.util.ArrayList<String> ok = new java.util.ArrayList<String>();
        boolean cam = false, mic = false;
        for (String r : req.getResources()) {
            if (r.equals(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                cam = true;
                ok.add(r);
            } else if (r.equals(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                mic = true;
                ok.add(r);
            }
        }
        if (ok.isEmpty()) {
            req.deny();
            return;
        }
        final boolean wantCam = cam, wantMic = mic;
        Boolean kc = wantCam ? SitePerms.get(host, SitePerms.CAMERA) : Boolean.TRUE;
        Boolean kmic = wantMic ? SitePerms.get(host, SitePerms.MIC) : Boolean.TRUE;
        Boolean known;
        if (Boolean.FALSE.equals(kc) || Boolean.FALSE.equals(kmic)) known = Boolean.FALSE;
        else if (kc == null || kmic == null) known = null;
        else known = Boolean.TRUE;
        if (known != null) {
            if (known) grantSite(req, ok.toArray(new String[0]), wantCam, wantMic); else req.deny();
            return;
        }
        String what = wantCam && wantMic ? S.t("camera and microphone")
                : wantCam ? S.t("camera") : S.t("microphone");
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(host)
                .setMessage(S.t("Allow this site to use the") + " " + what + "?")
                .setPositiveButton(S.t("Allow"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (wantCam) SitePerms.set(host, SitePerms.CAMERA, true);
                        if (wantMic) SitePerms.set(host, SitePerms.MIC, true);
                        grantSite(req, ok.toArray(new String[0]), wantCam, wantMic);
                    }
                })
                .setNegativeButton(S.t("Deny"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (wantCam) SitePerms.set(host, SitePerms.CAMERA, false);
                        if (wantMic) SitePerms.set(host, SitePerms.MIC, false);
                        req.deny();
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        req.deny();
                    }
                })
                .show();
    }

    /** Grants the web permission once Android's own camera / microphone permission is there. */
    private void grantSite(android.webkit.PermissionRequest req, String[] res, boolean cam, boolean mic) {
        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
        if (cam && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (mic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (need.isEmpty()) {
            req.grant(res);
            return;
        }
        if (pendingSiteReq != null) pendingSiteReq.deny();
        pendingSiteReq = req;
        pendingSiteGrant = res;
        requestPermissions(need.toArray(new String[0]), REQ_SITE);
    }

    @Override
    public void onGeoPrompt(Tab tab, final String origin,
                            final android.webkit.GeolocationPermissions.Callback cb) {
        final String host = WebFactory.hostOf(origin);
        Boolean known = SitePerms.get(host, SitePerms.LOCATION);
        if (known != null) {
            if (known) grantGeo(origin, cb); else cb.invoke(origin, false, false);
            return;
        }
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(host)
                .setMessage(S.t("Allow this site to use your location?"))
                .setPositiveButton(S.t("Allow"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        SitePerms.set(host, SitePerms.LOCATION, true);
                        grantGeo(origin, cb);
                    }
                })
                .setNegativeButton(S.t("Deny"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        SitePerms.set(host, SitePerms.LOCATION, false);
                        cb.invoke(origin, false, false);
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) {
                        cb.invoke(origin, false, false);
                    }
                })
                .show();
    }

    private void grantGeo(String origin, android.webkit.GeolocationPermissions.Callback cb) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            cb.invoke(origin, true, false);
            return;
        }
        pendingGeoOrigin = origin;
        pendingGeoCb = cb;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_SITE + 1);
    }

    // ------------------------------------------------------------------ address-bar suggestions

    private void hideSuggestions() {
        suggestGen++;
        suggestScroll.setVisibility(View.GONE);
        suggestList.removeAllViews();
    }

    private void showSuggestions(final String q) {
        final int gen = ++suggestGen;
        if (q.isEmpty()) {
            suggestScroll.setVisibility(View.GONE);
            suggestList.removeAllViews();
            return;
        }
        render(q, null);
        if (Settings.suggestNet() && !q.startsWith("http") && q.length() > 1) {
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (gen != suggestGen) return;
                    netIo.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != suggestGen) return;
                            final java.util.List<String> res = Suggest.fetch(q);
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (gen == suggestGen && urlField.hasFocus()) render(q, res);
                                }
                            });
                        }
                    });
                }
            }, 150);
        }
    }

    private void render(String q, java.util.List<String> net) {
        suggestList.removeAllViews();
        // 1) what was typed (opens it as an address or a search)
        addSuggestion(UI.SEARCH, q, q, q);
        // 2) matching bookmarks and history
        if (Settings.suggestLocal()) {
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            String low = q.toLowerCase(Locale.ROOT);
            int n = 0;
            for (HistoryDb.Entry e : HistoryDb.get(this).bookmarks()) {
                String t = e.title == null ? "" : e.title;
                if (n < 3 && (e.url.toLowerCase(Locale.ROOT).contains(low)
                        || t.toLowerCase(Locale.ROOT).contains(low)) && seen.add(e.url)) {
                    addSuggestion(UI.BOOKMARK, t.isEmpty() ? UI.hostOf(e.url) : t, e.url, e.url);
                    n++;
                }
            }
            for (HistoryDb.Entry e : HistoryDb.get(this).search(q, 4)) {
                if (seen.add(e.url)) {
                    String t = e.title == null ? "" : e.title;
                    addSuggestion(UI.HISTORY, t.isEmpty() ? UI.hostOf(e.url) : t, e.url, e.url);
                }
            }
        }
        // 3) the search engine's own suggestions
        if (net != null) {
            for (String sgs : net) {
                if (!sgs.equalsIgnoreCase(q)) addSuggestion(UI.SEARCH, sgs, sgs, sgs);
            }
        }
        suggestScroll.setVisibility(View.VISIBLE);
        suggestScroll.scrollTo(0, 0);
    }

    /** One row; tapping loads {@code target}, the arrow copies {@code fill} into the field. */
    private void addSuggestion(String glyph, String text, final String target, final String fill) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.ripple());
        row.setClickable(true);
        row.setPadding(dp(14), 0, dp(4), 0);
        row.addView(UI.icon(this, glyph, 20, UI.TEXT_MUTED),
                new LinearLayout.LayoutParams(dp(28), MATCH));
        TextView t = UI.label(this, text, 15, UI.TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.leftMargin = dp(12);
        row.addView(t, lp);
        TextView fillBtn = UI.iconButton(this, UI.UP, 20);
        fillBtn.setRotation(-45f);
        fillBtn.setTextColor(UI.TEXT_MUTED);
        fillBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                programmatic = true;
                urlField.setText(fill + (fill.startsWith("http") ? "" : " "));
                programmatic = false;
                urlField.setSelection(urlField.getText().length());
                showSuggestions(urlField.getText().toString().trim());
            }
        });
        row.addView(fillBtn, new LinearLayout.LayoutParams(dp(44), dp(48)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigate(target);
            }
        });
        suggestList.addView(row, new LinearLayout.LayoutParams(MATCH, dp(48)));
    }

    private void showSettings() {
        showOverlay(new SettingsView(this, new SettingsView.Listener() {
            @Override
            public void onClose() {
                closeOverlay();
            }

            @Override
            public void onPasswords() {
                passwordsFromSettings = true;
                showPasswords();
            }

            @Override
            public void onLanguageChanged() {
                saveSession();
                recreate();
            }

            @Override
            public void onDialChanged() {
                speedDial.refresh();
            }

            @Override
            public void onClearData() {
                confirmClearData();
            }

            @Override
            public void onClearCache() {
                clearCache();
            }

            @Override
            public void onPickFolder() {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_FOLDER);
                } catch (Exception e) {
                    toast(S.t("No file manager found"));
                }
            }

            @Override
            public void onWallpaper(boolean pick) {
                if (pick) {
                    pickWallpaper();
                } else {
                    new java.io.File(getFilesDir(), "wallpaper.jpg").delete();
                    speedDial.refresh();
                }
            }
        }));
    }

    private void clearCache() {
        boolean cleared = false;
        for (Tab t : tabs.all()) {
            if (t.wv != null) {
                t.wv.clearCache(true);   // shared disk cache, so one call is enough
                cleared = true;
                break;
            }
        }
        if (!cleared) {
            deleteTree(new java.io.File(getCacheDir(), "WebView"));
        }
        toast(S.t("Cache cleared"));
    }

    private static void deleteTree(java.io.File f) {
        if (f == null || !f.exists()) return;
        java.io.File[] kids = f.listFiles();
        if (kids != null) for (java.io.File k : kids) deleteTree(k);
        f.delete();
    }

    private void showPasswords() {
        requireAuth(new Runnable() {
            @Override
            public void run() {
                openPasswords();
            }
        });
    }

    private void openPasswords() {
        showOverlay(new PasswordsView(this, new PasswordsView.Listener() {
            @Override
            public void onClose() {
                closeOverlay();
                if (passwordsFromSettings) {
                    passwordsFromSettings = false;
                    showSettings();
                }
            }
        }));
    }

    @Override
    public void onPasswordSubmit(Tab tab, String user, String pass) {
        if (tab.priv || tab != shown || pwPromptOpen || !Settings.savePasswords()) return;
        final String host = WebFactory.hostOf(tab.url);
        if (host == null) return;
        final String u = user;
        final String p = pass;
        PasswordStore.Item old = PasswordStore.find(host, u);
        if (old != null && p.equals(old.pass)) return;
        final boolean update = old != null;
        pwPromptOpen = true;
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t(update ? "Update password?" : "Save password?"))
                .setMessage(host + (u.isEmpty() ? "" : "\n" + u))
                .setPositiveButton(S.t(update ? "Update" : "Save"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                toast(S.t(PasswordStore.save(host, u, p)
                                        ? "Password saved" : "Could not save the password"));
                            }
                        })
                .setNegativeButton(S.t("Not now"), null)
                .setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(android.content.DialogInterface d) {
                        pwPromptOpen = false;
                    }
                })
                .show();
    }

    private void toggleBookmark() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        HistoryDb db = HistoryDb.get(this);
        if (db.isBookmarked(t.url)) {
            db.removeBookmark(t.url);
            toast(S.t("Bookmark removed"));
        } else {
            db.addBookmark(t.url, t.title);
            toast(S.t("Bookmark added"));
        }
    }

    // ------------------------------------------------------------------ find in page

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        findField.setText("");
        findCount.setText("");
        findField.requestFocus();
        ui.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(findField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideFindBar() {
        if (findBar.getVisibility() != View.VISIBLE) return;
        findBar.setVisibility(View.GONE);
        Tab t = shown;
        if (t != null && t.wv != null) t.wv.clearMatches();
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(findField.getWindowToken(), 0);
        findField.clearFocus();
        rootLayout.requestFocus();
    }

    private void runFind(String q) {
        Tab t = shown;
        if (t == null || t.wv == null) return;
        if (q.isEmpty()) {
            t.wv.clearMatches();
            findCount.setText("");
            return;
        }
        t.wv.setFindListener(new WebView.FindListener() {
            @Override
            public void onFindResultReceived(int active, int total, boolean done) {
                findCount.setText(total == 0 ? "0/0" : (active + 1) + "/" + total);
            }
        });
        t.wv.findAllAsync(q);
    }

    private void findStep(boolean forward) {
        Tab t = shown;
        if (t != null && t.wv != null) t.wv.findNext(forward);
    }

    private void copyLink() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", t.url));
            toast(S.t("Link copied"));
        }
    }

    /** Drop-down under the address bar with everything that acts on the current page. */
    private void showPageTools() {
        final Tab t = shown;
        if (t == null || t.isBlank()) return;
        if (toolsPopup != null) toolsPopup.dismiss();
        boolean bookmarked = HistoryDb.get(this).isBookmarked(t.url);
        Object[][] rows = {
                {MenuSheet.BOOKMARK_PAGE, bookmarked ? UI.BOOKMARK : UI.BOOKMARK_BORDER,
                        bookmarked ? "Remove bookmark" : "Add bookmark"},
                {MenuSheet.SHARE, UI.SHARE, "Share page"},
                {MenuSheet.COPY_LINK, UI.COPY, "Copy link"},
                {MenuSheet.QR, UI.QR, "QR code"},
                {MenuSheet.FIND, UI.FIND, "Find in page"},
                {MenuSheet.READER, UI.READER, "Reader view"},
                {MenuSheet.TRANSLATE, UI.TRANSLATE, "Translate page"},
                {MenuSheet.TEXT_SIZE, UI.TEXT_FIELDS, "Text size"},
                {MenuSheet.ADD_HOME, UI.HOME, "Add to home screen"},
        };
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(6), 0, dp(6));
        for (final Object[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UI.ripple());
            row.setPadding(dp(14), 0, dp(18), 0);
            row.addView(UI.icon(this, (String) r[1], 20, UI.BUTTON_ICON),
                    new LinearLayout.LayoutParams(dp(30), MATCH));
            TextView tv = UI.label(this, (String) r[2], 14, UI.TEXT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.leftMargin = dp(10);
            row.addView(tv, lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (toolsPopup != null) toolsPopup.dismiss();
                    runAction((Integer) r[0]);
                }
            });
            col.addView(row, new LinearLayout.LayoutParams(MATCH, dp(46)));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        toolsPopup = new android.widget.PopupWindow(sv, dp(240), WRAP, true);
        toolsPopup.setBackgroundDrawable(UI.rounded(UI.SHEET_BG, dp(16)));
        toolsPopup.setElevation(dp(8));
        toolsPopup.setOutsideTouchable(true);
        toolsPopup.showAsDropDown(urlPill, urlPill.getWidth() - dp(240), dp(4));
    }

    private void showQr() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        QrCode q = QrCode.encode(t.url);
        if (q == null) {
            toast(S.t("The link is too long for a QR code"));
            return;
        }
        int quiet = 3, px = 10, n = q.size + quiet * 2;
        Bitmap bmp = Bitmap.createBitmap(n * px, n * px, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFFFFFFFF);
        for (int y = 0; y < q.size; y++) {
            for (int x = 0; x < q.size; x++) {
                if (!q.get(x, y)) continue;
                for (int dy = 0; dy < px; dy++) {
                    for (int dx = 0; dx < px; dx++) {
                        bmp.setPixel((x + quiet) * px + dx, (y + quiet) * px + dy, 0xFF000000);
                    }
                }
            }
        }
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        box.addView(iv, new LinearLayout.LayoutParams(MATCH, dp(280)));
        TextView u = UI.label(this, t.url, 12, UI.TEXT_MUTED);
        u.setMaxLines(2);
        u.setGravity(Gravity.CENTER);
        box.addView(u, new LinearLayout.LayoutParams(MATCH, WRAP));
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(S.t("QR code"))
                .setView(box)
                .setPositiveButton(S.t("Close"), null)
                .show();
    }

    private void sharePage() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, t.url);
        if (t.title != null && !t.title.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, t.title);
        startActivity(Intent.createChooser(i, S.t("Share page")));
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setMessage(S.t("Clear history, cookies, site data and cache?"))
                .setPositiveButton(S.t("Clear"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        HistoryDb.get(MainActivity.this).clear();
                        WebStorage.getInstance().deleteAllData();
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        for (Tab t : tabs.all()) {
                            if (t.wv != null) {
                                t.wv.clearCache(true);
                                t.wv.clearFormData();
                            }
                        }
                        toast(S.t("Browsing data cleared"));
                    }
                })
                .setNegativeButton(S.t("Cancel"), null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ session

    private void scheduleSave() {
        ui.removeCallbacks(saveRunnable);
        ui.postDelayed(saveRunnable, 1500);
    }

    private void saveSession() {
        String json = tabs.toJson();
        if (json != null) {
            getSharedPreferences("session", MODE_PRIVATE).edit().putString("data", json).apply();
        }
    }

    // ------------------------------------------------------------------ downloads

    private void startDownload(String url, String ua, String cd, String mime, String name) {
        if (url == null) return;
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast(S.t("This kind of download is not supported yet"));
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new String[]{url, ua, cd, mime, name};
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            askedNotifications = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        try {
            String saved = Downloads.enqueue(this, url, ua, cd, mime, name);
            toast(S.t("Downloading ") + saved);
        } catch (Exception e) {
            toast(S.t("Could not start the download"));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STORAGE) {
            String[] p = pendingDownload;
            pendingDownload = null;
            if (p == null) return;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload(p[0], p[1], p[2], p[3], p[4]);
            } else {
                toast(S.t("Storage permission is needed to save files"));
            }
        } else if (code == REQ_SITE) {
            android.webkit.PermissionRequest r = pendingSiteReq;
            pendingSiteReq = null;
            if (r == null) return;
            boolean all = results.length > 0;
            for (int x : results) if (x != PackageManager.PERMISSION_GRANTED) all = false;
            if (all) r.grant(pendingSiteGrant); else r.deny();
        } else if (code == REQ_SITE + 1) {
            android.webkit.GeolocationPermissions.Callback cb = pendingGeoCb;
            pendingGeoCb = null;
            if (cb == null) return;
            boolean any = false;
            for (int x : results) if (x == PackageManager.PERMISSION_GRANTED) any = true;
            cb.invoke(pendingGeoOrigin, any, false);
        }
    }

    // ------------------------------------------------------------------ WebFactory.Host

    @Override
    public void onTabState(Tab tab) {
        if (tab == shown) syncBars();
        scheduleSave();
    }

    @Override
    public void onProgress(Tab tab, int p) {
        if (tab != shown) return;
        progress.setProgress(tab.loading || p < 100 ? p : 100);
        updateShield();
    }

    @Override
    public Tab onOpenWindow(Tab opener) {
        Tab nt = tabs.add(null, opener, opener.priv);
        nt.wv = WebFactory.create(this, nt, this);
        showTab(nt);
        return nt;
    }

    @Override
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback cb) {
        if (customHolder != null) {
            cb.onCustomViewHidden();
            return;
        }
        customHolder = new FrameLayout(this);
        customHolder.setBackgroundColor(0xFF000000);
        customHolder.addView(view, new FrameLayout.LayoutParams(MATCH, MATCH));
        customCb = cb;
        screen.addView(customHolder, new FrameLayout.LayoutParams(MATCH, MATCH));
        setFullscreenMode(true);
    }

    @Override
    public void onHideCustomView() {
        if (customHolder == null) return;
        screen.removeView(customHolder);
        customHolder = null;
        setFullscreenMode(false);
        WebChromeClient.CustomViewCallback cb = customCb;
        customCb = null;
        if (cb != null) cb.onCustomViewHidden();
    }

    @SuppressWarnings("deprecation")
    private void setFullscreenMode(boolean on) {
        getWindow().getDecorView().setSystemUiVisibility(on
                ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                : (UI.light && android.os.Build.VERSION.SDK_INT >= 26
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : View.SYSTEM_UI_FLAG_VISIBLE));
    }

    @Override
    public boolean onFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
        if (fileCb != null) fileCb.onReceiveValue(null);
        fileCb = cb;
        try {
            startActivityForResult(params.createIntent(), REQ_FILE);
            return true;
        } catch (Exception e) {
            fileCb = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FOLDER) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(tree,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    Settings.setDlFolder(tree.toString());
                    toast(S.t("Download folder changed"));
                    if (downloadSheet != null) downloadSheet.refreshFolder();
                    else showSettings();
                } catch (Exception e) {
                    toast(S.t("Could not use this folder"));
                }
            }
            return;
        }
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        if (requestCode == REQ_WALL) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                saveWallpaper(data.getData());
            }
            return;
        }
        if (requestCode == REQ_AUTH) {
            Runnable r = pendingAuth;
            pendingAuth = null;
            if (r != null && resultCode == RESULT_OK) {
                lastAuth = System.currentTimeMillis();
                r.run();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDownload(final Tab tab, final String url, final String userAgent,
                           final String contentDisposition, final String mime, long length) {
        if (url == null) return;
        String guess = android.webkit.URLUtil.guessFileName(url, contentDisposition, mime);
        showDownloadSheet(url, userAgent, contentDisposition, mime, guess, length);
        // a link that opened a new tab only to download leaves an empty page behind: close it
        if (tab.openerId != null && tab.title.isEmpty()) {
            screen.post(new Runnable() {
                @Override
                public void run() {
                    if (tabs.all().contains(tab)) closeTab(tab);
                }
            });
        }
    }

    private DownloadSheet downloadSheet;

    private void showDownloadSheet(final String url, final String ua, final String cd,
                                   final String mime, String name, long length) {
        downloadSheet = new DownloadSheet(this, name, length, new DownloadSheet.Listener() {
            @Override
            public void onConfirm(String fileName) {
                downloadSheet = null;
                closeOverlay();
                startDownload(url, ua, cd, mime, fileName);
            }

            @Override
            public void onPickFolder() {
                pickDownloadFolder();
            }

            @Override
            public void onCancel() {
                downloadSheet = null;
                closeOverlay();
            }
        });
        showOverlay(downloadSheet, false);
    }

    private void pickDownloadFolder() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_FOLDER);
        } catch (Exception e) {
            toast(S.t("No file manager found"));
        }
    }

    @Override
    public void onExternalUrl(String url) {
        try {
            Intent i;
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            startActivity(i);
        } catch (Exception ignored) {
            // nothing installed that can handle it
        }
    }

    @Override
    public void onLinkLongPress(final Tab tab, final String url) {
        new AlertDialog.Builder(this, UI.dialogTheme())
                .setTitle(UI.hostOf(url))
                .setItems(new CharSequence[]{S.t("Open in new tab"), S.t("Copy link"), S.t("Share link")},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (which == 0) {
                                    tabs.add(url, tab, tab.priv);
                                    syncBars();
                                    scheduleSave();
                                    toast(S.t("Opened in a new tab"));
                                } else if (which == 1) {
                                    ClipboardManager cm =
                                            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    if (cm != null) {
                                        cm.setPrimaryClip(ClipData.newPlainText("link", url));
                                    }
                                    toast(S.t("Link copied"));
                                } else {
                                    Intent i = new Intent(Intent.ACTION_SEND);
                                    i.setType("text/plain");
                                    i.putExtra(Intent.EXTRA_TEXT, url);
                                    startActivity(Intent.createChooser(i, S.t("Share link")));
                                }
                            }
                        })
                .show();
    }

    @Override
    public void onRenderGone(Tab tab) {
        boolean wasShown = tab == shown;
        tab.state = null;
        destroyWebView(tab);
        tab.loading = false;
        if (++tab.crashes > 2) {
            tab.url = "";
            toast(S.t("This page keeps crashing"));
        }
        if (wasShown) {
            shown = null;
            showTab(tab);
        }
    }
}
