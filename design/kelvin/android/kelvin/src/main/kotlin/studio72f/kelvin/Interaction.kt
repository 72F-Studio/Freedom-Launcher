package studio72f.kelvin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Pull to refresh around a scrolling [content] (a mailbox, a feed). Pulled down past the top the
 * content follows the finger on Kelvin's rubber band and a ring in a raised pebble fills with the
 * pull; it ticks as the pull passes the point of refresh. Released past it, [onRefresh] runs and
 * the ring spins until [refreshing] turns false; short of it, everything springs back.
 */
@Composable
fun KelvinPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 80.dp.toPx() }
    val resting = with(density) { 64.dp.toPx() }
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberKelvinHaptics()
    val refresh by rememberUpdatedState(onRefresh)
    val busy by rememberUpdatedState(refreshing)
    var raw by remember { mutableFloatStateOf(0f) }
    val spatial = Kelvin.motion.spatial<Float>()
    LaunchedEffect(refreshing) {
        if (refreshing) pull.animateTo(resting, spatial) else { raw = 0f; pull.animateTo(0f, spatial) }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up while pulled: give the pull back before the list scrolls.
                if (available.y < 0f && raw > 0f && !busy) {
                    val used = maxOf(available.y, -raw)
                    raw += used
                    scope.launch { pull.snapTo(KelvinMotion.rubberBand(raw, threshold * 2f) * 2f) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && !busy) {
                    val before = pull.value
                    raw += available.y
                    val shown = KelvinMotion.rubberBand(raw, threshold * 2f) * 2f
                    scope.launch { pull.snapTo(shown) }
                    if ((before < threshold) != (shown < threshold)) haptics.tick()
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (raw <= 0f || busy) return Velocity.Zero
                if (pull.value >= threshold) { haptics.confirm(); refresh() } else { raw = 0f; pull.animateTo(0f, spatial) }
                raw = 0f
                return Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.graphicsLayer { translationY = pull.value }) { content() }
        val p = (pull.value / threshold).coerceIn(0f, 1f)
        if (pull.value > 1f || refreshing) Box(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                .graphicsLayer { translationY = pull.value - resting; alpha = p; val s = 0.6f + 0.4f * p; scaleX = s; scaleY = s }
                .size(40.dp).pebble(CircleShape, Kelvin.colors.raised),
            contentAlignment = Alignment.Center,
        ) { KelvinProgressRing(if (refreshing) null else p, size = 22.dp, stroke = 2.5.dp) }
    }
}

/**
 * A list whose order people set (the podcast queue, favourite folders). Each item gets a [handle]
 * modifier for its grip; dragging it lifts the item (it swells and floats above the rest), the
 * others slide out of its way as it passes, and on release it settles where it was let go and
 * [onMove] reports the move. TalkBack offers "move up" and "move down" on every item instead.
 */
@Composable
fun <T> KelvinReorderList(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, handle: Modifier, lifted: Boolean) -> Unit,
) {
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val move by rememberUpdatedState(onMove)
    val haptics = rememberKelvinHaptics()
    // Where the dragged item would land if let go now.
    fun target(): Int {
        if (dragging < 0) return -1
        var t = dragging
        var rest = offset
        if (rest > 0f) while (t < items.lastIndex && rest > (heights[t + 1] ?: 0) / 2f) { rest -= heights[t + 1] ?: 0; t++ }
        else while (t > 0 && -rest > (heights[t - 1] ?: 0) / 2f) { rest += heights[t - 1] ?: 0; t-- }
        return t
    }
    var lastTarget by remember { mutableIntStateOf(-1) }
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            key(key(item)) {
                val lifted = i == dragging
                val t = target()
                val draggedHeight = (heights[dragging] ?: 0).toFloat()
                // Items between the dragged one's place and its target step aside.
                val shift = when {
                    dragging < 0 || lifted -> 0f
                    i in (dragging + 1)..t -> -draggedHeight
                    i in t until dragging -> draggedHeight
                    else -> 0f
                }
                val shown by animateFloatAsState(shift, Kelvin.motion.spatial(), label = "reorder")
                val lift by animateFloatAsState(if (lifted) 1f else 0f, Kelvin.motion.snappy(), label = "lift")
                val handle = Modifier.pointerInput(i, items.size) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = i; offset = 0f; lastTarget = i; haptics.tick() },
                        onDragEnd = {
                            val to = target()
                            if (to >= 0 && to != dragging) move(dragging, to)
                            dragging = -1; offset = 0f
                        },
                        onDragCancel = { dragging = -1; offset = 0f },
                        onVerticalDrag = { ch, dy ->
                            ch.consume(); offset += dy
                            val now = target(); if (now != lastTarget) { lastTarget = now; haptics.tick() }
                        },
                    )
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[i] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (lifted) offset else shown
                            val s = 1f + 0.03f * lift
                            scaleX = s; scaleY = s
                            shadowElevation = 12.dp.toPx() * lift
                            shape = KelvinShapes.md
                            clip = lift > 0.01f
                        }
                        .semantics {
                            customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction(moveUpLabel) { move(i, i - 1); true } else null,
                                if (i < items.lastIndex) CustomAccessibilityAction(moveDownLabel) { move(i, i + 1); true } else null,
                            )
                        },
                ) { itemContent(item, handle, lifted) }
            }
        }
    }
}

/** The grip an item in a [KelvinReorderList] is dragged by: pass the list's handle modifier. */
@Composable
fun KelvinGrip(handle: Modifier, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).then(handle), contentAlignment = Alignment.Center) {
        KIcon(KelvinIcons.Grip, null, size = 20.dp, color = Kelvin.colors.ink2)
    }
}
