package org.blackorange.operafast;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Settings screen: language, search engine, start page, speed dial, passwords. */
final class SettingsView extends LinearLayout {

    interface Listener {
        void onClose();

        void onPasswords();

        void onLanguageChanged();

        void onDialChanged();

        void onClearData();

        void onClearCache();
    }

    private final Listener listener;
    private final LinearLayout list;

    SettingsView(Context c, final Listener listener) {
        super(c);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(UI.BG_APP);
        setClickable(true);
        addView(UI.header(c, "Settings", new View.OnClickListener() {
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
        build();
    }

    private void section(String title) {
        Context c = getContext();
        TextView h = UI.label(c, title, 13, UI.ACCENT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(UI.dp(c, 16), UI.dp(c, 20), UI.dp(c, 16), UI.dp(c, 6));
        list.addView(h);
    }

    private static long dirSize(java.io.File f) {
        if (f == null) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        java.io.File[] kids = f.listFiles();
        if (kids != null) for (java.io.File k : kids) n += dirSize(k);
        return n;
    }

    private LinearLayout row(String title, String value, View.OnClickListener click) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(VERTICAL);
        r.setBackground(UI.ripple());
        r.setClickable(true);
        r.setPadding(UI.dp(c, 16), UI.dp(c, 12), UI.dp(c, 16), UI.dp(c, 12));
        r.addView(UI.label(c, title, 16, UI.TEXT));
        if (value != null && !value.isEmpty()) {
            r.addView(UI.label(c, value, 13, UI.TEXT_MUTED));
        }
        r.setOnClickListener(click);
        list.addView(r, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void build() {
        final Context c = getContext();
        list.removeAllViews();

        section(S.t("General"));
        String l = Settings.lang();
        row(S.t("Language"), l.equals("uk") ? "Українська" : l.equals("en") ? "English"
                : S.t("Automatic"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLanguage();
            }
        });
        row(S.t("Search engine"), Settings.ENGINE_NAMES[Settings.engine()],
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickEngine();
                    }
                });

        section(S.t("Search"));
        row(S.t("Search suggestions"), Settings.suggestNet() ? S.t("On") : S.t("Off"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Settings.setSuggestNet(!Settings.suggestNet());
                        build();
                    }
                });
        row(S.t("History and bookmarks in suggestions"),
                Settings.suggestLocal() ? S.t("On") : S.t("Off"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Settings.setSuggestLocal(!Settings.suggestLocal());
                        build();
                    }
                });

        section(S.t("Home page"));
        String home = Settings.homeUrl();
        row(S.t("Start page"), home.isEmpty() ? S.t("Speed dial") : home,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickHome();
                    }
                });
        row(S.t("Reset speed dial"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.resetDial();
                listener.onDialChanged();
                android.widget.Toast.makeText(c, S.t("Speed dial reset"),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        section(S.t("Privacy"));
        row(S.t("Save passwords"), Settings.savePasswords() ? S.t("On") : S.t("Off"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Settings.setSavePasswords(!Settings.savePasswords());
                        build();
                    }
                });
        row(S.t("Manage passwords"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onPasswords();
            }
        });
        row(S.t("Prefer cache"), Settings.cacheFirst() ? S.t("On") : S.t("Off"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Settings.setCacheFirst(!Settings.cacheFirst());
                        build();
                    }
                });
        final LinearLayout cacheRow = row(S.t("Clear cache"), "…", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClearCache();
                build();
            }
        });
        final TextView cacheValue = (TextView) cacheRow.getChildAt(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long bytes = dirSize(c.getCacheDir());
                cacheValue.post(new Runnable() {
                    @Override
                    public void run() {
                        cacheValue.setText(Downloads.size(bytes));
                    }
                });
            }
        }).start();
        row(S.t("Clear data"), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClearData();
            }
        });
        TextView note = UI.label(c, "Private tabs don't keep history or saved passwords. "
                + "Cookies of the sites you visit are deleted when you close the last private tab.",
                12, UI.TEXT_MUTED);
        note.setMaxLines(6);
        note.setPadding(UI.dp(c, 16), UI.dp(c, 8), UI.dp(c, 16), UI.dp(c, 24));
        list.addView(note);
    }

    private void pickLanguage() {
        final String[] codes = {"auto", "en", "uk"};
        String[] names = {S.t("Automatic"), "English", "Українська"};
        int cur = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(Settings.lang())) cur = i;
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(S.t("Language"))
                .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (codes[which].equals(Settings.lang())) return;
                        Settings.setLang(codes[which]);
                        listener.onLanguageChanged();
                    }
                }).show();
    }

    private void pickEngine() {
        new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(S.t("Search engine"))
                .setSingleChoiceItems(Settings.ENGINE_NAMES, Settings.engine(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                Settings.setEngine(which);
                                d.dismiss();
                                build();
                            }
                        }).show();
    }

    private void pickHome() {
        final Context c = getContext();
        String[] names = {S.t("Speed dial"), S.t("Custom address")};
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(S.t("Start page"))
                .setSingleChoiceItems(names, Settings.homeUrl().isEmpty() ? 0 : 1,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                if (which == 0) {
                                    Settings.setHomeUrl("");
                                    build();
                                } else {
                                    askAddress();
                                }
                            }
                        }).show();
    }

    private void askAddress() {
        final Context c = getContext();
        final EditText in = new EditText(c);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        in.setText(Settings.homeUrl());
        in.setHint("https://");
        in.setTextColor(UI.TEXT);
        in.setHintTextColor(UI.TEXT_MUTED);
        LinearLayout box = new LinearLayout(c);
        box.setPadding(UI.dp(c, 20), UI.dp(c, 8), UI.dp(c, 20), 0);
        box.addView(in, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(S.t("Custom address"))
                .setView(box)
                .setPositiveButton(S.t("OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String u = MainActivity.toUrl(in.getText().toString());
                        Settings.setHomeUrl(u == null ? "" : u);
                        build();
                    }
                })
                .setNegativeButton(S.t("Cancel"), null)
                .show();
    }
}
