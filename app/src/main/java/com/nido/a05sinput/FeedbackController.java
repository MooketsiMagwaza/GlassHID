package com.nido.a05sinput;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Provides consistent physical feedback even when system touch feedback is disabled. */
final class FeedbackController {
    private final Vibrator vibrator;
    private final Runnable laptopClick;
    private boolean enabled = true;
    private boolean laptopClicksEnabled = true;

    FeedbackController(Context context, Runnable laptopClick) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) context.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        this.laptopClick = laptopClick;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void setLaptopClicksEnabled(boolean enabled) {
        laptopClicksEnabled = enabled;
    }

    void perform(View view, int kind) {
        if (kind == HapticFeedbackConstants.KEYBOARD_TAP && laptopClicksEnabled &&
                laptopClick != null)
            laptopClick.run();
        if (!enabled) return;

        long duration;
        int amplitude;
        if (kind == HapticFeedbackConstants.LONG_PRESS) {
            duration = 32;
            amplitude = 155;
        } else if (kind == HapticFeedbackConstants.CLOCK_TICK) {
            duration = 7;
            amplitude = 75;
        } else {
            duration = 11;
            amplitude = 105;
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            else
                vibrator.vibrate(duration);
        } else {
            view.performHapticFeedback(kind);
        }
    }
}
