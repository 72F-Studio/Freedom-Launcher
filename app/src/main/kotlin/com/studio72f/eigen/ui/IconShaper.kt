package com.studio72f.eigen.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

/**
 * Icon shapes and themed icons. Adaptive icons are redrawn from their layers inside the chosen shape; with
 * [themed] on Android 13+, an icon's monochrome layer is drawn in the accent over a dark plate, the way Android's
 * themed icons work. Legacy icons that can't be reshaped are left as their app drew them.
 */
object IconShaper {
    val SHAPES = listOf("system", "circle", "squircle", "rounded", "square", "teardrop")
    val NAMES = listOf("System", "Circle", "Squircle", "Rounded square", "Square", "Teardrop")
    private const val SIZE = 192

    @JvmStatic
    fun apply(res: Resources, icon: Drawable?, shape: String, themed: Boolean, accent: Int): Drawable? {
        if (icon !is AdaptiveIconDrawable) return icon
        val mono = if (themed && Build.VERSION.SDK_INT >= 33) icon.monochrome else null
        if (shape == "system" && mono == null) return icon
        val out = createBitmap(SIZE, SIZE)
        val canvas = Canvas(out)
        val path = path(if (shape == "system") "squircle" else shape, SIZE.toFloat())
        canvas.clipPath(path)
        // Adaptive layers are 108 units with the middle 72 visible: draw them 1.5× the icon so the safe zone fills it.
        val inset = SIZE / 4
        val bounds = android.graphics.Rect(-inset, -inset, SIZE + inset, SIZE + inset)
        if (mono != null) {
            canvas.drawColor(blend(0xFF15110E.toInt(), accent, 0.16f))
            mono.bounds = bounds
            mono.colorFilter = PorterDuffColorFilter(blend(accent, Color.WHITE, 0.35f), PorterDuff.Mode.SRC_IN)
            mono.draw(canvas)
            mono.colorFilter = null
        } else {
            icon.background?.let { it.bounds = bounds; it.draw(canvas) }
            icon.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        }
        return out.toDrawable(res)
    }

    /** [shape] as a path filling a [size] square. */
    fun path(shape: String, size: Float): Path = Path().apply {
        val r = RectF(0f, 0f, size, size)
        when (shape) {
            "circle" -> addOval(r, Path.Direction.CW)
            "rounded" -> addRoundRect(r, size * 0.24f, size * 0.24f, Path.Direction.CW)
            "square" -> addRoundRect(r, size * 0.08f, size * 0.08f, Path.Direction.CW)
            "teardrop" -> addRoundRect(r, floatArrayOf(size / 2, size / 2, size / 2, size / 2, size * 0.12f, size * 0.12f, size / 2, size / 2), Path.Direction.CW)
            else -> {
                // A superellipse, |x|^4 + |y|^4 = 1: softer than a rounded square, squarer than a circle.
                val half = size / 2
                for (i in 0..96) {
                    val t = i / 96.0 * 2 * Math.PI
                    val c = Math.cos(t); val s = Math.sin(t)
                    val x = half + half * Math.signum(c) * Math.pow(Math.abs(c), 0.5)
                    val y = half + half * Math.signum(s) * Math.pow(Math.abs(s), 0.5)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    /** A shape preview for the picker: the path filled in [color]. */
    fun preview(shape: String, color: Int, size: Int = 96): Bitmap = createBitmap(size, size).also {
        Canvas(it).drawPath(path(if (shape == "system") "squircle" else shape, size.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
}
