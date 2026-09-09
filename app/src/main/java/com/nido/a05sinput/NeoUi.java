package com.nido.a05sinput;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

/** Neo-brutalist view factory and interaction styling. */
final class NeoUi {
    private final Activity activity;
    private final FeedbackController feedback;
    private final int ink;

    NeoUi(Activity activity, FeedbackController feedback, int ink) {
        this.activity = activity;
        this.feedback = feedback;
        this.ink = ink;
    }

    Button button(String label, int color) {
        Button result = new Button(activity);
        result.setText(label);
        result.setAllCaps(false);
        result.setTextSize(12);
        result.setTextColor(ink);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setGravity(Gravity.CENTER);
        result.setSingleLine(!label.contains("\n"));
        result.setLineSpacing(0, 0.82f);
        result.setPadding(dp(5), 0, dp(5), dp(4));
        result.setMinHeight(0);
        result.setMinWidth(0);
        result.setBackground(interactiveBackground(color));
        result.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                feedback.perform(view, HapticFeedbackConstants.KEYBOARD_TAP);
                view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(55).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(75).start();
            }
            return false;
        });
        result.setOnHoverListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER)
                view.animate().scaleX(1.035f).scaleY(1.035f).setDuration(80).start();
            else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT)
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
            return false;
        });
        return result;
    }

    RadioButton radio(String label, int id) {
        RadioButton result = new RadioButton(activity);
        result.setText(label);
        result.setId(id);
        result.setTextSize(12);
        result.setTextColor(ink);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        result.setButtonTintList(ColorStateList.valueOf(ink));
        result.setPadding(0, 0, dp(3), 0);
        return result;
    }

    TextView text(String value, int sp) {
        TextView result = new TextView(activity);
        result.setText(value);
        result.setTextSize(sp);
        return result;
    }

    GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(16));
        if (color != android.graphics.Color.TRANSPARENT) shape.setStroke(dp(2), ink);
        return shape;
    }

    LayerDrawable background(int color) {
        return layered(color, 0, 0, 5, 5, 2);
    }

    StateListDrawable interactiveBackground(int color) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                layered(color, 4, 4, 1, 1, 2));
        states.addState(new int[]{android.R.attr.state_hovered},
                layered(color, 0, 0, 7, 7, 3));
        states.addState(new int[]{android.R.attr.state_focused},
                layered(color, 0, 0, 7, 7, 3));
        states.addState(new int[]{}, background(color));
        return states;
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private LayerDrawable layered(int color, int faceLeft, int faceTop,
                                  int faceRight, int faceBottom, int stroke) {
        GradientDrawable shadow = rounded(ink);
        GradientDrawable face = rounded(color);
        face.setStroke(dp(stroke), ink);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, face});
        layers.setLayerInset(0, dp(5), dp(5), 0, 0);
        layers.setLayerInset(1, dp(faceLeft), dp(faceTop), dp(faceRight), dp(faceBottom));
        return layers;
    }
}
