package org.blackorange.operafast;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Pull-to-refresh for the page area: drag down at the top of a page, release to reload. */
final class PullLayout extends FrameLayout {

    interface Listener {
        void onRefresh();
    }

    private final View spinner;
    private final Listener listener;
    private final int slop;
    private final int trigger;
    private float startX, startY, pull;
    private boolean pulling;

    PullLayout(Context c, View spinner, Listener listener) {
        super(c);
        this.spinner = spinner;
        this.listener = listener;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        this.trigger = UI.dp(c, 70);
    }

    private boolean atTop() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof WebView && v.getVisibility() == VISIBLE) return v.getScrollY() == 0;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = e.getX();
                startY = e.getY();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = e.getY() - startY;
                if (!pulling && e.getPointerCount() == 1 && dy > slop * 2
                        && dy > Math.abs(e.getX() - startX) * 2 && atTop()) {
                    pulling = true;
                    startY = e.getY();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!pulling) return false;
                pull = Math.max(0f, Math.min(trigger * 1.6f, (e.getY() - startY) * 0.55f));
                spinner.setAlpha(Math.min(1f, pull / trigger));
                spinner.setTranslationY(pull - spinner.getHeight() * 0.2f);
                spinner.setRotation(pull * 3f);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!pulling) return false;
                pulling = false;
                boolean go = e.getActionMasked() == MotionEvent.ACTION_UP && pull >= trigger;
                spinner.animate().alpha(0f).translationY(0f).setDuration(180).start();
                pull = 0;
                if (go) listener.onRefresh();
                return true;
            default:
                return pulling;
        }
    }
}
