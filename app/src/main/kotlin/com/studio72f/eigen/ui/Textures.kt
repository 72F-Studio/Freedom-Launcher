package com.studio72f.eigen.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Eigen's own wallpapers: textures drawn by AGSL shaders in the accent's colour and rendered once to an image, so
 * they go through the same path as a photo (dimming, the app drawer's frost, "Set as phone wallpaper"). Android 12
 * and older get a CPU approximation of the same idea.
 */
object Textures {
    data class Texture(val id: String, val name: String, val body: String)

    private const val HEADER = """
uniform float2 res;
uniform half4 accent;
uniform float seed;
float hash(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float noise(float2 p) {
    float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float v = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { v += a * noise(p); p = p * 2.03 + 17.1; a *= 0.5; } return v; }
"""

    /** Each body is the `main` of an AGSL shader; `accent` is the accent colour, `seed` varies the pattern. */
    val ALL = listOf(
        Texture("ember", "Ember", """
half4 main(float2 p) {
    float2 uv = p / res.y + seed;
    float2 q = float2(fbm(uv * 1.8), fbm(uv * 1.8 + 5.2));
    float f = fbm(uv * 1.4 + 2.2 * q);
    float v = smoothstep(0.38, 0.95, f);
    half3 col = mix(half3(0.025, 0.018, 0.012), accent.rgb, v * 0.9);
    col += half3(0.9, 0.85, 0.8) * pow(v, 6.0) * 0.25;
    float vig = 1.0 - 0.6 * length(p / res - float2(0.5, 0.4));
    col *= vig;
    col += (hash(p) - 0.5) * 0.03;
    return half4(col, 1.0);
}"""),
        Texture("silk", "Silk", """
half4 main(float2 p) {
    float2 uv = p / res.y + seed;
    float w = sin(uv.x * 2.6 + uv.y * 1.7 + fbm(uv * 1.3) * 5.0);
    float v = 0.5 + 0.5 * w;
    half3 dark = half3(0.04, 0.04, 0.045);
    half3 light = mix(half3(0.55, 0.55, 0.57), accent.rgb, 0.18);
    half3 col = mix(dark, light, pow(v, 3.0) * 0.7);
    col += (hash(p) - 0.5) * 0.025;
    return half4(col, 1.0);
}"""),
        Texture("contour", "Contour", """
half4 main(float2 p) {
    float2 uv = p / res.y + seed;
    float f = fbm(uv * 1.3);
    float band = abs(fract(f * 14.0) - 0.5);
    float line = 1.0 - smoothstep(0.0, 0.06, band);
    half3 col = half3(0.03, 0.028, 0.026) + accent.rgb * line * 0.45 * (0.4 + f);
    col += (hash(p) - 0.5) * 0.02;
    return half4(col, 1.0);
}"""),
        Texture("dusk", "Dusk", """
half4 main(float2 p) {
    float2 uv = p / res;
    float h = smoothstep(1.05, 0.2, uv.y + (fbm(float2(uv.x * 3.0 + seed, seed)) - 0.5) * 0.18);
    half3 col = mix(half3(0.015, 0.012, 0.012), accent.rgb * 0.95, pow(1.0 - h, 2.2));
    col += half3(1.0, 0.92, 0.85) * pow(1.0 - h, 9.0) * 0.3;
    col += (hash(p) - 0.5) * 0.03;
    return half4(col, 1.0);
}"""),
        Texture("grain", "Grain", """
half4 main(float2 p) {
    float2 uv = p / res;
    float d = length((uv - float2(0.85, 0.95)) * float2(1.0, res.y / res.x * 0.6));
    half3 col = mix(accent.rgb * 0.75, half3(0.02, 0.02, 0.02), smoothstep(0.0, 0.85, d));
    col += (hash(p + seed) - 0.5) * 0.09;
    return half4(col, 1.0);
}"""),
    )

    /** Renders [texture] at [w]×[h] in [accent]. */
    fun render(texture: Texture, w: Int, h: Int, accent: Int, seed: Float = 0.37f): Bitmap =
        if (Build.VERSION.SDK_INT >= 33) gpu(texture, w, h, accent, seed) else cpu(texture, w, h, accent, seed)

    @RequiresApi(33)
    private fun gpu(texture: Texture, w: Int, h: Int, accent: Int, seed: Float): Bitmap {
        val shader = RuntimeShader(HEADER + texture.body)
        shader.setFloatUniform("res", w.toFloat(), h.toFloat())
        shader.setColorUniform("accent", accent)
        shader.setFloatUniform("seed", seed)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawPaint(Paint().apply { this.shader = shader })
        return out
    }

    /** Android 12 and older: the same palette and layout at a low resolution, scaled up smoothly. */
    private fun cpu(texture: Texture, w: Int, h: Int, accent: Int, seed: Float): Bitmap {
        val sw = (w / 6).coerceAtLeast(1); val sh = (h / 6).coerceAtLeast(1)
        val ar = ((accent shr 16) and 255) / 255f; val ag = ((accent shr 8) and 255) / 255f; val ab = (accent and 255) / 255f
        val px = IntArray(sw * sh)
        fun hash(x: Float, y: Float): Float { val v = sin(x * 127.1f + y * 311.7f) * 43758.547f; return v - floor(v) }
        fun noise(x: Float, y: Float): Float {
            val ix = floor(x); val iy = floor(y); val fx = x - ix; val fy = y - iy
            val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
            val a = hash(ix, iy); val b = hash(ix + 1, iy); val c = hash(ix, iy + 1); val d = hash(ix + 1, iy + 1)
            return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
        }
        fun fbm(x: Float, y: Float): Float { var v = 0f; var amp = 0.5f; var px2 = x; var py2 = y; repeat(4) { v += amp * noise(px2, py2); px2 = px2 * 2.03f + 17.1f; py2 = py2 * 2.03f + 17.1f; amp *= 0.5f }; return v }
        for (j in 0 until sh) for (i in 0 until sw) {
            val u = i / sh.toFloat() + seed; val v = j / sh.toFloat() + seed
            val t = when (texture.id) {
                "silk" -> (0.5f + 0.5f * sin(u * 2.6f + v * 1.7f + fbm(u * 1.3f, v * 1.3f) * 5f)).let { it * it * it * 0.5f }
                "contour" -> (1f - (abs((fbm(u * 1.3f, v * 1.3f) * 14f) % 1f - 0.5f) / 0.06f).coerceIn(0f, 1f)) * 0.45f
                "dusk" -> (j / sh.toFloat()).let { it * it } * 0.9f
                "grain" -> (1f - sqrt(((i / sw.toFloat() - 0.85f).let { it * it }) + ((j / sh.toFloat() - 0.95f).let { it * it }))).coerceIn(0f, 1f) * 0.75f
                else -> ((fbm(u * 1.4f, v * 1.4f) - 0.38f) / 0.57f).coerceIn(0f, 1f) * 0.9f
            }
            val base = 0.03f
            val r = (base + (ar - base) * t).coerceIn(0f, 1f); val g = (base + (ag - base) * t).coerceIn(0f, 1f); val b = (base + (ab - base) * t).coerceIn(0f, 1f)
            px[j * sw + i] = (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
        val small = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, w, h, true).also { if (it !== small) small.recycle() }
    }
}
