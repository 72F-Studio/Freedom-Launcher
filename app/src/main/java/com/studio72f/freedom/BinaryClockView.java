package com.studio72f.freedom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import java.util.Calendar;

/**
 * Minimaa-inspired Binary Dot Clock.
 *
 * Visualizes time using binary-coded decimal (BCD) dot matrix columns:
 * - Col 0: Hours tens  (2 bits: 1, 2)
 * - Col 1: Hours units (4 bits: 1, 2, 4, 8)
 * - Col 2: Minutes tens (3 bits: 1, 2, 4)
 * - Col 3: Minutes units (4 bits: 1, 2, 4, 8)
 *
 * Active bits are drawn as solid glowing circles.
 * Inactive bits are drawn as dim translucent outlines/dots.
 */
public final class BinaryClockView extends View {
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dotBounds = new RectF();

    private int activeColor = Color.WHITE;
    private int inactiveColor = 0x33FFFFFF;
    private int glowColor = 0x40FFFFFF;
    private boolean showLabels = true;
    private boolean is24Hour = true;

    // Bit capacities per column in BCD:
    // H1 (0-2) -> 2 bits; H2 (0-9) -> 4 bits; M1 (0-5) -> 3 bits; M2 (0-9) -> 4 bits
    private static final int[] BITS_PER_COL = {2, 4, 3, 4};
    private final int[] colValues = new int[4];

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateTime();
            invalidate();
            long now = System.currentTimeMillis();
            // Align to next minute boundary or every 5 seconds
            long delay = Math.min(5000, 60000 - (now % 60000) + 50);
            mainHandler.postDelayed(this, delay);
        }
    };

    public BinaryClockView(Context context) {
        super(context);
        init();
    }

    public BinaryClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        activePaint.setStyle(Paint.Style.FILL);
        activePaint.setColor(activeColor);

        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setColor(inactiveColor);
        inactivePaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(glowColor);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0x88FFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(11f * getResources().getDisplayMetrics().density);

        is24Hour = DateFormat.is24HourFormat(getContext());
        updateTime();
    }

    public void setColors(int active, int inactive, int accent) {
        this.activeColor = active;
        this.inactiveColor = MetroArt.alpha(active, 0.22f);
        this.glowColor = MetroArt.alpha(accent != 0 ? accent : active, 0.35f);
        activePaint.setColor(activeColor);
        inactivePaint.setColor(inactiveColor);
        glowPaint.setColor(glowColor);
        textPaint.setColor(MetroArt.alpha(active, 0.55f));
        invalidate();
    }

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        invalidate();
    }

    private void updateTime() {
        Calendar cal = Calendar.getInstance();
        int hour = is24Hour ? cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR);
        if (!is24Hour && hour == 0) hour = 12;
        int minute = cal.get(Calendar.MINUTE);

        colValues[0] = hour / 10;
        colValues[1] = hour % 10;
        colValues[2] = minute / 10;
        colValues[3] = minute % 10;

        setContentDescription(String.format(java.util.Locale.getDefault(), "%02d:%02d binary clock", hour, minute));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.post(tickRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        mainHandler.removeCallbacks(tickRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int minW = Math.round(180 * density);
        int minH = Math.round((showLabels ? 130 : 100) * density);

        int w = resolveSize(minW, widthMeasureSpec);
        int h = resolveSize(minH, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float labelHeight = showLabels ? (20f * density) : 0f;
        float usableHeight = h - labelHeight - (16f * density);
        float topPadding = 8f * density;

        // 4 columns, with a wider gap between hours (col 1) and minutes (col 2)
        int cols = 4;
        int maxRows = 4; // bit 8, 4, 2, 1 (row 0 at bottom = bit 1, row 3 at top = bit 8)

        float dotRadius = Math.min(usableHeight / (maxRows * 2.6f), (w / 10f));
        dotRadius = Math.max(dotRadius, 6f * density);

        float rowSpacing = (usableHeight - (maxRows * 2 * dotRadius)) / (maxRows - 1);
        float colWidth = w / 5.2f;
        float middleGap = colWidth * 0.45f; // Gap between HH and MM
        float totalWidth = (4 * colWidth) + middleGap;
        float startX = (w - totalWidth) / 2f + (colWidth / 2f);

        for (int c = 0; c < cols; c++) {
            float cx = startX + (c * colWidth) + (c >= 2 ? middleGap : 0);
            int val = colValues[c];
            int bits = BITS_PER_COL[c];

            // Draw dots from bottom (bit 0 = 1) to top (bit 3 = 8)
            for (int r = 0; r < maxRows; r++) {
                int bitValue = 1 << r;
                float cy = topPadding + usableHeight - dotRadius - (r * (2 * dotRadius + rowSpacing));

                if (r < bits) {
                    boolean isActive = (val & bitValue) != 0;
                    if (isActive) {
                        // Subtle glow halo
                        canvas.drawCircle(cx, cy, dotRadius * 1.35f, glowPaint);
                        // Solid active dot
                        canvas.drawCircle(cx, cy, dotRadius, activePaint);
                    } else {
                        // Dim inactive dot ring
                        canvas.drawCircle(cx, cy, dotRadius * 0.75f, inactivePaint);
                    }
                }
            }

            // Optional digit label below column
            if (showLabels) {
                float textY = h - (6f * density);
                canvas.drawText(String.valueOf(val), cx, textY, textPaint);
            }
        }
    }
}
