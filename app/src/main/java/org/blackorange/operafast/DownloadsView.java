package org.blackorange.operafast;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Full-screen downloads list backed by DownloadManager; refreshes while visible. */
final class DownloadsView extends LinearLayout {

    interface Listener {
        void onClose();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout list;
    private boolean dialogOpen;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!dialogOpen) reload();
            handler.postDelayed(this, 1000);
        }
    };

    DownloadsView(Context c, final Listener listener) {
        super(c);
        setOrientation(VERTICAL);
        setBackgroundColor(UI.BG_APP);
        setClickable(true);

        addView(UI.header(c, "Downloads", new View.OnClickListener() {
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(refresh, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    private void reload() {
        Context c = getContext();
        list.removeAllViews();
        List<Downloads.Item> items = Downloads.list(c);
        if (items.isEmpty()) {
            TextView t = UI.label(c, "No downloads yet", 15, UI.TEXT_MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(c, 48), 0, 0);
            list.addView(t, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (Downloads.Item it : items) {
            list.addView(row(c, it), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View row(final Context c, final Downloads.Item it) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(UI.ripple());
        row.setClickable(true);
        row.setPadding(UI.dp(c, 16), UI.dp(c, 8), UI.dp(c, 4), UI.dp(c, 8));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (it.status != DownloadManager.STATUS_SUCCESSFUL) return;
                if (!Downloads.open(c, it)) {
                    Toast.makeText(c, S.t("No app can open this file"), Toast.LENGTH_SHORT).show();
                }
            }
        });

        TextView glyph = UI.icon(c, UI.DOWNLOAD, 22, UI.TEXT_MUTED);
        row.addView(glyph, new LayoutParams(UI.dp(c, 40), UI.dp(c, 40)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        text.addView(UI.label(c, it.title == null ? S.t("download") : it.title, 15, UI.TEXT));
        int statusColor = it.status == DownloadManager.STATUS_FAILED ? UI.ACCENT : UI.TEXT_MUTED;
        text.addView(UI.label(c, Downloads.statusText(it), 12, statusColor));
        row.addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView x = UI.iconButton(c, UI.DELETE, 20);
        x.setTextColor(UI.TEXT_MUTED);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(c, it);
            }
        });
        row.addView(x, new LayoutParams(UI.dp(c, 44), UI.dp(c, 44)));
        return row;
    }

    private void confirmDelete(Context c, final Downloads.Item it) {
        dialogOpen = true;
        boolean running = it.status != DownloadManager.STATUS_SUCCESSFUL
                && it.status != DownloadManager.STATUS_FAILED;
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(running ? S.t("Cancel this download?") : S.t("Delete") + " \"" + it.title + "\"?")
                .setPositiveButton(running ? S.t("Cancel download") : S.t("Delete"),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Downloads.remove(getContext(), it.id);
                            }
                        })
                .setNegativeButton(S.t("Keep"), null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        dialogOpen = false;
                        reload();
                    }
                })
                .show();
    }
}
