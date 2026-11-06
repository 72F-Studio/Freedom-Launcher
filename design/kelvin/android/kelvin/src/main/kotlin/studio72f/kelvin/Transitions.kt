package studio72f.kelvin

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Navigation motion. One gesture language for going forward and back: a screen that leaves lifts
 * into a card (continuous corners, a shadow, a slight squash like the pebbles), frosts as it goes
 * and travels with the finger; the screen beneath grows in from the edge the swipe started at,
 * a dim lifting off it. Pushing is the mirror of popping. Predictive back follows the finger
 * exactly; release springs on, a cancelled swipe wobbles home. Reduced motion snaps.
 *
 * Two ways in: KelvinStack, a back stack with all of it built in, for screens that keep their own
 * stack; and KelvinTransitions plus KelvinDestination for apps on Navigation Compose's NavHost.
 */

/*
 * Direction follows the hand. A back swipe dismisses the screen the way the finger moves: from
 * the left edge it leaves to the right, from the right edge to the left, and the screen beneath
 * comes in from the edge the swipe began at. A push, and a pop by a back button, use the
 * reading direction: in from the end and back out toward it ([rtl] mirrors that default).
 */

/** Which way a screen leaves for a swipe from [edge] (or, with no swipe, the reading direction). */
private fun exitDir(edge: Int, rtl: Boolean): Float = when (edge) {
    BackEventCompat.EDGE_RIGHT -> -1f
    BackEventCompat.EDGE_LEFT -> 1f
    else -> if (rtl) -1f else 1f
}

/** The leaving screen's look at [b] (0 settled, 1 gone), travelling as the swipe from [edge] goes. */
private fun GraphicsLayerScope.leaving(b: Float, edge: Int, lean: Float, rtl: Boolean) {
    val dir = exitDir(edge, rtl)
    val pivot = if (dir > 0) 0f else 1f
    // It becomes a card almost at once, then travels.
    val lift = smoothstep(0f, 0.14f, b)
    // Close to the finger mid-swipe, and wholly off-screen at the end (a push starts from there).
    translationX = dir * b * size.width * (0.92f + 0.08f * b * b)
    translationY = lean * lift
    // A little squash, wider than tall, like a pebble under the finger.
    scaleX = 1f - 0.07f * lift
    scaleY = 1f - 0.1f * lift
    transformOrigin = TransformOrigin(pivot, 0.5f)
    shape = ContinuousShape(32.dp * lift)
    clip = lift > 0.001f
    shadowElevation = 22.dp.toPx() * lift
    // Frost as it goes (Android 12 and later draw the blur).
    val blur = 16.dp.toPx() * smoothstep(0.2f, 1f, b)
    renderEffect = if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(blur, blur, TileMode.Decal) else null
}

/** The screen beneath at [u] (0 behind, 1 settled), coming in from the edge the swipe began at. */
private fun GraphicsLayerScope.beneath(u: Float, edge: Int, rtl: Boolean) {
    val dir = exitDir(edge, rtl)
    val k = 1f - u
    translationX = -dir * k * size.width * 0.22f
    val s = 0.9f + 0.1f * u
    scaleX = s; scaleY = s
    transformOrigin = TransformOrigin(if (dir > 0) 0f else 1f, 0.5f)
    shape = ContinuousShape(32.dp * smoothstep(0f, 0.3f, k))
    clip = k > 0.001f
}

/**
 * A back stack with Kelvin's motion and predictive back built in. [stack] is the screens, the last
 * on top; [onBack] pops it (called when a back swipe commits or the system back runs). Pushing
 * (adding to [stack]) slides the new screen in as a card over the old one; popping lifts the top
 * screen off. [key] identifies each entry, so its state survives while it is beneath another.
 */
@Composable
fun <T> KelvinStack(
    stack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    content: @Composable (T) -> Unit,
) {
    val c = Kelvin.colors
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val holder = rememberSaveableStateHolder()
    val haptics = rememberKelvinHaptics()
    val scope = rememberCoroutineScope()
    val reduce = Kelvin.reduceMotion
    // What is laid out: the stack, plus a screen being popped until it has gone.
    var shown by remember { mutableStateOf(stack) }
    val back = remember { Animatable(0f) }
    val push = remember { Animatable(1f) }
    var edge by remember { mutableIntStateOf(NoEdge) }
    var lean by remember { mutableFloatStateOf(0f) }
    var gesturing by remember { mutableStateOf(false) }
    val pop by rememberUpdatedState(onBack)
    val travel = spring<Float>(0.86f, 380f)
    val home = spring<Float>(0.62f, Spring.StiffnessMediumLow)

    LaunchedEffect(stack) {
        val wasTop = shown.lastOrNull()
        when {
            stack == shown -> Unit
            stack.size > shown.size && stack.take(shown.size).map(key) == shown.map(key) -> {
                shown = stack
                if (reduce) push.snapTo(1f) else { push.snapTo(0f); push.animateTo(1f, travel) }
            }
            stack.size < shown.size && shown.take(stack.size).map(key) == stack.map(key) && wasTop != null -> {
                // Popped: lift the top screen off from wherever a swipe left it, then drop it. A pop
                // with no swipe (a back button) leaves in the reading direction.
                if (!gesturing) edge = NoEdge
                if (!reduce) back.animateTo(1f, travel)
                shown = stack
                back.snapTo(0f)
                gesturing = false
            }
            else -> { shown = stack; back.snapTo(0f); push.snapTo(1f) }
        }
    }

    PredictiveBackHandler(enabled = shown.size > 1) { events ->
        var startY = Float.NaN
        try {
            events.collect { e ->
                gesturing = true
                edge = e.swipeEdge
                if (startY.isNaN()) startY = e.touchY
                lean = ((e.touchY - startY) * 0.08f).coerceIn(-40f, 40f)
                back.snapTo(e.progress)
            }
            haptics.tick()
            pop()
        } catch (cancel: CancellationException) {
            scope.launch { back.animateTo(0f, home); gesturing = false; lean = 0f }
            throw cancel
        }
    }

    Box(modifier.fillMaxSize().background(c.canvas)) {
        val top = shown.lastOrNull() ?: return@Box
        val under = shown.getOrNull(shown.size - 2)
        val b = back.value
        val p = push.value
        val showUnder = under != null && (b > 0.0005f || p < 0.9995f)
        // Behind the receding screen: the canvas under one even dim, so the space it shrinks
        // away from never shows black and the dim covers it edge to edge.
        if (showUnder) Box(Modifier.fillMaxSize().drawWithContent {
            val dim = if (b > 0.0005f) 1f - b else p
            drawRect(Color.Black, alpha = 0.16f * dim.coerceIn(0f, 1f))
        })
        // One keyed loop for both screens, so a screen moving between beneath and top (a push, a
        // pop) keeps its composition: nothing is rebuilt, so its glass never loses what it was
        // blurring and nothing flashes through the top bar. The screen beneath stays composed
        // while covered (undrawn, inert, hidden from TalkBack), ready for the next back swipe.
        val visible = if (under != null) listOf(under, top) else listOf(top)
        for (entry in visible) key(key(entry)) {
            val isTop = key(entry) == key(top)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (isTop) {
                            if (b > 0.0005f) leaving(b, edge, if (gesturing) lean else 0f, rtl)
                            else if (p < 0.9995f) leaving(1f - p, NoEdge, 0f, rtl).also { renderEffect = null }
                        } else if (!showUnder) {
                            alpha = 0f
                        } else if (b > 0.0005f) {
                            beneath(b, edge, rtl)
                        } else {
                            // A push is a pop played backwards: the old screen recedes as the new one arrives.
                            beneath(1f - p, NoEdge, rtl)
                        }
                    }
                    .then(if (isTop) Modifier else Modifier.inert())
                    .background(c.canvas)
                    .drawWithContent {
                        drawContent()
                        if (!isTop) {
                            // A dim on the screen beneath, lifting as it comes forward.
                            val dim = if (b > 0.0005f) 1f - b else p
                            drawRect(Color.Black, alpha = 0.16f * dim.coerceIn(0f, 1f))
                        }
                    },
            ) { holder.SaveableStateProvider(key(entry)) { content(entry) } }
        }
    }
}

/** How far switched tab content travels. */
enum class TabTravel(internal val fraction: Float) {
    /** Top-level tabs, the dock's: whole screens, a quarter of the width, frosting on the way. */
    Screen(0.25f),
    /** A pane within a page (title tabs, a segmented control, filter pills): an eighth, no frost. */
    Pane(0.125f),
}

/*
 * Tabs are places side by side, not a deck of cards: the bar that picks them (the dock, title
 * tabs, a segmented control, filter pills) is a map of them in order. Picking a tab to the right
 * pans the view right: the old content slides off to the left as the new one comes in from the
 * right, both on one unbouncing spring, the old fading early so the two never read as one. A
 * screen frosts as it leaves and clears as it lands, as screens do in the stack; a pane only
 * pans, and the page around it eases to its new height. Right-to-left layouts mirror the bar and
 * so the pan. A second pick mid-pan turns it from where it is. Reduced motion swaps in place.
 */

/**
 * Content that switches with a tab bar: [content] for the [selected] tab, panning in from the
 * side the tab lies on in its bar. Indices are the bar's order. Each tab keeps its saved state
 * (scroll position, fields) while another is showing. Use [TabTravel.Screen] under a dock, where
 * each tab is a whole page, and [TabTravel.Pane] for a part of a page under title tabs, a
 * segmented control or filter pills.
 */
@Composable
fun KelvinTabContent(
    selected: Int,
    modifier: Modifier = Modifier,
    travel: TabTravel = TabTravel.Screen,
    content: @Composable (Int) -> Unit,
) {
    val reduce = Kelvin.reduceMotion
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val holder = rememberSaveableStateHolder()
    AnimatedContent(
        selected,
        modifier,
        transitionSpec = {
            if (reduce || initialState == targetState) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val dir = if ((targetState > initialState) != rtl) 1 else -1
                (KelvinTransitions.tabEnter(dir, travel) togetherWith KelvinTransitions.tabExit(dir, travel))
                    .using(SizeTransform(clip = false) { _, _ -> spring(0.9f, 260f) })
            }
        },
        contentAlignment = Alignment.TopStart,
        label = "tab",
    ) { i ->
        val frosted = travel == TabTravel.Screen && !reduce && android.os.Build.VERSION.SDK_INT >= 31
        val frost by transition.animateFloat(transitionSpec = { spring(1f, 500f) }, label = "frost") { state ->
            if (state == EnterExitState.Visible || !frosted) 0f else 1f
        }
        Box(
            Modifier.graphicsLayer {
                val blur = 12.dp.toPx() * frost
                renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
            },
        ) { holder.SaveableStateProvider(i) { content(i) } }
    }
}

/** A covered screen: no touch reaches it and TalkBack does not see it. */
private fun Modifier.inert(): Modifier = this
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * Navigation Compose transitions in Kelvin's motion, for apps whose screens live in a NavHost:
 * pass them as the NavHost's or each destination's transitions, and wrap each destination's
 * content in [KelvinDestination] for the card, squash and frost as it leaves.
 *
 * ```
 * NavHost(nav, start,
 *     enterTransition = { KelvinTransitions.push() }, exitTransition = { KelvinTransitions.pushExit() },
 *     popEnterTransition = { KelvinTransitions.popEnter() }, popExitTransition = { KelvinTransitions.popExit() },
 *     predictivePopEnterTransition = { KelvinTransitions.predictivePopEnter(it) },
 *     predictivePopExitTransition = { KelvinTransitions.predictivePopExit(it) },
 * )
 * ```
 *
 * Top-level tabs pan toward the tab picked: give the tab destinations `tabEnter(dir)` and
 * `tabExit(dir)`, with `dir` +1 when the new tab sits to the right of the old in the dock, -1 to
 * the left. Or skip the NavHost for tabs and switch them with [KelvinTabContent].
 */
object KelvinTransitions {
    private fun <T> spatial() = spring<T>(0.86f, 380f)
    private fun <T> gentle() = spring<T>(0.9f, 260f)
    // Predictive back tracks the finger, so it is linear in time.
    private fun <T> tracked() = tween<T>(320, easing = LinearEasing)

    /** A screen pushed: it slides in a sixth of the width and fades up. */
    fun push(): EnterTransition = slideInHorizontally(spatial()) { it / 6 } + fadeIn(gentle())
    /** The screen a push covers: it eases a tenth of the way out. */
    fun pushExit(): ExitTransition = slideOutHorizontally(spatial()) { -it / 10 } + fadeOut(gentle())
    /** The screen a pop returns to: it eases back from a tenth out. */
    fun popEnter(): EnterTransition = slideInHorizontally(spatial()) { -it / 10 } + fadeIn(gentle())
    /** The screen popped: it slides a sixth out and fades. */
    fun popExit(): ExitTransition = slideOutHorizontally(spatial()) { it / 6 } + fadeOut(gentle())
    // A pan carries no bounce: the page settles, it does not rock.
    private fun <T> pan() = spring<T>(0.9f, 360f)

    /**
     * The tab switched to. Tabs sit side by side in their bar's order, so the new one comes in
     * from the side it lies on: [dir] is +1 when it is to the right of the old one on screen, -1
     * to the left (mind right-to-left layouts, which mirror the bar), 0 for a plain cross-fade.
     * [travel] says how far: a screen pans a quarter of its width, a pane within a page an eighth.
     */
    fun tabEnter(dir: Int = 0, travel: TabTravel = TabTravel.Screen): EnterTransition =
        if (dir == 0) fadeIn(gentle())
        else slideInHorizontally(pan()) { full -> (dir * full * travel.fraction).toInt() } + fadeIn(spring(1f, 500f))

    /** The tab switched from: it pans away from the new one, toward the side the bar left, and fades early. */
    fun tabExit(dir: Int = 0, travel: TabTravel = TabTravel.Screen): ExitTransition =
        if (dir == 0) fadeOut(gentle())
        else slideOutHorizontally(pan()) { full -> (-dir * full * travel.fraction).toInt() } + fadeOut(spring(1f, 900f))

    /** Predictive back, the screen leaving: the way the finger moves (from the right edge, to the left). */
    fun predictivePopExit(edge: Int): ExitTransition {
        val dir = if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        return slideOutHorizontally(tracked()) { full -> (dir * full * 0.92f).toInt() }
    }

    /** Predictive back, the screen returning: from the edge the swipe began at, growing to full size. */
    fun predictivePopEnter(edge: Int): EnterTransition {
        val dir = if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        return scaleIn(tracked(), initialScale = 0.9f, transformOrigin = TransformOrigin(if (dir > 0) 0f else 1f, 0.5f)) +
            slideInHorizontally(tracked()) { full -> (-dir * full * 0.22f).toInt() }
    }
}

/**
 * A NavHost destination's surface: the canvas behind it, and as it leaves (pushed under or
 * popped off, predictive back included, since the transition tracks the gesture) it lifts into a
 * card, squashes a little and frosts, as [KelvinStack] screens do.
 */
@Composable
fun AnimatedVisibilityScope.KelvinDestination(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Kelvin.colors
    val out by transition.animateFloat(transitionSpec = { tween(320, easing = LinearEasing) }, label = "leaving") { state ->
        if (state == EnterExitState.PostExit) 1f else 0f
    }
    Box(
        modifier.fillMaxSize().graphicsLayer {
            val lift = smoothstep(0f, 0.14f, out)
            scaleX = 1f - 0.05f * lift
            scaleY = 1f - 0.08f * lift
            shape = ContinuousShape(32.dp * lift)
            clip = lift > 0.001f
            shadowElevation = 22.dp.toPx() * lift
            val blur = 16.dp.toPx() * smoothstep(0.2f, 1f, out)
            renderEffect = if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) BlurEffect(blur, blur, TileMode.Decal) else null
        }.background(c.canvas),
        content = content,
    )
}

/** No swipe edge: a push or a back-button pop, which use the reading direction. */
private const val NoEdge = -1
