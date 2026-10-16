package dev.seventwo.launcher;

/**
 * Text legibility over images. Pure math (relative luminance, WCAG contrast) so it is testable off-device.
 * Light text is threatened by the bright spots of a photo, dark text by its dark spots; the decision uses
 * those percentiles after the launcher's dimming, not the photo's average.
 */
final class Legibility {
    static final int OFF = 0, SUBTLE = 1, STRONG = 2;
    static final String[] MODES = {"auto", "subtle", "strong", "off"};
    static final double TARGET = 4.5; // WCAG AA for body text

    private Legibility() { }

    static double contrast(double a, double b) { return (Math.max(a, b) + .05) / (Math.min(a, b) + .05); }

    /** Luminance after dimming toward the theme background (dim 0–1). */
    static double dimmed(double luminance, double background, double dim) { return luminance * (1 - dim) + background * dim; }

    /** Worst-case contrast of text over the image: against its bright spots (light text) or dark spots (dark text). */
    static double worstContrast(double text, double background, double darkSpot, double brightSpot, double dim) {
        double spot = text >= .5 ? dimmed(brightSpot, background, dim) : dimmed(darkSpot, background, dim);
        return contrast(text, spot);
    }

    static int auto(double text, double background, double darkSpot, double brightSpot, double dim) {
        return worstContrast(text, background, darkSpot, brightSpot, dim) >= TARGET ? SUBTLE : STRONG;
    }

    /** Resolves the user's mode. Solid and animated theme backdrops are designed for their text: no treatment. */
    static int resolve(String mode, boolean overImage, int automatic) {
        if (!overImage) return OFF;
        switch (mode) {
            case "off": return OFF;
            case "subtle": return SUBTLE;
            case "strong": return STRONG;
            default: return automatic;
        }
    }

    /** Extra dimming Strong adds on top of the user's setting (0–1). */
    static double extraDim(int level) { return level == STRONG ? .15 : 0; }
}
