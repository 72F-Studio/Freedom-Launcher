package com.studio72f.eigen;

import android.animation.ValueAnimator;
import android.os.Build;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * 72F's motion system: physical springs that carry the finger's velocity, a few shared presets, and graded
 * haptics. Everything respects Android's "remove animations" setting and the theme's motion switch.
 */
final class Motion {
    /** Spring presets (stiffness, damping ratio). Lower damping overshoots; higher stiffness settles faster. */
    static final float[] SPATIAL = {420f, .86f};          // pages, sheets, lists: settles with the faintest overshoot
    static final float[] SPATIAL_EXPRESSIVE = {360f, .72f}; // Material 3 Expressive: a visible, friendly bounce
    static final float[] SNAPPY = {900f, .88f};           // indicators, small controls
    static final float[] PRESS = {1500f, .62f};           // press-down and release on touch targets
    static final float[] EFFECT = {700f, 1f};             // opacity and color: no overshoot
    static final float[] RUBBER = {420f, .58f};           // a stretched control returning to rest: lively bounce
    static final float[] SQUASH = {650f, .60f};           // squash-and-stretch scale on press
    static final float[] WAVE = {700f, .86f};             // alphabet rail wave and bubble
    // Kelvin springs (stiffness, damping ratio), named as in the Kelvin guidelines.
    static final float[] K_SNAPPY = {700f, .82f};         // press, lift, switch knob
    static final float[] K_SPATIAL = {420f, .76f};        // things that move: drawers, sheets, pages
    static final float[] K_GENTLE = {260f, .90f};         // things that change: size, colour
    static final float[] K_WOBBLE = {400f, .55f};         // a soft control settling after release

    private static boolean enabled = true;
    /** Called when the theme changes: theme.motionMs == 0 turns motion off. */
    static void setEnabled(boolean on) { enabled = on; }
    static boolean on() { return enabled && ValueAnimator.areAnimatorsEnabled(); }

    interface Update { void at(float value, float velocity); }

    /**
     * Damped harmonic oscillator stepped on the display's frame clock. Retargeting a running spring keeps its
     * position and velocity, so interrupted motion never jumps.
     */
    static final class Spring implements Choreographer.FrameCallback {
        private final Update update;
        private float stiffness, damping, value, velocity, target, precision = .5f;
        private long last;
        private boolean running;
        private Runnable end;

        Spring(float[] preset, Update update) { this.update = update; set(preset); }
        Spring set(float[] preset) { stiffness = preset[0]; damping = preset[1]; return this; }
        /** Rest threshold in the spring's own units: .5 for pixels (the default), about .002 for 0–1 values. */
        Spring precision(float eps) { precision = eps; return this; }
        float value() { return value; }
        float target() { return target; }
        float velocity() { return velocity; }
        boolean running() { return running; }

        /** Jumps without animating. */
        void snap(float to) { cancel(); value = target = to; velocity = 0; update.at(value, 0); }

        void animate(float from, float to, float startVelocity, Runnable onEnd) {
            value = from; animateTo(to, startVelocity, onEnd);
        }
        void animateTo(float to, float startVelocity, Runnable onEnd) {
            target = to; velocity = startVelocity; end = onEnd;
            if (!on()) { snap(to); finish(); return; }
            if (!running) { running = true; last = 0; Choreographer.getInstance().postFrameCallback(this); }
        }
        /** Retargets while keeping the current velocity (interruptible motion). */
        void retarget(float to, Runnable onEnd) { animateTo(to, velocity, onEnd); }

        void cancel() { if (running) { running = false; Choreographer.getInstance().removeFrameCallback(this); } }

        @Override public void doFrame(long now) {
            if (!running) return;
            float dt = last == 0 ? 1 / 120f : Math.min(.032f, (now - last) / 1e9f);
            last = now;
            // Semi-implicit Euler in small substeps: stable for stiff springs at any frame rate.
            float omega = (float) Math.sqrt(stiffness), c = 2 * damping * omega;
            for (int i = 0; i < 4; i++) {
                float h = dt / 4, accel = -stiffness * (value - target) - c * velocity;
                velocity += accel * h; value += velocity * h;
            }
            boolean resting = Math.abs(value - target) < precision && Math.abs(velocity) < precision * 20;
            if (resting) { value = target; velocity = 0; update.at(value, 0); running = false; finish(); return; }
            update.at(value, velocity);
            Choreographer.getInstance().postFrameCallback(this);
        }
        private void finish() { Runnable r = end; end = null; if (r != null) r.run(); }
    }

    /** Convenience: animate a view property with a fresh spring. */
    static Spring translationX(View v, float[] preset) { return new Spring(preset, (x, s) -> v.setTranslationX(x)); }
    static Spring translationY(View v, float[] preset) { return new Spring(preset, (y, s) -> v.setTranslationY(y)); }

    /**
     * Kelvin press: the view squashes like warm wax while held (a little wider and lower) and springs back with a
     * small wobble on release. Leaves the view's click handling alone.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    static void springPress(View v) {
        Spring spring = new Spring(K_SNAPPY, (x, s) -> { v.setScaleX(1 + (1 - x) * .35f); v.setScaleY(x); }).precision(.001f);
        spring.snap(1f);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN: spring.set(K_SNAPPY).retarget(.94f, null); tick(view); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: spring.set(K_WOBBLE).retarget(1f, null); break;
            }
            return false;
        });
    }

    /** Rises a view into place (fade + lift) on the spatial spring, staggered by its index. */
    static void rise(View v, int index, float fromPx) {
        if (!on()) { v.setAlpha(1); v.setTranslationY(0); return; }
        v.setAlpha(0); v.setTranslationY(fromPx);
        v.postDelayed(() -> new Spring(K_SPATIAL, (y, s) -> { v.setTranslationY(y); v.setAlpha(Math.max(0, Math.min(1, 1 - y / fromPx))); })
                .animate(fromPx, 0, 0, null), index * 24L);
    }

    // Haptics, from lightest to strongest. Newer Android has more precise constants.
    static void tick(View v) { v.performHapticFeedback(Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : HapticFeedbackConstants.CLOCK_TICK); }
    static void step(View v) { v.performHapticFeedback(Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_TICK : HapticFeedbackConstants.CLOCK_TICK); }
    static void threshold(View v) { v.performHapticFeedback(Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE : HapticFeedbackConstants.CONTEXT_CLICK); }
    static void confirm(View v) { v.performHapticFeedback(Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY); }
    static void heavy(View v) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); }

    /** Follow-the-finger with resistance: moves less the further it is pulled (0.42 gain, 72dp softness). */
    static float follow(float displacement, float distance, float softness) { return displacement * .42f / (1 + distance / softness); }

    /** Rubber-band resistance past an edge: the further you pull, the less it moves. */
    static float rubber(float overscroll, float limit) {
        float sign = Math.signum(overscroll), x = Math.abs(overscroll);
        return sign * limit * (1 - (float) (1 / (x * .55f / limit + 1)));
    }
}
