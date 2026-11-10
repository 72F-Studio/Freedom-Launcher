package studio72f.kelvin

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
enum class KelvinDoodle(private val key: String) {
    /** Favourites: nothing saved yet. */
    Loving("loving"),
    /** A reading list or shelf with nothing on it. */
    Reading("reading"),
    /** A new album, shelf or category, still empty. */
    Unboxing("unboxing"),
    /** Something went wrong, and it is not the person's fault. */
    Clumsy("clumsy"),
    /** Getting ready: memories developing, a library filling in. */
    SittingReading("sitting_reading"),
    /** Nothing to do, on purpose. */
    Chilling("chilling"),
    /** All caught up. */
    Meditating("meditating"),
    /** A search that found nothing. */
    NothingFound("nothing_found"),
    /** Search is open, nothing typed yet. */
    SearchStart("search_start"),
    /** Loading, queued or pending. */
    Waiting("waiting"),
    /** Processing, indexing or importing. */
    Working("working"),
    /** No connection. */
    Offline("offline"),
    /** The thing asked for is not there any more. */
    NotFound("not_found"),
    /** Hidden or private until unlocked. */
    Locked("locked"),
    /** Asks for access to photos, notifications or contacts. */
    Permission("permission"),
    /** No notifications. */
    Quiet("quiet"),
    /** Trash is empty. */
    TrashEmpty("trash_empty"),
    /** Nothing kept for offline yet. */
    Downloads("downloads"),
    /** Catching up with the cloud. */
    Sync("sync"),
    /** The first screen after install or sign-in. */
    Welcome("welcome"),
    /** Something went out. */
    Sent("sent"),
    /** No room left. */
    StorageFull("storage_full"),
    /** Inbox zero. */
    InboxEmpty("inbox_empty"),
    /** An update is ready, or something new arrived. */
    Update("update"),
    /** Mail: no drafts. */
    DraftsEmpty("drafts_empty"),
    /** Mail: no spam. */
    SpamEmpty("spam_empty"),
    /** Mail: the archive is empty. */
    ArchiveEmpty("archive_empty"),
    /** Mail: no attachments. */
    AttachmentsEmpty("attachments_empty"),
    /** Mail: nothing snoozed. */
    SnoozedEmpty("snoozed_empty"),
    /** Mail: nothing scheduled. */
    ScheduledEmpty("scheduled_empty"),
    /** Mail: no contacts yet. */
    ContactsEmpty("contacts_empty"),
    /** Podcasts: no shows followed yet. */
    PodcastsEmpty("podcasts_empty"),
    /** Podcasts: nothing queued to play. */
    QueueEmpty("queue_empty"),
    /** Podcasts: the player with nothing loaded. */
    NothingPlaying("nothing_playing"),
    /** Passwords: no database open yet. */
    DatabaseNone("database_none"),
    /** Passwords: the vault is locked; unlock it. */
    Unlock("unlock"),
    /** Passwords: the master password was wrong. */
    WrongPassword("wrong_password"),
    /** Passwords: nothing weak or reused. */
    AllSecure("all_secure"),
    /** Passwords: nothing expiring soon. */
    ExpiringNone("expiring_none"),
    /** Passwords: unlock with a fingerprint. */
    Fingerprint("fingerprint"),
    ;

    /** The name of this doodle's path list in [set]: a raw resource on Android, a bundled file on desktop. */
    internal fun file(set: DoodleSet): String = when (set) {
        DoodleSet.Casual -> "kelvin_doodle_casual_$key"
        DoodleSet.SemiProfessional -> "kelvin_doodle_semi_$key"
        DoodleSet.Corporate -> "kelvin_doodle_corporate_$key"
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
    val read = rememberDoodleReader()
    val file = doodle.file(set)
    val parts by produceState(emptyList<DoodlePart>(), file) {
        value = withContext(Dispatchers.Default) {
            read(file).let { lines ->
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
