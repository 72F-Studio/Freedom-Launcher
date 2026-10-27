package com.studio72f.freedom;

import android.appwidget.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** App widget hosting: long-press aware host views and a swipeable stack for several widgets. */
final class WidgetHosting {
    interface PageListener { void onPage(int page); }

    static final class Host extends AppWidgetHost {
        Host(Context context, int id) { super(context, id); }
        @Override protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) { return new HostedView(context); }
    }

    /** Detects a long press before the widget's own click handlers can swallow it, like stock launchers do. */
    static final class HostedView extends AppWidgetHostView {
        Runnable onLongPress;
        private float downX, downY;
        private boolean fired;
        private final int slop;
        private final Runnable check = () -> {
            fired = true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            // Cancel the widget's own pressed state before showing options.
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            super.dispatchTouchEvent(cancel); cancel.recycle();
            if (onLongPress != null) onLongPress.run();
        };
        HostedView(Context context) { super(context); slop = ViewConfiguration.get(context).getScaledTouchSlop(); }
        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: fired = false; downX = e.getX(); downY = e.getY(); postDelayed(check, ViewConfiguration.getLongPressTimeout()); break;
                case MotionEvent.ACTION_MOVE: if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) removeCallbacks(check); break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: removeCallbacks(check); break;
            }
            return fired;
        }
        @Override public boolean performClick() { return super.performClick(); }
        // Taps belong to the hosted widget's own views; this only tracks long-press and paging.
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (fired) { if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) fired = false; return true; }
            return super.onTouchEvent(e);
        }
        @Override protected void onDetachedFromWindow() { removeCallbacks(check); super.onDetachedFromWindow(); }
    }

    /** Horizontal, page-snapping stack with dots. Created in code only, so no XML constructors. */
    @android.annotation.SuppressLint("ViewConstructor")
    static final class Stack extends LinearLayout {
        private final Pager pager;
        private final LinearLayout pages;
        private final Dots dots;
        private final ArrayList<View> children = new ArrayList<>();
        private PageListener listener;
        private int page;

        Stack(Context context, int active, int inactive) {
            super(context); setOrientation(VERTICAL);
            pager = new Pager(context); pager.setHorizontalScrollBarEnabled(false); pager.setOverScrollMode(OVER_SCROLL_NEVER);
            pages = new LinearLayout(context); pages.setOrientation(HORIZONTAL);
            pager.addView(pages, new HorizontalScrollView.LayoutParams(-2, -1));
            pager.setFillViewport(true);
            addView(pager, new LayoutParams(-1, 0, 1));
            dots = new Dots(context, active, inactive);
            addView(dots, new LayoutParams(-1, Math.round(14 * context.getResources().getDisplayMetrics().density)));
            pager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l == or - ol && b - t == ob - ot) return;
                for (View child : children) { ViewGroup.LayoutParams p = child.getLayoutParams(); p.width = r - l; p.height = b - t; child.setLayoutParams(p); }
                post(() -> pager.scrollTo(page * Math.max(1, pager.getWidth()), 0));
            });
            pager.setOnScrollChangeListener((v, x, y, ox, oy) -> { dots.position = x / (float) Math.max(1, pager.getWidth()); dots.invalidate(); });
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        void add(View widget) { children.add(widget); pages.addView(widget, new LinearLayout.LayoutParams(1, -1)); dots.count = children.size(); dots.setVisibility(children.size() > 1 ? VISIBLE : GONE); }
        void setPage(int value, PageListener l) { page = Math.max(0, Math.min(children.size() - 1, value)); listener = l; dots.position = page; }
        int count() { return children.size(); }

        private final class Pager extends HorizontalScrollView {
            private float downX; private int downScroll;
            Pager(Context c) { super(c); }
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = e.getX(); downScroll = getScrollX(); }
                return children.size() > 1 && super.onInterceptTouchEvent(e);
            }
            // Taps belong to the hosted widget's own views; this only tracks long-press and paging.
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            @Override public boolean onTouchEvent(MotionEvent e) {
                boolean handled = super.onTouchEvent(e);
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    int w = Math.max(1, getWidth()), from = Math.round(downScroll / (float) w), moved = getScrollX() - downScroll;
                    int target = Math.abs(moved) > w * .18f ? from + (moved > 0 ? 1 : -1) : from;
                    page = Math.max(0, Math.min(children.size() - 1, target));
                    smoothScrollTo(page * w, 0);
                    if (listener != null) listener.onPage(page);
                    announceForAccessibility(getContext().getString(R.string.widget_x_of_y, page + 1, children.size()));
                }
                return handled;
            }
            @Override public void fling(int velocity) { /* Snapping replaces free flings. */ }
            @Override public boolean performClick() { return super.performClick(); }
        }
    }

    @android.annotation.SuppressLint("ViewConstructor") // created in code only
    static final class Dots extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int active, inactive;
        int count; float position;
        Dots(Context c, int active, int inactive) { super(c); this.active = active; this.inactive = inactive; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            if (count < 2) return;
            float d = getResources().getDisplayMetrics().density, gap = 10 * d, start = getWidth() / 2f - gap * (count - 1) / 2f, y = getHeight() / 2f + d;
            for (int i = 0; i < count; i++) {
                float near = Math.max(0, 1 - Math.abs(position - i));
                paint.setColor(near > .5f ? active : inactive);
                c.drawCircle(start + gap * i, y, (2 + 1.2f * near) * d, paint);
            }
        }
    }
}
