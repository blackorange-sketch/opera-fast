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
    static final int BG_APP = 0xFF1A1A1A;
    static final int BG_BAR = 0xFF252525;
    static final int BG_INPUT = 0xFF3A3A3A;
    static final int BG_INPUT_FOCUS = 0xFF4A4A4A;
    static final int BG_CARD = 0xFF2E2E2E;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_MUTED = 0xFFA0A0A0;
    static final int ACCENT = 0xFFFF1B2D;
    static final int GREEN = 0xFF66FF66;
    static final int ORANGE = 0xFFFFA726;
    static final int DIVIDER = 0xFF333333;

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

    static Drawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null,
                new ColorDrawable(0xFFFFFFFF));
    }

    static TextView label(Context c, String text, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(text);
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

    /** Thin red page-load progress line under the address bar. */
    static final class ProgressLine extends View {
        private final Paint paint = new Paint();
        private int progress = 100;

        ProgressLine(Context c) {
            super(c);
            paint.setColor(ACCENT);
            setVisibility(INVISIBLE);
        }

        void setProgress(int p) {
            progress = p;
            setVisibility(p <= 0 || p >= 100 ? INVISIBLE : VISIBLE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth() * progress / 100f, getHeight(), paint);
        }
    }
}
