package com.studio72f.eigen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * Eigen's colour: one warm signal colour (Kesar, a saffron / motichoor-ladoo orange) over a dark, wallpaper-led
 * canvas, or a strict Greyscale mode in the spirit of Ratio.
 */
final class EigenPalette {
    /** Kesar: Eigen's accent. Vivid, never darkened toward brown. */
    static final int KESAR = 0xFFFF8A1F;
    /** The greyscale accent: a soft white, so the one signal still reads without colour. */
    static final int GREY_ACCENT = 0xFFECECEC;
    /** Accent choices offered in Appearance, Kesar first. */
    static final String[] ACCENT_NAMES = {"Kesar", "Fire", "Ladoo", "Haldi", "Greyscale"};
    static final int[] ACCENTS = {KESAR, 0xFFFF5A1F, 0xFFF7A21B, 0xFFFFC233, GREY_ACCENT};

    private EigenPalette() {}

    static boolean isGreyscale(int accent) {
        return Math.abs(Color.red(accent) - Color.green(accent)) < 12 && Math.abs(Color.green(accent) - Color.blue(accent)) < 12;
    }

    static int onAccent(int accent) {
        return Color.luminance(accent) > .45 ? 0xFF16110C : Color.WHITE;
    }

    static GradientDrawable rounded(int fill, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /**
     * A frosted pane fill for chrome over the wallpaper. The coat thins as the Glass setting moves toward Clear,
     * so the person's glass level governs every pane. {@code base} is the tint; {@code alpha} the coat at Frosted.
     */
    static GradientDrawable glass(Context context, int base, float alpha, float radiusDp) {
        float level = FrostedCard.getGlassLevel();
        float coat = alpha * (1.6f - level * 1.2f); // 0 → 1.6×, 0.3 → 1.24×, 1 → 0.4×
        GradientDrawable d = rounded(MetroArt.alpha(base, Math.max(.03f, Math.min(.9f, coat))), radiusDp * context.getResources().getDisplayMetrics().density);
        return d;
    }
}
