package studio72f.kelvin

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    if (visible) KelvinBackHandler { dismiss() }
    // Only a tap outside the panel dismisses, and it consumes nothing: consuming the release
    // here cancelled the click on the menu item under the finger.
    var panel by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null && !panel.contains(up.position)) dismiss()
            }
        }
    )
    val deform = Kelvin.glass.deform
    Column(
        modifier
            .width(width)
            .onGloballyPositioned { panel = it.boundsInParent() }
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
                item.destructive -> c.critical.legibleOn(c.glassTint)
                else -> c.ink
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .kelvinPress({ dismiss(); item.onClick() }, item.enabled, onClickLabel = item.label, shape = KelvinShapes.md)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (item.icon != null) KIcon(item.icon, null, size = 20.dp, color = tint)
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
                a.destructive -> c.critical.legibleOn(c.glassTint)
                else -> c.ink
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .kelvinPull(a.onClick, a.enabled, role = Role.Button, onClickLabel = a.label, shape = CapsuleShape),
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
expect fun KelvinWindowGlass(): Boolean

/** Whether a window can blur what is behind it at the current Glass level (Android 12+, blur on). */
@Composable
expect fun kelvinCrossWindowBlur(): Boolean

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
