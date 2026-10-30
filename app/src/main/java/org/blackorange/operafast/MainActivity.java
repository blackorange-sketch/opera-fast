package org.blackorange.operafast;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import android.widget.PopupWindow;
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
    private static final int MAX_LIVE = 4;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

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

    private View overlay;
    private PopupWindow menu;
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
                if (t != shown) release(t);
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
        LinearLayout top = new LinearLayout(this);
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

        urlField = new EditText(this);
        urlField.setBackground(null);
        urlField.setTextColor(UI.TEXT);
        urlField.setHintTextColor(UI.TEXT_MUTED);
        urlField.setHint("Search or enter address");
        urlField.setTextSize(14);
        urlField.setPadding(dp(4), 0, dp(4), 0);
        urlField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setSingleLine(true);
        urlField.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        urlField.setSelectAllOnFocus(true);
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
                if (hasFocus) {
                    Tab t = shown;
                    urlField.setTextColor(UI.TEXT);
                    urlField.setText(t == null || t.isBlank() ? "" : t.url);
                    urlField.selectAll();
                } else {
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
                if (t.loading) t.wv.stopLoading(); else t.wv.reload();
            }
        });
        urlPill.addView(reloadBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        top.addView(urlPill, new LinearLayout.LayoutParams(MATCH, dp(40)));
        rootLayout.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        progress = new UI.ProgressLine(this);
        rootLayout.addView(progress, new LinearLayout.LayoutParams(MATCH, dp(2)));

        // ---- middle: start page or WebView of the active tab
        FrameLayout content = new FrameLayout(this);
        speedDial = new SpeedDialView(this, new SpeedDialView.Listener() {
            @Override
            public void onOpen(String url) {
                loadUrl(shown, url);
            }
        });
        content.addView(speedDial, new FrameLayout.LayoutParams(MATCH, MATCH));
        webHost = new FrameLayout(this);
        content.addView(webHost, new FrameLayout.LayoutParams(MATCH, MATCH));
        rootLayout.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // ---- bottom: back / forward / new tab / tabs / menu
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setBackgroundColor(UI.BG_BAR);

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
        rootLayout.addView(bottom, new LinearLayout.LayoutParams(MATCH, dp(52)));

        screen.addView(rootLayout, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ------------------------------------------------------------------ tabs

    private void showTab(Tab t) {
        if (t == null) return;
        if (shown != null && shown != t && shown.wv != null) {
            webHost.removeView(shown.wv);
            shown.wv.onPause();
        }
        shown = t;
        tabs.activate(t);

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
        t.url = url;
        t.title = "";
        t.state = null;
        t.crashes = 0;
        showTab(t);               // creates the WebView and loads t.url if there was none
        if (had) t.wv.loadUrl(url);
    }

    private void newTab() {
        Tab t = tabs.add(null, null);
        showTab(t);
        urlField.requestFocus();
        ui.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager im =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(urlField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void closeTab(Tab t) {
        boolean wasShown = t == shown;
        destroyWebView(t);
        Tab next = tabs.close(t);
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
        while (live > MAX_LIVE) {
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
        progress.setProgress(t.loading ? t.progress : 100);
        updateShield();
    }

    private void updateUrlField() {
        if (urlField.hasFocus()) return;
        Tab t = shown;
        String text = "";
        int color = UI.TEXT;
        if (t != null && !t.isBlank()) {
            text = UI.hostOf(t.url);
            if (t.url.startsWith("http://")) color = UI.ORANGE;
        }
        if (!text.contentEquals(urlField.getText())) urlField.setText(text);
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
        String msg = "Blocked on this page: " + t.blocked.get()
                + "\nBlocked in total: " + AdBlock.totalBlocked()
                + "\nRules loaded: " + AdBlock.ruleCount();
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Ad blocker")
                .setMessage(msg)
                .setPositiveButton(AdBlock.isEnabled() ? "Turn off" : "Turn on",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                AdBlock.setEnabled(!AdBlock.isEnabled());
                                reloadActive();
                                updateShield();
                            }
                        })
                .setNegativeButton("Close", null);
        if (host != null && AdBlock.isEnabled()) {
            final boolean allowed = AdBlock.isAllowed(host);
            b.setNeutralButton(allowed ? "Block on this site" : "Allow on this site",
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
        if (t != null && t.wv != null) t.wv.reload();
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(urlField.getWindowToken(), 0);
    }

    private void clearUrlFocus() {
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
        return SEARCH_URL + Uri.encode(s);
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
        clearUrlFocus();
        closeOverlay();
        overlay = v;
        screen.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private void closeOverlay() {
        if (overlay != null) {
            screen.removeView(overlay);
            overlay = null;
        }
    }

    private void openSwitcher() {
        showOverlay(new TabSwitcherView(this, tabs, new TabSwitcherView.Listener() {
            @Override
            public void onSelect(Tab tab) {
                closeOverlay();
                showTab(tab);
            }

            @Override
            public void onClose(Tab tab) {
                closeTab(tab);
                if (overlay instanceof TabSwitcherView) ((TabSwitcherView) overlay).reload();
            }

            @Override
            public void onNew() {
                closeOverlay();
                newTab();
            }

            @Override
            public void onDone() {
                closeOverlay();
            }
        }));
    }

    private void showHistory() {
        showOverlay(new HistoryView(this, new HistoryView.Listener() {
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
        if (menu != null && menu.isShowing()) {
            menu.dismiss();
            return;
        }
        final Tab t = shown;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(UI.rounded(UI.BG_CARD, dp(12)));
        col.setPadding(0, dp(6), 0, dp(6));

        addMenuRow(col, UI.ADD, "New tab", false, new Runnable() {
            @Override
            public void run() {
                newTab();
            }
        });
        addMenuRow(col, UI.HISTORY, "History", false, new Runnable() {
            @Override
            public void run() {
                showHistory();
            }
        });
        addMenuRow(col, UI.DOWNLOAD, "Downloads", false, new Runnable() {
            @Override
            public void run() {
                showDownloads();
            }
        });
        if (t != null && !t.isBlank()) {
            addMenuRow(col, UI.SHARE, "Share page", false, new Runnable() {
                @Override
                public void run() {
                    sharePage();
                }
            });
            addMenuRow(col, UI.DESKTOP, "Desktop site", t.desktop, new Runnable() {
                @Override
                public void run() {
                    t.desktop = !t.desktop;
                    if (t.wv != null) {
                        WebFactory.applyUserAgent(t.wv, t.desktop);
                        t.wv.reload();
                    }
                    scheduleSave();
                }
            });
        }
        addMenuRow(col, UI.DELETE, "Clear browsing data", false, new Runnable() {
            @Override
            public void run() {
                confirmClearData();
            }
        });

        menu = new PopupWindow(col, dp(250), WRAP, true);
        menu.setBackgroundDrawable(new ColorDrawable(0));
        menu.showAtLocation(screen, Gravity.BOTTOM | Gravity.END, dp(8), dp(52) + dp(8));
    }

    private void addMenuRow(LinearLayout col, String glyph, String text, boolean checked,
                            final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.ripple());
        row.setClickable(true);
        row.setPadding(dp(8), 0, dp(8), 0);

        row.addView(UI.icon(this, glyph, 20, UI.TEXT_MUTED), new LinearLayout.LayoutParams(dp(40), dp(48)));
        TextView label = UI.label(this, text, 15, UI.TEXT);
        row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (checked) {
            row.addView(UI.icon(this, UI.CHECK, 20, UI.ACCENT),
                    new LinearLayout.LayoutParams(dp(32), dp(48)));
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (menu != null) menu.dismiss();
                action.run();
            }
        });
        col.addView(row, new LinearLayout.LayoutParams(MATCH, dp(48)));
    }

    private void sharePage() {
        Tab t = shown;
        if (t == null || t.isBlank()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, t.url);
        if (t.title != null && !t.title.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, t.title);
        startActivity(Intent.createChooser(i, "Share page"));
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage("Clear history, cookies, site data and cache?")
                .setPositiveButton("Clear", new android.content.DialogInterface.OnClickListener() {
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
                        toast("Browsing data cleared");
                    }
                })
                .setNegativeButton("Cancel", null)
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

    private void startDownload(String url, String ua, String cd, String mime) {
        if (url == null) return;
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast("This kind of download is not supported yet");
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = new String[]{url, ua, cd, mime};
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
            String name = Downloads.enqueue(this, url, ua, cd, mime);
            toast("Downloading " + name);
        } catch (Exception e) {
            toast("Could not start the download");
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
                startDownload(p[0], p[1], p[2], p[3]);
            } else {
                toast("Storage permission is needed to save files");
            }
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
        Tab nt = tabs.add(null, opener);
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
                : View.SYSTEM_UI_FLAG_VISIBLE);
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
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDownload(String url, String userAgent, String contentDisposition, String mime) {
        startDownload(url, userAgent, contentDisposition, mime);
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
    public void onRenderGone(Tab tab) {
        boolean wasShown = tab == shown;
        tab.state = null;
        destroyWebView(tab);
        tab.loading = false;
        if (++tab.crashes > 2) {
            tab.url = "";
            toast("This page keeps crashing");
        }
        if (wasShown) {
            shown = null;
            showTab(tab);
        }
    }
}
