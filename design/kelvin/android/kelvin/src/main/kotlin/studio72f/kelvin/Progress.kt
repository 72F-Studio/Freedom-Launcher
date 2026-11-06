package studio72f.kelvin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Progress: a bar, a ring, a download button that turns into its own progress, and skeleton
 * placeholders. Determinate progress moves on the gentle spring, never jumps; indeterminate
 * progress sweeps, and holds still with reduced motion.
 */

/**
 * A thin bar: [progress] 0 to 1 fills it in the accent; null sweeps a segment across it. On a page
 * of its own colour (a reader's sepia page) pass that page's [color] and [track], or wrap it in
 * [KelvinOnPage].
 */
@Composable
fun KelvinProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = Kelvin.colors.accent,
    track: Color = Kelvin.colors.sunken,
) {
    val c = Kelvin.colors
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), Kelvin.motion.gentle(), label = "progress")
    val sweep = indeterminate(progress == null)
    val fill = color
    Box(
        // Its own layer: a sweeping bar repaints itself each frame, never the page around it.
        modifier.fillMaxWidth().height(height).graphicsLayer()
            .semantics { progressBarRangeInfo = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress, 0f..1f) }
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(track, cornerRadius = r)
                if (progress != null) drawRoundRect(fill, size = Size(size.width * shown, size.height), cornerRadius = r)
                else {
                    val w = size.width * 0.3f
                    val x = (size.width + w) * sweep.value - w
                    drawRoundRect(fill, topLeft = Offset(x.coerceAtLeast(0f), 0f), size = Size((w + x.coerceAtMost(0f)).coerceAtMost(size.width - x.coerceAtLeast(0f)).coerceAtLeast(0f), size.height), cornerRadius = r)
                }
            },
    )
}

/** A ring: [progress] 0 to 1 draws the accent arc from the top; null spins a short arc. */
@Composable
fun KelvinProgressRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 28.dp, stroke: Dp = 3.dp) {
    val c = Kelvin.colors
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), Kelvin.motion.gentle(), label = "ring")
    val spin = indeterminate(progress == null)
    val track = c.sunken
    val fill = c.accent
    Box(
        modifier.size(size).graphicsLayer()
            .semantics { progressBarRangeInfo = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress, 0f..1f) }
            .drawBehind {
                val w = stroke.toPx()
                val inset = w / 2f
                val arcSize = Size(this.size.width - w, this.size.height - w)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                if (progress != null) drawArc(fill, -90f, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                else drawArc(fill, 360f * spin.value - 90f, 100f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            },
    )
}

/** Where a download stands, for [KelvinDownloadButton]. */
@Immutable
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Queued : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data object Done : DownloadState
}

/**
 * The download control of an episode or an attachment, one 48dp pebble that becomes its own
 * progress: a download glyph, a spinning ring while queued, a filling ring with a stop square
 * while it downloads, a filled accent tick when it is on the phone. [label] names the action for
 * TalkBack in each state ("Download", "Cancel download", "Remove download").
 */
@Composable
fun KelvinDownloadButton(state: DownloadState, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    Box(
        modifier.size(48.dp).kelvinPull(onClick, onClickLabel = label, shape = CircleShape)
            .semantics {
                contentDescription = label
                stateDescription = when (state) {
                    DownloadState.Idle -> "not downloaded"
                    DownloadState.Queued -> "queued"
                    is DownloadState.Downloading -> "${(state.progress * 100).toInt()}%"
                    DownloadState.Done -> "downloaded"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            DownloadState.Idle -> KIcon(KelvinIcons.Download, null, size = 22.dp)
            DownloadState.Queued -> KelvinProgressRing(null, size = 26.dp)
            is DownloadState.Downloading -> {
                KelvinProgressRing(state.progress, size = 26.dp)
                Box(Modifier.size(8.dp).clip(KelvinShapes.xs).drawBehind { drawRect(c.accent) })
            }
            DownloadState.Done -> KIcon(KelvinIcons.CircleCheck, null, size = 24.dp, color = c.accent.legibleOn(c.group, 3f))
        }
    }
}

/**
 * A placeholder in the shape of what is loading ([shape]: a line of text, an avatar, an artwork
 * tile), a soft light passing over it. With reduced motion it holds still.
 */
@Composable
fun KelvinSkeleton(modifier: Modifier = Modifier, shape: Shape = KelvinShapes.xs) {
    val c = Kelvin.colors
    val pass = indeterminate(true, 1400)
    val base = c.sunken
    val light = c.group
    Box(
        modifier.graphicsLayer().drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, base)
            val x = (size.width * 2f) * pass.value - size.width * 0.5f
            drawOutline(
                outline,
                Brush.horizontalGradient(
                    0f to base.copy(alpha = 0f), 0.5f to light.copy(alpha = 0.7f), 1f to base.copy(alpha = 0f),
                    startX = x - size.width * 0.4f, endX = x + size.width * 0.4f,
                ),
            )
        },
    )
}

/**
 * 0 to 1 repeating while [running] and motion is allowed; 0.35 otherwise. Returned as a State and
 * read only while drawing, so a looping indicator redraws each frame without recomposing.
 */
@Composable
private fun indeterminate(running: Boolean, period: Int = 1100): androidx.compose.runtime.State<Float> {
    val still = remember { androidx.compose.runtime.mutableFloatStateOf(0.35f) }
    if (!running || Kelvin.reduceMotion) return still
    val t = rememberInfiniteTransition(label = "indeterminate")
    return t.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
}
