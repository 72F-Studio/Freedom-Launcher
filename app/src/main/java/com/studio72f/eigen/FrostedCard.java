package com.studio72f.eigen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;

/**
 * Kelvin-inspired Frosted Surface helper for Eigen.
 *
 * Implements Kelvin's design principles:
 * - Pure squircle surfaces with ZERO stroke outline (no harsh borders)
 * - Stepless glass level (Opaque -> Frosted -> Glass -> Liquid -> Clear)
 * - True-black canvas with dark squircle raised tiles
 */
public final class FrostedCard {
    public static final int COLOR_GROUP = 0xFF1B1C1D;
    public static final int COLOR_RAISED = 0xFF28292B;
    public static final int COLOR_SUNKEN = 0xFF202123;

    public static final float RADIUS_TILE_DP = 18f;
    public static final float RADIUS_CARD_DP = 20f;
    public static final float RADIUS_PILL_DP = 100f;

    // Preserved constants for backward compatibility
    public static final int RATIO_FILL_COLOR = 0x22FFFFFF;
    public static final int RATIO_STROKE_COLOR = 0x00000000;
    public static final float DEFAULT_CORNER_RADIUS_DP = 18f;
    public static final float DEFAULT_STROKE_WIDTH_DP = 0f;

    private static float currentGlassLevel = 0.30f;

    private FrostedCard() {}

    public static void setGlassLevel(float level) {
        currentGlassLevel = Math.max(0f, Math.min(1f, level));
    }

    public static float getGlassLevel() {
        return currentGlassLevel;
    }

    /**
     * Resolves fill color alpha according to Kelvin's stepless glass level.
     * Level 0 (Opaque): 94% coat
     * Level 0.3 (Frosted, default): 70% coat
     * Level 0.55 (Glass): 42% coat
     * Level 0.8 (Liquid): 22% coat
     * Level 1.0 (Clear): 10% coat
     */
    public static int resolveFillColor(int baseRgb) {
        float t = currentGlassLevel;
        float alphaFrac;
        if (t <= 0.3f) {
            alphaFrac = 0.94f - (t / 0.3f) * 0.24f;
        } else if (t <= 0.55f) {
            alphaFrac = 0.70f - ((t - 0.3f) / 0.25f) * 0.28f;
        } else if (t <= 0.8f) {
            alphaFrac = 0.42f - ((t - 0.55f) / 0.25f) * 0.20f;
        } else {
            alphaFrac = 0.22f - ((t - 0.8f) / 0.2f) * 0.12f;
        }

        int alpha = Math.max(20, Math.min(255, Math.round(255 * alphaFrac)));
        return (alpha << 24) | (baseRgb & 0x00FFFFFF);
    }

    public static GradientDrawable create(float cornerRadiusDp, float density) {
        return create(COLOR_RAISED, cornerRadiusDp, density);
    }

    public static GradientDrawable create(float cornerRadiusDp, float strokeWidthDp, float density) {
        return create(COLOR_RAISED, cornerRadiusDp, density);
    }

    public static GradientDrawable create(int fillColor, int strokeColor, float cornerRadiusDp, float strokeWidthDp, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveFillColor(fillColor));
        drawable.setCornerRadius(cornerRadiusDp * density);
        // NO STROKE: Suppressed as per Kelvin guidelines ("No outlines. No drawn hairline goes round glass")
        drawable.setStroke(0, 0);
        return drawable;
    }

    public static GradientDrawable create(int baseColor, float cornerRadiusDp, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(resolveFillColor(baseColor));
        drawable.setCornerRadius(cornerRadiusDp * density);
        drawable.setStroke(0, 0);
        return drawable;
    }

    public static Drawable createInteractive(float cornerRadiusDp, float density) {
        return createInteractive(COLOR_RAISED, cornerRadiusDp, density);
    }

    public static Drawable createInteractive(int baseColor, float cornerRadiusDp, float density) {
        GradientDrawable content = create(baseColor, cornerRadiusDp, density);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(cornerRadiusDp * density);

        ColorStateList rippleColor = ColorStateList.valueOf(0x22FFFFFF);
        return new RippleDrawable(rippleColor, content, mask);
    }

    public static void apply(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(create(COLOR_GROUP, cornerRadiusDp, density));
        view.setClipToOutline(true);
    }

    public static void applyRaised(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(create(COLOR_RAISED, cornerRadiusDp, density));
        view.setClipToOutline(true);
    }

    public static void applySunken(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(create(COLOR_SUNKEN, cornerRadiusDp, density));
        view.setClipToOutline(true);
    }

    public static void applyInteractive(View view, float cornerRadiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.setBackground(createInteractive(COLOR_RAISED, cornerRadiusDp, density));
        view.setClipToOutline(true);
    }
}
