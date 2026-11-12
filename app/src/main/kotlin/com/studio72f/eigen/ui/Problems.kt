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
    const val DEVELOPER = "parth.k.design@gmail.com"

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
