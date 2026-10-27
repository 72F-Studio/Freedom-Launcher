package com.studio72f.freedom;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.function.Supplier;

/**
 * Glass over the app-owned backdrop. Foreground content stays sharp.
 *
 * Android 12+: the backdrop Drawable is replayed live into a per-surface RenderNode (no bitmap readback)
 * with a GPU effect chain, following Kyant's AndroidLiquidGlass: vibrancy (saturation) → Gaussian blur →
 * lens refraction (Android 13+, AGSL). Then a surface tint, an additive rim highlight and the chrome.
 * Android 9–11: one shared, diffused bitmap (GlassBackdrop) with vibrancy, tint and rim, without refraction.
 */
final class GlassSurface extends Drawable implements Runnable {
    private final View host, scene;
    private final Supplier<Drawable> source;
    private final GlassBackdrop backdrop;
    private final ThemeEngine theme;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Matrix matrix = new Matrix();
    private final int[] location = new int[2], sceneLocation = new int[2];
    private final GradientDrawable chrome;
    private final Live live;
    private final ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener positionListener;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
    private boolean active = true;
    private Shader rimFallback;
    /** Fully rounded ends (pills and circles), used by the dock. */
    boolean pill;

    GlassSurface(View host, View scene, Supplier<Drawable> source, GlassBackdrop backdrop,
                 ThemeEngine theme, float density) {
        this.host = host; this.scene = scene; this.source = source; this.backdrop = backdrop;
        this.theme = theme; this.density = density;
        chrome = ThemeEffects.glass(theme.background, theme.text, theme.glass, theme.liquid, density);
        live = Build.VERSION.SDK_INT >= 31 ? new Live() : null;
        paint.setColorFilter(new ColorMatrixColorFilter(vibrancy(saturation())));
        // Kyant's bars use a 40% container color; it is what keeps text legible over busy photos.
        boolean dark = Color.luminance(theme.background) < .5;
        tint.setColor(tintColor(theme, dark));
        rim.setStyle(Paint.Style.STROKE);
        rim.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); // Plus blending, as in the reference
        // Moving a lens re-samples a different part of the stationary backdrop.
        observer = scene.getViewTreeObserver();
        positionListener = () -> {
            host.getLocationInWindow(location); scene.getLocationInWindow(sceneLocation);
            int x = location[0] - sceneLocation[0], y = location[1] - sceneLocation[1];
            if (x != lastX || y != lastY) { lastX = x; lastY = y; invalidateSelf(); }
            return true;
        };
        observer.addOnPreDrawListener(positionListener);
    }
    void stop() {
        active = false; unscheduleSelf(this);
        ViewTreeObserver current = observer.isAlive() ? observer : scene.getViewTreeObserver();
        if (current.isAlive()) current.removeOnPreDrawListener(positionListener);
    }

    /** 0 = the theme's own tint; negative darkens toward black, positive lightens toward white. */
    static int tintColor(ThemeEngine theme, boolean dark) {
        float t = theme.glassTint / 100f;
        if (t > 0) return MetroArt.alpha(Color.WHITE, .08f + .62f * t);
        if (t < 0) return MetroArt.alpha(Color.BLACK, .20f + .62f * -t);
        return MetroArt.alpha(theme.background, theme.liquid ? (dark ? .34f : .40f) : .06f); // frosted chrome already tints
    }
    private float saturation() { return theme.liquid ? 1.5f : 1.25f; }
    /** Blur radius (px) of the glass itself, before the scene's own blur is added. */
    private float glassBlur() {
        if (theme.glassBlur >= 0) return theme.glassBlur * density;
        return (theme.liquid ? 2f + 6f * theme.glass / 100f : 4f + 12f * theme.glass / 100f) * density;
    }
    /** The scene's RenderEffect blur (Android 12+), so the glass shows what surrounds it. */
    private float sceneBlur() { return Build.VERSION.SDK_INT >= 31 ? theme.blur * density : 0; }

    private float radius() {
        float side = Math.min(getBounds().width(), getBounds().height());
        if (pill) return side / 2f;
        return theme.liquid ? Math.min(side * .28f, 28 * density) : Math.min(side / 2f, 12 * density);
    }
    @Override public void getOutline(Outline outline) {
        // Faint: an elevation shadow would otherwise show through the translucent glass.
        outline.setRoundRect(getBounds(), radius()); outline.setAlpha(theme.liquid ? .10f : .16f);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rimFallback = new LinearGradient(0, 0, bounds.width(), bounds.height(),
                new int[]{0x80ffffff, 0x14ffffff, 0x08ffffff, 0x40ffffff}, new float[]{0, .32f, .68f, 1}, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds(); if (bounds.isEmpty()) return;
        Drawable drawable = source.get();
        boolean animated = active && drawable instanceof Animatable && ((Animatable) drawable).isRunning();
        host.getLocationInWindow(location); scene.getLocationInWindow(sceneLocation);
        float x = location[0] - sceneLocation[0] + bounds.left, y = location[1] - sceneLocation[1] + bounds.top;
        float radius = radius();
        int w = bounds.width(), h = bounds.height();
        canvas.save(); canvas.translate(bounds.left, bounds.top);
        clip.reset(); clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        boolean drawn = false;
        if (drawable != null && live != null && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()) {
            live.draw(canvas, drawable, x, y, w, h, radius); drawn = true;
        }
        if (!drawn && drawable != null) {
            float diffusion = (float) Math.hypot(glassBlur(), sceneBlur());
            Bitmap image = backdrop.obtain(drawable, scene.getWidth(), scene.getHeight(), Math.max(1, Math.round(diffusion)), animated);
            if (image != null) {
                matrix.setScale(1 / backdrop.scale, 1 / backdrop.scale); matrix.postTranslate(-x, -y);
                canvas.drawBitmap(image, matrix, paint);
            }
        }
        canvas.drawRect(0, 0, w, h, tint);
        drawRim(canvas, w, h, radius);
        canvas.restore();
        if (!theme.liquid) { chrome.setCornerRadius(radius); chrome.setBounds(bounds); chrome.draw(canvas); }
        // Live surfaces follow an animated backdrop every frame; the bitmap fallback resamples at ~6 fps.
        if (animated) scheduleSelf(this, SystemClock.uptimeMillis() + (drawn ? 16 : GlassBackdrop.ANIMATED_INTERVAL));
    }

    /** Thin specular rim: brighter where the edge faces the light (45°), added rather than painted over. */
    private void drawRim(Canvas canvas, int w, int h, float radius) {
        if (!theme.liquid) return;
        float width = .5f * density;
        rim.setStrokeWidth(width * 2); // straddles the edge; the clip keeps the inner half
        if (Build.VERSION.SDK_INT >= 33 && live != null && live.highlight != null) {
            live.highlight.setFloatUniform("size", w, h);
            live.highlight.setFloatUniform("radius", radius);
            rim.setShader(live.highlight);
        } else { rim.setShader(rimFallback); }
        rim.setAlpha(255);
        box.set(0, 0, w, h); canvas.drawRoundRect(box, radius, radius, rim);
    }

    @Override public void run() { if (active) invalidateSelf(); }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Saturation color matrix (luminance weights 0.213/0.715/0.072), as in the reference's vibrancy(). */
    static ColorMatrix vibrancy(float saturation) {
        float inv = 1f - saturation, r = .213f * inv, g = .715f * inv, b = .072f * inv;
        return new ColorMatrix(new float[]{
            r + saturation, g, b, 0, 0,
            r, g + saturation, b, 0, 0,
            r, g, b + saturation, 0, 0,
            0, 0, 0, 1, 0});
    }

    /** Android 12+: live RenderNode replay of the backdrop through a GPU effect chain. */
    @androidx.annotation.RequiresApi(31)
    private final class Live {
        private final RenderNode node = new RenderNode("glass");
        private final RuntimeShader lens = Build.VERSION.SDK_INT >= 33 ? Shaders.lens() : null;
        final RuntimeShader highlight = Build.VERSION.SDK_INT >= 33 ? Shaders.highlight() : null;
        private int effectW = -1, effectH = -1; private float effectRadius = -1;

        void draw(Canvas canvas, Drawable drawable, float x, float y, int w, int h, float radius) {
            float blur = (float) Math.hypot(glassBlur(), sceneBlur());
            int pad = (int) Math.ceil(blur); // blurred edges sample real content, not clamped pixels
            node.setPosition(0, 0, w + 2 * pad, h + 2 * pad);
            RecordingCanvas rc = node.beginRecording(w + 2 * pad, h + 2 * pad);
            rc.translate(pad - x, pad - y);
            drawable.draw(rc);
            node.endRecording();
            if (w != effectW || h != effectH || radius != effectRadius) {
                effectW = w; effectH = h; effectRadius = radius;
                RenderEffect chain = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(vibrancy(saturation())));
                if (blur > 0) chain = RenderEffect.createBlurEffect(blur, blur, chain, Shader.TileMode.CLAMP);
                if (Build.VERSION.SDK_INT >= 33 && lens != null && theme.liquid && theme.refraction > 0) chain = RenderEffect.createChainEffect(lensEffect(w, h, radius, pad), chain);
                node.setRenderEffect(chain);
            }
            canvas.save(); canvas.translate(-pad, -pad);
            canvas.drawRenderNode(node);
            canvas.restore();
        }

        @androidx.annotation.RequiresApi(33)
        private RenderEffect lensEffect(int w, int h, float radius, int pad) {
            float side = Math.min(w, h), r = theme.refraction / 50f; // 1 = reference strength, 2 = double
            // Sized like the reference: 48dp button 12/24dp, 64dp bar 24/24dp; capped for large panels.
            float height = Math.min(24 * density * Math.max(1, r), Math.max(8 * density, side * .3f * Math.max(1, r)));
            height = Math.min(height, side * .5f);
            float amount = Math.min(24 * density, side * .5f) * r;
            lens.setFloatUniform("size", w, h);
            lens.setFloatUniform("offset", -pad, -pad);
            lens.setFloatUniform("radius", radius);
            lens.setFloatUniform("refractionHeight", height);
            lens.setFloatUniform("refractionAmount", -amount);
            // Chromatic dispersion reads seven taps; reserve it for the few larger panels.
            lens.setFloatUniform("chromaticAberration", side >= 56 * density ? 1f : 0f);
            return RenderEffect.createRuntimeShaderEffect(lens, "content");
        }
    }

    /**
     * Shaders adapted from AndroidLiquidGlass (Kyant, Apache-2.0): rounded-rect SDF, circle-mapped edge
     * refraction with seven-tap dispersion, and the angle-shaded rim highlight. Modified for a single
     * corner radius and a runtime dispersion switch. See third-party/android-liquid-glass/LICENSE.
     */
    @androidx.annotation.RequiresApi(33)
    static final class Shaders {
        static final String SDF =
            "float sdRoundedRect(float2 c,float2 h,float r){float2 q=abs(c)-(h-float2(r));" +
            "return length(max(q,0.0))-r+min(max(q.x,q.y),0.0);}" +
            "float2 gradSdRoundedRect(float2 c,float2 h,float r){float2 q=abs(c)-(h-float2(r));" +
            "if(q.x>=0.0||q.y>=0.0)return sign(c)*normalize(max(q,0.0));" +
            "float gx=step(q.y,q.x);return sign(c)*float2(gx,1.0-gx);}";
        static final String LENS =
            "uniform shader content;uniform float2 size;uniform float2 offset;uniform float radius;" +
            "uniform float refractionHeight;uniform float refractionAmount;uniform float chromaticAberration;" + SDF +
            "float circleMap(float x){return 1.0-sqrt(1.0-x*x);}" +
            "half4 main(float2 coord){float2 halfSize=size*0.5;float2 cc=(coord+offset)-halfSize;" +
            "float sd=sdRoundedRect(cc,halfSize,radius);if(-sd>=refractionHeight)return content.eval(coord);" +
            "sd=min(sd,0.0);float d=circleMap(1.0- -sd/refractionHeight)*refractionAmount;" +
            "float gr=min(radius*1.5,min(halfSize.x,halfSize.y));float2 grad=normalize(gradSdRoundedRect(cc,halfSize,gr));" +
            "float2 rc=coord+d*grad;if(chromaticAberration<0.5)return content.eval(rc);" +
            "float k=chromaticAberration*((cc.x*cc.y)/(halfSize.x*halfSize.y));float2 dc=d*grad*k;half4 c=half4(0.0);" +
            "half4 s=content.eval(rc+dc);c.r+=s.r/3.5;c.a+=s.a/7.0;" +
            "s=content.eval(rc+dc*(2.0/3.0));c.r+=s.r/3.5;c.g+=s.g/7.0;c.a+=s.a/7.0;" +
            "s=content.eval(rc+dc*(1.0/3.0));c.r+=s.r/3.5;c.g+=s.g/3.5;c.a+=s.a/7.0;" +
            "s=content.eval(rc);c.g+=s.g/3.5;c.a+=s.a/7.0;" +
            "s=content.eval(rc-dc*(1.0/3.0));c.g+=s.g/3.5;c.b+=s.b/3.0;c.a+=s.a/7.0;" +
            "s=content.eval(rc-dc*(2.0/3.0));c.b+=s.b/3.0;c.a+=s.a/7.0;" +
            "s=content.eval(rc-dc);c.r+=s.r/7.0;c.b+=s.b/3.0;c.a+=s.a/7.0;return c;}";
        static final String HIGHLIGHT =
            "uniform float2 size;uniform float radius;" + SDF +
            "half4 main(float2 coord){float2 halfSize=size*0.5;float2 cc=coord-halfSize;" +
            "float gr=min(radius*1.5,min(halfSize.x,halfSize.y));float2 grad=gradSdRoundedRect(cc,halfSize,gr);" +
            "float2 n=float2(0.7071,0.7071);float i=pow(abs(dot(grad,n)),1.0);" +
            "return half4(0.5,0.5,0.5,0.5)*half(0.25+0.75*i);}";
        static RuntimeShader lens() { return new RuntimeShader(LENS); }
        static RuntimeShader highlight() { return new RuntimeShader(HIGHLIGHT); }
    }
}
