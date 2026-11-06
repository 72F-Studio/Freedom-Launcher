package studio72f.kelvin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** An action revealed by swiping a [KelvinSwipeRow]: archive, delete, mark read, add to queue. */
@Immutable
data class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val onTrigger: () -> Unit)

/**
 * A row that can be swiped to act, as mail and podcast lists need. Pulled toward the end it
 * reveals [trailing] (swipe left), toward the start [leading]. The action follows the finger: its
 * background trades from the group grey to the action's colour as the row is pulled, its icon
 * turns white and grows, and it ticks as the pull passes the point of no return (35% of the row).
 * Released past it, the row slides away and the action runs; short of it, the row springs home.
 * TalkBack offers the actions in the row's actions menu.
 */
@Composable
fun KelvinSwipeRow(
    modifier: Modifier = Modifier,
    leading: SwipeAction? = null,
    trailing: SwipeAction? = null,
    content: @Composable () -> Unit,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    val wobble = Kelvin.motion.wobble<Float>()
    val spatial = Kelvin.motion.spatial<Float>()
    val lead by rememberUpdatedState(leading)
    val trail by rememberUpdatedState(trailing)
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .clipToBounds()
            .semantics {
                customActions = listOfNotNull(leading, trailing).map { a -> CustomAccessibilityAction(a.label) { a.onTrigger(); true } }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offset.value
                        val action = if (x > 0f) lead else trail
                        if (armed && action != null) {
                            scope.launch {
                                offset.animateTo(if (x > 0f) width else -width, spatial)
                                haptics.confirm()
                                action.onTrigger()
                                offset.snapTo(0f)
                            }
                        } else scope.launch { offset.animateTo(0f, wobble) }
                        armed = false
                    },
                    onDragCancel = { armed = false; scope.launch { offset.animateTo(0f, wobble) } },
                    onHorizontalDrag = { ch, dx ->
                        val next = offset.value + dx
                        // Only toward a side that has an action; past the row's width, rubber band.
                        val allowed = (next > 0f && lead != null) || (next < 0f && trail != null)
                        if (!allowed && next != 0f) return@detectHorizontalDragGestures
                        ch.consume()
                        val limited = if (abs(next) > width * 0.7f) kotlin.math.sign(next) * (width * 0.7f + KelvinMotion.rubberBand(abs(next) - width * 0.7f, width * 0.3f)) else next
                        scope.launch { offset.snapTo(limited) }
                        val nowArmed = abs(limited) > width * 0.35f
                        if (nowArmed != armed) { armed = nowArmed; haptics.tick() }
                    },
                )
            },
    ) {
        val x = offset.value
        val action = if (x > 0f) leading else if (x < 0f) trailing else null
        if (action != null) {
            // Follow the finger: grey at rest, the action's colour by the point of no return.
            val p = (abs(x) / (width * 0.35f)).coerceIn(0f, 1f)
            val bg = lerp(c.sunken, action.color, p)
            val ink = lerp(c.ink2, action.color.iconInk(), p)
            Row(
                Modifier.matchParentSize().background(bg).padding(horizontal = 24.dp),
                horizontalArrangement = if (x > 0f) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KIcon(action.icon, null, Modifier.graphicsLayer { val s = 0.8f + 0.3f * p; scaleX = s; scaleY = s }, size = 24.dp, color = ink)
                    KText(action.label, style = Kelvin.type.caption.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
                }
            }
        }
        Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.background(c.group)) { content() }
    }
}

/**
 * A row that opens to show more beneath it (a thread's earlier messages, an episode's notes,
 * chapters). The chevron turns as it opens and the content unfolds on the spatial spring.
 */
@Composable
fun KelvinExpandable(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kelvin.colors
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Kelvin.motion.spatial(), label = "chevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .kelvinPress(onToggle, role = Role.Button)
                .semantics { stateDescription = if (expanded) "expanded" else "collapsed"; heading() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                KText(title, style = Kelvin.type.rowTitle)
                if (summary != null) KText(summary, style = Kelvin.type.summary, color = c.ink2)
            }
            KIcon(KelvinIcons.ChevronDown, null, Modifier.graphicsLayer { rotationZ = turn }, size = 20.dp, color = c.ink2)
        }
        AnimatedVisibility(expanded, enter = expandVertically(Kelvin.motion.spatial()) + fadeIn(), exit = shrinkVertically(Kelvin.motion.spatial()) + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), content = content)
        }
    }
}

/**
 * Overlapping avatars (a thread's people, a shared album's members), each ringed in the group's
 * grey, and a `+N` figure for the rest past [max].
 */
@Composable
fun KelvinAvatarStack(names: List<String>, modifier: Modifier = Modifier, max: Int = 3, size: Dp = 32.dp) {
    val c = Kelvin.colors
    val shown = names.take(max)
    val rest = names.size - shown.size
    Row(modifier.semantics(mergeDescendants = true) { stateDescription = names.joinToString() }, horizontalArrangement = Arrangement.spacedBy(-size * 0.3f)) {
        shown.forEach { n ->
            Box(Modifier.size(size + 4.dp).background(c.group, CircleShape), contentAlignment = Alignment.Center) {
                KelvinAvatar(n, Modifier.size(size))
            }
        }
        if (rest > 0) Box(Modifier.size(size + 4.dp).background(c.group, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size).background(c.sunken, CircleShape), contentAlignment = Alignment.Center) {
                KelvinFigure("+$rest", style = Kelvin.type.figure.copy(fontWeight = FontWeight.SemiBold, fontSize = Kelvin.type.caption.fontSize), color = c.ink2, unit = c.ink2, rolling = false)
            }
        }
    }
}

/**
 * Page dots for a pager. [position] is fractional (a pager's current page plus its offset), so
 * the active dot follows the finger: it stretches toward the next page as the page is dragged
 * and settles into a pill on the new one.
 */
@Composable
fun KelvinPageIndicator(count: Int, position: Float, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    val dot = 6.dp
    val pill = 16.dp
    val slot = pill + 6.dp
    val rest = lerp(c.ink, c.canvas, 0.6f).legibleOn(c.group, 3f).copy(alpha = 0.5f)
    Box(
        modifier
            .width(slot * count)
            .height(dot)
            .semantics { stateDescription = "Page ${position.roundToInt() + 1} of $count" }
            .drawBehind {
                val d = dot.toPx(); val s = slot.toPx(); val half = pill.toPx() / 2f; val r = CornerRadius(d / 2f)
                fun centre(i: Float) = s * i + s / 2f
                for (i in 0 until count) drawRoundRect(rest, topLeft = Offset(centre(i.toFloat()) - d / 2f, 0f), size = Size(d, d), cornerRadius = r)
                // The active pill: its leading edge runs ahead toward the next page, its trailing
                // edge catches up, so it stretches as the page is dragged and settles on arrival.
                val p = position.coerceIn(0f, (count - 1).toFloat())
                val from = kotlin.math.floor(p); val f = p - from
                val lead = centre(from + (1f - (1f - f) * (1f - f)))
                val trail = centre(from + f * f)
                drawRoundRect(c.accent, topLeft = Offset(trail - half, 0f), size = Size(lead - trail + half * 2f, d), cornerRadius = r)
            },
    )
}
