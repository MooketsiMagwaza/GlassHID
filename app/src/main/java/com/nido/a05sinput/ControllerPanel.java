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

    private static final int HAT_NEUTRAL = DpadView.HAT_NEUTRAL;
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
    private final boolean swapControls;
    private final Button[] faceButtons = new Button[4];
    private TextView connectionStatus;
    private DpadView dpadView;
    private final int labelStyle;

    private int buttons;
    private int leftX;
    private int leftY;
    private int rightX;
    private int rightY;
    private int hat = HAT_NEUTRAL;

    ControllerPanel(Activity activity, NeoUi ui, FeedbackController feedback,
                    Listener listener, boolean swapControls, int labelStyle) {
        this.activity = activity;
        this.ui = ui;
        this.feedback = feedback;
        this.listener = listener;
        this.swapControls = swapControls;
        this.labelStyle = Math.max(0, Math.min(2, labelStyle));
    }

    View build() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(2), 0, 0);
        root.setMotionEventSplittingEnabled(true);

        LinearLayout identity = new LinearLayout(activity);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER);
        connectionStatus = ui.text("BT · STARTING INPUT", 11);
        connectionStatus.setTypeface(Typeface.DEFAULT_BOLD);
        connectionStatus.setTextColor(INK);
        connectionStatus.setGravity(Gravity.CENTER);
        identity.addView(connectionStatus, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView wordmark = ui.text("GLASSHID  /  GAMEPAD", 13);
        wordmark.setTypeface(Typeface.DEFAULT_BOLD);
        wordmark.setTextColor(INK);
        wordmark.setGravity(Gravity.CENTER);
        identity.addView(wordmark, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(identity, new LinearLayout.LayoutParams(-1, dp(48)));

        FrameLayout body = new FrameLayout(activity);
        body.setMotionEventSplittingEnabled(true);
        body.setBackground(ui.background(GREEN));

        View dpad = dPad();
        body.addView(dpad, swapControls
                ? frame(dp(148), dp(148), Gravity.LEFT | Gravity.BOTTOM,
                        dp(198), 0, 0, dp(10))
                : frame(dp(172), dp(172), Gravity.LEFT | Gravity.TOP,
                        dp(14), dp(8), 0, 0));

        FrameLayout symbols = faceSymbols();
        body.addView(symbols, swapControls
                ? frame(dp(148), dp(148), Gravity.RIGHT | Gravity.BOTTOM,
                        0, 0, dp(198), dp(10))
                : frame(dp(172), dp(172), Gravity.RIGHT | Gravity.TOP,
                        0, dp(8), dp(14), 0));

        LinearLayout systemButtons = horizontal();
        systemButtons.addView(gameButton(systemLabel(9), 9, PAPER), weighted(1.2f));
        systemButtons.addView(gameButton(systemLabel(13), 13, YELLOW), weighted(0.8f));
        systemButtons.addView(gameButton(systemLabel(10), 10, PAPER), weighted(1.2f));
        body.addView(systemButtons, frame(dp(290), dp(52),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, dp(12), 0, 0));

        body.addView(stick("L3", true), swapControls
                ? frame(dp(148), dp(148), Gravity.LEFT | Gravity.TOP,
                        dp(26), dp(8), 0, 0)
                : frame(dp(148), dp(148), Gravity.LEFT | Gravity.BOTTOM,
                        dp(210), 0, 0, dp(10)));
        body.addView(stick("R3", false), swapControls
                ? frame(dp(148), dp(148), Gravity.RIGHT | Gravity.TOP,
                        0, dp(8), dp(26), 0)
                : frame(dp(148), dp(148), Gravity.RIGHT | Gravity.BOTTOM,
                        0, 0, dp(210), dp(10)));

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    void releaseAll() {
        buttons = 0;
        leftX = leftY = rightX = rightY = 0;
        hat = HAT_NEUTRAL;
        if (dpadView != null) dpadView.release();
        dispatch();
    }

    void setButton(int buttonNumber, boolean down) {
        int mask = 1 << (buttonNumber - 1);
        int next = down ? buttons | mask : buttons & ~mask;
        if (next == buttons) return;
        buttons = next;
        dispatch();
    }

    void setConnectionStatus(String value) {
        if (connectionStatus != null) connectionStatus.setText(value);
    }

    private View dPad() {
        dpadView = new DpadView(activity, feedback, next -> {
            hat = next;
            dispatch();
        });
        return dpadView;
    }

    private FrameLayout faceSymbols() {
        FrameLayout pad = new FrameLayout(activity);
        pad.setMotionEventSplittingEnabled(true);
        faceButtons[3] = symbolButton(faceLabel(4), 4, YELLOW);
        faceButtons[2] = symbolButton(faceLabel(3), 3, BLUE);
        faceButtons[1] = symbolButton(faceLabel(2), 2, CORAL);
        faceButtons[0] = symbolButton(faceLabel(1), 1, GREEN);
        pad.addView(faceButtons[3], faceFrame(Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        pad.addView(faceButtons[2], faceFrame(Gravity.LEFT | Gravity.CENTER_VERTICAL));
        pad.addView(faceButtons[1], faceFrame(Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        pad.addView(faceButtons[0], faceFrame(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        return pad;
    }

    private String faceLabel(int buttonNumber) {
        if (labelStyle == 1) return new String[]{"A", "B", "X", "Y"}[buttonNumber - 1];
        if (labelStyle == 2) return Integer.toString(buttonNumber);
        return new String[]{"×", "○", "□", "△"}[buttonNumber - 1];
    }

    private String systemLabel(int buttonNumber) {
        if (labelStyle == 1) {
            if (buttonNumber == 9) return "VIEW";
            if (buttonNumber == 10) return "MENU";
            return "GUIDE";
        }
        if (labelStyle == 2) return Integer.toString(buttonNumber);
        if (buttonNumber == 9) return "SELECT";
        if (buttonNumber == 10) return "START";
        return "PS";
    }

    private Button symbolButton(String label, int buttonNumber, int color) {
        Button button = gameButton(label, buttonNumber, color);
        button.setTextSize(26);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        if (labelStyle == 0) {
            button.setText("");
            GamepadGlyphDrawable glyph = new GamepadGlyphDrawable(buttonNumber, dp(40));
            button.setCompoundDrawables(null, glyph, null, null);
        }
        return button;
    }

    private View stick(String clickLabel, boolean left) {
        FrameLayout base = new FrameLayout(activity);
        base.setBackground(ui.rounded(INK));
        TextView thumb = ui.text("●\n" + clickLabel, 16);
        thumb.setTypeface(Typeface.DEFAULT_BOLD);
        thumb.setTextColor(INK);
        thumb.setGravity(Gravity.CENTER);
        thumb.setBackground(ui.rounded(BLUE));
        base.addView(thumb, new FrameLayout.LayoutParams(dp(82), dp(82), Gravity.CENTER));
        base.setOnTouchListener(new StickListener(left,
                clickLabel.equals("L3") ? 11 : 12, thumb));
        return base;
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
        private final View thumb;
        private float downX;
        private float downY;
        private float travel;
        private long downAt;

        StickListener(boolean left, int clickButton, View thumb) {
            this.left = left;
            this.clickButton = clickButton;
            this.thumb = thumb;
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
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;
                travel = Math.max(travel, Math.abs(deltaX) + Math.abs(deltaY));
                int x = axisDelta(deltaX, view.getWidth());
                int y = axisDelta(deltaY, view.getHeight());
                if (left) {
                    if (x == leftX && y == leftY) return true;
                    leftX = x;
                    leftY = y;
                } else {
                    if (x == rightX && y == rightY) return true;
                    rightX = x;
                    rightY = y;
                }
                float visualRange = dp(24);
                thumb.setTranslationX((x / 127f) * visualRange);
                thumb.setTranslationY((y / 127f) * visualRange);
                dispatch();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (left) leftX = leftY = 0;
                else rightX = rightY = 0;
                thumb.animate().translationX(0).translationY(0).setDuration(90).start();
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

    private int axisDelta(float delta, int size) {
        if (size <= 0) return 0;
        float normalized = delta / (size * 0.34f);
        if (Math.abs(normalized) < 0.08f) return 0;
        return Math.max(-127, Math.min(127, Math.round(normalized * 127f)));
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
