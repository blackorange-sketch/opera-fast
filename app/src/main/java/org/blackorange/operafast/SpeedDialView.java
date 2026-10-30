package org.blackorange.operafast;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Start page of a blank tab: a grid of quick links (built natively, no WebView needed). */
final class SpeedDialView extends ScrollView {

    interface Listener {
        void onOpen(String url);
    }

    private static final String[][] SHORTCUTS = {
            {"DuckDuckGo", "https://duckduckgo.com", "#DE5833", "D"},
            {"Google", "https://google.com", "#4285F4", "G"},
            {"YouTube", "https://youtube.com", "#FF0000", "Y"},
            {"Wikipedia", "https://wikipedia.org", "#555555", "W"},
            {"GitHub", "https://github.com", "#2B3137", "G"},
            {"Reddit", "https://reddit.com", "#FF4500", "R"},
            {"Telegram", "https://web.telegram.org", "#229ED9", "T"},
            {"X", "https://x.com", "#2F2F2F", "X"},
    };
    private static final int COLUMNS = 4;

    SpeedDialView(Context c, final Listener listener) {
        super(c);
        setBackgroundColor(UI.BG_APP);
        setFillViewport(true);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UI.dp(c, 16), UI.dp(c, 36), UI.dp(c, 16), UI.dp(c, 16));

        TextView title = UI.label(c, "OperaFast", 26, UI.ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 28));
        col.addView(new View(c), gap);

        LinearLayout row = null;
        for (int i = 0; i < SHORTCUTS.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final String[] sc = SHORTCUTS[i];
            row.addView(tile(c, sc, listener), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static View tile(Context c, final String[] sc, final Listener listener) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, UI.dp(c, 8), 0, UI.dp(c, 8));
        t.setBackground(UI.ripple());
        t.setClickable(true);

        TextView circle = new TextView(c);
        circle.setText(sc[3]);
        circle.setTextColor(UI.TEXT);
        circle.setTextSize(24);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(android.graphics.Color.parseColor(sc[2]));
        circle.setBackground(bg);
        t.addView(circle, new LinearLayout.LayoutParams(UI.dp(c, 56), UI.dp(c, 56)));

        TextView name = UI.label(c, sc[0], 11, UI.TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(c, 6);
        t.addView(name, lp);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(sc[1]);
            }
        });
        return t;
    }
}
