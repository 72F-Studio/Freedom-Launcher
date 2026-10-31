package com.studio72f.eigen.ui

import androidx.core.graphics.drawable.toDrawable
import android.app.Dialog
import android.graphics.Color as AColor
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.CapsuleShape
import studio72f.kelvin.ContinuousShape
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinWindowGlass

/** A choice picked in a sheet, for Java callers. */
fun interface Pick { fun onPick(index: Int) }

/**
 * Kelvin sheets and dialogs in their own window, for the activity's menus and messages (they can open over the
 * settings sheet, which is a View above the Compose page). The window blurs what is behind it on Android 12+.
 */
object EigenSheets {
    private val SheetShape = ContinuousShape(32.dp, flatBottom = true)
    private val DialogShape = ContinuousShape(32.dp)

    /** A list of choices. "name · detail" options show the detail quieter, on the right. */
    @JvmStatic
    fun menu(activity: ComponentActivity, state: EigenState, title: String, options: Array<String>, pick: Pick): Dialog =
        menu(activity, state, title, options, pick, null, -1)

    /** A list of choices; with [swatches], each row leads with its colour and [selected] is ticked. */
    @JvmStatic
    fun menu(activity: ComponentActivity, state: EigenState, title: String, options: Array<String>, pick: Pick, swatches: IntArray?, selected: Int): Dialog =
        window(activity, state) { close ->
            Sheet(title) {
                Column(Modifier.heightIn(max = with(androidx.compose.ui.platform.LocalDensity.current) { (androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height * 0.6f).toDp() }).verticalScroll(rememberScrollState())) {
                    KelvinGroup(gutter = 0.dp) {
                        options.forEachIndexed { i, o ->
                            val dot = o.indexOf(" · ")
                            if (swatches != null) SwatchRow(sentence(o), androidx.compose.ui.graphics.Color(swatches[i]), i == selected) { close(); pick.onPick(i) }
                            else KelvinRow(
                                sentence(if (dot > 0) o.substring(0, dot) else o),
                                value = if (dot > 0) o.substring(dot + 3) else null,
                                onClick = { close(); pick.onPick(i) },
                            )
                        }
                    }
                }
                KelvinButton("Cancel", close, Modifier.fillMaxWidth().padding(top = 12.dp), kind = ButtonKind.Secondary)
            }
        }

    /** A short message with one button. */
    @JvmStatic
    fun message(activity: ComponentActivity, state: EigenState, title: String, body: String): Dialog =
        window(activity, state) { close ->
            Column(
                Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().pane(KelvinWindowGlass(), DialogShape).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KText(sentence(title), style = Kelvin.type.headline)
                KText(body, style = Kelvin.type.body, color = Kelvin.colors.ink2)
                Spacer(Modifier.size(6.dp))
                KelvinButton("OK", close, Modifier.fillMaxWidth())
            }
        }

    /**
     * Kelvin's rule for windows: over a window the system has blurred, the panel is glass-tint at the Glass level's
     * coat, never thinner than half; opaque where the phone cannot blur across windows.
     */
    private fun Modifier.pane(blurred: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier = composed {
        background(Kelvin.colors.glassTint.copy(alpha = if (blurred) maxOf(Kelvin.glass.tintAlpha, 0.5f) else 1f), shape)
    }

    @Composable
    private fun SwatchRow(title: String, colour: androidx.compose.ui.graphics.Color, selected: Boolean, onClick: () -> Unit) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).then(Modifier.eigenPress(onClick)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).background(colour, androidx.compose.foundation.shape.CircleShape))
            KText(title, Modifier.weight(1f).padding(start = 14.dp), Kelvin.type.rowTitle, maxLines = 1)
            if (selected) studio72f.kelvin.KIcon(studio72f.kelvin.KelvinIcons.Check, "Current", size = 22.dp, color = Kelvin.colors.accent)
        }
    }

    @Composable
    private fun Sheet(title: String, content: @Composable () -> Unit) {
        val blurred = KelvinWindowGlass()
        Column(Modifier.fillMaxWidth().pane(blurred, SheetShape).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).background(Kelvin.colors.hairline, CapsuleShape))
            KText(sentence(title), Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), Kelvin.type.title, align = TextAlign.Center, maxLines = 2)
            content()
        }
    }

    /** Kelvin copy is sentence case; older menu strings are all lowercase. */
    private fun sentence(s: String) = s.replaceFirstChar { it.uppercaseChar() }

    /** A bottom-anchored window showing [content] in Eigen's theme, shown at once. */
    private fun window(activity: ComponentActivity, state: EigenState, content: @Composable (close: () -> Unit) -> Unit): Dialog {
        val dialog = ComponentDialog(activity)
        val view = ComposeView(activity)
        view.setContent {
            EigenTheme(state) {
                // Rises from below on the spatial spring, like every Kelvin sheet.
                val rise = remember { Animatable(1f) }
                val spatial = Kelvin.motion.spatial<Float>()
                LaunchedEffect(Unit) { rise.animateTo(0f, spatial) }
                Box(Modifier.graphicsLayer { translationY = rise.value * size.height; alpha = 1f - rise.value * 0.4f }) {
                    content { dialog.dismiss() }
                }
            }
        }
        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(AColor.TRANSPARENT.toDrawable())
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            if (android.os.Build.VERSION.SDK_INT >= 30) setDecorFitsSystemWindows(false)
            // Blur what is behind the sheet. Kelvin's KelvinWindowGlass only finds Compose's own dialog windows, so
            // the flag goes on this window directly; the panel thins once blur is really on.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                attributes = attributes.also { it.blurBehindRadius = (activity.resources.displayMetrics.density * 40).toInt() }
                setDimAmount(0.2f)
            }
        }
        // Monochrome only reaches sheets too: they are windows of their own.
        if (state.monochrome) dialog.window?.decorView?.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        })
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}
