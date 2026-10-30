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

    interface Listener {
        /** Flip the setting; return its new state. */
        boolean onToggle(int id);

        void onAction(int id);

        void onDismiss();
    }

    private static final int TILE_ON_BG = 0xFFFFB3B8;
    private static final int TILE_ON_ICON = 0xFF5C0A14;
    private static final int TILE_OFF_BG = 0xFF2A2A33;
    private static final int BUTTON_BG = 0xFF2A2A33;
    private static final int BUTTON_ICON = 0xFFC9B8FF;

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
        sheet.setBackground(UI.roundedTop(0xFF1E1E26, UI.dp(c, 26)));
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

        // ---- action grid
        addButtons(c, body, listener,
                button(c, BOOKMARKS, UI.BOOKMARK, "Bookmarks", true, listener),
                button(c, DOWNLOADS, UI.DOWNLOAD, "Downloads", true, listener));
        addButtons(c, body, listener,
                button(c, HISTORY, UI.HISTORY, "History", true, listener),
                button(c, BOOKMARK_PAGE, bookmarked ? UI.BOOKMARK : UI.BOOKMARK_BORDER,
                        bookmarked ? "Remove bookmark" : "Add bookmark", pageOpen, listener));
        addButtons(c, body, listener,
                button(c, FIND, UI.FIND, "Find in page", pageOpen, listener),
                button(c, SHARE, UI.SHARE, "Share page", pageOpen, listener));
        addButtons(c, body, listener,
                button(c, NEW_PRIVATE, UI.PRIVATE, "New private tab", true, listener),
                button(c, PASSWORDS, UI.KEY, "Passwords", true, listener));
        addButtons(c, body, listener,
                button(c, SETTINGS, UI.SETTINGS, "Settings", true, listener),
                button(c, CLEAR, UI.DELETE, "Clear data", true, listener));

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
        GradientDrawable bg = UI.rounded(on ? TILE_ON_BG : TILE_OFF_BG, UI.dp(c, 28));
        tileIcons[slot].setBackground(bg);
        tileIcons[slot].setTextColor(on ? TILE_ON_ICON : UI.TEXT);
        tileSubs[slot].setText(S.t(on ? "On" : "Off"));
    }

    // ---------------------------------------------------------------- action buttons

    private View button(Context c, final int id, String glyph, String label, boolean enabled,
                        final Listener listener) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(UI.rounded(BUTTON_BG, UI.dp(c, 22)));
        b.setPadding(UI.dp(c, 16), 0, UI.dp(c, 12), 0);

        b.addView(UI.icon(c, glyph, 22, BUTTON_ICON),
                new LinearLayout.LayoutParams(UI.dp(c, 30), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView t = UI.label(c, label, 14, UI.TEXT);
        t.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = UI.dp(c, 10);
        b.addView(t, lp);

        if (enabled) {
            b.setClickable(true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onAction(id);
                }
            });
        } else {
            b.setAlpha(0.4f);
        }
        return b;
    }

    private void addButtons(Context c, LinearLayout body, Listener listener, View left, View right) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int m = UI.dp(c, 4);

        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, UI.dp(c, 56), 1f);
        l.setMargins(m, m, m, m);
        row.addView(left, l);

        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, UI.dp(c, 56), 1f);
        r.setMargins(m, m, m, m);
        row.addView(right != null ? right : new View(c), r);

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
