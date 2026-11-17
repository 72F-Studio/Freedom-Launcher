package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One step of a desk app's opening (onboarding) in a desktop window: the picture, a title and a line, centred in a
 * column no wider than reads well, over the drifting aurora at the top of the window. [content] follows in the same
 * column and scrolls; the foot does not.
 *
 * **The foot is a bar fixed at the bottom of the window, the same on every step**, so someone who only presses on
 * never moves the pointer: Back in the window's far start corner ([onBack], with [backText]; its place stays empty
 * on the first step), where you are in the steps in the middle, and [actions] in the far end corner. Put the action that moves on (Continue, or the
 * last step's way out) last in [actions], so it is always in the same corner; keep any other action before it.
 *
 * Unlike [KelvinFlowTour], this is not a pager to swipe: a desktop window steps with the button and the keys, so the
 * app holds [step] and redraws this with the next one. Each step arrives as it is shown ([deskArrive] on [step]),
 * the column a beat after the picture. [onSkip], with [skipText], adds a quiet way past at the top end; leave it null
 * on the last step. [steps] of 1 or less draws no indicator.
 *
 * Keep it to three or four steps of what the app really does, each shown with the app's own components (a
 * [KelvinDeskPoint], a [KelvinShortcutList], [KelvinChoiceCard]s), and say what happens to the person's data before
 * asking them for an account.
 */
@Composable
fun KelvinDeskIntro(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    step: Int = 0,
    steps: Int = 0,
    onSkip: (() -> Unit)? = null,
    skipText: String = "",
    onBack: (() -> Unit)? = null,
    backText: String = kelvinString(KelvinString.Back),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
    art: @Composable () -> Unit,
) {
    val c = Kelvin.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.canvas)) {
        val window = maxHeight
        Box(Modifier.fillMaxWidth().height(window * DeskIntroLight).horizon(animate = true))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The column stands a little above the middle of the window, as a page does, and scrolls on a short one.
            Column(
                Modifier.fillMaxWidth().heightIn(min = window)
                    .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = DeskIntroBarHeight + window * DeskIntroLift),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(
                    Modifier.widthIn(max = DeskIntroWidth).fillMaxWidth().deskArrive(step, fromX = 0.dp, fromY = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(DeskIntroArt), contentAlignment = Alignment.Center) { art() }
                    KText(
                        title,
                        Modifier.padding(top = 20.dp).semantics { heading() },
                        style = Kelvin.type.display,
                        align = TextAlign.Center,
                    )
                    if (summary != null) {
                        KText(
                            summary,
                            Modifier.padding(top = 8.dp),
                            style = Kelvin.type.body,
                            color = c.ink2,
                            align = TextAlign.Center,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content,
                    )
                }
            }
        }
        DeskIntroBar(step, steps, onBack, backText, actions, Modifier.align(Alignment.BottomCenter))
        if (onSkip != null) {
            KelvinButton(
                skipText,
                onSkip,
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 12.dp),
                kind = ButtonKind.Text,
            )
        }
    }
}

/**
 * The foot of a [KelvinDeskIntro]: a bar across the whole window, one height on every step, with Back in the far
 * start corner (or its place kept), the step dots centred, and the actions in the far end corner, as a desktop
 * setup assistant has them. The corners stay where they are whatever the window's width and the step's content.
 */
@Composable
private fun DeskIntroBar(
    step: Int,
    steps: Int,
    onBack: (() -> Unit)?,
    backText: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(DeskIntroBarHeight).background(Kelvin.colors.canvas).padding(horizontal = 24.dp)) {
        if (onBack != null) {
            KelvinButton(backText, onBack, Modifier.align(Alignment.CenterStart), kind = ButtonKind.Secondary)
        }
        if (steps > 1) KelvinPageIndicator(steps, step.toFloat(), Modifier.align(Alignment.Center))
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

/**
 * One thing an app does or promises, as a line in an opening or an empty state: its glyph on a quiet wash of the
 * accent, its name, and a line that says it in plain words. [trailing] holds what goes with it at the end, such as
 * the key that does it ([KelvinKbd]). Stack a few in a column; they share the group's fill and keep their glyphs in
 * one column.
 */
@Composable
fun KelvinDeskPoint(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Kelvin.colors
    Row(
        modifier.fillMaxWidth().background(c.group, KelvinShapes.lg).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(c.accent.copy(alpha = KelvinSelectedWash), KelvinShapes.md),
            contentAlignment = Alignment.Center,
        ) { KIcon(icon, null, size = 20.dp, color = c.accent) }
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.name, maxLines = 1)
            if (summary != null) KText(summary, Modifier.padding(top = 2.dp), style = Kelvin.type.summary, color = c.ink2)
        }
        trailing?.invoke()
    }
}

/** The widest a [KelvinDeskIntro]'s column grows: a comfortable line of reading. */
val DeskIntroWidth = 560.dp

/** The height of a [KelvinDeskIntro]'s bar of actions, the same on every step. */
val DeskIntroBarHeight = 88.dp

/** The square a [KelvinDeskIntro]'s picture is centred in. */
val DeskIntroArt = 72.dp

/** How much of the window's height the aurora fills behind a [KelvinDeskIntro]. */
private const val DeskIntroLight = 0.45f

/** How far above the window's middle a [KelvinDeskIntro]'s column stands, as a share of the window's height. */
private const val DeskIntroLift = 0.12f
