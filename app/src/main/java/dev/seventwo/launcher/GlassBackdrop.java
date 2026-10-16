package dev.seventwo.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** One scene-sized texture shared by every lens. Moving a lens never recaptures the image. */
final class GlassBackdrop {
    private Drawable previous;
    private Bitmap bitmap;
    private int width, height;
    private long capturedAt;
    float scale;

    Bitmap obtain(Drawable source, int w, int h, float density, boolean animated) {
        if (source == null || w <= 0 || h <= 0) { clear(); return null; }
        long now = SystemClock.uptimeMillis();
        if (bitmap != null && previous == source && width == w && height == h
                && (!animated || now - capturedAt < 80)) return bitmap;
        scale = Math.min(1f, 1280f / Math.max(w, h));
        Bitmap raw = Bitmap.createBitmap(Math.max(1, Math.round(w * scale)),
                Math.max(1, Math.round(h * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(raw);
        canvas.scale(scale, scale);
        source.draw(canvas);
        // A little diffusion before the lens, not a handful of offset taps after it.
        bitmap = ThemeEffects.blur(raw, Math.max(1, Math.round(1.2f * density * scale)), 1280);
        if (bitmap != raw) raw.recycle();
        previous = source; width = w; height = h; capturedAt = now;
        return bitmap;
    }

    void clear() {
        // Let HWUI release any recorded bitmap references before GC frees their storage.
        bitmap = null; previous = null;
    }
}
