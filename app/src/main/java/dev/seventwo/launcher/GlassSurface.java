package dev.seventwo.launcher;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.function.Supplier;

/** A clipped lens over a stationary, shared backdrop. Foreground content stays sharp. */
final class GlassSurface extends Drawable implements Runnable {
    private final View host, scene;
    private final Supplier<Drawable> source;
    private final GlassBackdrop backdrop;
    private final ThemeEngine theme;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final Matrix matrix = new Matrix();
    private final Paint finish = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader faceLight, rimLight;
    private final int[] location = new int[2], sceneLocation = new int[2];
    private final GradientDrawable chrome;
    private final Gpu gpu;
    private final ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener positionListener;
    private int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
    private boolean active = true;

    GlassSurface(View host, View scene, Supplier<Drawable> source, GlassBackdrop backdrop,
                 ThemeEngine theme, float density) {
        this.host = host; this.scene = scene; this.source = source; this.backdrop = backdrop;
        this.theme = theme; this.density = density;
        chrome = ThemeEffects.glass(theme.background, theme.text, theme.glass, theme.liquid, density);
        gpu = Build.VERSION.SDK_INT >= 33 && theme.liquid ? new Gpu() : null;
        // Scrolling/translation can reuse a View display list without calling Drawable.draw.
        // Invalidate only when the lens moves relative to the stationary texture.
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
    /** Fully rounded ends (pills and circles), used by the dock. */
    boolean pill;
    private float radius() {
        float side = Math.min(getBounds().width(), getBounds().height());
        if (pill) return side / 2f;
        return theme.liquid ? Math.min(side * .28f, 28 * density) : Math.min(side / 2f, 12 * density);
    }
    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius()); outline.setAlpha(.24f);
    }
    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        faceLight = new LinearGradient(0, 0, bounds.width()*.7f, bounds.height(),
                new int[]{0x16ffffff, 0x03ffffff, 0x09000000}, new float[]{0, .42f, 1}, Shader.TileMode.CLAMP);
        rimLight = new LinearGradient(0, 0, bounds.width(), bounds.height(),
                new int[]{0x88ffffff, 0x16ffffff, 0x08ffffff, 0x48ffffff},
                new float[]{0, .32f, .68f, 1}, Shader.TileMode.CLAMP);
    }
    private void drawFinish(Canvas canvas, int w, int h, float radius) {
        // Fine directional reflections, not a uniform outline around the material.
        finish.setStyle(Paint.Style.FILL); finish.setShader(faceLight);
        canvas.drawRoundRect(0, 0, w, h, radius, radius, finish);
        float inset = .5f*density;
        finish.setStyle(Paint.Style.STROKE); finish.setStrokeWidth(.65f*density);
        finish.setShader(rimLight);
        canvas.drawRoundRect(inset, inset, w-inset, h-inset,
                Math.max(0,radius-inset), Math.max(0,radius-inset), finish);
        finish.setShader(null); finish.setColor(0x10000000); finish.setStrokeWidth(.5f*density);
        inset=1.3f*density;
        canvas.drawRoundRect(inset, inset, w-inset, h-inset,
                Math.max(0,radius-inset), Math.max(0,radius-inset), finish);
        finish.setStyle(Paint.Style.FILL);
    }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds(); if (bounds.isEmpty()) return;
        Drawable drawable = source.get();
        boolean animated = active && drawable instanceof Animatable && ((Animatable) drawable).isRunning();
        Bitmap image = backdrop.obtain(drawable, scene.getWidth(), scene.getHeight(), diffusion(), animated);
        host.getLocationInWindow(location); scene.getLocationInWindow(sceneLocation);
        float x = location[0] - sceneLocation[0] + bounds.left;
        float y = location[1] - sceneLocation[1] + bounds.top;
        float radius = radius();
        canvas.save(); canvas.translate(bounds.left, bounds.top);
        clip.reset(); clip.addRoundRect(0, 0, bounds.width(), bounds.height(), radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        if (image != null) {
            if (gpu != null && canvas.isHardwareAccelerated()) {
                gpu.draw(canvas, image, backdrop.scale, bounds.width(), bounds.height(), radius,
                        theme.glass / 100f, density, x, y);
            } else {
                // Android 9–12 (and software canvases): the same anchored, diffused texture without
                // refraction. A moving bitmap mesh stretched pixels and opened seams at the corners.
                matrix.setScale(1 / backdrop.scale, 1 / backdrop.scale);
                matrix.postTranslate(-x, -y);
                canvas.drawBitmap(image, matrix, paint);
            }
        }
        if (theme.liquid) drawFinish(canvas, bounds.width(), bounds.height(), radius);
        canvas.restore();
        // Chrome follows the lens shape (pills, circles); a fixed radius would be clipped at the ends.
        if (!theme.liquid) { chrome.setCornerRadius(radius); chrome.setBounds(bounds); chrome.draw(canvas); }
        if (animated) scheduleSelf(this, SystemClock.uptimeMillis() + GlassBackdrop.ANIMATED_INTERVAL);
    }
    /**
     * Blur radius in scene pixels. Liquid glass is a clear lens with a hint of diffusion; subtle/frosted
     * glass frosts with strength. Both add the scene's own blur so the lens shows what surrounds it:
     * on Android 12+ that blur is a RenderEffect on the backdrop view, which the captured Drawable lacks
     * (older versions already draw a pre-blurred wallpaper, so nothing is added there).
     */
    private int diffusion() {
        float frost = (theme.liquid ? 1.2f : 4f + 12f * theme.glass / 100f) * density;
        float scene = Build.VERSION.SDK_INT >= 31 ? .58f * theme.blur * density : 0;
        return Math.max(1, Math.round((float) Math.hypot(frost, scene)));
    }
    @Override public void run() { if (active) invalidateSelf(); }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @android.annotation.TargetApi(33)
    static final class Gpu {
        /* Lens math adapted from AndroidLiquidGlass, Copyright 2025 Kyant, Apache-2.0.
         * Modified for a shared BitmapShader, uniform corners and subtle edge lighting.
         * See third-party/android-liquid-glass/LICENSE and NOTICE.md. */
        static final String CODE =
            "uniform shader scene; uniform float2 size; uniform float radius; " +
            "uniform float height; uniform float amount; uniform float density; " +
            "float sdf(float2 p,float r){float2 q=abs(p)-(size*.5-r);" +
            "return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;} " +
            "float2 normal(float2 p,float r){float2 q=abs(p)-(size*.5-r);" +
            "float2 v=max(q,0.0);float len=length(v);" +
            "if(len>0.0001)return sign(p)*v/len;" +
            "return sign(p)*(q.x>q.y?float2(1,0):float2(0,1));} " +
            "half4 main(float2 p){float2 centered=p-size*.5;float sd=sdf(centered,radius);" +
            "float t=clamp(1.0+sd/height,0.0,1.0);" +
            "float d=(1.0-sqrt(max(0.0,1.0-t*t)))*(-amount);" +
            "float2 n=normal(centered,min(radius*1.5,min(size.x,size.y)*.5));" +
            "float2 uv=p+d*n;half4 c=scene.eval(uv);" +
            "float dispersion=t*t*.22*density;" +
            "c.r=scene.eval(uv+n*dispersion).r;c.b=scene.eval(uv-n*dispersion).b;" +
            "float rim=1.0-smoothstep(0.0,1.2*density,-sd);" +
            "float light=dot(n,normalize(float2(-.6,-1.0)));" +
            "c.rgb*=half(1.0-rim*max(-light,0.0)*.12);" +
            "c.rgb+=half3(rim*max(light,0.0)*.12)*c.a;" +
            "return half4(min(c.rgb,half3(c.a)),c.a);}";
        private final RuntimeShader shader = new RuntimeShader(CODE);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private Bitmap previous;
        private BitmapShader texture;
        void draw(Canvas canvas, Bitmap image, float scale, int w, int h, float radius,
                  float strength, float density, float x, float y) {
            if (previous != image) {
                texture = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                previous = image;
            }
            matrix.setScale(1 / scale, 1 / scale); matrix.postTranslate(-x, -y);
            texture.setLocalMatrix(matrix); shader.setInputShader("scene", texture);
            shader.setFloatUniform("size", w, h); shader.setFloatUniform("radius", radius);
            shader.setFloatUniform("height", Math.max(1, Math.min(18 * density, Math.min(w,h) * .45f)));
            shader.setFloatUniform("amount", Math.min((6 + 20 * strength) * density, Math.min(w,h) * .18f));
            shader.setFloatUniform("density", density);
            paint.setShader(shader); canvas.drawRect(0, 0, w, h, paint);
        }
    }
}
