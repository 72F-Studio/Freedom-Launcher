package studio72f.kelvin

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

@Composable
internal actual fun kelvinString(string: KelvinString): String = stringResource(
    when (string) {
        KelvinString.Back -> R.string.kelvin_back
        KelvinString.Selected -> R.string.kelvin_selected
        KelvinString.Chosen -> R.string.kelvin_chosen
        KelvinString.SelectMessage -> R.string.kelvin_select_message
        KelvinString.CurrentAccount -> R.string.kelvin_current_account
        KelvinString.CurrentPlan -> R.string.kelvin_current_plan
        KelvinString.ImagesHidden -> R.string.kelvin_images_hidden
        KelvinString.ShowImages -> R.string.kelvin_show_images
        KelvinString.ErrorViewMore -> R.string.kelvin_error_view_more
        KelvinString.ErrorCode -> R.string.kelvin_error_code
        KelvinString.ErrorWhy -> R.string.kelvin_error_why
        KelvinString.ErrorDetail -> R.string.kelvin_error_detail
        KelvinString.ErrorLogPreview -> R.string.kelvin_error_log_preview
        KelvinString.ErrorEmailLogs -> R.string.kelvin_error_email_logs
        KelvinString.TextureLiquid -> R.string.kelvin_texture_liquid
        KelvinString.TextureLiquidSummary -> R.string.kelvin_texture_liquid_summary
        KelvinString.TextureFrost -> R.string.kelvin_texture_frost
        KelvinString.TextureFrostSummary -> R.string.kelvin_texture_frost_summary
        KelvinString.PlayerPlay -> R.string.kelvin_player_play
        KelvinString.PlayerPause -> R.string.kelvin_player_pause
        KelvinString.PlayerBack -> R.string.kelvin_player_back
        KelvinString.PlayerForward -> R.string.kelvin_player_forward
        KelvinString.PlayerPosition -> R.string.kelvin_player_position
        KelvinString.PlayerQueue -> R.string.kelvin_player_queue
        KelvinString.PageOf -> R.string.kelvin_page_of
    },
)

/**
 * Both faces are variable on weight (Android 8 and later); before that the nearest bundled static weight stands in.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun family(variable: Int, statics: Map<Int, Int>): FontFamily =
    if (Build.VERSION.SDK_INT >= 26) {
        FontFamily(
            (100..900 step 100).map { w ->
                Font(variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            },
        )
    } else {
        FontFamily(statics.map { (w, res) -> Font(res, FontWeight(w)) })
    }

private val display: FontFamily = family(
    R.font.onest_variable,
    mapOf(300 to R.font.onest_300, 400 to R.font.onest_400, 500 to R.font.onest_500, 600 to R.font.onest_600, 700 to R.font.onest_700),
)

private val text: FontFamily = family(
    R.font.montserrat_variable,
    mapOf(400 to R.font.montserrat_400, 500 to R.font.montserrat_500, 600 to R.font.montserrat_600, 700 to R.font.montserrat_700),
)

internal actual fun kelvinFamily(face: KelvinFace): FontFamily = if (face == KelvinFace.Display) display else text

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal actual fun kelvinDisplayAt(weight: Int): FontFamily =
    if (Build.VERSION.SDK_INT < 26) {
        display
    } else {
        FontFamily(
            Font(
                R.font.onest_variable,
                FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            ),
        )
    }

@Composable
internal actual fun rememberKelvinAccessibility(appReduceMotion: Boolean): KelvinAccessibility {
    val context = LocalContext.current
    return remember(context, appReduceMotion) { context.readAccessibility(appReduceMotion) }
}

private fun Context.readAccessibility(appReduceMotion: Boolean): KelvinAccessibility {
    val highContrast = runCatching { Settings.Secure.getInt(contentResolver, "high_text_contrast_enabled", 0) == 1 }.getOrDefault(false)
    val power = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    // areAnimatorsEnabled is Android 8+; below it, the animator duration scale says the same (0 is off).
    val animationsOff = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        runCatching { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    return KelvinAccessibility(highContrast, power, appReduceMotion || animationsOff)
}

internal actual fun DrawScope.drawCapsuleShadow(blur: Float, offsetY: Float, alpha: Float) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.TRANSPARENT
        setShadowLayer(blur, 0f, offsetY, android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0))
    }
    val r = size.height / 2f
    drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
}

@Composable
internal actual fun rememberDoodleReader(): (String) -> List<String> {
    val resources = LocalContext.current.resources
    return remember(resources) {
        { file -> doodleFiles[file]?.let { resources.openRawResource(it).bufferedReader().readLines() }.orEmpty() }
    }
}

/**
 * Haptics, the way the best OEM skins use them: a crisp segment tick when a selection moves, distinct on and off
 * clicks for switches, a confirm for a committed action. Each falls back to the nearest constant on older Android.
 */
@Composable
internal actual fun rememberHapticPerformer(): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind ->
            val sdk = Build.VERSION.SDK_INT
            view.performHapticFeedback(
                when (kind) {
                    HapticKind.Tick ->
                        if (sdk >= 34) android.view.HapticFeedbackConstants.SEGMENT_TICK else android.view.HapticFeedbackConstants.CLOCK_TICK
                    HapticKind.ToggleOn ->
                        if (sdk >= 34) android.view.HapticFeedbackConstants.TOGGLE_ON else android.view.HapticFeedbackConstants.CLOCK_TICK
                    HapticKind.ToggleOff ->
                        if (sdk >= 34) android.view.HapticFeedbackConstants.TOGGLE_OFF else android.view.HapticFeedbackConstants.CLOCK_TICK
                    HapticKind.Confirm ->
                        if (sdk >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY
                },
            )
        }
    }
}

/** Each doodle file's raw resource, named in code so resource shrinking keeps them. */
private val doodleFiles: Map<String, Int> = mapOf(
    "kelvin_doodle_casual_all_secure" to R.raw.kelvin_doodle_casual_all_secure,
    "kelvin_doodle_casual_archive_empty" to R.raw.kelvin_doodle_casual_archive_empty,
    "kelvin_doodle_casual_attachments_empty" to R.raw.kelvin_doodle_casual_attachments_empty,
    "kelvin_doodle_casual_chilling" to R.raw.kelvin_doodle_casual_chilling,
    "kelvin_doodle_casual_clumsy" to R.raw.kelvin_doodle_casual_clumsy,
    "kelvin_doodle_casual_contacts_empty" to R.raw.kelvin_doodle_casual_contacts_empty,
    "kelvin_doodle_casual_database_none" to R.raw.kelvin_doodle_casual_database_none,
    "kelvin_doodle_casual_downloads" to R.raw.kelvin_doodle_casual_downloads,
    "kelvin_doodle_casual_drafts_empty" to R.raw.kelvin_doodle_casual_drafts_empty,
    "kelvin_doodle_casual_expiring_none" to R.raw.kelvin_doodle_casual_expiring_none,
    "kelvin_doodle_casual_fingerprint" to R.raw.kelvin_doodle_casual_fingerprint,
    "kelvin_doodle_casual_inbox_empty" to R.raw.kelvin_doodle_casual_inbox_empty,
    "kelvin_doodle_casual_locked" to R.raw.kelvin_doodle_casual_locked,
    "kelvin_doodle_casual_loving" to R.raw.kelvin_doodle_casual_loving,
    "kelvin_doodle_casual_meditating" to R.raw.kelvin_doodle_casual_meditating,
    "kelvin_doodle_casual_not_found" to R.raw.kelvin_doodle_casual_not_found,
    "kelvin_doodle_casual_nothing_found" to R.raw.kelvin_doodle_casual_nothing_found,
    "kelvin_doodle_casual_nothing_playing" to R.raw.kelvin_doodle_casual_nothing_playing,
    "kelvin_doodle_casual_offline" to R.raw.kelvin_doodle_casual_offline,
    "kelvin_doodle_casual_permission" to R.raw.kelvin_doodle_casual_permission,
    "kelvin_doodle_casual_podcasts_empty" to R.raw.kelvin_doodle_casual_podcasts_empty,
    "kelvin_doodle_casual_queue_empty" to R.raw.kelvin_doodle_casual_queue_empty,
    "kelvin_doodle_casual_quiet" to R.raw.kelvin_doodle_casual_quiet,
    "kelvin_doodle_casual_reading" to R.raw.kelvin_doodle_casual_reading,
    "kelvin_doodle_casual_scheduled_empty" to R.raw.kelvin_doodle_casual_scheduled_empty,
    "kelvin_doodle_casual_search_start" to R.raw.kelvin_doodle_casual_search_start,
    "kelvin_doodle_casual_sent" to R.raw.kelvin_doodle_casual_sent,
    "kelvin_doodle_casual_sitting_reading" to R.raw.kelvin_doodle_casual_sitting_reading,
    "kelvin_doodle_casual_snoozed_empty" to R.raw.kelvin_doodle_casual_snoozed_empty,
    "kelvin_doodle_casual_spam_empty" to R.raw.kelvin_doodle_casual_spam_empty,
    "kelvin_doodle_casual_storage_full" to R.raw.kelvin_doodle_casual_storage_full,
    "kelvin_doodle_casual_sync" to R.raw.kelvin_doodle_casual_sync,
    "kelvin_doodle_casual_trash_empty" to R.raw.kelvin_doodle_casual_trash_empty,
    "kelvin_doodle_casual_unboxing" to R.raw.kelvin_doodle_casual_unboxing,
    "kelvin_doodle_casual_unlock" to R.raw.kelvin_doodle_casual_unlock,
    "kelvin_doodle_casual_update" to R.raw.kelvin_doodle_casual_update,
    "kelvin_doodle_casual_waiting" to R.raw.kelvin_doodle_casual_waiting,
    "kelvin_doodle_casual_welcome" to R.raw.kelvin_doodle_casual_welcome,
    "kelvin_doodle_casual_working" to R.raw.kelvin_doodle_casual_working,
    "kelvin_doodle_casual_wrong_password" to R.raw.kelvin_doodle_casual_wrong_password,
    "kelvin_doodle_corporate_all_secure" to R.raw.kelvin_doodle_corporate_all_secure,
    "kelvin_doodle_corporate_archive_empty" to R.raw.kelvin_doodle_corporate_archive_empty,
    "kelvin_doodle_corporate_attachments_empty" to R.raw.kelvin_doodle_corporate_attachments_empty,
    "kelvin_doodle_corporate_chilling" to R.raw.kelvin_doodle_corporate_chilling,
    "kelvin_doodle_corporate_clumsy" to R.raw.kelvin_doodle_corporate_clumsy,
    "kelvin_doodle_corporate_contacts_empty" to R.raw.kelvin_doodle_corporate_contacts_empty,
    "kelvin_doodle_corporate_database_none" to R.raw.kelvin_doodle_corporate_database_none,
    "kelvin_doodle_corporate_downloads" to R.raw.kelvin_doodle_corporate_downloads,
    "kelvin_doodle_corporate_drafts_empty" to R.raw.kelvin_doodle_corporate_drafts_empty,
    "kelvin_doodle_corporate_expiring_none" to R.raw.kelvin_doodle_corporate_expiring_none,
    "kelvin_doodle_corporate_fingerprint" to R.raw.kelvin_doodle_corporate_fingerprint,
    "kelvin_doodle_corporate_inbox_empty" to R.raw.kelvin_doodle_corporate_inbox_empty,
    "kelvin_doodle_corporate_locked" to R.raw.kelvin_doodle_corporate_locked,
    "kelvin_doodle_corporate_loving" to R.raw.kelvin_doodle_corporate_loving,
    "kelvin_doodle_corporate_meditating" to R.raw.kelvin_doodle_corporate_meditating,
    "kelvin_doodle_corporate_not_found" to R.raw.kelvin_doodle_corporate_not_found,
    "kelvin_doodle_corporate_nothing_found" to R.raw.kelvin_doodle_corporate_nothing_found,
    "kelvin_doodle_corporate_nothing_playing" to R.raw.kelvin_doodle_corporate_nothing_playing,
    "kelvin_doodle_corporate_offline" to R.raw.kelvin_doodle_corporate_offline,
    "kelvin_doodle_corporate_permission" to R.raw.kelvin_doodle_corporate_permission,
    "kelvin_doodle_corporate_podcasts_empty" to R.raw.kelvin_doodle_corporate_podcasts_empty,
    "kelvin_doodle_corporate_queue_empty" to R.raw.kelvin_doodle_corporate_queue_empty,
    "kelvin_doodle_corporate_quiet" to R.raw.kelvin_doodle_corporate_quiet,
    "kelvin_doodle_corporate_reading" to R.raw.kelvin_doodle_corporate_reading,
    "kelvin_doodle_corporate_scheduled_empty" to R.raw.kelvin_doodle_corporate_scheduled_empty,
    "kelvin_doodle_corporate_search_start" to R.raw.kelvin_doodle_corporate_search_start,
    "kelvin_doodle_corporate_sent" to R.raw.kelvin_doodle_corporate_sent,
    "kelvin_doodle_corporate_sitting_reading" to R.raw.kelvin_doodle_corporate_sitting_reading,
    "kelvin_doodle_corporate_snoozed_empty" to R.raw.kelvin_doodle_corporate_snoozed_empty,
    "kelvin_doodle_corporate_spam_empty" to R.raw.kelvin_doodle_corporate_spam_empty,
    "kelvin_doodle_corporate_storage_full" to R.raw.kelvin_doodle_corporate_storage_full,
    "kelvin_doodle_corporate_sync" to R.raw.kelvin_doodle_corporate_sync,
    "kelvin_doodle_corporate_trash_empty" to R.raw.kelvin_doodle_corporate_trash_empty,
    "kelvin_doodle_corporate_unboxing" to R.raw.kelvin_doodle_corporate_unboxing,
    "kelvin_doodle_corporate_unlock" to R.raw.kelvin_doodle_corporate_unlock,
    "kelvin_doodle_corporate_update" to R.raw.kelvin_doodle_corporate_update,
    "kelvin_doodle_corporate_waiting" to R.raw.kelvin_doodle_corporate_waiting,
    "kelvin_doodle_corporate_welcome" to R.raw.kelvin_doodle_corporate_welcome,
    "kelvin_doodle_corporate_working" to R.raw.kelvin_doodle_corporate_working,
    "kelvin_doodle_corporate_wrong_password" to R.raw.kelvin_doodle_corporate_wrong_password,
    "kelvin_doodle_semi_all_secure" to R.raw.kelvin_doodle_semi_all_secure,
    "kelvin_doodle_semi_archive_empty" to R.raw.kelvin_doodle_semi_archive_empty,
    "kelvin_doodle_semi_attachments_empty" to R.raw.kelvin_doodle_semi_attachments_empty,
    "kelvin_doodle_semi_chilling" to R.raw.kelvin_doodle_semi_chilling,
    "kelvin_doodle_semi_clumsy" to R.raw.kelvin_doodle_semi_clumsy,
    "kelvin_doodle_semi_contacts_empty" to R.raw.kelvin_doodle_semi_contacts_empty,
    "kelvin_doodle_semi_database_none" to R.raw.kelvin_doodle_semi_database_none,
    "kelvin_doodle_semi_downloads" to R.raw.kelvin_doodle_semi_downloads,
    "kelvin_doodle_semi_drafts_empty" to R.raw.kelvin_doodle_semi_drafts_empty,
    "kelvin_doodle_semi_expiring_none" to R.raw.kelvin_doodle_semi_expiring_none,
    "kelvin_doodle_semi_fingerprint" to R.raw.kelvin_doodle_semi_fingerprint,
    "kelvin_doodle_semi_inbox_empty" to R.raw.kelvin_doodle_semi_inbox_empty,
    "kelvin_doodle_semi_locked" to R.raw.kelvin_doodle_semi_locked,
    "kelvin_doodle_semi_loving" to R.raw.kelvin_doodle_semi_loving,
    "kelvin_doodle_semi_meditating" to R.raw.kelvin_doodle_semi_meditating,
    "kelvin_doodle_semi_not_found" to R.raw.kelvin_doodle_semi_not_found,
    "kelvin_doodle_semi_nothing_found" to R.raw.kelvin_doodle_semi_nothing_found,
    "kelvin_doodle_semi_nothing_playing" to R.raw.kelvin_doodle_semi_nothing_playing,
    "kelvin_doodle_semi_offline" to R.raw.kelvin_doodle_semi_offline,
    "kelvin_doodle_semi_permission" to R.raw.kelvin_doodle_semi_permission,
    "kelvin_doodle_semi_podcasts_empty" to R.raw.kelvin_doodle_semi_podcasts_empty,
    "kelvin_doodle_semi_queue_empty" to R.raw.kelvin_doodle_semi_queue_empty,
    "kelvin_doodle_semi_quiet" to R.raw.kelvin_doodle_semi_quiet,
    "kelvin_doodle_semi_reading" to R.raw.kelvin_doodle_semi_reading,
    "kelvin_doodle_semi_scheduled_empty" to R.raw.kelvin_doodle_semi_scheduled_empty,
    "kelvin_doodle_semi_search_start" to R.raw.kelvin_doodle_semi_search_start,
    "kelvin_doodle_semi_sent" to R.raw.kelvin_doodle_semi_sent,
    "kelvin_doodle_semi_sitting_reading" to R.raw.kelvin_doodle_semi_sitting_reading,
    "kelvin_doodle_semi_snoozed_empty" to R.raw.kelvin_doodle_semi_snoozed_empty,
    "kelvin_doodle_semi_spam_empty" to R.raw.kelvin_doodle_semi_spam_empty,
    "kelvin_doodle_semi_storage_full" to R.raw.kelvin_doodle_semi_storage_full,
    "kelvin_doodle_semi_sync" to R.raw.kelvin_doodle_semi_sync,
    "kelvin_doodle_semi_trash_empty" to R.raw.kelvin_doodle_semi_trash_empty,
    "kelvin_doodle_semi_unboxing" to R.raw.kelvin_doodle_semi_unboxing,
    "kelvin_doodle_semi_unlock" to R.raw.kelvin_doodle_semi_unlock,
    "kelvin_doodle_semi_update" to R.raw.kelvin_doodle_semi_update,
    "kelvin_doodle_semi_waiting" to R.raw.kelvin_doodle_semi_waiting,
    "kelvin_doodle_semi_welcome" to R.raw.kelvin_doodle_semi_welcome,
    "kelvin_doodle_semi_working" to R.raw.kelvin_doodle_semi_working,
    "kelvin_doodle_semi_wrong_password" to R.raw.kelvin_doodle_semi_wrong_password,
)

internal actual fun pathShadowPainter(
    path: androidx.compose.ui.graphics.Path,
    blur: Float,
    offsetY: Float,
    color: androidx.compose.ui.graphics.Color,
): DrawScope.() -> Unit {
    val native = path.asAndroidPath()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(blur, 0f, offsetY, color.toArgb())
    }
    return { drawContext.canvas.nativeCanvas.drawPath(native, paint) }
}

/** AGSL needs Android 13; below it a pebble is a plain fill on its shadow. */
internal actual fun pebblePainter(agsl: String): PebblePainter? =
    if (Build.VERSION.SDK_INT >= 33) AndroidPebblePainter(agsl) else null

@androidx.annotation.RequiresApi(33)
private class AndroidPebblePainter(agsl: String) : PebblePainter {
    // One shader per pebble for its whole life: parsing the source is the expensive part, so only uniforms update.
    private val shader = android.graphics.RuntimeShader(agsl)

    override fun DrawScope.draw(
        outline: androidx.compose.ui.graphics.Outline,
        radii: FloatArray,
        color: androidx.compose.ui.graphics.Color,
        rimWidth: Float,
        strength: Float,
        rim: Float,
        heat: androidx.compose.ui.graphics.Color,
        heatAmount: Float,
    ) {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", radii)
        shader.setColorUniform("color", android.graphics.Color.valueOf(color.red, color.green, color.blue, color.alpha))
        shader.setFloatUniform("rimWidth", rimWidth)
        shader.setFloatUniform("px", density)
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("rim", rim)
        shader.setFloatUniform("heat", heat.red, heat.green, heat.blue)
        shader.setFloatUniform("heatAmt", heatAmount)
        drawOutline(outline, androidx.compose.ui.graphics.ShaderBrush(shader))
    }
}

@Composable
internal actual fun KelvinBackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled, onBack)
