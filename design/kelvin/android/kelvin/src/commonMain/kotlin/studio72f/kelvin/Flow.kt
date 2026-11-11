package studio72f.kelvin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * One screen of a short flow (sign-in, account setup, onboarding): the aurora header with a title, an optional
 * step indicator and back button, content that scrolls, and the actions in the thumb's reach at the bottom.
 *
 * Put the one [ButtonKind.Primary] action first in [actions]. The actions get the page's backdrop so their
 * pebbles are glass. They rise with the keyboard, and the content scrolls clear of them and the keyboard. [onBack]
 * adds a round back button at the start of the header, and the title sits on the same line beside it (72F Mail
 * owner decision, 2026-11-11), so a step spends one line on where you are and the rest of the window on what it
 * asks. The foot holds only the action that moves on.
 *
 * Text is passed in by the app so it can be translated. [step] is 1-based; leave it null for no indicator. The
 * step's bar grows from the step before as the page arrives, and the content comes in a beat after the header
 * ([arrive]); parts of it can carry that on with `Modifier.arrive(n)`. Pass [arrives] false to have it in place.
 *
 * For the screen a flow opens on, and for the waits inside it, use [KelvinFlowHero]. For the hand-over between the
 * screens, give the navigation host [rememberKelvinFlowTransitions].
 */
@Composable
fun KelvinFlowPage(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    step: Int? = null,
    steps: Int = 0,
    stepText: String = "",
    onBack: (() -> Unit)? = null,
    backLabel: String = kelvinString(KelvinString.Back),
    arrives: Boolean = true,
    actions: @Composable ColumnScope.(backdrop: KelvinBackdrop) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kelvin.colors
    val page = rememberKelvinBackdrop()
    val stepped = step != null && steps > 0
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize().background(c.canvas)) {
        Box(Modifier.fillMaxSize().kelvinBackdrop(page).background(c.canvas)) {
            // The light keeps its own height, whatever the header under it holds, so a one-line header is not a
            // thin strip of colour.
            Box(Modifier.fillMaxWidth().horizon().statusBarsPadding().height(FlowLight))
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // From the top, so the button stands beside the first line of a title long enough to wrap.
                    Row(verticalAlignment = Alignment.Top) {
                        // The button's disc, not its 48dp touch square, stands on the gutter, over the text below.
                        if (onBack != null) KelvinIconButton(KelvinIcons.Back, backLabel, onBack, Modifier.offset(x = (-4).dp))
                        // As tall as the button with or without one, so the title stays put from step to step, and
                        // centred in that height, so one line (or the step over the title) sits level with the button.
                        Column(Modifier.weight(1f).heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                            if (stepped) KelvinEyebrow(stepText)
                            KText(title, Modifier.semantics { heading() }, Kelvin.type.display)
                        }
                    }
                    if (step != null && stepped) FlowProgress(step, steps)
                    if (summary != null) KText(summary, style = Kelvin.type.body, color = c.ink2)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().dissolveAtEnds(scroll).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(if (arrives) Modifier.arrive(1) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
                // Room for the actions that float over the page. It grows with the keyboard, so fields scroll clear of both.
                Box(Modifier.navigationBarsPadding().imePadding().padding(bottom = 96.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions(page)
        }
    }
}

/**
 * The line over a screen's foot that says where the person stands before they act ("No folders selected",
 * "3 selected · subfolders included"): centred over the full-width button it explains, in caption type and the
 * secondary ink, so it reads as part of the button's foot and not as a stray label at the gutter. Put it first in a
 * [KelvinFlowPage]'s actions, or above the one button in any other foot; the foot's 8dp spacing stands it off the
 * button. It is a live region, so a changed count is announced.
 */
@Composable
fun KelvinFootCaption(text: String, modifier: Modifier = Modifier) {
    KText(
        text,
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        Kelvin.type.caption,
        Kelvin.colors.ink2,
        align = TextAlign.Center,
    )
}

/**
 * Content that scrolls out of a flow page's room dissolves over a short distance, under the header and over the
 * actions, instead of being cut off by a straight edge mid-line. An end the content rests against is left sharp:
 * nothing is faded that is not on its way out.
 */
private fun Modifier.dissolveAtEnds(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = FlowDissolve.toPx()
        // Each end fades in step with how far the content has gone past it, so the fade never appears at a jump.
        val top = (scroll.value / fade).coerceIn(0f, 1f)
        val bottom = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
        if (top > 0f) {
            drawRect(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - top), 1f to Color.Black, startY = 0f, endY = fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom > 0f) {
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - bottom), startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** The step's bar: it starts where the step before left it and grows to this one as the page arrives. */
@Composable
private fun FlowProgress(step: Int, steps: Int) {
    val reduce = Kelvin.reduceMotion
    var at by remember { mutableIntStateOf(if (reduce) step else step - 1) }
    LaunchedEffect(step) { at = step }
    KelvinProgressBar(at.coerceIn(0, steps).toFloat() / steps)
}

/**
 * The screen a flow opens on, closes on or waits on: a picture, a title and one line, all centred, standing in the
 * upper part of the window. The three are centred on the line a quarter of the way down and never start above a
 * tenth, so on a phone they fill the band from about 10% to 40% of its height: high enough to be the first thing
 * seen, low enough to clear the status bar and sit under the aurora, which here fills the upper half and drifts.
 * [content] follows, centred too, and scrolls with them on a short window. [actions] stay at the foot as in
 * [KelvinFlowPage]; [onBack] adds the same round button in the same corner.
 *
 * [art] is the app's own picture (its logo, a [KelvinDoodle]) or a [KelvinWaitMark] for a wait. The screen
 * assembles from the top down as it opens ([arrive]); the app can add `Modifier.arrive(n)` to parts of [content] to
 * carry that on. Pass [arrives] false when this screen replaces one just like it (one wait after another), so the
 * two read as one screen whose line changed. A change of [summary] crosses over in place and is announced.
 *
 * On the screen an app opens on, when [art] is the app's own mark, pass [lifts] true: the mark starts where the
 * system's splash showed it, in the middle of the window and a little larger, and lifts into its place while the
 * rest waits for it, so the splash hands over to the app instead of being cut to it. It lifts once, at the opening.
 *
 * Several of these side by side, to open an app with, are a [KelvinFlowTour].
 */
@Composable
fun KelvinFlowHero(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = kelvinString(KelvinString.Back),
    arrives: Boolean = true,
    lifts: Boolean = false,
    actions: @Composable ColumnScope.(backdrop: KelvinBackdrop) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
    art: @Composable () -> Unit,
) {
    val c = Kelvin.colors
    val page = rememberKelvinBackdrop()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The lift belongs to the app's opening, once: not to a rotation, nor to coming back to this screen.
    var lifted by rememberSaveable { mutableStateOf(false) }
    val lift = remember { lifts && !lifted }
    LaunchedEffect(Unit) { lifted = true }
    BoxWithConstraints(modifier.fillMaxSize().background(c.canvas)) {
        val window = maxHeight
        Box(Modifier.fillMaxSize().kelvinBackdrop(page).background(c.canvas)) {
            Box(Modifier.fillMaxWidth().height(window * HeroLight).horizon(animate = true))
            Column(Modifier.fillMaxSize()) {
                HeroBody(
                    window = window,
                    // Clear of the status bar, and of the back button when there is one.
                    clear = statusBar + if (onBack != null) 68.dp else 16.dp,
                    title = title,
                    summary = summary,
                    arrives = arrives,
                    lift = lift,
                    modifier = Modifier.weight(1f),
                    content = content,
                    art = art,
                )
                Box(Modifier.navigationBarsPadding().imePadding().padding(bottom = 96.dp))
            }
        }
        if (onBack != null) {
            KelvinIconButton(KelvinIcons.Back, backLabel, onBack, Modifier.statusBarsPadding().padding(start = 12.dp, top = 12.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp).then(if (arrives) Modifier.arrive(3) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions(page)
        }
    }
}

/**
 * The opening of an app as a short tour: a few hero pages side by side, each a picture, a title and a line with
 * room for a little more, under one drifting aurora and over one foot. The person swipes between them or steps
 * with the button; the dots over the [actions] follow the finger. Each page assembles from the top down as it
 * comes into view ([LocalKelvinInView]), so a page's picture and the parts under it arrive while it slides in.
 *
 * Draw each page with [KelvinTourScope.Page] inside [page]. Keep it to what the app really does, shown rather
 * than told: a page's picture is best made of the app's own components showing a sample (a card, a row). Give
 * the foot the same [actions] on every page (change the button's words, not how many there are), so it does not
 * jump. [onSkip], with [skipText], adds a quiet way past the tour at the top end; pass null on the last page.
 * Stepping is the app's: call `state.animateScrollToPage` from the button, and from the system Back.
 */
@Composable
fun KelvinFlowTour(
    pages: Int,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { pages },
    onSkip: (() -> Unit)? = null,
    skipText: String = "",
    actions: @Composable ColumnScope.(backdrop: KelvinBackdrop) -> Unit = {},
    page: @Composable KelvinTourScope.(index: Int) -> Unit,
) {
    val c = Kelvin.colors
    val backdrop = rememberKelvinBackdrop()
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The foot's own height, so a page scrolls clear of it however many actions it holds.
    var foot by remember { mutableIntStateOf(0) }
    // The opening page's mark lifts from the splash once: not on a rotation, nor when the page is swiped back to.
    val lifted = rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(c.canvas)) {
        val window = maxHeight
        val scope = remember(window, statusBar) { KelvinTourScope(window, statusBar + 56.dp, lifted) }
        Box(Modifier.fillMaxSize().kelvinBackdrop(backdrop).background(c.canvas)) {
            Box(Modifier.fillMaxWidth().height(window * HeroLight).horizon(animate = true))
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { index ->
                    // A pager composes the next page before it is turned to. A page counts as in view from the
                    // moment any of it shows, and stays so.
                    val inView = remember { mutableStateOf(state.currentPage == index) }
                    LaunchedEffect(Unit) {
                        snapshotFlow { abs(state.currentPage + state.currentPageOffsetFraction - index) < 1f }.first { it }
                        inView.value = true
                    }
                    CompositionLocalProvider(LocalKelvinInView provides inView.value) { scope.page(index) }
                }
                Box(Modifier.height(with(density) { foot.toDp() }))
            }
        }
        if (onSkip != null) {
            KelvinButton(skipText, onSkip, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp, top = 12.dp), kind = ButtonKind.Text)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { foot = it.height }.navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp).arrive(3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KelvinPageIndicator(pages, state.currentPage + state.currentPageOffsetFraction, Modifier.padding(top = 4.dp, bottom = 8.dp))
            actions(backdrop)
        }
    }
}

/** Where a [KelvinFlowTour] draws its pages: it knows the window they stand in. */
@Stable
class KelvinTourScope internal constructor(private val window: Dp, private val clear: Dp, private val lifted: MutableState<Boolean>) {
    /**
     * One page of the tour, laid out as a [KelvinFlowHero] is: [art], [title] and [summary] centred in the upper
     * part of the window, [content] under them. [lifts] is the hero's: true on the first page when [art] is the
     * app's mark, to carry it up from where the system's splash showed it.
     */
    @Composable
    fun Page(
        title: String,
        modifier: Modifier = Modifier,
        summary: String? = null,
        lifts: Boolean = false,
        content: @Composable ColumnScope.() -> Unit = {},
        art: @Composable () -> Unit,
    ) {
        val lift = remember { lifts && !lifted.value }
        if (lifts) LaunchedEffect(Unit) { lifted.value = true }
        HeroBody(window, clear, title, summary, arrives = true, lift = lift, modifier = modifier.fillMaxSize(), content = content, art = art)
    }
}

/**
 * The hero itself: the picture, the title and the line where [HeroLayout] stands them, then [content]; it scrolls.
 * With [lift] the picture comes up from the middle of the window and the rest waits [LiftBeats] for it.
 */
@Composable
private fun HeroBody(
    window: Dp,
    clear: Dp,
    title: String,
    summary: String?,
    arrives: Boolean,
    lift: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
    art: @Composable () -> Unit,
) {
    val wait = if (lift) LiftBeats else 0
    fun Modifier.part(order: Int) = if (arrives) arrive(order + wait) else this
    HeroLayout(window, clear, modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(if (lift) Modifier.liftFromSplash() else Modifier.part(0)) { art() }
            KText(
                title,
                Modifier.padding(top = 20.dp).part(1).semantics { heading() },
                Kelvin.type.display,
                align = TextAlign.Center,
            )
            if (summary != null) HeroLine(summary, Modifier.padding(top = 8.dp).widthIn(max = 320.dp).part(2))
        }
        // What the app puts under the hero keeps its own order; while the picture lifts, all of it waits with the rest.
        CompositionLocalProvider(LocalArriveWait provides wait) {
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/**
 * Carries the app's mark from the system's splash into its place on the first screen. It starts where the splash
 * drew it, at the middle of the window and [LiftScale] times its size here, holds for the window to finish
 * opening, and rises on a soft spring that passes its place by a hair and settles. In place at once under reduced
 * motion.
 */
private fun Modifier.liftFromSplash(): Modifier = composed {
    if (Kelvin.reduceMotion) return@composed this
    val at = remember { Animatable(0f) }
    // How far below its place the middle of the window is; not known until it is placed.
    val below = remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(Unit) {
        snapshotFlow { below.floatValue }.first { !it.isNaN() }
        delay(LiftHold)
        at.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 110f))
    }
    onGloballyPositioned { here ->
        if (below.floatValue.isNaN()) {
            below.floatValue = here.findRootCoordinates().size.height / 2f - (here.positionInRoot().y + here.size.height / 2f)
        }
    }.graphicsLayer {
        val from = below.floatValue
        // Unseen for the one frame before it knows where the middle is, so it never shows in its place and jumps.
        alpha = if (from.isNaN()) 0f else 1f
        translationY = if (from.isNaN()) 0f else (1f - at.value) * from
        val size = LiftScale + (1f - LiftScale) * at.value
        scaleX = size
        scaleY = size
    }
}

/** How many beats later than usual [arrive] brings a part in here: the wait for a mark lifting from the splash. */
private val LocalArriveWait = compositionLocalOf { 0 }

/**
 * Whether the part of the screen this is read in has come into view: true on a screen of its own, and on a page of a
 * [KelvinFlowTour] from the moment any of the page shows. [arrive] starts on it. Start on it whatever
 * else an app sets moving when a page arrives (a sample that counts up or sorts itself), never on first composition:
 * a pager composes the next page before it is shown, and the motion would be over by the time anyone saw it.
 */
val LocalKelvinInView = compositionLocalOf { true }

/**
 * Places the hero (the first child) centred on [HeroCentre] of the [window]'s height, never above [HeroTop] of it
 * nor above [clear], and what follows (the second child) straight under it. It reports the height of all of it, so
 * the scroll around it works on a window too short to hold the hero where it wants to stand.
 */
@Composable
private fun HeroLayout(window: Dp, clear: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val hero = measurables[0].measure(loose)
        val rest = measurables[1].measure(loose)
        val height = window.roundToPx()
        val top = maxOf((height * HeroCentre).roundToInt() - hero.height / 2, (height * HeroTop).roundToInt(), clear.roundToPx())
        layout(constraints.maxWidth, (top + hero.height + rest.height).coerceAtLeast(constraints.minHeight)) {
            hero.placeRelative((constraints.maxWidth - hero.width) / 2, top)
            rest.placeRelative((constraints.maxWidth - rest.width) / 2, top + hero.height)
        }
    }
}

/** The hero's one line. When it changes (a wait moving on), the old line leaves upward as the new one rises in. */
@Composable
private fun HeroLine(text: String, modifier: Modifier) {
    val reduce = Kelvin.reduceMotion
    AnimatedContent(
        targetState = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        transitionSpec = {
            if (reduce) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (fadeIn(tween(LineIn, delayMillis = LineOut)) + slideInVertically(tween(LineIn, delayMillis = LineOut)) { it / 3 }) togetherWith
                    (fadeOut(tween(LineOut)) + slideOutVertically(tween(LineOut)) { -it / 3 })
            }
        },
        contentAlignment = Alignment.Center,
        label = "hero line",
    ) { line ->
        KText(line, style = Kelvin.type.body, color = Kelvin.colors.ink2, align = TextAlign.Center)
    }
}

/**
 * Brings a part of a screen in as the screen opens: it fades in while rising a short way into its place. A part
 * with a higher [order] follows the one before it a beat later, so the screen assembles from the top down instead
 * of appearing all at once. Only the drawing moves, never the layout, so nothing around it jumps and the part can
 * be tapped from the first frame. In place at once under reduced motion.
 */
fun Modifier.arrive(order: Int = 0): Modifier = composed {
    if (Kelvin.reduceMotion) return@composed this
    val at = remember { Animatable(0f) }
    val wait = LocalArriveWait.current
    val inView = LocalKelvinInView.current
    LaunchedEffect(inView) {
        if (!inView) return@LaunchedEffect
        delay(ArriveBeat * (order + wait))
        at.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 170f))
    }
    val rise = with(LocalDensity.current) { ArriveRise.toPx() }
    graphicsLayer {
        alpha = at.value.coerceIn(0f, 1f)
        translationY = (1f - at.value) * rise
    }
}

/**
 * The picture of a wait: a ring turning in the accent. When the work is [done] the ring closes, turns to the
 * positive colour and a tick draws itself inside, so the wait ends on a small yes before the flow moves on. The
 * whole of that takes under half a second; hold the screen that long before leaving it.
 *
 * The turn is read from the clock, not counted from when the ring first drew, so the ring on the next screen of a
 * flow stands at the same angle as the one it replaces and the two cross over as one. It keeps turning under
 * reduced motion, as every spinner does; only the closing and the tick lose their motion.
 */
@Composable
fun KelvinWaitMark(modifier: Modifier = Modifier, done: Boolean = false, size: Dp = 72.dp) {
    val c = Kelvin.colors
    val reduce = Kelvin.reduceMotion
    val turn = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(done) {
        if (done) return@LaunchedEffect
        // An endless animation, and said to be one, so a UI test waiting for the screen to settle is not kept waiting.
        while (true) withInfiniteAnimationFrameMillis { turn.floatValue = (it % TurnMillis) / TurnMillis.toFloat() }
    }
    val close by animateFloatAsState(if (done) 1f else 0f, if (reduce) snap() else tween(CloseMillis, easing = FastOutSlowInEasing), label = "close")
    val tick by animateFloatAsState(if (done) 1f else 0f, if (reduce) snap() else tween(TickMillis, delayMillis = TickDelay, easing = FastOutSlowInEasing), label = "tick")
    val ring = lerp(c.accent, c.positive, close)
    val track = c.sunken
    val fill = c.positiveSoft
    val mark = c.positive
    val full = remember { Path() }
    val drawn = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(
        // Its own layer: the ring repaints itself each frame, never the page around it.
        modifier.size(size).graphicsLayer().semantics { if (!done) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val w = this.size.minDimension * RingStroke
        val arc = Size(this.size.width - w, this.size.height - w)
        val inset = Offset(w / 2f, w / 2f)
        if (close > 0f) drawCircle(fill, radius = (this.size.minDimension - w) / 2f, alpha = close)
        drawArc(track, 0f, 360f, false, inset, arc, style = Stroke(w))
        drawArc(ring, turn.floatValue * 360f - 90f, RingSweep + (360f - RingSweep) * close, false, inset, arc, style = Stroke(w, cap = StrokeCap.Round))
        if (tick > 0f) {
            val s = this.size.minDimension
            full.reset()
            full.moveTo(s * 0.32f, s * 0.52f)
            full.lineTo(s * 0.45f, s * 0.64f)
            full.lineTo(s * 0.69f, s * 0.38f)
            measure.setPath(full, false)
            drawn.reset()
            measure.getSegment(0f, measure.length * tick, drawn, true)
            drawPath(drawn, mark, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * How one screen of a flow hands over to the next, for a navigation host's four transitions. The screens of a flow
 * share their chrome (the aurora, the back button, the action at the foot), so the hand-over is a true cross-over:
 * the new screen fades in over the old one, which holds still under it until it is covered, and Back fades the top
 * screen away off the one beneath. What the two screens share never flickers or moves; only what differs changes.
 * What is new then comes in with [arrive]. Give the host the screen above the other on the way forward and beneath
 * it on the way back, as Jetpack Navigation's `NavHost` does. Instant under reduced motion.
 */
@Immutable
class KelvinFlowTransitions internal constructor(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

@Composable
fun rememberKelvinFlowTransitions(): KelvinFlowTransitions {
    val reduce = Kelvin.reduceMotion
    return remember(reduce) {
        if (reduce) {
            KelvinFlowTransitions(fadeIn(snap()), fadeOut(snap()), fadeIn(snap()), fadeOut(snap()))
        } else {
            KelvinFlowTransitions(
                enter = fadeIn(tween(PageCross, easing = FastOutSlowInEasing)),
                // Held, not faded: two screens fading at once would let the window show through what they share.
                exit = fadeOut(snap(delayMillis = PageCross)),
                popEnter = fadeIn(snap()),
                popExit = fadeOut(tween(PageCross, easing = FastOutSlowInEasing)),
            )
        }
    }
}

/** How tall the aurora stands behind a flow page's header, under the status bar. */
private val FlowLight = 200.dp

/** The distance over which a flow page's content dissolves where it scrolls out of its room. */
private val FlowDissolve = 20.dp

/** How much of the window's height the aurora fills behind a hero. */
private const val HeroLight = 0.58f

/** The hero is centred this far down the window, and never starts above [HeroTop] of it. */
private const val HeroCentre = 0.25f
private const val HeroTop = 0.10f

/** A part arrives this long after the one before it, from this far below. */
private const val ArriveBeat = 70L
private val ArriveRise = 14.dp

/**
 * A mark lifting from the splash: how much larger the splash drew it, how long it holds before it moves, and how
 * many beats the rest of the screen waits so that nothing fades in across its path.
 */
private const val LiftScale = 1.2f
private const val LiftHold = 220L
private const val LiftBeats = 6

/** The hero's line: how long the old one takes to leave and the new one to come in. */
private const val LineOut = 110
private const val LineIn = 220

/** The wait mark: one turn, the arc it shows while turning, its stroke as a share of its size, and its ending. */
private const val TurnMillis = 1000L
private const val RingSweep = 100f
private const val RingStroke = 0.07f
private const val CloseMillis = 240
private const val TickDelay = 140
private const val TickMillis = 240

/** How long one screen of a flow takes to cross over to the next. */
private const val PageCross = 260
