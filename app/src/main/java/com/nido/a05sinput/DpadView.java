package com.nido.a05sinput;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Classic four-arm D-pad with live cardinal and diagonal feedback. */
final class DpadView extends View {
    interface Listener {
        void onHatChanged(int hat);
    }

    static final int HAT_NEUTRAL = 8;
    private static final int INK = Color.rgb(24, 24, 24);
    private static final int PAPER = Color.rgb(247, 243, 234);
    private static final int YELLOW = Color.rgb(250, 204, 80);

    private final FeedbackController feedback;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path directionMark = new Path();
    private int hat = HAT_NEUTRAL;

    DpadView(Context context, FeedbackController feedback, Listener listener) {
        super(context);
        this.feedback = feedback;
        this.listener = listener;
        setContentDescription("Directional pad");
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean[] active = activeDirections();
        float width = getWidth();
        float height = getHeight();
        float cx = width / 2f;
        float cy = height / 2f;
        float armHalf = Math.min(width, height) * 0.17f;
        float edge = dp(5);
        float radius = dp(10);
        float centerHalf = dp(15);
        RectF vertical = new RectF(cx - armHalf, edge, cx + armHalf, height - edge);
        RectF horizontal = new RectF(edge, cy - armHalf, width - edge, cy + armHalf);

        paint.setStrokeWidth(dp(3));
        paint.setStrokeJoin(Paint.Join.ROUND);
        drawArm(canvas, vertical, radius, false);
        drawArm(canvas, horizontal, radius, false);
        if (active[0]) drawClippedArm(canvas, vertical, radius,
                new RectF(0, 0, width, cy - centerHalf));
        if (active[1]) drawClippedArm(canvas, horizontal, radius,
                new RectF(cx + centerHalf, 0, width, height));
        if (active[2]) drawClippedArm(canvas, vertical, radius,
                new RectF(0, cy + centerHalf, width, height));
        if (active[3]) drawClippedArm(canvas, horizontal, radius,
                new RectF(0, 0, cx - centerHalf, height));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        canvas.drawRoundRect(new RectF(cx - centerHalf, cy - centerHalf,
                cx + centerHalf, cy + centerHalf), dp(5), dp(5), paint);

        float markOffset = Math.min(width, height) * 0.29f;
        drawDirectionMark(canvas, cx, cy - markOffset, 0, active[0]);
        drawDirectionMark(canvas, cx + markOffset, cy, 90, active[1]);
        drawDirectionMark(canvas, cx, cy + markOffset, 180, active[2]);
        drawDirectionMark(canvas, cx - markOffset, cy, 270, active[3]);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            feedback.perform(this, HapticFeedbackConstants.VIRTUAL_KEY);
            animate().scaleX(0.98f).scaleY(0.98f).setDuration(45).start();
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - getWidth() / 2f;
            float dy = event.getY() - getHeight() / 2f;
            float deadZone = Math.min(getWidth(), getHeight()) * 0.10f;
            setHat(Math.hypot(dx, dy) < deadZone ? HAT_NEUTRAL : hatFor(dx, dy), true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            setHat(HAT_NEUTRAL, false);
            animate().scaleX(1f).scaleY(1f).setDuration(70).start();
            if (action == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    void release() {
        setHat(HAT_NEUTRAL, false);
        setScaleX(1f);
        setScaleY(1f);
    }

    private void setHat(int next, boolean hapticTick) {
        if (next == hat) return;
        hat = next;
        invalidate();
        if (hapticTick) feedback.perform(this, HapticFeedbackConstants.CLOCK_TICK);
        listener.onHatChanged(hat);
    }

    private boolean[] activeDirections() {
        return new boolean[]{
                hat == 0 || hat == 1 || hat == 7,
                hat == 1 || hat == 2 || hat == 3,
                hat == 3 || hat == 4 || hat == 5,
                hat == 5 || hat == 6 || hat == 7
        };
    }

    private void drawArm(Canvas canvas, RectF bounds, float radius, boolean active) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? YELLOW : INK);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(INK);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }

    private void drawClippedArm(Canvas canvas, RectF bounds, float radius, RectF clip) {
        canvas.save();
        canvas.clipRect(clip);
        drawArm(canvas, bounds, radius, true);
        canvas.restore();
    }

    private void drawDirectionMark(Canvas canvas, float cx, float cy,
                                   float rotation, boolean active) {
        float size = dp(7);
        directionMark.reset();
        directionMark.moveTo(cx, cy - size);
        directionMark.lineTo(cx + size, cy + size);
        directionMark.lineTo(cx - size, cy + size);
        directionMark.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? INK : PAPER);
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawPath(directionMark, paint);
        canvas.restore();
    }

    private int hatFor(float x, float y) {
        double degrees = Math.toDegrees(Math.atan2(y, x));
        if (degrees < 0) degrees += 360;
        int sector = ((int) Math.round(degrees / 45.0)) & 7;
        return new int[]{2, 3, 4, 5, 6, 7, 0, 1}[sector];
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
