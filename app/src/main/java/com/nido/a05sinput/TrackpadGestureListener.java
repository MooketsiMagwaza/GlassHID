package com.nido.a05sinput;

import android.os.Handler;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Interprets pointer movement, dragging, two-finger scrolling, and three-finger swipes. */
final class TrackpadGestureListener implements View.OnTouchListener {
    enum Swipe { LEFT, RIGHT, UP, DOWN }

    interface Host {
        int dp(int value);
        int dragHoldMs();
        int pointerPercent();
        int scrollPercent();
        void move(int dx, int dy);
        void scroll(int amount);
        void clickLeft();
        void setLeftButton(boolean down);
        void threeFingerSwipe(Swipe direction);
        void haptic(View view, int kind);
        void trackpadVisual(View view, int state);
    }

    static final int VISUAL_IDLE = 0;
    static final int VISUAL_PRESSED = 1;
    static final int VISUAL_DRAGGING = 2;

    private final Handler handler;
    private final Host host;
    private float lastX;
    private float lastY;
    private float lastTapX;
    private float lastTapY;
    private float gestureStartX;
    private float gestureStartY;
    private long downAt;
    private long lastTapAt;
    private float travel;
    private boolean fingerDown;
    private boolean dragging;
    private boolean multiFinger;
    private boolean threeFinger;
    private boolean gestureFired;
    private View activeView;
    private final Runnable longPress;

    TrackpadGestureListener(Handler handler, Host host) {
        this.handler = handler;
        this.host = host;
        this.longPress = () -> {
            if (fingerDown && !multiFinger && travel < host.dp(12) && activeView != null)
                beginDrag(activeView);
        };
    }

    private void beginDrag(View view) {
        if (dragging) return;
        dragging = true;
        handler.removeCallbacks(longPress);
        host.setLeftButton(true);
        host.haptic(view, HapticFeedbackConstants.LONG_PRESS);
        host.trackpadVisual(view, VISUAL_DRAGGING);
    }

    private void finishGesture(View view) {
        handler.removeCallbacks(longPress);
        fingerDown = false;
        if (dragging) host.setLeftButton(false);
        dragging = false;
        multiFinger = false;
        threeFinger = false;
        gestureFired = false;
        view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
        host.trackpadVisual(view, VISUAL_IDLE);
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        float x = centroidX(event);
        float y = centroidY(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                host.haptic(view, HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate().scaleX(0.992f).scaleY(0.992f).setDuration(55).start();
                host.trackpadVisual(view, VISUAL_PRESSED);
                long now = SystemClock.uptimeMillis();
                fingerDown = true;
                dragging = false;
                multiFinger = false;
                threeFinger = false;
                gestureFired = false;
                activeView = view;
                lastX = x;
                lastY = y;
                travel = 0;
                downAt = now;
                if (now - lastTapAt < 330 &&
                        Math.abs(x - lastTapX) + Math.abs(y - lastTapY) < host.dp(64)) {
                    lastTapAt = 0;
                    beginDrag(view);
                } else {
                    handler.postDelayed(longPress, host.dragHoldMs());
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                multiFinger = true;
                handler.removeCallbacks(longPress);
                if (dragging) {
                    host.setLeftButton(false);
                    dragging = false;
                }
                if (event.getPointerCount() >= 3) {
                    threeFinger = true;
                    gestureFired = false;
                    gestureStartX = x;
                    gestureStartY = y;
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                int rawDx = Math.round(x - lastX);
                int rawDy = Math.round(y - lastY);
                travel += Math.abs(rawDx) + Math.abs(rawDy);
                if (threeFinger || event.getPointerCount() >= 3) {
                    if (!threeFinger) {
                        threeFinger = true;
                        gestureStartX = x;
                        gestureStartY = y;
                    }
                    detectThreeFingerSwipe(view, x - gestureStartX, y - gestureStartY);
                } else if (event.getPointerCount() >= 2) {
                    host.scroll(clamp(Math.round(-rawDy * host.scrollPercent() / 300f)));
                } else {
                    if (!dragging && travel >= host.dp(12)) handler.removeCallbacks(longPress);
                    host.move(Math.round(rawDx * host.pointerPercent() / 100f),
                            Math.round(rawDy * host.pointerPercent() / 100f));
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
                boolean wasDragging = dragging;
                boolean wasMultiFinger = multiFinger;
                finishGesture(view);
                long releasedAt = SystemClock.uptimeMillis();
                if (!wasDragging && !wasMultiFinger && travel < host.dp(12) &&
                        releasedAt - downAt < 350) {
                    host.clickLeft();
                    lastTapAt = releasedAt;
                    lastTapX = x;
                    lastTapY = y;
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                finishGesture(view);
                return true;

            default:
                return true;
        }
    }

    private void detectThreeFingerSwipe(View view, float dx, float dy) {
        if (gestureFired || Math.max(Math.abs(dx), Math.abs(dy)) < host.dp(58)) return;
        gestureFired = true;
        Swipe direction;
        if (Math.abs(dx) > Math.abs(dy)) direction = dx < 0 ? Swipe.LEFT : Swipe.RIGHT;
        else direction = dy < 0 ? Swipe.UP : Swipe.DOWN;
        host.haptic(view, HapticFeedbackConstants.LONG_PRESS);
        host.threeFingerSwipe(direction);
    }

    private static float centroidX(MotionEvent event) {
        float value = 0;
        for (int i = 0; i < event.getPointerCount(); i++) value += event.getX(i);
        return value / event.getPointerCount();
    }

    private static float centroidY(MotionEvent event) {
        float value = 0;
        for (int i = 0; i < event.getPointerCount(); i++) value += event.getY(i);
        return value / event.getPointerCount();
    }

    private static int clamp(int value) {
        return Math.max(-127, Math.min(127, value));
    }
}
