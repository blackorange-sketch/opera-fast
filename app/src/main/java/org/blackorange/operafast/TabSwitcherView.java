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

import java.util.List;

/** Full-screen tab overview: two-column grid of cards, tap to switch, X to close, + for new. */
final class TabSwitcherView extends LinearLayout {

    interface Listener {
        void onSelect(Tab tab);

        void onClose(Tab tab);

        void onNew();

        void onDone();
    }

    private final TabManager tabs;
    private final Listener listener;
    private final LinearLayout grid;
    private final TextView title;

    TabSwitcherView(Context c, TabManager tabs, final Listener listener) {
        super(c);
        this.tabs = tabs;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.BG_APP);
        setClickable(true);

        LinearLayout header = UI.header(c, "Tabs", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDone();
            }
        }, UI.ADD, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onNew();
            }
        });
        title = (TextView) header.getChildAt(1);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(c);
        grid = new LinearLayout(c);
        grid.setOrientation(VERTICAL);
        int pad = UI.dp(c, 8);
        grid.setPadding(pad, pad, pad, pad);
        sv.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        reload();
    }

    void reload() {
        Context c = getContext();
        grid.removeAllViews();
        List<Tab> list = tabs.all();
        title.setText("Tabs (" + list.size() + ")");

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                grid.addView(row, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(card(c, list.get(i)), cardParams(c));
        }
        if (list.size() % 2 == 1 && row != null) {
            row.addView(new View(c), cardParams(c)); // keep the last card at half width
        }
    }

    private LayoutParams cardParams(Context c) {
        LayoutParams lp = new LayoutParams(0, UI.dp(c, 110), 1f);
        int m = UI.dp(c, 6);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View card(Context c, final Tab tab) {
        boolean isActive = tab == tabs.active();

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        GradientDrawable bg = UI.rounded(UI.BG_CARD, UI.dp(c, 12));
        if (isActive) bg.setStroke(UI.dp(c, 2), UI.ACCENT);
        card.setBackground(bg);
        card.setClickable(true);
        card.setPadding(UI.dp(c, 10), UI.dp(c, 8), UI.dp(c, 4), UI.dp(c, 8));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSelect(tab);
            }
        });

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        String host = UI.hostOf(tab.url);
        String letter = tab.isBlank() || host.isEmpty() ? "+" : host.substring(0, 1).toUpperCase();
        TextView badge = new TextView(c);
        badge.setText(letter);
        badge.setTextColor(UI.TEXT);
        badge.setTextSize(13);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(UI.BG_INPUT);
        badge.setBackground(dot);
        top.addView(badge, new LayoutParams(UI.dp(c, 24), UI.dp(c, 24)));

        top.addView(new View(c), new LayoutParams(UI.dp(c, 8), 1));
        TextView name = UI.label(c, tab.isBlank() ? "New tab" : host, 13, UI.TEXT_MUTED);
        top.addView(name, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView x = UI.iconButton(c, UI.CLOSE, 18);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClose(tab);
            }
        });
        top.addView(x, new LayoutParams(UI.dp(c, 36), UI.dp(c, 36)));
        card.addView(top, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String t = tab.title != null && !tab.title.isEmpty() ? tab.title
                : (tab.isBlank() ? "Start page" : tab.url);
        TextView body = new TextView(c);
        body.setText(t);
        body.setTextColor(UI.TEXT);
        body.setTextSize(14);
        body.setMaxLines(3);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams bp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = UI.dp(c, 6);
        bp.rightMargin = UI.dp(c, 6);
        card.addView(body, bp);
        return card;
    }
}
