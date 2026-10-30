package org.blackorange.operafast;

import android.app.AlertDialog;
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

/** Full-screen downloads list backed by the own download engine; refreshes while visible. */
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
        List<Downloads.Item> items = Downloads.list();
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
                if (it.status == Downloads.FAILED || it.status == Downloads.PAUSED) {
                    Downloads.resume(it);
                    reload();
                } else if (it.status == Downloads.RUNNING || it.status == Downloads.QUEUED) {
                    Downloads.pause(it);
                    reload();
                } else if (!Downloads.open(c, it)) {
                    Toast.makeText(c, S.t("No app can open this file"), Toast.LENGTH_SHORT).show();
                }
            }
        });

        TextView glyph = UI.icon(c, UI.DOWNLOAD, 22, UI.TEXT_MUTED);
        row.addView(glyph, new LayoutParams(UI.dp(c, 40), UI.dp(c, 40)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        text.addView(UI.label(c, it.name == null ? S.t("download") : it.name, 15, UI.TEXT));
        int statusColor = it.status == Downloads.FAILED ? UI.ACCENT : UI.TEXT_MUTED;
        text.addView(UI.label(c, Downloads.statusText(it), 12, statusColor));
        if (it.status != Downloads.DONE && it.status != Downloads.FAILED) {
            android.widget.ProgressBar pb = new android.widget.ProgressBar(c, null,
                    android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress(it.total > 0 ? (int) (it.done * 1000 / it.total) : 0);
            pb.setIndeterminate(it.total <= 0 && it.status == Downloads.RUNNING);
            pb.getProgressDrawable().setColorFilter(UI.ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 4));
            pl.topMargin = UI.dp(c, 6);
            text.addView(pb, pl);
        }
        row.addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (it.status != Downloads.DONE) {
            boolean active = it.status == Downloads.RUNNING || it.status == Downloads.QUEUED;
            TextView act = UI.iconButton(c, active ? UI.PAUSE : (it.status == Downloads.FAILED ? UI.REFRESH : UI.PLAY), 22);
            act.setTextColor(UI.TEXT);
            act.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (it.status == Downloads.RUNNING || it.status == Downloads.QUEUED) Downloads.pause(it);
                    else Downloads.resume(it);
                    reload();
                }
            });
            row.addView(act, new LayoutParams(UI.dp(c, 44), UI.dp(c, 44)));
        }
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
        final boolean done = it.status == Downloads.DONE;
        new AlertDialog.Builder(c, UI.dialogTheme())
                .setMessage(done ? S.t("Delete") + " \"" + it.name + "\"?" : S.t("Cancel this download?"))
                .setPositiveButton(done ? S.t("Delete") : S.t("Cancel download"),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Downloads.remove(it, true);
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
