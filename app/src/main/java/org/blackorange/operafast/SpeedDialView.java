package org.blackorange.operafast;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Start page of a blank tab: an editable grid of quick links (built natively, no WebView needed).
 * Long-press a tile to edit / remove it; the last tile adds a new one. In a private tab it shows
 * a short explanation instead.
 */
final class SpeedDialView extends ScrollView {

    interface Listener {
        void onOpen(String url);
    }

    private static final int COLUMNS = 4;
    private static final int[] PALETTE = {
            0xFFDE5833, 0xFF4285F4, 0xFFE53935, 0xFF8E24AA, 0xFF00897B, 0xFFFB8C00,
            0xFF229ED9, 0xFF5C6BC0, 0xFF43A047, 0xFF6D4C41};

    private final Listener listener;
    private final LinearLayout col;
    private boolean privateMode;

    SpeedDialView(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setFillViewport(true);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UI.dp(c, 16), UI.dp(c, 36), UI.dp(c, 16), UI.dp(c, 16));
        addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
    }

    void setPrivateMode(boolean on) {
        if (on == privateMode) return;
        privateMode = on;
        refresh();
    }

    void refresh() {
        final Context c = getContext();
        col.removeAllViews();
        if (privateMode) {
            setBackgroundColor(0xFF1C1428);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView icon = UI.icon(c, UI.PRIVATE, 56, 0xFFC9B8FF);
            col.addView(icon, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView title = UI.label(c, "Private tab", 22, UI.TEXT);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = UI.dp(c, 12);
            col.addView(title, tl);
            TextView note = UI.label(c, "Private tabs don't keep history or saved passwords. "
                    + "Cookies of the sites you visit are deleted when you close the last private tab.",
                    14, UI.TEXT_MUTED);
            note.setMaxLines(8);
            note.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = UI.dp(c, 12);
            col.addView(note, nl);
            return;
        }
        setBackgroundColor(UI.BG_APP);
        col.setGravity(Gravity.NO_GRAVITY);

        TextView title = UI.label(c, "OperaFast", 26, UI.ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(new View(c), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 28)));

        final List<String[]> items = Settings.dial();
        int total = items.size() + 1; // + the "add" tile
        LinearLayout row = null;
        for (int i = 0; i < total; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            View tile = i < items.size() ? tile(c, items, i) : addTile(c, items);
            row.addView(tile, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // keep the last row's tiles the same width
        if (row != null) {
            for (int k = row.getChildCount(); k < COLUMNS; k++) {
                row.addView(new View(c), new LinearLayout.LayoutParams(
                        0, 1, 1f));
            }
        }
    }

    private View frame(Context c, String letter, int color, boolean dashed, String label) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, UI.dp(c, 8), 0, UI.dp(c, 8));
        t.setBackground(UI.ripple());
        t.setClickable(true);

        TextView circle = new TextView(c);
        circle.setText(letter);
        circle.setTextColor(UI.TEXT);
        circle.setTextSize(24);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (dashed) {
            bg.setColor(0x00000000);
            bg.setStroke(UI.dp(c, 2), 0xFF555555, UI.dp(c, 4), UI.dp(c, 4));
        } else {
            bg.setColor(color);
        }
        circle.setBackground(bg);
        t.addView(circle, new LinearLayout.LayoutParams(UI.dp(c, 56), UI.dp(c, 56)));

        TextView name = UI.label(c, label, 11, UI.TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(c, 6);
        t.addView(name, lp);
        return t;
    }

    private View tile(final Context c, final List<String[]> items, final int index) {
        final String[] sc = items.get(index);
        String letter = sc[0].isEmpty() ? "?" : sc[0].substring(0, 1).toUpperCase();
        int color = PALETTE[Math.abs(sc[1].hashCode()) % PALETTE.length];
        View t = frame(c, letter, color, false, sc[0]);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(sc[1]);
            }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle(sc[0])
                        .setItems(new CharSequence[]{S.t("Edit"), S.t("Delete")},
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        if (which == 0) {
                                            edit(items, index);
                                        } else {
                                            items.remove(index);
                                            Settings.setDial(items);
                                            refresh();
                                        }
                                    }
                                }).show();
                return true;
            }
        });
        return t;
    }

    private View addTile(Context c, final List<String[]> items) {
        View t = frame(c, "+", 0, true, S.t("Add site"));
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(items, -1);
            }
        });
        return t;
    }

    /** Add (index < 0) or edit one entry. */
    private void edit(final List<String[]> items, final int index) {
        final Context c = getContext();
        final EditText name = field(c, S.t("Name"), InputType.TYPE_CLASS_TEXT);
        final EditText url = field(c, S.t("Address"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (index >= 0) {
            name.setText(items.get(index)[0]);
            url.setText(items.get(index)[1]);
        }
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UI.dp(c, 20), UI.dp(c, 8), UI.dp(c, 20), 0);
        box.addView(name);
        box.addView(url);
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(S.t(index >= 0 ? "Edit" : "Add site"))
                .setView(box)
                .setPositiveButton(S.t("OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String u = MainActivity.toUrl(url.getText().toString());
                        if (u == null) return;
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) n = UI.hostOf(u).replaceFirst("^www\\.", "");
                        String[] e = {n, u};
                        if (index >= 0) items.set(index, e); else items.add(e);
                        Settings.setDial(items);
                        refresh();
                    }
                })
                .setNegativeButton(S.t("Cancel"), null)
                .show();
    }

    private static EditText field(Context c, String hint, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setTextColor(UI.TEXT);
        e.setHintTextColor(UI.TEXT_MUTED);
        return e;
    }
}
