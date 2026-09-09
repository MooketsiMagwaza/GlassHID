package com.nido.a05sinput;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Font-independent PlayStation-style glyphs centered on their face buttons. */
final class GamepadGlyphDrawable extends Drawable {
    private static final int INK = Color.rgb(24, 24, 24);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int shape;
    private final int size;

    GamepadGlyphDrawable(int shape, int size) {
        this.shape = shape;
        this.size = size;
        paint.setColor(INK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3, size / 11f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, size, size);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.30f;
        if (shape == 1) {
            canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, paint);
            canvas.drawLine(cx + radius, cy - radius, cx - radius, cy + radius, paint);
        } else if (shape == 2) {
            canvas.drawCircle(cx, cy, radius, paint);
        } else if (shape == 3) {
            canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint);
        } else {
            Path triangle = new Path();
            triangle.moveTo(cx, cy - radius * 1.12f);
            triangle.lineTo(cx + radius, cy + radius * 0.82f);
            triangle.lineTo(cx - radius, cy + radius * 0.82f);
            triangle.close();
            canvas.drawPath(triangle, paint);
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
}
