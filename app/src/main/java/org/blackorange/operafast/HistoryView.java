package org.blackorange.operafast;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Full-screen history list grouped by day. Tap opens, X removes one entry. */
final class HistoryView extends LinearLayout {

    interface Listener {
        void onOpen(String url);

        void onClose();
    }

    private static final int LIMIT = 300;

    private final Listener listener;
    private LinearLayout list;

    HistoryView(final Context c, final Listener listener) {
        super(c);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.BG_APP);
        setClickable(true);

        addView(UI.header(c, "History", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClose();
            }
        }, UI.SWEEP, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setMessage("Clear all browsing history?")
                        .setPositiveButton("Clear", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                HistoryDb.get(c).clear();
                                list.removeAllViews();
                                showEmpty();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        reload();
    }

    private void showEmpty() {
        TextView t = UI.label(getContext(), "No history yet", 15, UI.TEXT_MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, UI.dp(getContext(), 48), 0, 0);
        list.addView(t, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void reload() {
        Context c = getContext();
        list.removeAllViews();
        List<HistoryDb.Entry> entries = HistoryDb.get(c).recent(LIMIT);
        if (entries.isEmpty()) {
            showEmpty();
            return;
        }
        int today = dayKey(System.currentTimeMillis());
        int yesterday = dayKey(System.currentTimeMillis() - 24L * 3600 * 1000);
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        int lastKey = -1;
        for (HistoryDb.Entry e : entries) {
            int key = dayKey(e.time);
            if (key != lastKey) {
                lastKey = key;
                String label = key == today ? "Today"
                        : key == yesterday ? "Yesterday" : df.format(new Date(e.time));
                TextView h = UI.label(c, label, 13, UI.ACCENT);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setPadding(UI.dp(c, 16), UI.dp(c, 16), UI.dp(c, 16), UI.dp(c, 6));
                list.addView(h, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            list.addView(row(c, e), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static int dayKey(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
    }

    private View row(final Context c, final HistoryDb.Entry e) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.ripple());
        row.setClickable(true);
        row.setPadding(UI.dp(c, 16), UI.dp(c, 8), UI.dp(c, 4), UI.dp(c, 8));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(e.url);
            }
        });

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        String title = e.title == null || e.title.isEmpty() ? UI.hostOf(e.url) : e.title;
        text.addView(UI.label(c, title, 15, UI.TEXT));
        text.addView(UI.label(c, e.url, 12, UI.TEXT_MUTED));
        row.addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView x = UI.iconButton(c, UI.CLOSE, 18);
        x.setTextColor(UI.TEXT_MUTED);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HistoryDb.get(c).delete(e.id);
                ((ViewGroup) row.getParent()).removeView(row);
            }
        });
        row.addView(x, new LayoutParams(UI.dp(c, 44), UI.dp(c, 44)));
        return row;
    }
}
