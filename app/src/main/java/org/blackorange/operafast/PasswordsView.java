package org.blackorange.operafast;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** List of saved logins. Tap one to reveal / copy the password or delete it. */
final class PasswordsView extends LinearLayout {

    interface Listener {
        void onClose();
    }

    private final LinearLayout list;

    PasswordsView(final Context c, final Listener listener) {
        super(c);
        setOrientation(VERTICAL);
        setBackgroundColor(UI.BG_APP);
        setClickable(true);
        addView(UI.header(c, "Passwords", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClose();
            }
        }, null, null), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        reload();
    }

    private void reload() {
        final Context c = getContext();
        list.removeAllViews();
        List<PasswordStore.Item> items = PasswordStore.list();
        if (items.isEmpty()) {
            TextView t = UI.label(c, "No saved passwords", 15, UI.TEXT_MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(c, 48), 0, 0);
            list.addView(t, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (final PasswordStore.Item it : items) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(VERTICAL);
            row.setBackground(UI.ripple());
            row.setClickable(true);
            row.setPadding(UI.dp(c, 16), UI.dp(c, 10), UI.dp(c, 16), UI.dp(c, 10));
            row.addView(UI.label(c, it.host, 15, UI.TEXT));
            row.addView(UI.label(c, it.user.isEmpty() ? "(no username)" : it.user, 13,
                    UI.TEXT_MUTED));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(it);
                }
            });
            list.addView(row, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void open(final PasswordStore.Item shown) {
        final Context c = getContext();
        final PasswordStore.Item full = PasswordStore.find(shown.host, shown.user);
        final String pass = full == null ? "" : full.pass;
        new AlertDialog.Builder(c, UI.dialogTheme())
                .setTitle(shown.host)
                .setMessage((shown.user.isEmpty() ? "" : shown.user + "\n") + pass)
                .setPositiveButton(S.t("Copy password"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager) c.getSystemService(
                                Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("password", pass));
                        Toast.makeText(c, S.t("Password copied"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(S.t("Delete"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        PasswordStore.delete(shown.host, shown.user);
                        reload();
                    }
                })
                .setNeutralButton(S.t("Close"), null)
                .show();
    }
}
