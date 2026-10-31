package studio72f.kelvin

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/** One row of a [KelvinMenu] or one action of a [KelvinActionBar]. */
@Immutable
data class KelvinAction(
    val icon: ImageVector?,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/**
 * A glass menu that grows out of the control that opened it ([origin], as a fraction of the
 * menu's own size, e.g. bottom-end for a menu above the action orb) and shrinks back into it.
 * Place it last in a full-screen Box and position it with [modifier]; a tap anywhere else or
 * Back dismisses it. Pass the page [backdrop] so the panel refracts what it floats over.
 */
@Composable
fun KelvinMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    items: List<KelvinAction>,
    backdrop: KelvinBackdrop?,
    title: String,
    modifier: Modifier = Modifier,
    origin: TransformOrigin = TransformOrigin(1f, 1f),
    width: Dp = 240.dp,
) {
    val c = Kelvin.colors
    val spatial = Kelvin.motion.spatial<Float>()
    val snappy = Kelvin.motion.snappy<Float>()
    val shown = remember { Animatable(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) { shown.animateTo(if (visible) 1f else 0f, if (visible) spatial else snappy) }
    if (!visible && shown.value <= 0.001f) return
    if (visible) BackHandler { dismiss() }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } })
    val deform = Kelvin.glass.deform
    Column(
        modifier
            .width(width)
            .semantics { paneTitle = title }
            // A menu carries reading text, so like sheets and dialogs it never clears past the Glass mark.
            .glass(
                backdrop,
                KelvinShapes.lg,
                GlassContent.Pane,
                elevated = true,
                motion = {
                    val s = shown.value
                    // Grows from the opener, overshooting a touch at higher Glass levels.
                    scaleX = 0.4f + 0.6f * s
                    scaleY = 0.2f + 0.8f * s + 0.02f * deform * (1f - s) * s * 4f
                    alpha = s.coerceIn(0f, 1f)
                    transformOrigin = origin
                },
            )
            .padding(vertical = 6.dp),
    ) {
        for (item in items) {
            val tint = when {
                !item.enabled -> c.ink3
                item.destructive -> c.critical
                else -> c.ink
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .kelvinPress({ dismiss(); item.onClick() }, item.enabled, onClickLabel = item.label, shape = KelvinShapes.md)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (item.icon != null) KIcon(item.icon, null, size = 22.dp, color = tint)
                KText(item.label, Modifier.weight(1f), Kelvin.type.rowTitle, tint, maxLines = 1)
            }
        }
    }
}

/**
 * The floating action bar for a selection, as ColorOS draws it: a glass capsule above the
 * bottom edge with an icon over a short label for each action. Use it in place of the dock
 * while items are selected; destructive actions take `critical`.
 */
@Composable
fun KelvinActionBar(
    actions: List<KelvinAction>,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
) {
    val c = Kelvin.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp)
            .glass(backdrop, CapsuleShape, elevated = true)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (a in actions) {
            val tint = when {
                !a.enabled -> c.ink3
                a.destructive -> c.critical
                else -> c.ink
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .kelvinPress(a.onClick, a.enabled, role = Role.Button, onClickLabel = a.label, shape = CapsuleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (a.icon != null) KIcon(a.icon, null, size = 22.dp, color = tint)
                KText(a.label, Modifier.padding(top = 2.dp), Kelvin.type.micro.copy(fontWeight = FontWeight.Medium), tint, align = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/**
 * Glass for content in its own window (a dialog or a bottom sheet hosted in a dialog window),
 * where the page's [KelvinBackdrop] cannot be seen. Call it inside the window's content: on
 * Android 12+ with cross-window blur on, the system blurs everything behind the window by the
 * Glass level's frost, and [windowPane] tints the panel over it. Otherwise nothing is blurred
 * and [windowPane] stays dense enough for text.
 */
@Composable
fun KelvinWindowGlass(): Boolean {
    val view = LocalView.current
    val params = Kelvin.glass
    val density = LocalDensity.current
    val blur = kelvinCrossWindowBlur()
    val radius = with(density) { (params.frostDp * 1.6f + 8f).dp.roundToPx() }
    DisposableEffect(view, blur, radius) {
        val window = view.dialogWindow()
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = radius }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        onDispose { }
    }
    return blur
}

private fun View.dialogWindow(): android.view.Window? {
    var v: Any? = this
    while (v is View) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent
    }
    return null
}

/** Whether a window can blur what is behind it at the current Glass level (Android 12+, blur on). */
@Composable
fun kelvinCrossWindowBlur(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return !Kelvin.glass.isSolid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled }.getOrDefault(false)
}

/** Panel colour for a window that uses [KelvinWindowGlass]; see [windowPane]. */
@Composable
fun kelvinWindowTint(): androidx.compose.ui.graphics.Color {
    val tint = Kelvin.colors.glassTint
    return if (kelvinCrossWindowBlur()) tint.copy(alpha = maxOf(Kelvin.glass.tintAlpha, 0.5f)) else tint
}

/**
 * The panel of a window that uses [KelvinWindowGlass]: `glass-tint` at the Glass level's
 * coat when the window behind is blurred, never thinner than half so sheet text stays
 * readable; opaque when it is not.
 */
fun Modifier.windowPane(blurred: Boolean, shape: Shape): Modifier = composed {
    val params = Kelvin.glass
    val tint = Kelvin.colors.glassTint
    val alpha = if (blurred) maxOf(params.tintAlpha, 0.5f) else 1f
    background(tint.copy(alpha = alpha), shape)
}
