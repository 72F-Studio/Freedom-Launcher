package studio72f.kelvin

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Shared bottom-rising overlay: dims and follows the finger, flings down to dismiss, stretches
 * a little when pulled up past its height. Place it last in a full-screen Box.
 */
@Composable
private fun RisingOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable (Modifier) -> Unit,
) {
    val c = Kelvin.colors
    val spatial = Kelvin.motion.spatial<Float>()
    val wobble = Kelvin.motion.wobble<Float>()
    val deform = Kelvin.glass.deform
    val shown = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        if (visible) drag.snapTo(0f)
        shown.animateTo(if (visible) 1f else 0f, spatial)
    }
    if (!visible && shown.value <= 0.001f) return
    // A window of its own, so the scrim covers the whole screen (top chrome and dock included)
    // wherever the overlay is composed. Back and outside taps dismiss it.
    androidx.compose.ui.window.Popup(
        popupPositionProvider = WindowOrigin,
        onDismissRequest = { if (visible) dismiss() },
        properties = androidx.compose.ui.window.PopupProperties(focusable = visible, clippingEnabled = false),
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(c.scrim)
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        val pulled = drag.value
        content(
            Modifier
                .align(Alignment.BottomCenter)
                .semantics { paneTitle = title; dismiss { dismiss(); true } }
                .graphicsLayer {
                    translationY = (1f - shown.value) * h * 0.6f + pulled.coerceAtLeast(0f)
                    // Pulled up past the top: stretch on the rubber band instead of moving.
                    val up = (-pulled).coerceAtLeast(0f)
                    scaleY = 1f + KelvinMotion.rubberBand(up / h, 0.15f) * (0.3f + 0.7f * deform)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker1D(isDataDifferential = false)
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            // A flick down dismisses however short it was; otherwise the sheet
                            // springs home carrying the finger's speed.
                            val v = tracker.calculateVelocity()
                            if (drag.value > 120.dp.toPx() || (drag.value > 0f && v > KelvinMotion.FLICK_VELOCITY.dp.toPx())) dismiss()
                            else scope.launch { drag.animateTo(0f, wobble, initialVelocity = v) }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, wobble) } },
                        onVerticalDrag = { ch, dy ->
                            ch.consume()
                            val next = drag.value + dy
                            tracker.addDataPoint(ch.uptimeMillis, next)
                            scope.launch { drag.snapTo(next) }
                        },
                    )
                },
        )
    }}
}

/**
 * Bottom sheet for choices and short forms: a glass pane anchored to the bottom edge with
 * continuous top corners, grabber, centred title with close and confirm circles. Keep it
 * composed and drive [visible]. Sheets never stack.
 */
@Composable
fun KelvinSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    closeLabel: String,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    onConfirm: (() -> Unit)? = null,
    confirmLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    RisingOverlay(visible, onDismiss, title) { motion ->
        Column(
            modifier.then(motion)
                .fillMaxWidth()
                .glass(backdrop, SheetShape, GlassContent.Pane, elevated = true)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).background(Kelvin.colors.hairline, CapsuleShape))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KelvinIconButton(KelvinIcons.X, closeLabel, onDismiss, kind = IconButtonKind.Raised)
                KText(title, Modifier.weight(1f), Kelvin.type.rowTitle.copy(fontSize = Kelvin.type.title.fontSize, fontWeight = FontWeight.SemiBold), align = TextAlign.Center, maxLines = 1)
                if (onConfirm != null && confirmLabel != null) KelvinIconButton(KelvinIcons.Check, confirmLabel, onConfirm, kind = IconButtonKind.Accent)
                else Spacer(Modifier.width(48.dp))
            }
            content()
        }
    }
}

/**
 * Confirmation that rises from the bottom as a floating glass card: centred title, one or two
 * sentences, Cancel beside the exact action. For forms and choices use [KelvinSheet].
 */
@Composable
fun KelvinDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String,
    backdrop: KelvinBackdrop?,
    destructive: Boolean = false,
) {
    RisingOverlay(visible, onDismiss, title) { motion ->
        Column(
            motion.navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .glass(backdrop, KelvinShapes.xl, GlassContent.Pane, elevated = true)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KText(title, style = Kelvin.type.rowTitle.copy(fontSize = Kelvin.type.title.fontSize, fontWeight = FontWeight.SemiBold), align = TextAlign.Center)
            KText(message, Modifier.padding(bottom = 12.dp), Kelvin.type.body, Kelvin.colors.glassInk2, align = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KelvinButton(cancelText, onDismiss, Modifier.weight(1f), ButtonKind.Secondary)
                KelvinButton(confirmText, onConfirm, Modifier.weight(1f), if (destructive) ButtonKind.Danger else ButtonKind.Primary)
            }
        }
    }
}

/** Places an overlay's window at the screen's top-left, wherever it is composed. */
private object WindowOrigin : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ) = androidx.compose.ui.unit.IntOffset.Zero
}

/**
 * A glass bottom sheet with free-form content and only a grabber on top, for sheets that bring
 * their own heading (an explanation, details about a photo). Pull it down or tap outside to close.
 */
@Composable
fun KelvinSheetPane(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    RisingOverlay(visible, onDismiss, title) { motion ->
        Column(
            modifier.then(motion)
                .fillMaxWidth()
                .glass(backdrop, SheetShape, GlassContent.Pane, elevated = true)
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(36.dp, 5.dp).background(Kelvin.colors.hairline, CapsuleShape))
            content()
        }
    }
}

/**
 * A glass dialog with free-form content: the same rising liquid-glass card as [KelvinDialog],
 * for content that is more than a title, a sentence and two buttons (a choice of actions, a
 * field). Content is laid out in a column with 8dp gaps; put actions last.
 */
@Composable
fun KelvinDialogPane(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    backdrop: KelvinBackdrop?,
    content: @Composable ColumnScope.() -> Unit,
) {
    RisingOverlay(visible, onDismiss, title) { motion ->
        Column(
            motion.navigationBarsPadding().imePadding().padding(12.dp).fillMaxWidth()
                .glass(backdrop, KelvinShapes.xl, GlassContent.Pane, elevated = true)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

private val SheetShape = ContinuousShape(32.dp, flatBottom = true)

/** Glass slab above the dock for a result, with at most one action. */
@Composable
fun KelvinToast(
    message: String,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
            // Reading text: capped at the Glass mark like sheets and dialogs.
            .glass(backdrop, KelvinShapes.md, GlassContent.Pane, elevated = true)
            .padding(start = 20.dp, end = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText(message, Modifier.weight(1f), Kelvin.type.body, maxLines = 1)
        if (actionText != null && onAction != null) KelvinButton(actionText, onAction, kind = ButtonKind.Text)
    }
}

/**
 * A one-time tip about the screen it is on: a card inset like the dock, the topic's icon on an
 * `accent-soft` disc, a title, a sentence or two, and a tinted "Got it" that dismisses it for
 * good. Glass over [backdrop], else a `raised` card on a soft shadow. [placement] says where it
 * sits: floating clear of the dock or of the navigation bar (align it to the bottom of the
 * screen; it never hides under the dock), or inline in a list that already has its margins.
 */
@Composable
fun KelvinTip(
    icon: ImageVector,
    title: String,
    message: String,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: KelvinBackdrop? = null,
    placement: TipPlacement = TipPlacement.AboveDock,
) {
    val c = Kelvin.colors
    Column(
        modifier.fillMaxWidth()
            .then(when (placement) {
                TipPlacement.AboveDock -> Modifier.navigationBarsPadding().padding(bottom = KelvinDockClearance).padding(horizontal = 16.dp)
                TipPlacement.AboveNavigationBar -> Modifier.navigationBarsPadding().padding(bottom = 16.dp).padding(horizontal = 16.dp)
                TipPlacement.Inline -> Modifier
            })
            .then(
                if (backdrop != null) Modifier.glass(backdrop, KelvinShapes.lg, GlassContent.Pane, elevated = true)
                else Modifier.shadow(12.dp, KelvinShapes.lg, ambientColor = c.glassEdge, spotColor = c.glassEdge).background(c.raised, KelvinShapes.lg)
            )
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                KIcon(icon, null, size = 20.dp, color = c.onAccentSoft)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KText(title, style = Kelvin.type.body.copy(fontWeight = FontWeight.SemiBold))
                KText(message, style = Kelvin.type.summary, color = if (backdrop != null) c.glassInk2 else c.ink2)
            }
        }
        KelvinButton(dismissText, onDismiss, Modifier.align(Alignment.End).padding(top = 8.dp), kind = ButtonKind.Tinted)
    }
}

/** Where a [KelvinTip] sits. */
enum class TipPlacement { AboveDock, AboveNavigationBar, Inline }

/** Space from the bottom of the screen (above the navigation bar) to just over the dock, with a gap. */
// The dock sits 12dp above the navigation bar and is 64dp tall; the tip keeps 12dp over it.
val KelvinDockClearance = 12.dp + 64.dp + 12.dp

/** Fixed spacer that keeps list content clear of the floating dock. */
@Composable
fun KelvinDockSpacer() = Spacer(Modifier.navigationBarsPadding().height(64.dp + 12.dp + 24.dp))
