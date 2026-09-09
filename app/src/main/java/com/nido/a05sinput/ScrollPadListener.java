package com.nido.a05sinput;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Dedicated one-finger scroll-strip recognizer. */
final class ScrollPadListener implements View.OnTouchListener {
    interface Host {
        int dp(int value);
        int scrollPercent();
        void scroll(int amount);
        void haptic(View view, int kind);
        void scrollVisual(View view, boolean pressed);
    }

    private final Host host;
    private float lastY;
    private float remainder;

    ScrollPadListener(Host host) {
        this.host = host;
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = event.getY();
                remainder = 0;
                host.haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate().scaleX(0.965f).scaleY(0.985f).setDuration(55).start();
                host.scrollVisual(view, true);
                return true;
            case MotionEvent.ACTION_MOVE:
                remainder += event.getY() - lastY;
                lastY = event.getY();
                int stepSize = host.dp(Math.max(4, 1000 / host.scrollPercent()));
                int steps = (int) (remainder / stepSize);
                if (steps != 0) {
                    host.scroll(Math.max(-127, Math.min(127, -steps)));
                    remainder -= steps * stepSize;
                    host.haptic(view, HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
                host.scrollVisual(view, false);
                return true;
            default:
                return true;
        }
    }
}
