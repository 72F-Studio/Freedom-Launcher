package studio72f.kelvin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Asking for a store review without pestering. The rule is the same in every 72F app:
//  - nothing until the third use; a use is the app coming to the front after half an hour or more away;
//  - the first ask offers Rate and "Maybe next time";
//  - every later ask also offers "Don't show again", and comes only after five more uses and ten more days;
//  - Rate or "Don't show again" ends it for good. Dismissing the card counts as "Maybe next time".
// Android keeps the state and hands Rate to Google Play's in-app review (KelvinReviewRequest, androidMain).

/** Where one person is with the review request. Kept by the app; read by [KelvinReviewPacing]. */
@Immutable
data class KelvinReviewState(
    /** Uses so far, this one included. */
    val uses: Int = 0,
    /** How many times the card has been shown. */
    val asked: Int = 0,
    /** [uses] when the card was last shown. */
    val lastAskedUse: Int = 0,
    val lastAskedAtMillis: Long = 0L,
    /** They chose Rate or "Don't show again"; never ask again. */
    val finished: Boolean = false,
)

object KelvinReviewPacing {
    /** The first use the card may appear on. */
    const val FIRST_USE = 3

    /** After "Maybe next time": uses that must pass before the next ask… */
    const val USES_BETWEEN = 5

    /** …and days that must pass as well. */
    const val DAYS_BETWEEN = 10

    /** Time away after which coming back counts as a new use. */
    const val NEW_USE_AFTER_MILLIS = 30L * 60 * 1000

    /** How long a use runs before the card appears, so it never meets someone at the door. */
    const val SETTLE_MILLIS = 12_000L

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** Coming to the front at [nowMillis], having left at [leftAtMillis] (0 for never), starts a new use. */
    fun startsUse(leftAtMillis: Long, nowMillis: Long): Boolean =
        leftAtMillis <= 0L || nowMillis - leftAtMillis >= NEW_USE_AFTER_MILLIS || nowMillis < leftAtMillis

    /** The card should be shown in the current use. */
    fun isDue(state: KelvinReviewState, nowMillis: Long): Boolean = when {
        state.finished -> false
        state.uses < FIRST_USE -> false
        state.asked == 0 -> true
        state.lastAskedUse >= state.uses -> false
        else -> state.uses - state.lastAskedUse >= USES_BETWEEN &&
            nowMillis - state.lastAskedAtMillis >= DAYS_BETWEEN * DAY_MILLIS
    }

    /** [state] once a new use starts. */
    fun used(state: KelvinReviewState): KelvinReviewState = state.copy(uses = state.uses + 1)

    /** [state] once the card is shown at [nowMillis]. */
    fun asked(state: KelvinReviewState, nowMillis: Long): KelvinReviewState =
        state.copy(asked = state.asked + 1, lastAskedUse = state.uses, lastAskedAtMillis = nowMillis)

    /** For [state] as [asked] left it: this is the second ask or later, so "Don't show again" is offered. */
    fun offersNever(state: KelvinReviewState): Boolean = state.asked >= 2
}

/**
 * The review card: a glass dialog that asks to rate [appName] on Google Play. It asks for a rating only, never
 * whether the person likes the app (Google Play's policy). [offerNever] adds "Don't show again" under the two
 * buttons. Back, a tap outside or a pull down is [onLater]. Most apps want [KelvinReviewRequest] (Android), which
 * decides when to show this and does the rest.
 */
@Composable
fun KelvinReviewDialog(
    visible: Boolean,
    appName: String,
    offerNever: Boolean,
    onRate: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    backdrop: KelvinBackdrop?,
) {
    val title = kelvinString(KelvinString.ReviewTitle).replace("%1\$s", appName)
    KelvinDialogPane(visible, onLater, title, backdrop) {
        KText(title, Modifier.fillMaxWidth(), Kelvin.type.title, align = TextAlign.Center)
        KText(
            kelvinString(KelvinString.ReviewMessage).replace("%1\$s", appName),
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            Kelvin.type.body,
            Kelvin.colors.glassInk2,
            align = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KelvinButton(kelvinString(KelvinString.ReviewLater), onLater, Modifier.weight(1f), ButtonKind.Secondary)
            KelvinButton(kelvinString(KelvinString.ReviewRate), onRate, Modifier.weight(1f), ButtonKind.Primary)
        }
        if (offerNever) {
            KelvinButton(kelvinString(KelvinString.ReviewNever), onNever, Modifier.fillMaxWidth(), ButtonKind.Text)
        }
    }
}
