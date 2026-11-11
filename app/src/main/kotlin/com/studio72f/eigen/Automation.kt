package com.studio72f.eigen

import androidx.core.content.edit
import androidx.core.net.toUri
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.studio72f.eigen.ui.EigenState
import com.studio72f.eigen.ui.Problems
import java.util.Locale

/**
 * Eigen and automation apps (Tasker, MacroDroid, Automate). Both directions are off until switched on in
 * Settings → Automation, and both are Plus.
 *
 * In: broadcasts to [Receiver] hold a context, release it or change the colour. The receiver only writes the
 * request down; home applies it the next time it is on screen, so nothing runs in the background.
 * Out: when the context changes Eigen broadcasts [EVENT_CHANGED] with the new and previous names, and a context can
 * run a Tasker task by name.
 */
object Automation {
    const val HOLD = "com.studio72f.eigen.action.HOLD_CONTEXT"
    const val RELEASE = "com.studio72f.eigen.action.RELEASE_CONTEXT"
    const val SET_COLOUR = "com.studio72f.eigen.action.SET_COLOUR"
    const val EVENT_CHANGED = "com.studio72f.eigen.event.CONTEXT_CHANGED"
    const val PENDING_COLOUR = "automation_pending_colour"
    const val PENDING_ERROR = "automation_pending_error"

    private const val TASKER = "net.dinglisch.android.taskerm"
    private const val TASKER_RUN = "net.dinglisch.android.tasker.ACTION_TASK"
    const val TASKER_PERMISSION = "net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"
    const val TASKER_REQUEST = 51

    fun announce(context: Context, name: String, previous: String) {
        context.sendBroadcast(Intent(EVENT_CHANGED).putExtra("context", name).putExtra("previous", previous))
    }

    fun runTasker(activity: Activity, state: EigenState, task: String) {
        val installed = runCatching { activity.packageManager.getPackageInfo(TASKER, 0) }.isSuccess
        if (!installed) {
            Problems.show(state, activity, Problems.AUT_TASKER, "automation:tasker-missing", "Tasker isn't installed",
                "This context runs the Tasker task “$task”, but Tasker isn't on this phone. Install it, or clear the task in the context.",
                "Eigen hands the task to Tasker, and found no Tasker app to hand it to.")
            return
        }
        if (activity.checkSelfPermission(TASKER_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(TASKER_PERMISSION), TASKER_REQUEST)
            return
        }
        // Tasker's documented external-task intent; a unique data URI keeps repeated runs from merging.
        val intent = Intent(TASKER_RUN).setData(("id:" + System.nanoTime()).toUri())
            .putExtra("version_number", "1.1").putExtra("task_name", task).putExtra("task_priority", 5)
        try { activity.sendBroadcast(intent) } catch (e: Exception) {
            Problems.show(state, activity, Problems.AUT_TASKER, "automation:tasker-run", "Tasker didn't take the task",
                "In Tasker, open Preferences → Misc and turn on Allow External Access, then switch contexts again.",
                "Android refused to deliver the run-task request to Tasker.", e)
        }
    }

    /** Takes commands from automation apps while switched on; home applies them when it is next on screen. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("automation_in", false) || !EigenBilling.hasPlus(context)) return
            when (intent.action) {
                HOLD -> {
                    val wanted = intent.getStringExtra("context")?.trim()?.lowercase(Locale.ROOT)
                    val rule = ContextStore.load(prefs).firstOrNull { it.name.lowercase(Locale.ROOT) == wanted || it.id == wanted }
                    if (rule == null) { prefs.edit { putString(PENDING_ERROR, "hold") }; return }
                    val minutes = intent.getIntExtra("minutes", 60).coerceIn(1, 24 * 60)
                    prefs.edit { putString("contextPin", rule.id); putLong("contextPinUntil", System.currentTimeMillis() + minutes * 60_000L) }
                }
                RELEASE -> prefs.edit { remove("contextPin"); remove("contextPinUntil") }
                SET_COLOUR -> {
                    val wanted = intent.getStringExtra("colour")?.trim()?.lowercase(Locale.ROOT)
                    val i = EigenPalette.ACCENT_NAMES.indexOfFirst { it.lowercase(Locale.ROOT) == wanted }
                    if (i < 0) prefs.edit { putString(PENDING_ERROR, "colour") } else prefs.edit { putInt(PENDING_COLOUR, i) }
                }
            }
        }
    }
}
