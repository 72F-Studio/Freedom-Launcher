package com.studio72f.eigen.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinProblem
import studio72f.kelvin.KelvinProblemSheet
import studio72f.kelvin.kelvinAppLine
import studio72f.kelvin.kelvinEmailLogs
import studio72f.kelvin.kelvinProblemDetail

/**
 * Eigen's failures, one stable code per stage that can fail (docs/ERROR_CODES.md). Each is shown in Kelvin's
 * problem sheet: a headline, what to try, then View more with why, the code and a redacted report the person can
 * email themselves. Nothing is sent without them.
 */
object Problems {
    const val DEVELOPER = "support@72fstudio.in"

    // Gestures (GES)
    const val GES_ACCESS = "EIG-GES-01"      // a system gesture action needs Eigen's accessibility service
    const val GES_ACTION = "EIG-GES-02"      // Android refused the system action
    const val GES_TORCH = "EIG-GES-03"       // no flashlight, or the camera holds it
    const val GES_APP = "EIG-GES-04"         // the app a gesture opens is gone
    // Search (SRC)
    const val SRC_CONTACTS = "EIG-SRC-01"    // contacts query failed
    const val SRC_SHORTCUT = "EIG-SRC-02"    // a shortcut could not start
    const val SRC_HANDOFF = "EIG-SRC-03"     // no app handles web, maps or store search
    // Icons (ICN)
    const val ICN_PACK = "EIG-ICN-01"        // the icon pack could not be read
    const val ICN_PICK = "EIG-ICN-02"        // the chosen icon is missing from the pack
    // Private apps (PRV)
    const val PRV_NO_LOCK = "EIG-PRV-01"     // no screen lock to protect them with
    const val PRV_AUTH = "EIG-PRV-02"        // unlocking failed or was cancelled by the system
    // Automation (AUT)
    const val AUT_TASKER = "EIG-AUT-01"      // Tasker missing or refused the task
    const val AUT_COMMAND = "EIG-AUT-02"     // an automation command was malformed
    // Setups (SET)
    const val SET_EXPORT = "EIG-SET-01"      // writing the setup file failed
    const val SET_READ = "EIG-SET-02"        // the file couldn't be opened
    const val SET_INVALID = "EIG-SET-03"     // the file isn't an Eigen setup, or is damaged
    const val SET_NEWER = "EIG-SET-04"       // made by a newer Eigen
    // Assistant actions (AST)
    const val AST_ACTION = "EIG-AST-01"      // no app handles a timer, alarm, call or playback request
    const val AST_SUMMARY = "EIG-AST-02"     // nothing to summarise, or the model failed
    // Textures (TEX)
    const val TEX_RENDER = "EIG-TEX-01"      // the living texture could not be redrawn
    // On-device model (AI): Gemma when the phone has no Gemini Nano
    const val AI_DOWNLOAD = "EIG-AI-01"      // the model download failed or was cut off (network, server)
    const val AI_SPACE = "EIG-AI-02"         // not enough free storage for the model
    const val AI_CHECKSUM = "EIG-AI-03"      // the downloaded file didn't match its checksum, so it was deleted
    const val AI_TERMS = "EIG-AI-04"         // the Gemma terms weren't accepted yet
    const val AI_MODEL = "EIG-AI-05"         // the model wouldn't run (AI Share AIS- code in the report)
    // Backup and restore (BAK)
    const val BAK_EXPORT = "EIG-BAK-01"      // writing the backup file failed
    const val BAK_READ = "EIG-BAK-02"        // the backup couldn't be opened or unpacked
    const val BAK_INVALID = "EIG-BAK-03"     // not an Eigen backup, damaged, or a value out of range
    const val BAK_APPLY = "EIG-BAK-04"       // saving the restored settings failed; the old ones were put back
    // Wallpaper (WAL)
    const val WAL_OPEN = "EIG-WAL-01"        // the chosen photo couldn't be read or decoded
    const val WAL_SAVE = "EIG-WAL-02"        // Eigen couldn't store its copy of the photo or texture
    const val WAL_SYSTEM = "EIG-WAL-03"      // Android refused to set the phone's wallpaper
    const val WAL_PICKER = "EIG-WAL-04"      // no app can pick an image
    // Look (LOK)
    const val LOK_APPLY = "EIG-LOK-01"       // a colour, look or icon pack change couldn't be applied
    // Billing (BIL)
    const val BIL_CONNECT = "EIG-BIL-01"     // Play Billing wasn't connected
    const val BIL_DETAILS = "EIG-BIL-02"     // Play hadn't sent the product details yet
    const val BIL_FLOW = "EIG-BIL-03"        // Play ended the purchase with an error
    // About (ABT) and feedback (FDB)
    const val ABT_NOTICES = "EIG-ABT-01"     // the bundled open-source notices couldn't be read
    const val FDB_MAIL = "EIG-FDB-01"        // no mail app for Send feedback
    // Apps (APP)
    const val APP_LAUNCH = "EIG-APP-01"      // Android refused to start an app (paused profile, just removed)
    const val APP_INFO = "EIG-APP-02"        // the app details page wouldn't open
    const val APP_SHORTCUT = "EIG-APP-03"    // an app shortcut from the long-press menu wouldn't start
    const val APP_HANDOFF = "EIG-APP-04"     // no app handles an action Eigen hands off (uninstall, settings)
    // Widgets (WID)
    const val WID_BIND = "EIG-WID-01"        // the phone has no screen to grant widget permission
    const val WID_CONFIGURE = "EIG-WID-02"   // a widget's settings screen wouldn't start
    const val WID_PLACE = "EIG-WID-03"       // a widget came back from setup with no room in its context

    /** Builds a problem with a redacted report for [code] at [stage]. */
    @JvmStatic
    fun of(context: Context, code: String, stage: String, headline: String, description: String, why: String, error: Throwable? = null, lines: List<String> = emptyList()) =
        KelvinProblem(headline, description, code, why, kelvinProblemDetail(code, stage, context.kelvinAppLine(), error, lines))

    /** Shows [problem] in the sheet. Safe from any thread that owns the state (the main thread). */
    @JvmStatic
    fun show(state: EigenState, problem: KelvinProblem) { state.problem = problem }

    @JvmStatic
    fun show(state: EigenState, context: Context, code: String, stage: String, headline: String, description: String, why: String, error: Throwable? = null) =
        show(state, of(context, code, stage, headline, description, why, error))
}

@Composable
fun ProblemSheet(state: EigenState, backdrop: KelvinBackdrop) {
    val context = LocalContext.current
    KelvinProblemSheet(
        problem = state.problem,
        onDismiss = { state.problem = null },
        closeLabel = "Close",
        backdrop = backdrop,
        onEmailLogs = { report ->
            val code = state.problem?.code ?: ""
            if (!context.kelvinEmailLogs(Problems.DEVELOPER, "Eigen problem $code", report)) state.problem = state.problem?.copy(
                description = "There's no mail app on this phone. The report is under View more; copy it into a message to " + Problems.DEVELOPER + ".",
            )
        },
    )
}
