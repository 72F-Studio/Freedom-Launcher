package studio72f.kelvin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

/**
 * Asks for a Google Play review on the pacing every 72F app shares ([KelvinReviewPacing]): from the third use,
 * first with Rate and "Maybe next time", later also with "Don't show again", never twice in a use and never
 * again once they rate or opt out. Rate opens Play's own review card inside the app, or the store page when
 * that card can't be had.
 *
 * Put it once inside the app's [KelvinTheme] and [KelvinProblemHost]; it counts uses itself. Pass
 * [enabled] = false while something else owns the screen (first run, a paywall, another prompt). [noStoreCode] is
 * the app's stable code ("RDM_E30_REVIEW_NO_STORE") for the one failure the person can see: neither Play's card
 * nor the store page would open.
 */
@Composable
fun KelvinReviewRequest(
    backdrop: KelvinBackdrop?,
    noStoreCode: String,
    enabled: Boolean = true,
    /** Tells the person neither opened; by default the [KelvinProblemHost] sheet. For an app with its own sheet. */
    onNoStore: ((KelvinProblem) -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val store = remember(activity) { ReviewStore(activity.applicationContext) }
    var state by remember { mutableStateOf(store.read()) }
    var foreground by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(false) }
    var offerNever by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        // Added after the activity started, the observer is replayed up to STARTED, so this use counts too.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val now = System.currentTimeMillis()
                    if (KelvinReviewPacing.startsUse(store.leftAt(), now)) {
                        state = KelvinReviewPacing.used(state).also(store::write)
                    }
                    foreground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    store.left(System.currentTimeMillis())
                    foreground = false
                }
                else -> Unit
            }
        }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(observer)
        onDispose { (activity as? ComponentActivity)?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(foreground, enabled, state.uses) {
        if (!foreground || !enabled || showing) return@LaunchedEffect
        if (!KelvinReviewPacing.isDue(state, System.currentTimeMillis())) return@LaunchedEffect
        delay(KelvinReviewPacing.SETTLE_MILLIS)
        val now = System.currentTimeMillis()
        if (!KelvinReviewPacing.isDue(state, now)) return@LaunchedEffect
        state = KelvinReviewPacing.asked(state, now).also(store::write)
        offerNever = KelvinReviewPacing.offersNever(state)
        showing = true
    }

    val problems = LocalKelvinProblems.current
    val noStore = KelvinProblem(
        headline = kelvinString(KelvinString.ReviewNoStoreHeadline),
        description = kelvinString(KelvinString.ReviewNoStoreDescription),
        code = noStoreCode,
        why = kelvinString(KelvinString.ReviewNoStoreWhy),
    )
    val appName = remember(activity) {
        runCatching { activity.applicationInfo.loadLabel(activity.packageManager).toString() }.getOrDefault("")
    }

    KelvinReviewDialog(
        visible = showing,
        appName = appName,
        offerNever = offerNever,
        onRate = {
            showing = false
            state = state.copy(finished = true).also(store::write)
            activity.launchPlayReview { error ->
                if (!activity.openStoreListing()) {
                    val problem = noStore.copy(
                        detail = kelvinProblemDetail(noStore.code, "open the Google Play review or store page", activity.kelvinAppLine(), error),
                    )
                    if (onNoStore != null) onNoStore(problem) else problems.show(problem, false)
                }
            }
        },
        onLater = { showing = false },
        onNever = {
            showing = false
            state = state.copy(finished = true).also(store::write)
        },
        backdrop = backdrop,
    )
}

/** Play's in-app review card; [onUnavailable] when Play can't give one (no Play services, an old Play Store). */
private fun Activity.launchPlayReview(onUnavailable: (Throwable?) -> Unit) {
    val manager = runCatching { ReviewManagerFactory.create(this) }.getOrElse { onUnavailable(it); return }
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (!request.isSuccessful) {
            onUnavailable(request.exception)
            return@addOnCompleteListener
        }
        // Play decides on its own whether the card shows (it has a quota) and never says; success here only means
        // the flow ran, so there's nothing more to do either way.
        manager.launchReviewFlow(this, request.result).addOnCompleteListener { launch ->
            if (!launch.isSuccessful) onUnavailable(launch.exception)
        }
    }
}

/** The app's Google Play page: in the Play Store if there is one, else in a browser. False when neither opens. */
private fun Activity.openStoreListing(): Boolean {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage("com.android.vending")
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
    return listOf(market, web).any { intent ->
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The review state on this phone, in its own small preferences file. Nothing about the person is kept. */
private class ReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences("kelvin_review", Context.MODE_PRIVATE)

    fun read() = KelvinReviewState(
        uses = prefs.getInt("uses", 0),
        asked = prefs.getInt("asked", 0),
        lastAskedUse = prefs.getInt("last_asked_use", 0),
        lastAskedAtMillis = prefs.getLong("last_asked_at", 0L),
        finished = prefs.getBoolean("finished", false),
    )

    fun write(state: KelvinReviewState) = prefs.edit {
        putInt("uses", state.uses)
        putInt("asked", state.asked)
        putInt("last_asked_use", state.lastAskedUse)
        putLong("last_asked_at", state.lastAskedAtMillis)
        putBoolean("finished", state.finished)
    }

    fun leftAt(): Long = prefs.getLong("left_at", 0L)

    fun left(atMillis: Long) = prefs.edit { putLong("left_at", atMillis) }
}
