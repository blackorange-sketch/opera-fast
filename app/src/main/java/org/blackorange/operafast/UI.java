package org.blackorange.operafast;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Palette, Material Icons glyphs and small view helpers shared by all screens. */
final class UI {
    private UI() {}

    // Opera-like dark palette
    static int BG_APP = 0xFF1A1A1A;
    static int BG_BAR = 0xFF252525;
    static int BG_INPUT = 0xFF3A3A3A;
    static int BG_INPUT_FOCUS = 0xFF4A4A4A;
    static int BG_CARD = 0xFF2E2E2E;
    static int TEXT = 0xFFFFFFFF;
    static int TEXT_MUTED = 0xFFA0A0A0;
    static int ACCENT = 0xFFFF1B2D;   // changed by applyAccent() at startup
    static final int[] ACCENTS = {0xFFFF1B2D, 0xFF3D8BFF, 0xFF2ECC71, 0xFFA66BFF, 0xFFFF9F1A, 0xFFFF4FA3};
    static final String[] ACCENT_NAMES = {"Red", "Blue", "Green", "Purple", "Orange", "Pink"};

    static int SHEET_BG = 0xFF1E1E26, TILE_OFF = 0xFF2A2A33, BUTTON_ICON = 0xFFC9B8FF,
            PRIV_BAR = 0xFF241B35, POP_BG = 0xFF33303F;
    static boolean light;

    /** Switches the shared palette; call before any view is built. */
    static void applyTheme(boolean lightTheme) {
        light = lightTheme;
        if (lightTheme) {
            BG_APP = 0xFFF4F4F6; BG_BAR = 0xFFFFFFFF; BG_INPUT = 0xFFE8E8EC;
            BG_INPUT_FOCUS = 0xFFDADAE0; BG_CARD = 0xFFFFFFFF; TEXT = 0xFF1B1B1F;
            TEXT_MUTED = 0xFF6B6B73; DIVIDER = 0xFFDDDDE2;
            SHEET_BG = 0xFFFFFFFF; TILE_OFF = 0xFFEDEDF2; BUTTON_ICON = 0xFF5B3FC0;
            PRIV_BAR = 0xFFE6DEF5; POP_BG = 0xFFE9E6F2;
        } else {
            BG_APP = 0xFF1A1A1A; BG_BAR = 0xFF252525; BG_INPUT = 0xFF3A3A3A;
            BG_INPUT_FOCUS = 0xFF4A4A4A; BG_CARD = 0xFF2E2E2E; TEXT = 0xFFFFFFFF;
            TEXT_MUTED = 0xFFA0A0A0; DIVIDER = 0xFF333333;
            SHEET_BG = 0xFF1E1E26; TILE_OFF = 0xFF2A2A33; BUTTON_ICON = 0xFFC9B8FF;
            PRIV_BAR = 0xFF241B35; POP_BG = 0xFF33303F;
        }
    }

    static int dialogTheme() {
        return light ? android.app.AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
                : android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK;
    }

    static void applyAccent(int index) {
        ACCENT = ACCENTS[index >= 0 && index < ACCENTS.length ? index : 0];
    }
    static final int GREEN = 0xFF66FF66;
    static final int ORANGE = 0xFFFFA726;
    static int DIVIDER = 0xFF333333;

    // Material Icons codepoints (font is bundled in assets/fonts)
    static final String BACK = "";
    static final String FORWARD = "";
    static final String REFRESH = "";
    static final String CLOSE = "";
    static final String ADD = "";
    static final String MORE = "";
    static final String SHIELD = "";
    static final String HISTORY = "";
    static final String DOWNLOAD = "";
    static final String DELETE = "";
    static final String SWEEP = "";
    static final String SHARE = "";
    static final String DESKTOP = "";
    static final String CHECK = "";
    static final String OPEN = "";
    static final String BOOKMARK = "";
    static final String BOOKMARK_BORDER = "";
    static final String FIND = "";
    static final String MOON = "";
    static final String IMAGE = "";
    static final String UP = "";
    static final String DOWN = "";
    static final String PAUSE = "\ue034";
    static final String PLAY = "\ue037";
    static final String MORE_V = "\ue5d4";
    static final String COPY = "\ue14d";
    static final String QR = "\ue00a";
    static final String GLOBE = "";

    static final String PRIVATE = "\ue8f5";
    static final String SETTINGS = "\ue8b8";
    static final String READER = "\ue86d";
    static final String TRANSLATE = "\ue8e2";
    static final String TEXT_FIELDS = "\ue262";
    static final String HOME = "\ue88a";
    static final String LOCK = "\ue897";
    static final String LOCK_OPEN = "\ue898";
    static final String SEARCH = "\ue8b6";
    static final String KEY = "\ue0da";

    private static Typeface iconFont;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static Typeface icons(Context c) {
        if (iconFont == null) {
            iconFont = Typeface.createFromAsset(c.getAssets(), "fonts/MaterialIcons-Regular.ttf");
        }
        return iconFont;
    }

    static String hostOf(String url) {
        if (url == null) return "";
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return url;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return url;
        }
    }

    static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable roundedTop(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        return d;
    }

    static Drawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null,
                new ColorDrawable(0xFFFFFFFF));
    }

    static TextView label(Context c, String text, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(S.t(text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static TextView icon(Context c, String glyph, int sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(icons(c));
        t.setText(glyph);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static TextView iconButton(Context c, String glyph, int sp) {
        TextView t = icon(c, glyph, sp, TEXT);
        t.setBackground(ripple());
        t.setClickable(true);
        return t;
    }

    /** Full-screen screen header: back arrow, title, optional right action. */
    static LinearLayout header(Context c, String title, View.OnClickListener onBack,
                               String rightGlyph, View.OnClickListener onRight) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(BG_BAR);

        TextView back = iconButton(c, BACK, 24);
        back.setOnClickListener(onBack);
        h.addView(back, new LinearLayout.LayoutParams(dp(c, 52), dp(c, 52)));

        TextView tv = label(c, title, 18, TEXT);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rightGlyph != null) {
            TextView r = iconButton(c, rightGlyph, 24);
            r.setOnClickListener(onRight);
            h.addView(r, new LinearLayout.LayoutParams(dp(c, 52), dp(c, 52)));
        }
        return h;
    }

    /** Tab-count badge (rounded square with a number), like Opera's tabs button. */
    static final class TabCount extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int count = 1;

        TabCount(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(TEXT);
            stroke.setStrokeWidth(dp(c, 2));
            text.setColor(TEXT);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                    c.getResources().getDisplayMetrics()));
        }

        void setCount(int n) {
            if (n != count) {
                count = n;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = dp(getContext(), 22);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            float r = dp(getContext(), 5);
            canvas.drawRoundRect(rect, r, r, stroke);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(String.valueOf(Math.min(count, 99)), cx,
                    cy - (fm.ascent + fm.descent) / 2f, text);
        }
    }

    /**
     * Thin red page-load progress line under the address bar. The bar glides to each new value
     * and fades out after reaching 100, instead of jumping.
     */
    static final class ProgressLine extends View {
        private final Paint paint = new Paint();
        private float shown;                       // 0 = idle, 0..100 = loading
        private android.animation.ValueAnimator anim;

        ProgressLine(Context c) {
            super(c);
            paint.setColor(ACCENT);
            setAlpha(0f);
        }

        void setProgress(int p) {
            if (anim != null) anim.cancel();
            animate().cancel();
            if (p <= 0) {
                shown = 0f;
                setAlpha(0f);
                invalidate();
                return;
            }
            final boolean finishing = p >= 100;
            if (finishing && (shown <= 0.01f || shown >= 100f)) return; // nothing was loading
            setAlpha(1f);
            anim = android.animation.ValueAnimator.ofFloat(shown, finishing ? 100f : (float) p);
            anim.setDuration(finishing ? 150 : 250);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    shown = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            if (finishing) {
                anim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator a) {
                        if (shown < 99.9f) return; // cancelled by a newer value
                        animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                shown = 0f;
                                invalidate();
                            }
                        }).start();
                    }
                });
            }
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth() * shown / 100f, getHeight(), paint);
        }
    }
}
