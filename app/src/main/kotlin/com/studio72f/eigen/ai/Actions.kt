package com.studio72f.eigen.ai

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import com.studio72f.eigen.MediaAccessService
import java.util.Locale

/**
 * Things the assistant does rather than says, all through other apps' standard intents: timers and alarms, music,
 * directions, calls and texts (dialled, never placed), the flashlight and web searches. And "catch me up": a
 * summary of the Conversations timeline, written by Gemini Nano on the phone.
 */
object Actions {
    private val TIMER = Regex("^(?:set (?:a )?)?timer (?:for )?(\\d+)\\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?)$|^(\\d+)\\s*(s|sec|secs|seconds?|m|min|mins|minutes?|h|hr|hrs|hours?) timer$")
    private val ALARM = Regex("^(?:set (?:an )?alarm (?:for |at )?|wake me (?:up )?(?:at )?)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$")
    private val PLAY = Regex("^play (.+?)(?: on (spotify|youtube music|youtube|apple music|soundcloud|deezer|tidal))?$")
    private val NAVIGATE = Regex("^(?:navigate to|directions to|take me to|how do i get to) (.+)$")
    private val CALL = Regex("^(call|ring|phone|text|message|sms) (.+)$")
    private val SEARCH = Regex("^(?:search|google|look up|search for) (.+)$")
    private val TORCH = Regex("^(?:turn )?(?:the )?(?:flashlight|torch|flash light)(?: (on|off))?$|^(?:turn )?(on|off) (?:the )?(?:flashlight|torch)$")

    private const val QUICK_TIMER_MAX = 24 * 3600

    /** Runs an action and returns what to say, or null when [query] isn't one. */
    fun handle(context: Context, query: String, dryRun: Boolean = false): String? {
        this.dryRun = dryRun
        val q = query.lowercase(Locale.ROOT).trim().removeSuffix(".").removeSuffix("?").replace(Regex("^(please |can you |could you |hey eigen,? )"), "")
        TIMER.find(q)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toInt()
            val unit = m.groupValues[2].ifEmpty { m.groupValues[4] }
            val seconds = when (unit.first()) { 's' -> n; 'h' -> n * 3600; else -> n * 60 }.coerceIn(1, QUICK_TIMER_MAX)
            return start(context, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, "Eigen"),
                "Timer set for " + duration(seconds) + ".", "a timer")
        }
        ALARM.find(q)?.let { m ->
            var hour = m.groupValues[1].toInt(); val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            val half = m.groupValues[3]
            if (hour > 23 || minute > 59 || (half.isNotEmpty() && hour !in 1..12)) return "That isn't a time on the clock."
            if (half == "pm" && hour < 12) hour += 12
            if (half == "am" && hour == 12) hour = 0
            val shown = String.format(Locale.getDefault(), "%d:%02d", hour, minute)
            return start(context, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "Alarm set for $shown.", "an alarm")
        }
        PLAY.find(q)?.let { m ->
            if (m.groupValues[1] in setOf("music", "something", "some music")) return start(context, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(android.app.SearchManager.QUERY, ""), "Playing.", "music")
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(android.app.SearchManager.QUERY, m.groupValues[1])
            playerPackage(m.groupValues[2])?.let { intent.setPackage(it) }
            return start(context, intent, "Playing “${m.groupValues[1]}”.", "music")
        }
        NAVIGATE.find(q)?.let { m -> return start(context, Intent(Intent.ACTION_VIEW, ("google.navigation:q=" + Uri.encode(m.groupValues[1])).toUri()).takeIf { canHandle(context, it) }
            ?: Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(m.groupValues[1])).toUri()), "Directions to ${m.groupValues[1]}.", "maps") }
        CALL.find(q)?.let { m ->
            val who = m.groupValues[2]
            val number = number(context, who) ?: return if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                "To call or text people by name, turn on Contacts in Settings → Search." else "There's no “$who” with a number in your contacts."
            val texting = m.groupValues[1] in setOf("text", "message", "sms")
            return start(context, Intent(if (texting) Intent.ACTION_SENDTO else Intent.ACTION_DIAL, ((if (texting) "smsto:" else "tel:") + number).toUri()),
                if (texting) "Opening a message to ${title(who)}." else "Dialling ${title(who)}. Tap call when you're ready.", if (texting) "messages" else "calls")
        }
        TORCH.find(q)?.let { m -> if (dryRun) return "Tap to switch the flashlight."; return torch(context, (m.groupValues[1].ifEmpty { m.groupValues[2] }).ifEmpty { null }) }
        SEARCH.find(q)?.let { m -> return start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, m.groupValues[1]), "Searching for “${m.groupValues[1]}”.", "web searches") }
        return null
    }

    @Volatile private var dryRun = false

    private fun start(context: Context, intent: Intent, said: String, what: String): String = if (dryRun) "Tap to go ahead with $what." else try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); said
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("EigenActions", "No app for $what (EIG-AST-01)")
        "There's no app on this phone for $what. Install one and ask again. (EIG-AST-01)"
    } catch (e: SecurityException) {
        android.util.Log.w("EigenActions", "Refused: $what (EIG-AST-01)")
        "Android didn't let Eigen ask for $what. Open the app and do it there. (EIG-AST-01)"
    }

    private fun canHandle(context: Context, intent: Intent) = intent.resolveActivity(context.packageManager) != null

    private fun playerPackage(app: String): String? = when (app) {
        "spotify" -> "com.spotify.music"
        "youtube music" -> "com.google.android.apps.youtube.music"
        "youtube" -> "com.google.android.youtube"
        "apple music" -> "com.apple.android.music"
        "soundcloud" -> "com.soundcloud.android"
        "deezer" -> "deezer.android.app"
        "tidal" -> "com.aspiro.tidal"
        else -> null
    }

    private fun number(context: Context, who: String): String? {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(who))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC")?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private var torchOn = false
    private fun torch(context: Context, want: String?): String {
        val camera = context.getSystemService(CameraManager::class.java) ?: return "This phone has no flashlight."
        return try {
            val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return "This phone has no flashlight."
            torchOn = when (want) { "on" -> true; "off" -> false; else -> !torchOn }
            camera.setTorchMode(id, torchOn)
            if (torchOn) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) { torchOn = false; "The flashlight is busy: another app is using the camera. (EIG-GES-03)" }
    }

    private fun duration(seconds: Int): String = when {
        seconds % 3600 == 0 -> "${seconds / 3600} hour" + if (seconds / 3600 == 1) "" else "s"
        seconds % 60 == 0 -> "${seconds / 60} minute" + if (seconds / 60 == 1) "" else "s"
        else -> "$seconds seconds"
    }

    private fun title(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.getDefault()) } }

    // ---- Catch me up

    private val CATCH_UP = Regex("^(catch me up|what did i miss|summari[sz]e (?:my )?(?:messages|chats|notifications|conversations)|any (?:new )?messages|what did (.+?) (?:say|send|write))$")

    fun isCatchUp(query: String) = CATCH_UP.matches(query.lowercase(Locale.ROOT).trim().removeSuffix("?").removeSuffix("."))

    /** The Conversations timeline, newest last, narrowed to one person when asked "what did X say". */
    fun messages(query: String): List<MediaAccessService.ConversationItem> {
        val who = CATCH_UP.find(query.lowercase(Locale.ROOT).trim().removeSuffix("?").removeSuffix("."))?.groupValues?.getOrNull(2).orEmpty()
        val all = MediaAccessService.getConversations().sortedBy { it.postTime }
        val picked = if (who.isBlank()) all else all.filter { (it.sender ?: "").lowercase(Locale.ROOT).contains(who) || (it.conversationTitle ?: "").lowercase(Locale.ROOT).contains(who) }
        return picked.takeLast(30)
    }

    fun nothingToSummarise(query: String): String =
        if (!MediaAccessService.isServiceConnected()) "To catch you up, Eigen needs notification access. Turn it on in Settings → Access."
        else if (isCatchUp(query) && query.lowercase(Locale.ROOT).startsWith("what did")) "There's nothing from them in your notifications right now."
        else "You're all caught up: no messages waiting."

    /** Without the model: who wrote and how much. */
    fun plainDigest(items: List<MediaAccessService.ConversationItem>): String {
        val bySender = items.groupBy { it.conversationTitle ?: it.sender ?: it.appName }
        return bySender.entries.joinToString("; ", postfix = ".") { (who, list) -> "$who: ${list.size} message" + if (list.size == 1) "" else "s" }
    }

    /** A prompt for Nano. Message text goes only to the model on this phone. */
    fun summaryPrompt(system: String, question: String, items: List<MediaAccessService.ConversationItem>): String = buildString {
        append(system).append('\n')
        append("Here are recent messages from the person's notifications, oldest first. Summarise who needs what in three short sentences or fewer. Mention anything that needs a reply. Don't invent anything.\n")
        for (m in items) append("[").append(m.appName).append("] ").append(m.conversationTitle?.let { "$it / " } ?: "").append(m.sender ?: "Someone").append(": ").append((m.text ?: "").take(280)).append('\n')
        append("User: ").append(question).append("\nEigen:")
    }
}
