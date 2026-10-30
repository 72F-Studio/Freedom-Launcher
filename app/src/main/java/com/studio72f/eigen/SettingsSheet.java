package com.studio72f.eigen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/**
 * Two-stage bottom sheet: it opens at a "peek" height (quick settings) and expands to a full page when
 * dragged up. Pages are rebuilt from builders so theme changes can re-render the open sheet.
 */
final class SettingsSheet extends FrameLayout {
    interface Page { View build(); }
    interface Listener { void onHidden(); }
    static final int HIDDEN = 0, PEEK = 1, FULL = 2;

    private final View scrim;
    private final LinearLayout panel;
    private final View handle;
    private final ScrollView scroller;
    private final ArrayList<Page> pages = new ArrayList<>();
    private final ArrayList<Integer> scrolls = new ArrayList<>();
    private final int slop, minFling;
    private final float density;
    private Listener listener;
    private View peekAnchor;
    private int state = HIDDEN, topInset, bottomInset;
    private float downX, downY, startTranslation;
    private boolean dragging, scrimTouch;
    private VelocityTracker velocity;
    private final Motion.Spring spring;

    SettingsSheet(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop(); minFling = (int) (900 * density);
        scrim = new View(context); scrim.setBackgroundColor(Color.BLACK); scrim.setAlpha(0);
        addView(scrim, new LayoutParams(-1, -1));
        panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
        handle = new View(context);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL; handleParams.setMargins(0, dp(12), 0, dp(10));
        panel.addView(handle, handleParams);
        scroller = new ScrollView(context); scroller.setVerticalScrollBarEnabled(false); scroller.setClipToPadding(false);
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(panel, new LayoutParams(-1, -1));
        spring = new Motion.Spring(Motion.SPATIAL, (y, v) -> { panel.setTranslationY(y); updateScrim(); });
        setVisibility(GONE);
        setOnApplyWindowInsetsListener((v, insets) -> {
            topInset = insets.getSystemWindowInsetTop(); bottomInset = insets.getSystemWindowInsetBottom();
            ((LayoutParams) panel.getLayoutParams()).topMargin = topInset + dp(12);
            scroller.setPadding(0, 0, 0, bottomInset + dp(16));
            return insets;
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setListener(Listener l) { listener = l; }

    void setColors(int surface, int border, int handleColor) {
        GradientDrawable d = new GradientDrawable(); d.setColor(surface); d.setStroke(0, 0);
        float r = dp(28); d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(d);
        GradientDrawable h = new GradientDrawable(); h.setColor(handleColor); h.setCornerRadius(dp(2)); handle.setBackground(h);
    }

    /** The bottom edge of this view (inside the first page) defines the peek height. */
    void setPeekAnchor(View anchor) { peekAnchor = anchor; }

    boolean isShowing() { return state != HIDDEN; }
    int state() { return state; }
    int depth() { return pages.size(); }

    void open(Page root) {
        pages.clear(); scrolls.clear(); pages.add(root); scrolls.add(0);
        setVisibility(VISIBLE); render(0);
        panel.setTranslationY(getHeight() > 0 ? getHeight() : 10000);
        post(() -> settle(state == FULL ? FULL : PEEK));
    }

    void push(Page page) {
        scrolls.set(scrolls.size() - 1, scroller.getScrollY());
        pages.add(page); scrolls.add(0); render(0);
        if (state != FULL) settle(FULL);
    }

    /** Back: pop a page, collapse a full sheet, then hide. */
    boolean back() {
        if (state == HIDDEN) return false;
        if (pages.size() > 1) { pages.remove(pages.size() - 1); scrolls.remove(scrolls.size() - 1); render(scrolls.get(scrolls.size() - 1)); return true; }
        if (state == FULL) { scroller.smoothScrollTo(0, 0); settle(PEEK); }
        else settle(HIDDEN);
        return true;
    }

    void expand() { if (state != HIDDEN) settle(FULL); }

    void dismiss() { if (state != HIDDEN) settle(HIDDEN); }

    /** Re-renders the visible page, keeping the scroll position. */
    void refresh() { if (!pages.isEmpty()) render(scroller.getScrollY()); }

    private void render(int scrollY) {
        if (pages.size() == 1) peekAnchor = null;
        View content = pages.get(pages.size() - 1).build();
        scroller.removeAllViews(); scroller.addView(content, new ScrollView.LayoutParams(-1, -2));
        scroller.post(() -> scroller.scrollTo(0, scrollY));
        if (state == PEEK && pages.size() == 1) post(() -> panel.setTranslationY(position(PEEK)));
    }

    private float position(int target) {
        float height = panel.getHeight();
        if (target == HIDDEN) return height + dp(24);
        if (target == FULL) return 0;
        int peek = dp(320);
        if (peekAnchor != null && peekAnchor.getHeight() > 0) {
            int[] a = new int[2], p = new int[2];
            peekAnchor.getLocationInWindow(a); panel.getLocationInWindow(p);
            peek = a[1] - p[1] + peekAnchor.getHeight() + dp(8) + bottomInset;
        }
        return Math.max(0, height - Math.min(peek, height * .78f));
    }

    private void settle(int target) { settle(target, 0); }
    /** Springs to a resting state, carrying the release velocity (px/s) so a flick feels continuous. */
    private void settle(int target, float releaseVelocity) {
        if (target != HIDDEN && (panel.getHeight() == 0 || peekAnchor != null && peekAnchor.getHeight() == 0)) { post(() -> settle(target, releaseVelocity)); return; }
        float to = position(target);
        if (target == HIDDEN && pages.size() > 1) { pages.subList(1, pages.size()).clear(); scrolls.subList(1, scrolls.size()).clear(); }
        int previous = state; state = target;
        if (target != FULL) scroller.scrollTo(0, 0);
        spring.set(target == HIDDEN ? Motion.SNAPPY : Motion.SPATIAL);
        spring.animate(panel.getTranslationY(), to, releaseVelocity, () -> {
            if (state == HIDDEN) { setVisibility(GONE); pages.clear(); scrolls.clear(); if (listener != null) listener.onHidden(); }
        });
        if (previous != target) Motion.step(this);
        announceForAccessibility(getContext().getString(target == FULL ? R.string.all_settings : target == PEEK ? R.string.quick_settings_label : R.string.settings_closed));
    }

    private void updateScrim() {
        float height = Math.max(1, panel.getHeight());
        scrim.setAlpha(Math.max(0, Math.min(.6f, .6f * (1 - panel.getTranslationY() / height) * 1.6f)));
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); dragging = false;
                scrimTouch = e.getY() < panel.getTop() + panel.getTranslationY();
                track(e, true);
                return scrimTouch;
            case MotionEvent.ACTION_MOVE:
                track(e, false);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    boolean atTop = !scroller.canScrollVertically(-1);
                    if (state != FULL || dy > 0 && atTop) { beginDrag(e); return true; }
                }
                return false;
            default: return false;
        }
    }

    private void beginDrag(MotionEvent e) {
        dragging = true; downY = e.getY(); startTranslation = panel.getTranslationY();
        spring.cancel(); getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e, e.getActionMasked() == MotionEvent.ACTION_DOWN);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                scrimTouch = e.getY() < panel.getTop() + panel.getTranslationY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getY() - downY) > slop) beginDrag(e);
                if (dragging) {
                    float t = Math.max(0, startTranslation + e.getY() - downY);
                    panel.setTranslationY(t); updateScrim();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    velocity.computeCurrentVelocity(1000); float v = velocity.getYVelocity(), t = panel.getTranslationY();
                    int target;
                    if (v < -minFling) target = FULL;
                    else if (v > minFling) target = t > position(PEEK) - dp(8) || state == PEEK ? HIDDEN : PEEK;
                    else {
                        float full = position(FULL), peek = position(PEEK), hidden = position(HIDDEN);
                        target = t < (full + peek) / 2 ? FULL : t < (peek + hidden) / 2 ? PEEK : HIDDEN;
                    }
                    dragging = false; settle(target, v);
                } else if (scrimTouch) { performClick(); dismiss(); }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) { dragging = false; settle(state); }
                return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void track(MotionEvent e, boolean reset) {
        if (velocity == null || reset) { if (velocity != null) velocity.recycle(); velocity = VelocityTracker.obtain(); }
        velocity.addMovement(e);
    }

    private int dp(float v) { return Math.round(v * density); }
}
