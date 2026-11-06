package studio72f.kelvin

import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which of Kelvin's three doodle sets an app draws. Every [KelvinDoodle] exists in all three, so an app
 * picks once, in [KelvinTheme], and no screen changes.
 */
enum class DoodleSet {
    /** Cats, a dog and birds in a loose pen line. For everyday apps: Gallery, Rediom. */
    Casual,
    /** The same cast drawn with a steady hand on a soft round backdrop. For consumer apps that must feel trustworthy: Mail, Podcasts. */
    SemiProfessional,
    /** Objects only, an even line and one accent. For business tools. */
    Corporate,
}

val LocalDoodleSet = staticCompositionLocalOf { DoodleSet.Casual }

/**
 * Kelvin's own scenes for empty, waiting and error states, drawn in code by `tools/doodles-own` and stored
 * as path lists in res/raw, one per set.
 */
enum class KelvinDoodle(@RawRes private val casual: Int, @RawRes private val semi: Int, @RawRes private val corporate: Int) {
    /** Favourites: nothing saved yet. */
    Loving(R.raw.kelvin_doodle_casual_loving, R.raw.kelvin_doodle_semi_loving, R.raw.kelvin_doodle_corporate_loving),
    /** A reading list or shelf with nothing on it. */
    Reading(R.raw.kelvin_doodle_casual_reading, R.raw.kelvin_doodle_semi_reading, R.raw.kelvin_doodle_corporate_reading),
    /** A new album, shelf or category, still empty. */
    Unboxing(R.raw.kelvin_doodle_casual_unboxing, R.raw.kelvin_doodle_semi_unboxing, R.raw.kelvin_doodle_corporate_unboxing),
    /** Something went wrong, and it is not the person's fault. */
    Clumsy(R.raw.kelvin_doodle_casual_clumsy, R.raw.kelvin_doodle_semi_clumsy, R.raw.kelvin_doodle_corporate_clumsy),
    /** Getting ready: memories developing, a library filling in. */
    SittingReading(R.raw.kelvin_doodle_casual_sitting_reading, R.raw.kelvin_doodle_semi_sitting_reading, R.raw.kelvin_doodle_corporate_sitting_reading),
    /** Nothing to do, on purpose. */
    Chilling(R.raw.kelvin_doodle_casual_chilling, R.raw.kelvin_doodle_semi_chilling, R.raw.kelvin_doodle_corporate_chilling),
    /** All caught up. */
    Meditating(R.raw.kelvin_doodle_casual_meditating, R.raw.kelvin_doodle_semi_meditating, R.raw.kelvin_doodle_corporate_meditating),
    /** A search that found nothing. */
    NothingFound(R.raw.kelvin_doodle_casual_nothing_found, R.raw.kelvin_doodle_semi_nothing_found, R.raw.kelvin_doodle_corporate_nothing_found),
    /** Search is open, nothing typed yet. */
    SearchStart(R.raw.kelvin_doodle_casual_search_start, R.raw.kelvin_doodle_semi_search_start, R.raw.kelvin_doodle_corporate_search_start),
    /** Loading, queued or pending. */
    Waiting(R.raw.kelvin_doodle_casual_waiting, R.raw.kelvin_doodle_semi_waiting, R.raw.kelvin_doodle_corporate_waiting),
    /** Processing, indexing or importing. */
    Working(R.raw.kelvin_doodle_casual_working, R.raw.kelvin_doodle_semi_working, R.raw.kelvin_doodle_corporate_working),
    /** No connection. */
    Offline(R.raw.kelvin_doodle_casual_offline, R.raw.kelvin_doodle_semi_offline, R.raw.kelvin_doodle_corporate_offline),
    /** The thing asked for is not there any more. */
    NotFound(R.raw.kelvin_doodle_casual_not_found, R.raw.kelvin_doodle_semi_not_found, R.raw.kelvin_doodle_corporate_not_found),
    /** Hidden or private until unlocked. */
    Locked(R.raw.kelvin_doodle_casual_locked, R.raw.kelvin_doodle_semi_locked, R.raw.kelvin_doodle_corporate_locked),
    /** Asks for access to photos, notifications or contacts. */
    Permission(R.raw.kelvin_doodle_casual_permission, R.raw.kelvin_doodle_semi_permission, R.raw.kelvin_doodle_corporate_permission),
    /** No notifications. */
    Quiet(R.raw.kelvin_doodle_casual_quiet, R.raw.kelvin_doodle_semi_quiet, R.raw.kelvin_doodle_corporate_quiet),
    /** Trash is empty. */
    TrashEmpty(R.raw.kelvin_doodle_casual_trash_empty, R.raw.kelvin_doodle_semi_trash_empty, R.raw.kelvin_doodle_corporate_trash_empty),
    /** Nothing kept for offline yet. */
    Downloads(R.raw.kelvin_doodle_casual_downloads, R.raw.kelvin_doodle_semi_downloads, R.raw.kelvin_doodle_corporate_downloads),
    /** Catching up with the cloud. */
    Sync(R.raw.kelvin_doodle_casual_sync, R.raw.kelvin_doodle_semi_sync, R.raw.kelvin_doodle_corporate_sync),
    /** The first screen after install or sign-in. */
    Welcome(R.raw.kelvin_doodle_casual_welcome, R.raw.kelvin_doodle_semi_welcome, R.raw.kelvin_doodle_corporate_welcome),
    /** Something went out. */
    Sent(R.raw.kelvin_doodle_casual_sent, R.raw.kelvin_doodle_semi_sent, R.raw.kelvin_doodle_corporate_sent),
    /** No room left. */
    StorageFull(R.raw.kelvin_doodle_casual_storage_full, R.raw.kelvin_doodle_semi_storage_full, R.raw.kelvin_doodle_corporate_storage_full),
    /** Inbox zero. */
    InboxEmpty(R.raw.kelvin_doodle_casual_inbox_empty, R.raw.kelvin_doodle_semi_inbox_empty, R.raw.kelvin_doodle_corporate_inbox_empty),
    /** An update is ready, or something new arrived. */
    Update(R.raw.kelvin_doodle_casual_update, R.raw.kelvin_doodle_semi_update, R.raw.kelvin_doodle_corporate_update),
    /** Mail: no drafts. */
    DraftsEmpty(R.raw.kelvin_doodle_casual_drafts_empty, R.raw.kelvin_doodle_semi_drafts_empty, R.raw.kelvin_doodle_corporate_drafts_empty),
    /** Mail: no spam. */
    SpamEmpty(R.raw.kelvin_doodle_casual_spam_empty, R.raw.kelvin_doodle_semi_spam_empty, R.raw.kelvin_doodle_corporate_spam_empty),
    /** Mail: the archive is empty. */
    ArchiveEmpty(R.raw.kelvin_doodle_casual_archive_empty, R.raw.kelvin_doodle_semi_archive_empty, R.raw.kelvin_doodle_corporate_archive_empty),
    /** Mail: no attachments. */
    AttachmentsEmpty(R.raw.kelvin_doodle_casual_attachments_empty, R.raw.kelvin_doodle_semi_attachments_empty, R.raw.kelvin_doodle_corporate_attachments_empty),
    /** Mail: nothing snoozed. */
    SnoozedEmpty(R.raw.kelvin_doodle_casual_snoozed_empty, R.raw.kelvin_doodle_semi_snoozed_empty, R.raw.kelvin_doodle_corporate_snoozed_empty),
    /** Mail: nothing scheduled. */
    ScheduledEmpty(R.raw.kelvin_doodle_casual_scheduled_empty, R.raw.kelvin_doodle_semi_scheduled_empty, R.raw.kelvin_doodle_corporate_scheduled_empty),
    /** Mail: no contacts yet. */
    ContactsEmpty(R.raw.kelvin_doodle_casual_contacts_empty, R.raw.kelvin_doodle_semi_contacts_empty, R.raw.kelvin_doodle_corporate_contacts_empty),
    /** Podcasts: no shows followed yet. */
    PodcastsEmpty(R.raw.kelvin_doodle_casual_podcasts_empty, R.raw.kelvin_doodle_semi_podcasts_empty, R.raw.kelvin_doodle_corporate_podcasts_empty),
    /** Podcasts: nothing queued to play. */
    QueueEmpty(R.raw.kelvin_doodle_casual_queue_empty, R.raw.kelvin_doodle_semi_queue_empty, R.raw.kelvin_doodle_corporate_queue_empty),
    /** Podcasts: the player with nothing loaded. */
    NothingPlaying(R.raw.kelvin_doodle_casual_nothing_playing, R.raw.kelvin_doodle_semi_nothing_playing, R.raw.kelvin_doodle_corporate_nothing_playing),
    /** Passwords: no database open yet. */
    DatabaseNone(R.raw.kelvin_doodle_casual_database_none, R.raw.kelvin_doodle_semi_database_none, R.raw.kelvin_doodle_corporate_database_none),
    /** Passwords: the vault is locked; unlock it. */
    Unlock(R.raw.kelvin_doodle_casual_unlock, R.raw.kelvin_doodle_semi_unlock, R.raw.kelvin_doodle_corporate_unlock),
    /** Passwords: the master password was wrong. */
    WrongPassword(R.raw.kelvin_doodle_casual_wrong_password, R.raw.kelvin_doodle_semi_wrong_password, R.raw.kelvin_doodle_corporate_wrong_password),
    /** Passwords: nothing weak or reused. */
    AllSecure(R.raw.kelvin_doodle_casual_all_secure, R.raw.kelvin_doodle_semi_all_secure, R.raw.kelvin_doodle_corporate_all_secure),
    /** Passwords: nothing expiring soon. */
    ExpiringNone(R.raw.kelvin_doodle_casual_expiring_none, R.raw.kelvin_doodle_semi_expiring_none, R.raw.kelvin_doodle_corporate_expiring_none),
    /** Passwords: unlock with a fingerprint. */
    Fingerprint(R.raw.kelvin_doodle_casual_fingerprint, R.raw.kelvin_doodle_semi_fingerprint, R.raw.kelvin_doodle_corporate_fingerprint),
    ;

    @RawRes internal fun res(set: DoodleSet): Int = when (set) {
        DoodleSet.Casual -> casual
        DoodleSet.SemiProfessional -> semi
        DoodleSet.Corporate -> corporate
    }
}

/** One path of a doodle: what colour it takes and whether it is filled or stroked. */
private class DoodlePart(val role: Char, val path: Path, val stroke: Float)

/**
 * Draws [doodle] from [set] in the theme: line art in `ink`, its coloured shapes in the accent, soft fills
 * in `accentSoft`, and anything that hides a line behind it in [paper], the colour of the surface the doodle
 * sits on (the group grey inside a group, the canvas elsewhere). Decorative: it has no name for TalkBack;
 * the empty state's title and message say what it means.
 */
@Composable
fun KelvinDoodle(
    doodle: KelvinDoodle,
    modifier: Modifier = Modifier,
    set: DoodleSet = LocalDoodleSet.current,
    paper: Color = if (LocalInKelvinGroup.current) Kelvin.colors.group else Kelvin.colors.canvas,
) {
    val resources = LocalContext.current.resources
    val res = doodle.res(set)
    val parts by produceState(emptyList<DoodlePart>(), res) {
        value = withContext(Dispatchers.Default) {
            resources.openRawResource(res).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.map { line ->
                    // <role> <how> <tx> <ty> <path>: how is 0 / 1 for a nonzero / even-odd fill, or s<width> for a stroke
                    val (role, how, tx, ty, data) = line.split(' ', limit = 5)
                    val path = PathParser().parsePathString(data).toPath()
                    path.translate(Offset(tx.toFloat(), ty.toFloat()))
                    if (how == "1") path.fillType = PathFillType.EvenOdd
                    DoodlePart(role[0], path, if (how.startsWith("s")) how.substring(1).toFloat() else 0f)
                }.toList()
            }
        }
    }
    val c = Kelvin.colors
    Canvas(modifier.aspectRatio(1024f / 768f)) {
        val k = size.width / 1024f
        scale(k, k, pivot = Offset.Zero) {
            parts.forEach { p ->
                val colour = when (p.role) {
                    'a' -> c.accent
                    'p' -> paper
                    'w' -> c.accentSoft
                    else -> c.ink
                }
                drawPath(p.path, colour, style = if (p.stroke > 0f) Stroke(p.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill)
            }
        }
    }
}
