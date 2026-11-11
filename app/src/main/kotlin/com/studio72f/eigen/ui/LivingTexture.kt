package com.studio72f.eigen.ui

import androidx.core.content.edit
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * A texture that follows the day (Plus): cool and bright in the morning, neutral at noon, amber at dusk, deep and
 * dim at night, with its pattern drifting a little each day. Redrawn when home comes back and half an hour has
 * passed; nothing runs in the background.
 */
object LivingTexture {
    private const val PREF = "living_texture"
    private const val LAST = "living_texture_at"
    private const val EVERY = 30 * 60_000L

    fun enable(prefs: SharedPreferences, id: String?) {
        prefs.edit { if (id == null) remove(PREF) else putString(PREF, id); remove(LAST) }
    }

    /** On resume: redraws the living texture when it's due. */
    fun refreshIfDue(activity: Activity, state: EigenState, host: EigenHost, prefs: SharedPreferences) {
        val id = prefs.getString(PREF, null) ?: return
        if (!state.plus || state.wallpaperMode != "photo") return
        if (System.currentTimeMillis() - prefs.getLong(LAST, 0L) < EVERY) return
        val texture = Textures.ALL.firstOrNull { it.id == id } ?: return
        prefs.edit { putLong(LAST, System.currentTimeMillis()) }
        val metrics = activity.resources.displayMetrics
        state.scope.launch {
            try {
                val image = withContext(Dispatchers.Default) { render(texture, metrics.widthPixels, metrics.heightPixels, state.accent, Calendar.getInstance()) }
                host.useLivingWallpaper(image)
            } catch (e: Exception) {
                Problems.show(state, activity, Problems.TEX_RENDER, "texture:living", "Texture didn't update",
                    "Your wallpaper stays as it was. Pick the texture again in Wallpaper → Textures if this keeps happening.",
                    "Drawing the texture for this time of day failed, usually because the phone was short of memory.", e)
            }
        }
    }

    fun render(texture: Textures.Texture, w: Int, h: Int, accent: Int, now: Calendar): Bitmap {
        val seed = 0.37f + (now.get(Calendar.DAY_OF_YEAR) % 40) * 0.011f
        val base = Textures.render(texture, w, h, accent, seed)
        val hour = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f
        val (light, warm) = mood(hour)
        val m = ColorMatrix(floatArrayOf(
            light * (1f + warm), 0f, 0f, 0f, 0f,
            0f, light, 0f, 0f, 0f,
            0f, 0f, light * (1f - warm), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawBitmap(base, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        base.recycle()
        return out
    }

    /** Brightness and warmth through the day: −0.12 cool … +0.18 amber. */
    fun mood(hour: Float): Pair<Float, Float> = when {
        hour < 5f -> 0.55f to 0.05f
        hour < 8f -> lerp(0.55f, 0.95f, (hour - 5f) / 3f) to -0.12f
        hour < 16f -> 1f to lerp(-0.12f, 0f, (hour - 8f) / 8f)
        hour < 20f -> lerp(1f, 0.8f, (hour - 16f) / 4f) to lerp(0f, 0.18f, (hour - 16f) / 4f)
        else -> lerp(0.8f, 0.55f, (hour - 20f) / 4f) to lerp(0.18f, 0.05f, (hour - 20f) / 4f)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
}
