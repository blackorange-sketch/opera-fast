package org.blackorange.operafast;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom sheet shown when a download starts: rename, pick the folder, confirm or cancel. */
final class DownloadSheet extends FrameLayout {

    interface Listener {
        void onConfirm(String fileName);

        void onPickFolder();

        void onCancel();
    }

    private final LinearLayout sheet;
    private final TextView folderLabel;

    DownloadSheet(final Context c, String name, long length, final Listener listener) {
        super(c);
        setBackgroundColor(0x99000000);
        setClickable(true);
        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCancel();
            }
        });

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(UI.roundedTop(UI.SHEET_BG, UI.dp(c, 26)));
        sheet.setClickable(true);
        sheet.setPadding(UI.dp(c, 18), UI.dp(c, 16), UI.dp(c, 18), UI.dp(c, 4));

        // ---- icon + editable name
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = UI.icon(c, UI.DOWNLOAD, 20, 0xFF4A2C00);
        icon.setBackground(UI.rounded(0xFFFFB74D, UI.dp(c, 22)));
        top.addView(icon, new LinearLayout.LayoutParams(UI.dp(c, 44), UI.dp(c, 44)));

        final EditText field = new EditText(c);
        field.setText(name);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setTextColor(UI.TEXT);
        field.setTextSize(16);
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(UI.TEXT_MUTED));
        field.setSelectAllOnFocus(false);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fl.leftMargin = UI.dp(c, 12);
        top.addView(field, fl);
        TextView pencil = UI.icon(c, UI.EDIT, 18, UI.TEXT_MUTED);
        pencil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                field.requestFocus();
                field.setSelection(field.getText().length());
                android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager)
                        c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(field, 0);
            }
        });
        top.addView(pencil, new LinearLayout.LayoutParams(UI.dp(c, 40), UI.dp(c, 40)));
        sheet.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- folder chip
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable stroke = UI.rounded(0x00000000, UI.dp(c, 12));
        stroke.setStroke(UI.dp(c, 1), UI.TEXT_MUTED);
        chip.setBackground(stroke);
        chip.setPadding(UI.dp(c, 12), 0, UI.dp(c, 8), 0);
        chip.addView(UI.icon(c, UI.FOLDER, 16, UI.TEXT),
                new LinearLayout.LayoutParams(UI.dp(c, 28), ViewGroup.LayoutParams.MATCH_PARENT));
        folderLabel = UI.label(c, "", 13, UI.TEXT);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = UI.dp(c, 8);
        chip.addView(folderLabel, ll);
        chip.addView(UI.icon(c, UI.DROP_DOWN, 18, UI.TEXT),
                new LinearLayout.LayoutParams(UI.dp(c, 28), ViewGroup.LayoutParams.MATCH_PARENT));
        chip.setClickable(true);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onPickFolder();
            }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UI.dp(c, 32));
        cl.topMargin = UI.dp(c, 10);
        sheet.addView(chip, cl);
        refreshFolder();

        View line = new View(c);
        line.setBackgroundColor(UI.DIVIDER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 1));
        dl.topMargin = UI.dp(c, 10);
        sheet.addView(line, dl);

        // ---- actions
        String sizeText = length > 0 ? Downloads.size(length) : "";
        sheet.addView(action(c, UI.DOWNLOAD, S.t("Download"), sizeText, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = field.getText().toString().trim().replace('/', '_');
                listener.onConfirm(n);
            }
        }), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 54)));
        sheet.addView(action(c, UI.CLOSE, S.t("Cancel"), "", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCancel();
            }
        }), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 46)));

        addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        animateIn();
    }

    private View action(Context c, String glyph, String title, String sub, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.ripple());
        row.addView(UI.icon(c, glyph, 20, UI.TEXT),
                new LinearLayout.LayoutParams(UI.dp(c, 32), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = UI.label(c, title, 16, UI.TEXT);
        text.addView(t);
        if (sub != null && !sub.isEmpty()) text.addView(UI.label(c, sub, 12, UI.TEXT_MUTED));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = UI.dp(c, 14);
        row.addView(text, lp);
        row.setOnClickListener(click);
        return row;
    }

    /** Shows the currently chosen download folder. */
    void refreshFolder() {
        String f = Settings.dlFolder();
        String label = "Download";
        if (!f.isEmpty()) {
            try {
                String id = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(f));
                label = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
                if (label.isEmpty()) label = "/";
            } catch (Exception e) {
                label = f;
            }
        }
        folderLabel.setText(label);
    }

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
