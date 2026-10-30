package org.blackorange.operafast;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Opera-style tab switcher: a horizontally snapping carousel of big page previews (the centred
 * card is full size, neighbours are smaller and dimmer), a strip of small thumbnails under it,
 * and a floating pill bar with the "+" button.
 */
final class TabSwitcherView extends FrameLayout {

    interface Listener {
        void onSelect(Tab tab);

        void onClose(Tab tab);

        void onNew(boolean priv);

        void onDone();

        void onHistory();

        void onDownloads();

        void onMenu();
    }

    private static final int CARD_BG = 0xFF2E2A3C;

    private final TabManager tabs;
    private final Listener listener;
    private final float aspect;                 // page area height / width
    private final Handler handler = new Handler(Looper.getMainLooper());

    private HorizontalScrollView carousel;
    private LinearLayout cards;
    private HorizontalScrollView stripScroll;
    private LinearLayout strip;
    private TextView countLabel;
    private TextView tabsTitle;
    private TextView privTitle;
    private View tabsLine;
    private View privLine;
    private TextView emptyLabel;
    private UI.TabCount pillCount;
    private boolean priv;

    private int builtWidth = -1;
    private int step = 1;
    private int index = -1;
    private int lastScrollX = Integer.MIN_VALUE;

    TabSwitcherView(Context c, TabManager tabs, float aspect, Listener listener) {
        super(c);
        this.tabs = tabs;
        this.listener = listener;
        this.aspect = aspect;
        this.priv = tabs.active() != null && tabs.active().priv;
        setClickable(true);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF2B2140, 0xFF15141C}));

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- header: "Tabs" with accent underline, tab count on the right
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UI.dp(c, 20), UI.dp(c, 8), UI.dp(c, 20), 0);

        LinearLayout[] boxes = new LinearLayout[2];
        for (int k = 0; k < 2; k++) {
            final boolean forPrivate = k == 1;
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setClickable(true);
            TextView title = UI.label(c, forPrivate ? "Private" : "Tabs", 18, UI.TEXT);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View underline = new View(c);
            underline.setBackgroundColor(UI.ACCENT);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 2));
            ulp.topMargin = UI.dp(c, 6);
            box.addView(underline, ulp);
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (priv != forPrivate) {
                        priv = forPrivate;
                        index = -1;
                        reload();
                    }
                }
            });
            if (forPrivate) {
                privTitle = title;
                privLine = underline;
            } else {
                tabsTitle = title;
                tabsLine = underline;
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (forPrivate) blp.leftMargin = UI.dp(c, 22);
            header.addView(box, blp);
        }

        View grow = new View(c);
        header.addView(grow, new LinearLayout.LayoutParams(0, 1, 1f));
        countLabel = UI.label(c, "", 14, UI.TEXT_MUTED);
        header.addView(countLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 56)));

        // ---- carousel of big cards
        FrameLayout carouselArea = new FrameLayout(c);
        carousel = new HorizontalScrollView(c);
        carousel.setHorizontalScrollBarEnabled(false);
        carousel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        cards = new LinearLayout(c);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        carousel.addView(cards, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        carouselArea.addView(carousel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
        emptyLabel = UI.label(c, "No tabs", 15, UI.TEXT_MUTED);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setVisibility(View.GONE);
        carouselArea.addView(emptyLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        column.addView(carouselArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        carousel.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                updateEffects(sx);
            }
        });
        carousel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    handler.removeCallbacks(snapCheck);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    lastScrollX = Integer.MIN_VALUE;
                    handler.removeCallbacks(snapCheck);
                    handler.postDelayed(snapCheck, 50);
                }
                return false;
            }
        });

        // ---- thumbnail strip
        stripScroll = new HorizontalScrollView(c);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setFillViewport(true);
        strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER);
        strip.setPadding(UI.dp(c, 12), 0, UI.dp(c, 12), 0);
        stripScroll.addView(strip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(stripScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 84)));

        // room for the floating bar
        column.addView(new View(c), new LinearLayout.LayoutParams(1, UI.dp(c, 96)));

        addView(buildBar(c), barParams(c));

        // Build straight away from the screen size; do not depend on a layout callback.
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        rebuild(dm.widthPixels, dm.heightPixels - UI.dp(c, 24));
    }

    // ---------------------------------------------------------------- floating bar

    private FrameLayout.LayoutParams barParams(Context c) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, 64), Gravity.BOTTOM);
        int m = UI.dp(c, 16);
        lp.setMargins(m, 0, m, m);
        return lp;
    }

    private View buildBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(UI.rounded(0xFF1E1E26, UI.dp(c, 32)));
        bar.setElevation(UI.dp(c, 8));

        TextView dl = UI.iconButton(c, UI.DOWNLOAD, 24);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDownloads();
            }
        });
        bar.addView(dl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView hist = UI.iconButton(c, UI.HISTORY, 24);
        hist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHistory();
            }
        });
        bar.addView(hist, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout fabSlot = new FrameLayout(c);
        TextView fab = UI.icon(c, UI.ADD, 28, 0xFFFFFFFF);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(UI.ACCENT);
        fab.setBackground(fabBg);
        fab.setElevation(UI.dp(c, 6));
        fab.setClickable(true);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onNew(priv);
            }
        });
        fabSlot.addView(fab, new FrameLayout.LayoutParams(UI.dp(c, 54), UI.dp(c, 54), Gravity.CENTER));
        bar.addView(fabSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout countBtn = new FrameLayout(c);
        countBtn.setBackground(UI.ripple());
        countBtn.setClickable(true);
        UI.TabCount count = new UI.TabCount(c);
        pillCount = count;
        count.setCount(visible().size());
        countBtn.addView(count, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        countBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDone();
            }
        });
        bar.addView(countBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView more = UI.iconButton(c, UI.MORE, 24);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMenu();
            }
        });
        bar.addView(more, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return bar;
    }

    // ---------------------------------------------------------------- building cards

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && w != builtWidth) rebuild(w, h);
    }

    /** Tabs shown in the current mode (normal or private). */
    private List<Tab> visible() {
        List<Tab> out = new java.util.ArrayList<Tab>();
        for (Tab t : tabs.all()) {
            if (t.priv == priv) out.add(t);
        }
        return out;
    }

    private int lastW, lastH;

    /** Called by the host after a tab was closed. */
    void reload() {
        if (lastW > 0) rebuild(lastW, lastH);
    }

    private void rebuild(int w, int h) {
        try {
            doRebuild(w, h);
        } catch (Throwable e) {
            // never leave the screen blank without saying why
            cards.removeAllViews();
            TextView err = UI.label(getContext(), String.valueOf(e), 12, UI.TEXT_MUTED);
            err.setMaxLines(8);
            err.setEllipsize(null);
            cards.addView(err, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void doRebuild(int w, int h) {
        final Context c = getContext();
        lastW = w;
        lastH = h;
        builtWidth = w;
        List<Tab> list = visible();
        int bright = 0xFFFFFFFF, dim = UI.TEXT_MUTED;
        tabsTitle.setTextColor(priv ? dim : bright);
        privTitle.setTextColor(priv ? bright : dim);
        tabsLine.setVisibility(priv ? View.INVISIBLE : View.VISIBLE);
        privLine.setVisibility(priv ? View.VISIBLE : View.INVISIBLE);
        pillCount.setCount(list.size());
        emptyLabel.setText(S.t(priv ? "No private tabs" : "No tabs"));
        emptyLabel.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        countLabel.setText(S.tabs(list.size()));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                priv ? new int[]{0xFF2A1646, 0xFF120C1E} : new int[]{0xFF2B2140, 0xFF15141C}));
        if (list.isEmpty()) {
            cards.removeAllViews();
            strip.removeAllViews();
            return;
        }
        if (index < 0) index = Math.max(0, list.indexOf(tabs.active()));
        index = Math.min(index, list.size() - 1);

        int cardW = (int) (w * 0.68f);
        int margin = UI.dp(c, 8);
        int titleH = UI.dp(c, 46);
        int availH = h - UI.dp(c, 56) - UI.dp(c, 84) - UI.dp(c, 96);
        int cardH = Math.min(titleH + (int) (cardW * aspect), availH);
        step = cardW + 2 * margin;

        cards.removeAllViews();
        int side = (w - cardW) / 2 - margin;
        cards.setPadding(side, 0, side, 0);
        for (int i = 0; i < list.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(margin, 0, margin, 0);
            cards.addView(card(list.get(i), i, titleH), lp);
        }

        strip.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            strip.addView(stripThumb(list.get(i), i));
        }

        final int target = index;
        carousel.post(new Runnable() {
            @Override
            public void run() {
                carousel.scrollTo(target * step, 0);
                updateEffects(target * step);
                updateStrip();
            }
        });
    }

    private View card(final Tab tab, final int position, int titleH) {
        final Context c = getContext();
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = UI.rounded(CARD_BG, UI.dp(c, 22));
        if (tab == tabs.active()) bg.setStroke(UI.dp(c, 2), UI.ACCENT);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSelect(tab);   // one tap, whichever card it is
            }
        });
        attachSwipeToClose(card, tab);

        // title row: favicon / letter, title, X
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UI.dp(c, 12), 0, UI.dp(c, 4), 0);

        String host = UI.hostOf(tab.url);
        if (tab.favicon != null) {
            ImageView icon = new ImageView(c);
            icon.setImageBitmap(tab.favicon);
            top.addView(icon, new LinearLayout.LayoutParams(UI.dp(c, 22), UI.dp(c, 22)));
        } else {
            String letter = tab.isBlank() || host.isEmpty() ? "+" : host.substring(0, 1).toUpperCase();
            TextView badge = new TextView(c);
            badge.setText(letter);
            badge.setTextColor(UI.TEXT);
            badge.setTextSize(12);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(UI.BG_INPUT);
            badge.setBackground(dot);
            top.addView(badge, new LinearLayout.LayoutParams(UI.dp(c, 22), UI.dp(c, 22)));
        }
        View gap = new View(c);
        top.addView(gap, new LinearLayout.LayoutParams(UI.dp(c, 10), 1));

        String name = tab.title != null && !tab.title.isEmpty() ? tab.title
                : (tab.isBlank() ? "New tab" : host);
        TextView title = UI.label(c, name, 14, UI.TEXT);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView x = UI.iconButton(c, UI.CLOSE, 20);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                x.setEnabled(false);
                dismiss(card, tab, -UI.dp(c, 60), 170);
            }
        });
        top.addView(x, new LinearLayout.LayoutParams(UI.dp(c, 40), UI.dp(c, 40)));
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, titleH));

        // preview area
        FrameLayout area = new FrameLayout(c);
        if (tab.thumb != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(tab.thumb);
            area.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            area.setBackgroundColor(0xFF1E1E26);
            LinearLayout ph = new LinearLayout(c);
            ph.setOrientation(LinearLayout.VERTICAL);
            ph.setGravity(Gravity.CENTER);
            ph.addView(UI.icon(c, UI.GLOBE, 40, UI.TEXT_MUTED));
            TextView t = UI.label(c, tab.isBlank() ? "Start page" : host, 13, UI.TEXT_MUTED);
            t.setGravity(Gravity.CENTER);
            ph.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            area.addView(ph, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        card.addView(area, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return card;
    }

    private void dismiss(View card, final Tab tab, float toY, int ms) {
        card.animate().translationY(toY).alpha(0f).setDuration(ms)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        listener.onClose(tab);
                    }
                }).start();
    }

    /** Drag a card up (or down) and let go to close the tab; short drags spring back. */
    private void attachSwipeToClose(final View card, final Tab tab) {
        final Context c = getContext();
        final int slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        final int threshold = UI.dp(c, 90);
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = e.getRawY() - downY;
                        float dx = e.getRawX() - downX;
                        if (!dragging && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                            dragging = true;
                            v.setPressed(false);
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        if (dragging) {
                            v.setTranslationY(dy);
                            v.setAlpha(Math.max(0.2f, 1f - Math.abs(dy) / (threshold * 2.5f)));
                            return true;
                        }
                        return false;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging) return false;
                        dragging = false;
                        float dy = e.getRawY() - downY;
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dy) > threshold) {
                            dismiss(v, tab, dy < 0 ? -v.getHeight() : v.getHeight(), 140);
                        } else {
                            v.animate().translationY(0f).alpha(1f).setDuration(160).start();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private View stripThumb(final Tab tab, final int position) {
        Context c = getContext();
        FrameLayout f = new FrameLayout(c);
        f.setBackground(UI.rounded(0xFF1E1E26, UI.dp(c, 6)));
        f.setClipToOutline(true);
        f.setClickable(true);
        if (tab.thumb != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageBitmap(tab.thumb);
            f.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            f.addView(UI.icon(c, UI.GLOBE, 16, UI.TEXT_MUTED), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSelect(tab);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UI.dp(c, 38), UI.dp(c, 62));
        lp.setMargins(UI.dp(c, 5), 0, UI.dp(c, 5), 0);
        f.setLayoutParams(lp);
        return f;
    }

    // ---------------------------------------------------------------- scrolling / snapping

    /** Scale and dim cards by their distance from the centre; track the centred index. */
    private void updateEffects(int scrollX) {
        int n = cards.getChildCount();
        for (int i = 0; i < n; i++) {
            float d = Math.min(1f, Math.abs(scrollX - i * step) / (float) step);
            View card = cards.getChildAt(i);
            float s = 1f - 0.12f * d;
            card.setScaleX(s);
            card.setScaleY(s);
            card.setAlpha(1f - 0.45f * d);
        }
        int nearest = Math.max(0, Math.min(n - 1, Math.round(scrollX / (float) step)));
        if (nearest != index) {
            index = nearest;
            updateStrip();
        }
    }

    private void updateStrip() {
        int n = strip.getChildCount();
        for (int i = 0; i < n; i++) {
            View t = strip.getChildAt(i);
            boolean on = i == index;
            t.setAlpha(on ? 1f : 0.55f);
            t.setScaleX(on ? 1.15f : 1f);
            t.setScaleY(on ? 1.15f : 1f);
        }
        if (index >= 0 && index < n) {
            View t = strip.getChildAt(index);
            stripScroll.smoothScrollTo(t.getLeft() - (stripScroll.getWidth() - t.getWidth()) / 2, 0);
        }
    }

    private void snapTo(int position) {
        int n = cards.getChildCount();
        if (n == 0) return;
        int p = Math.max(0, Math.min(n - 1, position));
        carousel.smoothScrollTo(p * step, 0);
    }

    /** After the finger lifts: wait until the fling settles, then snap to the nearest card. */
    private final Runnable snapCheck = new Runnable() {
        @Override
        public void run() {
            int x = carousel.getScrollX();
            if (x == lastScrollX) {
                snapTo(Math.round(x / (float) step));
                lastScrollX = Integer.MIN_VALUE;
            } else {
                lastScrollX = x;
                handler.postDelayed(this, 50);
            }
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
