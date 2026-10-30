package org.blackorange.operafast;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Bottom-sheet menu in the spirit of Opera's: a row of round on/off toggles on top and a grid of
 * actions below. Slides up over a dimmed scrim.
 */
final class MenuSheet extends FrameLayout {

    // toggles
    static final int ADBLOCK = 1;
    static final int NIGHT = 2;
    static final int DESKTOP = 3;
    static final int IMAGES = 4;
    // actions
    static final int BOOKMARKS = 10;
    static final int DOWNLOADS = 11;
    static final int HISTORY = 12;
    static final int BOOKMARK_PAGE = 13;
    static final int FIND = 14;
    static final int SHARE = 15;
    static final int CLEAR = 16;
    static final int NEW_PRIVATE = 17;
    static final int PASSWORDS = 18;
    static final int SETTINGS = 19;
    static final int RECENT = 20;
    static final int READER = 21;
    static final int TRANSLATE = 22;
    static final int ADD_HOME = 23;
    static final int TEXT_SIZE = 24;
    static final int QR = 25;
    static final int COPY_LINK = 26;

    interface Listener {
        /** Flip the setting; return its new state. */
        boolean onToggle(int id);

        void onAction(int id);

        void onDismiss();
    }

    private static final int TILE_ON_BG = 0xFFFFB3B8;
    private static final int TILE_ON_ICON = 0xFF5C0A14;

    private final ScrollView sheet;
    private final TextView[] tileIcons = new TextView[4];
    private final TextView[] tileSubs = new TextView[4];

    MenuSheet(Context c, boolean[] toggles, boolean pageOpen, boolean bookmarked,
              final Listener listener) {
        super(c);
        setBackgroundColor(0x99000000);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDismiss();
            }
        });

        sheet = new ScrollView(c);
        sheet.setBackground(UI.roundedTop(UI.SHEET_BG, UI.dp(c, 26)));
        sheet.setClipToOutline(true);
        sheet.setClickable(true); // swallow taps so they do not reach the scrim
        sheet.setVerticalScrollBarEnabled(false);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(c, 16);
        body.setPadding(pad, UI.dp(c, 20), pad, UI.dp(c, 20));

        // ---- toggles
        LinearLayout toggleRow = new LinearLayout(c);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        addTile(c, toggleRow, 0, ADBLOCK, UI.SHIELD, "Ad blocker", toggles[0], listener);
        addTile(c, toggleRow, 1, NIGHT, UI.MOON, "Night mode", toggles[1], listener);
        addTile(c, toggleRow, 2, DESKTOP, UI.DESKTOP, "Desktop site", toggles[2], listener);
        addTile(c, toggleRow, 3, IMAGES, UI.IMAGE, "No images", toggles[3], listener);
        body.addView(toggleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View gap = new View(c);
        body.addView(gap, new LinearLayout.LayoutParams(1, UI.dp(c, 18)));

        // ---- grouped actions: icon over label, four per row
        header(c, body, "Library");
        addCells(c, body, listener,
                cell(c, BOOKMARKS, UI.BOOKMARK, "Bookmarks", listener),
                cell(c, HISTORY, UI.HISTORY, "History", listener),
                cell(c, DOWNLOADS, UI.DOWNLOAD, "Downloads", listener),
                cell(c, RECENT, UI.HISTORY, "Recently closed", listener));
        header(c, body, "More");
        addCells(c, body, listener,
                cell(c, NEW_PRIVATE, UI.PRIVATE, "New private tab", listener),
                cell(c, PASSWORDS, UI.KEY, "Passwords", listener),
                cell(c, SETTINGS, UI.SETTINGS, "Settings", listener),
                null);

        sheet.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        animateIn();
    }

    // ---------------------------------------------------------------- toggle tiles

    private void addTile(final Context c, LinearLayout row, final int slot, final int id,
                         String glyph, String label, boolean on, final Listener listener) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClickable(true);

        TextView icon = UI.icon(c, glyph, 24, UI.TEXT);
        tileIcons[slot] = icon;
        tile.addView(icon, new LinearLayout.LayoutParams(UI.dp(c, 66), UI.dp(c, 56)));

        TextView name = UI.label(c, label, 12, UI.TEXT);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = UI.dp(c, 6);
        tile.addView(name, nlp);

        TextView sub = UI.label(c, "", 11, UI.TEXT_MUTED);
        sub.setGravity(Gravity.CENTER);
        tileSubs[slot] = sub;
        tile.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        applyTile(c, slot, on);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyTile(c, slot, listener.onToggle(id));
            }
        });
        row.addView(tile, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void applyTile(Context c, int slot, boolean on) {
        GradientDrawable bg = UI.rounded(on ? TILE_ON_BG : UI.TILE_OFF, UI.dp(c, 28));
        tileIcons[slot].setBackground(bg);
        tileIcons[slot].setTextColor(on ? TILE_ON_ICON : UI.TEXT);
        tileSubs[slot].setText(S.t(on ? "On" : "Off"));
    }

    // ---------------------------------------------------------------- action cells

    private void header(Context c, LinearLayout body, String text) {
        TextView h = UI.label(c, text, 12, UI.TEXT_MUTED);
        h.setPadding(UI.dp(c, 6), UI.dp(c, 18), 0, UI.dp(c, 4));
        body.addView(h, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View cell(Context c, final int id, String glyph, String label, final Listener listener) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(UI.rounded(UI.TILE_OFF, UI.dp(c, 18)));
        b.setPadding(UI.dp(c, 2), UI.dp(c, 8), UI.dp(c, 2), UI.dp(c, 8));
        b.addView(UI.icon(c, glyph, 24, UI.BUTTON_ICON),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(c, 30)));
        TextView t = UI.label(c, label, 11, UI.TEXT);
        t.setMaxLines(2);
        t.setGravity(Gravity.CENTER);
        b.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAction(id);
            }
        });
        return b;
    }

    private void addCells(Context c, LinearLayout body, Listener listener, View... cells) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int m = UI.dp(c, 4);
        for (View v : cells) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UI.dp(c, 78), 1f);
            lp.setMargins(m, m, m, m);
            row.addView(v != null ? v : new View(c), lp);
        }
        body.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---------------------------------------------------------------- animation

    private void animateIn() {
        setAlpha(0f);
        sheet.setVisibility(INVISIBLE);
        post(new Runnable() {
            @Override
            public void run() {
                sheet.setTranslationY(sheet.getHeight());
                sheet.setVisibility(VISIBLE);
                animate().alpha(1f).setDuration(180).start();
                sheet.animate().translationY(0f).setDuration(240)
                        .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            }
        });
    }

    void animateOut(Runnable end) {
        sheet.animate().translationY(sheet.getHeight()).setDuration(190)
                .setInterpolator(new AccelerateInterpolator(1.3f)).start();
        animate().alpha(0f).setDuration(190).withEndAction(end).start();
    }
}
