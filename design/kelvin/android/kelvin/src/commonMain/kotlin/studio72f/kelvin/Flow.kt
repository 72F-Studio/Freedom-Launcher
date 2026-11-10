package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One screen of a short flow (sign-in, account setup, onboarding): the aurora header with a title, an optional
 * step indicator and back button, content that scrolls, and the actions in the thumb's reach at the bottom.
 *
 * Put the one [ButtonKind.Primary] action first in [actions]. The actions get the page's backdrop so their
 * pebbles are glass. They rise with the keyboard, and the content scrolls clear of them and the keyboard. [onBack]
 * adds a round back button in the header's top-left corner (72F Mail owner decision, 2026-11-09), so the foot holds
 * only the action that moves on.
 *
 * Text is passed in by the app so it can be translated. [step] is 1-based; leave it null for no indicator.
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
    actions: @Composable ColumnScope.(backdrop: KelvinBackdrop) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Kelvin.colors
    val page = rememberKelvinBackdrop()
    Box(modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize().kelvinBackdrop(page).background(c.canvas)) {
            Box(Modifier.fillMaxWidth().horizon().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                // Back is a round button in the top-left corner, where Android puts it, so the foot holds only the one
                // action that moves on. The title starts below it.
                if (onBack != null) {
                    KelvinIconButton(KelvinIcons.Back, backLabel, onBack, Modifier.align(Alignment.TopStart).padding(top = 8.dp))
                }
                Column(
                    Modifier.padding(start = 8.dp, top = if (onBack != null) 64.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (step != null && steps > 0) {
                        KelvinEyebrow(stepText)
                        KelvinProgressBar(step.coerceIn(0, steps).toFloat() / steps)
                    }
                    KText(title, Modifier.semantics { heading() }, Kelvin.type.display)
                    if (summary != null) KText(summary, style = Kelvin.type.body, color = c.ink2)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            // Room for the actions that float over the page. It grows with the keyboard, so fields scroll clear of both.
            Box(Modifier.navigationBarsPadding().imePadding().padding(bottom = 96.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions(page)
        }
    }
}
