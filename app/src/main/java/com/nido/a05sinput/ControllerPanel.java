package com.nido.a05sinput;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** PS3-inspired landscape Bluetooth HID controller with true multi-touch state. */
final class ControllerPanel {
    interface Listener {
        void onGamepadReport(int buttons, int leftX, int leftY,
                             int rightX, int rightY, int hat);
    }

    private static final int HAT_NEUTRAL = 8;
    private static final int PAPER = Color.rgb(247, 243, 234);
    private static final int INK = Color.rgb(24, 24, 24);
    private static final int GREEN = Color.rgb(139, 214, 170);
    private static final int YELLOW = Color.rgb(250, 204, 80);
    private static final int CORAL = Color.rgb(255, 126, 103);
    private static final int BLUE = Color.rgb(139, 188, 255);

    private final Activity activity;
    private final NeoUi ui;
    private final FeedbackController feedback;
    private final Listener listener;

    private int buttons;
    private int leftX;
    private int leftY;
    private int rightX;
    private int rightY;
    private int hat = HAT_NEUTRAL;

    ControllerPanel(Activity activity, NeoUi ui, FeedbackController feedback,
                    Listener listener) {
        this.activity = activity;
        this.ui = ui;
        this.feedback = feedback;
        this.listener = listener;
    }

    View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(2), 0, 0);
        root.setMotionEventSplittingEnabled(true);

        LinearLayout shoulders = horizontal();
        shoulders.addView(gameButton("L2", 7, BLUE), weighted(1));
        shoulders.addView(gameButton("L1", 5, PAPER), weighted(1));

        TextView wordmark = ui.text("A05s  /  GAMEPAD", 13);
        wordmark.setTypeface(Typeface.DEFAULT_BOLD);
        wordmark.setTextColor(INK);
        wordmark.setGravity(Gravity.CENTER);
        shoulders.addView(wordmark, weighted(2.6f));

        shoulders.addView(gameButton("R1", 6, PAPER), weighted(1));
        shoulders.addView(gameButton("R2", 8, BLUE), weighted(1));
        root.addView(shoulders, new LinearLayout.LayoutParams(-1, dp(58)));

        FrameLayout body = new FrameLayout(activity);
        body.setMotionEventSplittingEnabled(true);
        body.setBackground(ui.background(GREEN));

        View dpad = dPad();
        body.addView(dpad, frame(dp(172), dp(172), Gravity.LEFT | Gravity.TOP,
                dp(14), dp(8), 0, 0));

        FrameLayout symbols = faceSymbols();
        body.addView(symbols, frame(dp(172), dp(172), Gravity.RIGHT | Gravity.TOP,
                0, dp(8), dp(14), 0));

        LinearLayout systemButtons = horizontal();
        systemButtons.addView(gameButton("SELECT", 9, PAPER), weighted(1.2f));
        systemButtons.addView(gameButton("PS", 13, YELLOW), weighted(0.8f));
        systemButtons.addView(gameButton("START", 10, PAPER), weighted(1.2f));
        body.addView(systemButtons, frame(dp(250), dp(52),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, dp(12), 0, 0));

        body.addView(stick("L3", true), frame(dp(148), dp(148),
                Gravity.LEFT | Gravity.BOTTOM, dp(210), 0, 0, dp(10)));
        body.addView(stick("R3", false), frame(dp(148), dp(148),
                Gravity.RIGHT | Gravity.BOTTOM, 0, 0, dp(210), dp(10)));

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    void releaseAll() {
        buttons = 0;
        leftX = leftY = rightX = rightY = 0;
        hat = HAT_NEUTRAL;
        dispatch();
    }

    private View dPad() {
        TextView pad = ui.text("↑\n←   •   →\n↓", 22);
        pad.setTypeface(Typeface.DEFAULT_BOLD);
        pad.setTextColor(PAPER);
        pad.setGravity(Gravity.CENTER);
        pad.setBackground(ui.rounded(INK));
        pad.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                feedback.perform(view, HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(50).start();
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - view.getWidth() / 2f;
                float dy = event.getY() - view.getHeight() / 2f;
                float deadZone = Math.min(view.getWidth(), view.getHeight()) * 0.14f;
                int next = Math.hypot(dx, dy) < deadZone ? HAT_NEUTRAL : hatFor(dx, dy);
                if (next != hat) {
                    hat = next;
                    feedback.perform(view, HapticFeedbackConstants.CLOCK_TICK);
                    dispatch();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (hat != HAT_NEUTRAL) {
                    hat = HAT_NEUTRAL;
                    dispatch();
                }
                view.animate().scaleX(1f).scaleY(1f).setDuration(70).start();
            }
            return true;
        });
        return pad;
    }

    private FrameLayout faceSymbols() {
        FrameLayout pad = new FrameLayout(activity);
        pad.setMotionEventSplittingEnabled(true);
        pad.addView(gameButton("△", 4, YELLOW), faceFrame(Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        pad.addView(gameButton("□", 3, BLUE), faceFrame(Gravity.LEFT | Gravity.CENTER_VERTICAL));
        pad.addView(gameButton("○", 2, CORAL), faceFrame(Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        pad.addView(gameButton("×", 1, GREEN), faceFrame(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        return pad;
    }

    private View stick(String clickLabel, boolean left) {
        TextView stick = ui.text("●\n" + clickLabel, 18);
        stick.setTypeface(Typeface.DEFAULT_BOLD);
        stick.setTextColor(PAPER);
        stick.setGravity(Gravity.CENTER);
        stick.setBackground(ui.rounded(INK));
        stick.setOnTouchListener(new StickListener(left, clickLabel.equals("L3") ? 11 : 12));
        return stick;
    }

    private Button gameButton(String label, int buttonNumber, int color) {
        Button button = ui.button(label, color);
        int mask = 1 << (buttonNumber - 1);
        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                feedback.perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
                buttons |= mask;
                dispatch();
                view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(45).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                buttons &= ~mask;
                dispatch();
                view.animate().scaleX(1f).scaleY(1f).setDuration(65).start();
            }
            return true;
        });
        return button;
    }

    private final class StickListener implements View.OnTouchListener {
        private final boolean left;
        private final int clickButton;
        private float downX;
        private float downY;
        private float travel;
        private long downAt;

        StickListener(boolean left, int clickButton) {
            this.left = left;
            this.clickButton = clickButton;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                travel = 0;
                downAt = SystemClock.uptimeMillis();
                feedback.perform(view, HapticFeedbackConstants.VIRTUAL_KEY);
                view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(50).start();
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                travel = Math.max(travel, Math.abs(event.getX() - downX) +
                        Math.abs(event.getY() - downY));
                int x = axis(event.getX(), view.getWidth());
                int y = axis(event.getY(), view.getHeight());
                if (left) {
                    if (x == leftX && y == leftY) return true;
                    leftX = x;
                    leftY = y;
                } else {
                    if (x == rightX && y == rightY) return true;
                    rightX = x;
                    rightY = y;
                }
                dispatch();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (left) leftX = leftY = 0;
                else rightX = rightY = 0;
                dispatch();
                if (action == MotionEvent.ACTION_UP && travel < dp(14) &&
                        SystemClock.uptimeMillis() - downAt < 280) {
                    pulseButton(view, clickButton);
                }
                view.animate().scaleX(1f).scaleY(1f).setDuration(70).start();
            }
            return true;
        }
    }

    private void pulseButton(View view, int buttonNumber) {
        int mask = 1 << (buttonNumber - 1);
        feedback.perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
        buttons |= mask;
        dispatch();
        view.postDelayed(() -> {
            buttons &= ~mask;
            dispatch();
        }, 55);
    }

    private void dispatch() {
        listener.onGamepadReport(buttons, leftX, leftY, rightX, rightY, hat);
    }

    private int axis(float position, int size) {
        if (size <= 0) return 0;
        float normalized = (position - size / 2f) / (size / 2f);
        if (Math.abs(normalized) < 0.08f) return 0;
        return Math.max(-127, Math.min(127, Math.round(normalized * 127f)));
    }

    private int hatFor(float x, float y) {
        double degrees = Math.toDegrees(Math.atan2(y, x));
        if (degrees < 0) degrees += 360;
        int sector = ((int) Math.round(degrees / 45.0)) & 7;
        return new int[]{2, 3, 4, 5, 6, 7, 0, 1}[sector];
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMotionEventSplittingEnabled(true);
        return layout;
    }

    private LinearLayout.LayoutParams weighted(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, weight);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        return params;
    }

    private FrameLayout.LayoutParams faceFrame(int gravity) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(62), dp(62), gravity);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private FrameLayout.LayoutParams frame(int width, int height, int gravity,
                                           int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return ui.dp(value);
    }
}
