package com.studio72f.freedom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;

/**
 * Ratio-styled Frosted Glass Card helper.
 *
 * Implements Ratio's signature glass geometry:
 * - 0x22FFFFFF (approx 13% opacity white) translucent fill
 * - 0x99FFFFFF (60% opacity white) 1dp hairline stroke rim
 * - 16dp rounded corners
 * - Plus-blended touch feedback ripple
 */
public final class FrostedCard {
    public static final int RATIO_FILL_COLOR = 0x22FFFFFF;
    public static final int RATIO_STROKE_COLOR = 0x99FFFFFF;
    public static final float DEFAULT_CORNER_RADIUS_DP = 16f;
    public static final float DEFAULT_STROKE_WIDTH_DP = 1f;

    private FrostedCard() {}

    /**
     * Create a standard Ratio frosted card drawable with custom corner radius and border width.
     */
    public static GradientDrawable create(float cornerRadiusDp, float strokeWidthDp, float density) {
        return create(RATIO_FILL_COLOR, RATIO_STROKE_COLOR, cornerRadiusDp, strokeWidthDp, density);
    }

    /**
     * Create a frosted card drawable with customized colors.
     */
    public static GradientDrawable create(int fillColor, int strokeColor, float cornerRadiusDp, float strokeWidthDp, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(cornerRadiusDp * density);
        int strokePx = Math.max(1, Math.round(strokeWidthDp * density));
        drawable.setStroke(strokePx, strokeColor);
        return drawable;
    }

    /**
     * Create an interactive Ratio frosted card with ripple touch feedback.
     */
    public static Drawable createInteractive(float cornerRadiusDp, float density) {
        GradientDrawable content = create(cornerRadiusDp, DEFAULT_STROKE_WIDTH_DP, density);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(cornerRadiusDp * density);

        ColorStateList rippleColor = ColorStateList.valueOf(0x33FFFFFF);
        return new RippleDrawable(rippleColor, content, mask);
    }

    /**
     * Apply Ratio frosted styling directly to any Android view.
     */
    public static void apply(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(create(cornerRadiusDp, DEFAULT_STROKE_WIDTH_DP, density));
        view.setClipToOutline(true);
    }

    /**
     * Apply interactive Ratio frosted styling with touch ripple directly to any Android view.
     */
    public static void applyInteractive(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(createInteractive(cornerRadiusDp, density));
        view.setClipToOutline(true);
    }
}
