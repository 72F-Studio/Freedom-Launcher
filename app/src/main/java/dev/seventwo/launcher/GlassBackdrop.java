package dev.seventwo.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * One scene-sized texture shared by every lens. Moving a lens never recaptures the image.
 * Animated backdrops recapture at about 6 fps into two alternating, reused bitmaps, so the
 * render thread never samples a bitmap that is being rewritten and nothing is allocated per frame.
 */
final class GlassBackdrop {
    static final long ANIMATED_INTERVAL = 160;
    private Drawable previous;
    private final Bitmap[] buffers = new Bitmap[2];
    private int[] pixels, scratch;
    private Bitmap bitmap;
    private int width, height, diffusion, next;
    private long capturedAt;
    float scale;

    /** @param diffusion blur radius in scene pixels (frost plus the scene's own blur). */
    Bitmap obtain(Drawable source, int w, int h, int diffusion, boolean animated) {
        if (source == null || w <= 0 || h <= 0) { clear(); return null; }
        long now = SystemClock.uptimeMillis();
        if (bitmap != null && previous == source && width == w && height == h && this.diffusion == diffusion
                && (!animated || now - capturedAt < ANIMATED_INTERVAL)) return bitmap;
        // Blurred content needs little resolution; moving content needs less still.
        scale = Math.min(1f, (animated ? 720f : 1280f) / Math.max(w, h));
        int bw = Math.max(1, Math.round(w * scale)), bh = Math.max(1, Math.round(h * scale));
        Bitmap target = buffers[next];
        if (target == null || target.getWidth() != bw || target.getHeight() != bh) {
            target = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); buffers[next] = target;
            pixels = null;
        } else target.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        source.draw(canvas);
        if (pixels == null || pixels.length != bw * bh) { pixels = new int[bw * bh]; scratch = new int[bw * bh]; }
        ThemeEffects.blurInPlace(target, Math.max(1, Math.round(diffusion * scale)), pixels, scratch);
        bitmap = target; next = 1 - next;
        previous = source; width = w; height = h; this.diffusion = diffusion; capturedAt = now;
        return bitmap;
    }

    void clear() {
        // Let HWUI release any recorded bitmap references before GC frees their storage.
        bitmap = null; previous = null; buffers[0] = buffers[1] = null; pixels = scratch = null;
    }
}
