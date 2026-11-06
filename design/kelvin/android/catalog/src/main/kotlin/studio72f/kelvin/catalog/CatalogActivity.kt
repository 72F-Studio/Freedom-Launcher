package studio72f.kelvin.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import studio72f.kelvin.Accent
import studio72f.kelvin.AccentPreset
import studio72f.kelvin.ButtonKind
import studio72f.kelvin.DockItem
import studio72f.kelvin.FrostLevel
import studio72f.kelvin.GlassLevel
import studio72f.kelvin.GlassTexture
import studio72f.kelvin.RefractionLevel
import studio72f.kelvin.GlassRangeLabels
import studio72f.kelvin.IconButtonKind
import studio72f.kelvin.KIcon
import studio72f.kelvin.KText
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinButton
import studio72f.kelvin.KelvinDialog
import studio72f.kelvin.KelvinActionOrb
import studio72f.kelvin.KelvinDock
import studio72f.kelvin.KelvinDockSpacer
import studio72f.kelvin.KelvinGlassControl
import studio72f.kelvin.KelvinGroup
import studio72f.kelvin.KelvinIconButton
import studio72f.kelvin.KelvinIcons
import studio72f.kelvin.KelvinMessageRow
import studio72f.kelvin.KelvinNotice
import studio72f.kelvin.KelvinReachHeader
import studio72f.kelvin.rememberKelvinReach
import studio72f.kelvin.KelvinReachBar
import studio72f.kelvin.KelvinFigure
import kotlin.math.roundToInt
import studio72f.kelvin.KelvinStatRow
import studio72f.kelvin.*
import studio72f.kelvin.KelvinBarChart
import studio72f.kelvin.KelvinBar
import studio72f.kelvin.KelvinSlideToConfirm
import studio72f.kelvin.KelvinControlBar
import studio72f.kelvin.KelvinControlCapsule
import studio72f.kelvin.LocalVeilInset
import studio72f.kelvin.KelvinVeiledPage
import androidx.compose.foundation.isSystemInDarkTheme
import studio72f.kelvin.KelvinPill
import studio72f.kelvin.KelvinRow
import studio72f.kelvin.KelvinSearchField
import studio72f.kelvin.KelvinSectionHeader
import studio72f.kelvin.KelvinSegmented
import studio72f.kelvin.KelvinSheet
import studio72f.kelvin.KelvinSlider
import studio72f.kelvin.KelvinStatCard
import studio72f.kelvin.KelvinTitleTabs
import studio72f.kelvin.KelvinTimeLockup
import studio72f.kelvin.KelvinSwitch
import studio72f.kelvin.KelvinTabs
import studio72f.kelvin.KelvinTextField
import studio72f.kelvin.KelvinTheme
import studio72f.kelvin.KelvinTile
import studio72f.kelvin.KelvinTiles
import studio72f.kelvin.KelvinToast
import studio72f.kelvin.Tone
import studio72f.kelvin.kelvinBackdrop
import studio72f.kelvin.kelvinPress
import studio72f.kelvin.kelvinWordmark
import studio72f.kelvin.rememberKelvinBackdrop

class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent { Catalog() }
    }
}

private val Photos = listOf(
    listOf(Color(0xFFA9CDEE), Color(0xFF6C9FCF), Color(0xFF3D6B56), Color(0xFF25402F)),
    listOf(Color(0xFFF6C27A), Color(0xFFE0774A), Color(0xFF6B2D3A)),
    listOf(Color(0xFFD9D4CC), Color(0xFF9A9083), Color(0xFF4A443E)),
    listOf(Color(0xFFFFD84A), Color(0xFFF2B705), Color(0xFF1A1A1A)),
    listOf(Color(0xFF7FD3C8), Color(0xFF2E7F8C), Color(0xFF13343F)),
    listOf(Color(0xFFF4A6B8), Color(0xFFB04C7A), Color(0xFF3B1838)),
)

@Composable
private fun Photo(i: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = Photos[i % Photos.size]
        drawRect(Brush.linearGradient(colors, Offset(0f, 0f), Offset(size.width * 0.4f, size.height)))
        drawCircle(Color.White.copy(alpha = 0.35f), size.minDimension * 0.12f, Offset(size.width * 0.7f, size.height * 0.28f))
    }
}

@Composable
private fun Catalog() {
    // Theme lives in Appearance, as in every 72F app: System, Light or Dark.
    var theme by remember { mutableIntStateOf(0) }
    val dark = when (theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    // One intensity per texture, so switching back restores where each was left.
    // Frost and Kelvin's pebbles by default; Liquid glass is the option.
    var texture by remember { mutableStateOf(GlassTexture.Frost) }
    var glass by remember { mutableFloatStateOf(GlassLevel.Frosted) }
    var frost by remember { mutableFloatStateOf(FrostLevel.Default) }
    var refraction by remember { mutableFloatStateOf(RefractionLevel.Default) }
    var accent by remember { mutableStateOf<Accent>(Accent.Ember) }
    var reduceMotion by remember { mutableStateOf(false) }
    var systemFont by remember { mutableStateOf(false) }
    KelvinTheme(dark = dark, accent = accent, glass = if (texture == GlassTexture.Frost) frost else glass, texture = texture, refraction = refraction, reduceMotion = reduceMotion, fonts = if (systemFont) studio72f.kelvin.KelvinFonts.System else studio72f.kelvin.KelvinFonts.Kelvin) {
        val page = rememberKelvinBackdrop()
        val scroll = rememberScrollState()
        var tab by remember { mutableIntStateOf(0) }
        var filter by remember { mutableIntStateOf(0) }
        var view by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var exposure by remember { mutableFloatStateOf(0.62f) }
        var cache by remember { mutableFloatStateOf(0.4f) }
        var lock by remember { mutableStateOf(true) }
        var sheet by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(false) }
        var toast by remember { mutableStateOf(false) }
        // Deleting is immediate and undoable for a few seconds, never a dead end.
        var deleted by remember { mutableStateOf(false) }
        LaunchedEffect(deleted) { if (deleted) { kotlinx.coroutines.delay(5000); deleted = false } }
        var key by remember { mutableStateOf("gsk_12") }
        var playing by remember { mutableStateOf(false) }
        var day by remember { mutableIntStateOf(3) }
        var pane by remember { mutableIntStateOf(0) }
        var elapsed by remember { mutableLongStateOf(52_420L) }
        // The recorder's clock runs while playing, so its digits roll.
        LaunchedEffect(playing) {
            while (playing) { kotlinx.coroutines.delay(70); elapsed += 70 }
        }
        val c = Kelvin.colors

        val reach = rememberKelvinReach()
        // The catalog is one stack: Smart search opens a detail screen, so predictive back and the
        // push can be tried for real.
        var stack by remember { mutableStateOf(listOf("home")) }
        KelvinStack(stack, { stack = stack.dropLast(1) }) { route ->
        if (route == "detail") SearchDetail(onBack = { stack = stack.dropLast(1) })
        else Box(Modifier.fillMaxSize().background(c.canvas)) {
          // One-handed: the large title opens the page low, within the thumb's reach, and
          // scrolls away; the compact bar on the veil takes over once it has gone.
          Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().kelvinBackdrop(page).background(c.canvas).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KelvinReachHeader(reach, summary = "Everything you touch heats up and cools. Hold a row, drag a slider, pick a tab.")
                Box(Modifier.padding(horizontal = 16.dp)) { KelvinSearchField(query, { query = it }, "Search photos, places and text", "Clear search") }
                KelvinTabs(listOf("All", "Images", "Notes", "Audio", "Bookmarks"), filter, { filter = it })

                // Hierarchy: views switch as titles, figures carry accent marks and small units.
                KelvinTitleTabs(listOf("Today", "Money"), pane, { pane = it }, Modifier.padding(top = 12.dp))
                if (pane == 0) {
                    val now = remember { java.util.Date() }
                    KelvinTimeLockup(
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now),
                        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(now),
                        java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(now),
                        Modifier.padding(horizontal = 24.dp),
                    )
                } else {
                    KelvinStatRow(Modifier.padding(horizontal = 16.dp)) {
                        KelvinStatCard("Income", "₹48.2k", Modifier.weight(1f), detail = "12 transactions", tone = Tone.Positive)
                        KelvinStatCard("Spent", "₹31.9k", Modifier.weight(1f), detail = "38 transactions", tone = Tone.Critical)
                    }
                }

                KelvinSectionHeader("Appearance")
                KelvinGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        KText("Theme", style = Kelvin.type.rowTitle)
                        KelvinSegmented(listOf("System", "Light", "Dark"), theme, { theme = it })
                    }
                    Column(Modifier.padding(16.dp)) {
                        KText("Glass", style = Kelvin.type.rowTitle)
                        KText("Frost and Kelvin's pebbles, or Liquid glass that bends the page at its rim.", style = Kelvin.type.summary, color = c.ink2)
                        Spacer(Modifier.height(8.dp))
                        KelvinSegmented(listOf("Frost", "Liquid glass"), if (texture == GlassTexture.Frost) 0 else 1, { texture = if (it == 0) GlassTexture.Frost else GlassTexture.Liquid })
                        // Frost does not refract: it has only its transparency.
                        if (texture == GlassTexture.Liquid) {
                            Spacer(Modifier.height(8.dp))
                            KelvinGlassControl(refraction, { refraction = it }, GlassRangeLabels("Subtle", "Strong", null), "Refraction")
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        KText("Transparency", style = Kelvin.type.rowTitle)
                        KText("How much of the page shows through.", style = Kelvin.type.summary, color = c.ink2)
                        Spacer(Modifier.height(8.dp))
                        if (texture == GlassTexture.Frost) {
                            KelvinGlassControl(frost, { frost = it }, GlassRangeLabels("Opaque", "Sheer", null), "Frost transparency")
                        } else {
                            KelvinGlassControl(glass, { glass = it }, GlassRangeLabels("Opaque", "Clear", "Clear glass has no contrast protection. Text over photos may be hard to read."), "Transparency")
                        }
                    }
                    KelvinRow("Reduce motion", summary = "Controls stop stretching and wobbling", divider = true, trailing = { KelvinSwitch(reduceMotion, { reduceMotion = it }) })
                    KelvinRow("System font", summary = "Use the phone's own font instead of Onest and Montserrat", divider = true, trailing = { KelvinSwitch(systemFont, { systemFont = it }) })
                    KelvinRow("Accent", icon = KelvinIcons.Sun, tile = KelvinTiles.Orange, value = accent.name(), chevron = true, divider = true, onClick = { sheet = true })
                }

                // Busy content so the glass has something to work with.
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { i -> Photo(i + 3, Modifier.weight(1f).aspectRatio(1f).clip(Kelvin.shape.sm)) }
                }

                KelvinSectionHeader("Albums")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KelvinTile("Favourites", "48 items", {}, Modifier.weight(1f), KelvinIcons.Heart, "Favourites") { Photo(0, Modifier.fillMaxSize()) }
                        KelvinTile("Bruges", "312 items", {}, Modifier.weight(1f), KelvinIcons.Cloud, "In pCloud") { Photo(2, Modifier.fillMaxSize()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KelvinTile("Camera", "5,120 items", {}, Modifier.weight(1f), KelvinIcons.SdCard, "On this phone") { Photo(3, Modifier.fillMaxSize()) }
                        KelvinTile("Sunsets", "27 items", {}, Modifier.weight(1f)) { Photo(1, Modifier.fillMaxSize()) }
                    }
                }

                KelvinSectionHeader("Library")
                KelvinGroup {
                    KelvinRow("Connected clouds", summary = "Koofr, pCloud, Dropbox", icon = KelvinIcons.Cloud, value = "3", chevron = true, onClick = {})
                    KelvinRow("Lock hidden albums", icon = KelvinIcons.Lock, tile = KelvinTiles.Green, divider = true, trailing = { KelvinSwitch(lock, { lock = it }) })
                    KelvinRow("Smart search", summary = "On-device, 1,204 photos indexed", icon = KelvinIcons.Sparkles, tile = KelvinTiles.Violet, chevron = true, divider = true, onClick = { stack = stack + "detail" })
                }
                KelvinGroup { KelvinRow("Sign out of Koofr", danger = true, onClick = { dialog = true }) }

                KelvinSectionHeader("Controls")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    KelvinSegmented(listOf("Day", "Month", "Year"), view, { view = it })
                    KelvinSlider(exposure, { exposure = it }, "Exposure", thick = true, icon = KelvinIcons.Sun, valueText = "${(exposure * 100).toInt()}")
                    KelvinSlider(cache, { cache = it }, "Cache size", valueText = "${(cache * 8).toInt()} GB")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KelvinButton("Back up now", { toast = true })
                        KelvinButton("Add cloud", {}, kind = ButtonKind.Tinted, icon = KelvinIcons.Plus)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KelvinButton("Cancel", {}, kind = ButtonKind.Secondary)
                        KelvinButton("Delete", { deleted = true }, kind = ButtonKind.Danger, icon = KelvinIcons.Trash)
                        KelvinButton("See all", {}, kind = ButtonKind.Text)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KelvinPill("Ready", tone = Tone.Positive, icon = KelvinIcons.Check)
                        KelvinPill("Paused", tone = Tone.Caution, icon = KelvinIcons.Alert)
                        KelvinPill("Failed", tone = Tone.Critical, icon = KelvinIcons.X)
                    }
                    KelvinTextField(key, { key = it }, "API key", icon = KelvinIcons.Lock, error = if (key.length < 8) "That key is too short. Keys start with gsk_ and are 56 characters." else null)
                }

                KelvinSectionHeader("Air this week")
                KelvinGroup(contentPadding = 16.dp) {
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    val aqi = listOf(42, 58, 35, 71, 49, 28, 33)
                    KelvinBarChart(
                        days.mapIndexed { i, d -> KelvinBar(aqi[i] / 80f, d) },
                        day, { day = it },
                        valueText = { "${aqi[it]} AQI" },
                        caption = { names[it] },
                        average = aqi.average().toFloat() / 80f,
                        averageText = "avg ${aqi.average().roundToInt()}",
                    )
                }

                KelvinSectionHeader("Slide to confirm")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KelvinSlideToConfirm("Slide to back up now", { toast = true }, icon = KelvinIcons.Cloud)
                    KelvinSlideToConfirm("Slide to delete 3 photos", { deleted = true }, icon = KelvinIcons.Trash, destructive = true)
                }

                KelvinSectionHeader("Recorder")
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    KelvinFigure(
                        "%02d:%02d.%02d".format(elapsed / 60000, elapsed / 1000 % 60, elapsed / 10 % 100),
                        style = Kelvin.type.ticking(Kelvin.type.hero.copy(fontSize = Kelvin.type.numeral.fontSize * 1.4f)),
                    )
                    KText("High quality", style = Kelvin.type.summary, color = c.ink2)
                    KelvinControlBar(
                        leading = { KelvinIconButton(KelvinIcons.X, "Discard", {}) },
                        trailing = { KelvinIconButton(KelvinIcons.Adjustments, "Recording settings", {}) },
                    ) {
                        KelvinControlCapsule {
                            KelvinIconButton(KelvinIcons.SkipBack, "Back 5 seconds", {}, kind = IconButtonKind.Plain)
                            KelvinIconButton(if (playing) KelvinIcons.Pause else KelvinIcons.Play, if (playing) "Pause" else "Play", { playing = !playing }, kind = IconButtonKind.Plain)
                            KelvinIconButton(KelvinIcons.SkipForward, "Forward 5 seconds", {}, kind = IconButtonKind.Plain)
                        }
                    }
                }

                KelvinNotice("Indexing 1,204 photos", "You can keep browsing. Search gets better as this finishes.", progress = 0.38f)
                KelvinNotice("Couldn't reach pCloud", "Check your connection and try again.", tone = Tone.Critical, actionText = "Try again", onAction = {})

                KelvinStatRow(Modifier.padding(horizontal = 16.dp)) {
                    KelvinStatCard("Storage used", "86%", Modifier.weight(1f), hero = true, detail = "110 of 128 GB")
                    KelvinStatCard("Next flight", "06:40", Modifier.weight(1f), detail = "LH 490, Sunday")
                }

                KelvinSectionHeader("Inbox")
                KelvinGroup {
                    var starA by remember { mutableStateOf(false) }
                    var starB by remember { mutableStateOf(true) }
                    KelvinMessageRow("Koofr", "18:42", "Your vault is ready", "Koofr Vault is set up on this phone. Photos you add to Private are encrypted before they leave.", true, {},
                        attachment = true, badge = studio72f.kelvin.SenderBadge.Verified("Verified sender"), onStar = { starA = !starA }, starred = starA, starLabel = "Star")
                    KelvinMessageRow("Ana Marić", "Tue", "Bruges photos", "Added 32 photos to the shared album. The canal ones came out great.", false, {},
                        badge = studio72f.kelvin.SenderBadge.Vip("Favourite"), onStar = { starB = !starB }, starred = starB, starLabel = "Star")
                    KelvinMessageRow("Tomás Ruiz", "Mon", "Lisbon trip", "Booked the flat in Alfama. Sharing the album when we land.", true, {},
                        important = true, attachment = true)
                }
                KelvinSectionHeader("Swipe a message")
                KelvinGroup {
                    var archived by remember { mutableStateOf(0) }
                    KelvinSwipeRow(
                        leading = SwipeAction(KelvinIcons.Archive, "Archive", Kelvin.colors.positive) { archived++ },
                        trailing = SwipeAction(KelvinIcons.Trash, "Delete", Kelvin.colors.critical) { deleted = true },
                    ) {
                        KelvinMessageRow("Lena Fischer", "09:15", "Proofs are in", "The printer sent the proofs for the Bruges book. Two pages need a second look.", true, {}, attachment = true)
                    }
                }

                KelvinSectionHeader("Compose")
                KelvinGroup {
                    val to = remember { mutableStateListOf("ana@koofr.net", "Tomás Ruiz") }
                    KelvinRecipientField("To", to, { to.add(it) }, { to.removeAt(it) }, { "Remove $it" }, placeholder = "Add people")
                    Row(Modifier.kelvinCarousel(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KelvinAttachmentChip("Bruges-proofs.pdf", "2.4 MB", {}, icon = KelvinIcons.Book, tile = KelvinTiles.Red)
                        KelvinAttachmentChip("Canal.jpg", "840 KB", {}, icon = KelvinIcons.Photo, onRemove = {}, removeLabel = "Remove Canal.jpg")
                    }
                }

                KelvinSectionHeader("Podcasts")
                KelvinGroup {
                    var dl by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
                    LaunchedEffect(dl) {
                        if (dl == DownloadState.Queued) { kotlinx.coroutines.delay(700); dl = DownloadState.Downloading(0f) }
                        val d = dl
                        if (d is DownloadState.Downloading) { kotlinx.coroutines.delay(120); dl = if (d.progress >= 1f) DownloadState.Done else DownloadState.Downloading(d.progress + 0.05f) }
                    }
                    KelvinEpisodeRow("Slow Light", "The colour of a candle, and why 1,900 K feels like home", "Nov 4 · 42 min", {}, {}, "Play", unplayed = true,
                        download = dl, onDownload = { dl = if (dl == DownloadState.Idle) DownloadState.Queued else DownloadState.Idle }, downloadLabel = "Download") { Photo(4, Modifier.fillMaxSize()) }
                    KelvinEpisodeRow("Field Notes", "Mapping Lisbon by its tiles", "Oct 30 · 1 h 08 min", {}, {}, "Play", progress = 0.4f,
                        download = DownloadState.Done, onDownload = {}, downloadLabel = "Remove download") { Photo(1, Modifier.fillMaxSize()) }
                }
                var playing by remember { mutableStateOf(false) }
                var at by remember { mutableLongStateOf(754_000L) }
                LaunchedEffect(playing) { while (playing) { kotlinx.coroutines.delay(1000); at += 1000 } }
                KelvinMiniPlayer("Mapping Lisbon by its tiles", "Field Notes", playing, { playing = !playing }, {}, at / 2_520_000f, "Play", "Pause", null) { Photo(1, Modifier.fillMaxSize()) }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    KelvinScrubber(at, 2_520_000L, { at = it }, "Position in episode")
                }
                KelvinSectionHeader("Music, and a page of its own")
                var track by remember { mutableIntStateOf(2) }
                KelvinMiniPlayer("Golden Hour", "Album · Track ${track + 1}", playing, { playing = !playing }, {}, null, "Play", "Pause", null,
                    previous = TransportControl(KelvinIcons.SkipBack, "Previous track", { track-- }, track > 0),
                    next = TransportControl(KelvinIcons.SkipForward, "Next track", { track++ })) { Photo(3, Modifier.fillMaxSize()) }
                // A reader's sepia page: Kelvin's controls inked in the page's own colours.
                KelvinOnPage(ground = Color(0xFFF4ECD8), ink = Color(0xFF3B2F25)) {
                    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Kelvin.colors.canvas, KelvinShapes.lg).padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var wpm by remember { mutableIntStateOf(300) }
                        KelvinTransport(playing, { playing = !playing }, "Play", "Pause", size = 64.dp,
                            previous = TransportControl(KelvinIcons.SkipBack, "Previous chapter", {}, false),
                            back = TransportControl(KelvinIcons.TrackPrevious, "Back a sentence", {}),
                            forward = TransportControl(KelvinIcons.TrackNext, "Forward a sentence", {}),
                            next = TransportControl(KelvinIcons.SkipForward, "Next chapter", {}))
                        KelvinStepper(wpm, { wpm = it }, "Slower", "Faster", Modifier.padding(horizontal = 16.dp).fillMaxWidth(), range = 100..1000, step = 10,
                            format = { "$it wpm" }, caption = if (wpm < 250) "relaxed" else if (wpm < 400) "conversational" else "skimming", onValueClick = {}, fill = true)
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var note by remember { mutableStateOf(false) }
                    var noteAt by remember { mutableLongStateOf(14_000L) }
                    KelvinVoiceNote(note, { note = !note }, noteAt, 48_000L, { noteAt = it }, "Play", "Pause", "Voice note")
                }

                KelvinSectionHeader("Empty")
                KelvinEmptyState(KelvinIcons.Book, "Nothing saved yet", "Books you save show up here, ready when you are.",
                    actionText = "Browse books", onAction = {}, doodle = KelvinDoodle.SittingReading)

                KelvinSectionHeader("Sleep timer")
                KelvinGroup(contentPadding = 8.dp) {
                    var sleep by remember { mutableIntStateOf(3) }
                    KelvinWheelPicker(listOf("Off", "5 min", "10 min", "15 min", "30 min", "45 min", "1 h", "End of episode"), sleep, { sleep = it }, "Sleep timer")
                }

                KelvinSectionHeader("Choices")
                KelvinGroup {
                    var a by remember { mutableStateOf(true) }
                    var b by remember { mutableStateOf(false) }
                    var speed by remember { mutableIntStateOf(1) }
                    KelvinRow("Download on Wi-Fi only", trailing = { KelvinCheckbox(a, { a = it }) })
                    KelvinRow("Delete played episodes", trailing = { KelvinCheckbox(b, { b = it }) })
                    listOf("1×", "1.5×", "2×").forEachIndexed { i, label ->
                        KelvinRow(label, trailing = { KelvinRadio(speed == i, { speed = i }) })
                    }
                    var skip by remember { mutableIntStateOf(30) }
                    KelvinRow("Skip forward", trailing = { KelvinStepper(skip, { skip = it }, "Shorter", "Longer", range = 5..60, step = 5, format = { "$it s" }) })
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    var news by remember { mutableStateOf(true) }
                    var tech by remember { mutableStateOf(false) }
                    KelvinChip("News", selected = news, onClick = { news = !news })
                    KelvinChip("Tech", selected = tech, onClick = { tech = !tech })
                    KelvinChip("Mail", icon = KelvinIcons.Mail, onRemove = {}, removeLabel = "Remove Mail")
                    KelvinBadged(12) { KIcon(KelvinIcons.Bell, "Notifications", size = 26.dp) }
                }

                KelvinSectionHeader("Loading")
                KelvinGroup(contentPadding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        KelvinProgressBar(0.62f)
                        KelvinProgressBar(null)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            KelvinProgressRing(0.7f)
                            KelvinProgressRing(null)
                            KelvinAvatarStack(listOf("Ana Marić", "Tomás Ruiz", "Lena Fischer", "Koofr", "Sam Okafor"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            KelvinSkeleton(Modifier.size(44.dp), CircleShape)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                KelvinSkeleton(Modifier.fillMaxWidth(0.6f).height(14.dp))
                                KelvinSkeleton(Modifier.fillMaxWidth().height(12.dp))
                            }
                        }
                    }
                }
                KelvinGroup {
                    var open by remember { mutableStateOf(false) }
                    KelvinExpandable("Show notes", open, { open = !open }, summary = "Chapters, links and credits") {
                        KText("00:00 Candlelight · 07:40 Tungsten · 19:05 Daylight", style = Kelvin.type.summary, color = Kelvin.colors.ink2)
                    }
                }
                var page by remember { mutableFloatStateOf(0f) }
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KelvinPageIndicator(4, page)
                    KelvinSlider(page / 3f, { page = it * 3f }, "Page")
                }

                KelvinSectionHeader("Calendar")
                KelvinGroup(contentPadding = 12.dp) {
                    var ym by remember { mutableStateOf(2026 to 11) }
                    var dayPick by remember { mutableStateOf<Triple<Int, Int, Int>?>(Triple(2026, 11, 5)) }
                    KelvinMonthCalendar(
                        ym.first, ym.second, dayPick, { y, m, d -> dayPick = Triple(y, m, d) }, { y, m -> ym = y to m },
                        listOf("S", "M", "T", "W", "T", "F", "S"), "Previous month", "Next month",
                        today = Triple(2026, 11, 5),
                        marks = { y, m, d ->
                            if (y != 2026 || m != 11) null
                            else when (d) { in 12..14 -> DayMark(Tone.Critical, "Trip"); 20, 27 -> DayMark(Tone.Info, "New episode"); else -> null }
                        },
                    )
                }

                KelvinSectionHeader("Sign-in code")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var code by remember { mutableStateOf("42") }
                    KelvinCodeField(code, { code = it }, "Sign-in code", error = code.length == 6 && code != "123456", errorText = "That code didn't match. Check the newest email.")
                }

                KelvinSectionHeader("Compose formatting")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    var bold by remember { mutableStateOf(true) }
                    var italic by remember { mutableStateOf(false) }
                    var list by remember { mutableStateOf(false) }
                    KelvinFormatBar(listOf(
                        FormatAction(KelvinIcons.Bold, "Bold", bold) { bold = !bold },
                        FormatAction(KelvinIcons.Italic, "Italic", italic) { italic = !italic },
                        FormatAction(KelvinIcons.Underline, "Underline", false) {},
                        FormatAction(KelvinIcons.ListNumbers, "Numbered list", list) { list = !list },
                        FormatAction(KelvinIcons.Quote, "Quote", false) {},
                        FormatAction(KelvinIcons.Link, "Link", false) {},
                    ))
                }

                KelvinSectionHeader("Up next")
                KelvinGroup {
                    val queue = remember { mutableStateListOf("Mapping Lisbon by its tiles", "The colour of a candle", "Why trains hum", "Bruges at night") }
                    KelvinReorderList(queue, { it }, { from, to -> queue.add(to, queue.removeAt(from)) }, "Move up", "Move down") { item, handle, lifted ->
                        Row(Modifier.fillMaxWidth().background(Kelvin.colors.group).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Photo(queue.indexOf(item), Modifier.size(40.dp).clip(Kelvin.shape.sm))
                            KText(item, Modifier.weight(1f), Kelvin.type.rowTitle, maxLines = 1)
                            KelvinGrip(handle)
                        }
                    }
                }

                KelvinSectionHeader("Pull to refresh")
                KelvinGroup {
                    var refreshing by remember { mutableStateOf(false) }
                    LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(1600); refreshing = false } }
                    KelvinPullToRefresh(refreshing, { refreshing = true }, Modifier.height(260.dp)) {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(6) { i ->
                                KelvinMessageRow(listOf("Koofr", "Ana Marić", "Tomás Ruiz", "Lena Fischer", "pCloud", "Sam Okafor")[i], "0${9 - i}:1$i", "Message ${i + 1}", "Pull this list down from the top to refresh it.", i < 2, {})
                            }
                        }
                    }
                }

                KelvinSectionHeader("Player")
                KelvinGroup {
                    var p2 by remember { mutableStateOf(false) }
                    var pos by remember { mutableLongStateOf(1_204_000L) }
                    LaunchedEffect(p2) { while (p2) { kotlinx.coroutines.delay(1000); pos += 1000 } }
                    KelvinFullPlayer(
                        "Mapping Lisbon by its tiles", "Field Notes", pos, 4_080_000L, p2, { p2 = !p2 }, { pos = it },
                        { pos = (pos - 15_000).coerceAtLeast(0) }, { pos += 30_000 }, speed = "1.5×", sleep = "30 min",
                    ) { Photo(1, Modifier.fillMaxSize()) }
                }

                KelvinSectionHeader("At a glance")
                // With large text the ring and the tiles stack instead of sharing a cramped row.
                val tiles: @Composable (Modifier) -> Unit = { m ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        var wifi by remember { mutableStateOf(true) }
                        var dnd by remember { mutableStateOf(false) }
                        KelvinQuickTile(KelvinIcons.Wifi, "Wi-Fi only", wifi, { wifi = !wifi }, Modifier.fillMaxWidth())
                        KelvinQuickTile(KelvinIcons.Moon, "Quiet hours", dnd, { dnd = !dnd }, Modifier.fillMaxWidth(), tile = KelvinTiles.Violet)
                    }
                }
                if (largeText()) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KelvinRingMeter(0.86f, "86%", caption = "110 of 128 GB")
                    tiles(Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KelvinRingMeter(0.86f, "86%", caption = "110 of 128 GB")
                    tiles(Modifier.weight(1f))
                }
                KelvinActionList(listOf(
                    ListAction(KelvinIcons.Send, "Forward", {}),
                    ListAction(KelvinIcons.Folder, "Move to folder", {}, KelvinTiles.Orange),
                    ListAction(KelvinIcons.Archive, "Archive", {}, KelvinTiles.Green),
                    ListAction(KelvinIcons.Trash, "Delete", {}, destructive = true),
                ))

                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { i -> Photo(i, Modifier.weight(1f).aspectRatio(1f)) }
                }
                KelvinDockSpacer()
            }
            // Search lives in the capsule under the opening, within the thumb's reach; a second
            // search circle in the top corner would be the hardest target on the screen.
            KelvinReachBar(kelvinWordmark("72F", "Kelvin"), reach, page)
          }

            Column(Modifier.align(Alignment.BottomCenter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleted) KelvinToast("3 photos deleted", page, actionText = "Undo", onAction = { deleted = false })
                else if (toast) KelvinToast("Backed up 24 photos to Koofr", page, actionText = "View", onAction = { toast = false })
                KelvinDock(
                    listOf(
                        DockItem("Photos", KelvinIcons.Photo),
                        DockItem("Albums", KelvinIcons.Album),
                        DockItem("Memories", KelvinIcons.Calendar, badge = true),
                        DockItem("Settings", KelvinIcons.Settings),
                    ),
                    tab, { tab = it }, page,
                    action = { KelvinActionOrb(KelvinIcons.Sparkles, "Ask", { toast = !toast }, backdrop = page) },
                )
            }

            KelvinSheet(sheet, { sheet = false }, "Accent", "Close", page, onConfirm = { sheet = false }, confirmLabel = "Done") {
                val options = listOf<Accent>(Accent.Ember) + AccentPreset.entries.map { Accent.Preset(it) } + Accent.Graphite
                for (o in options) {
                    val on = o == accent
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(Kelvin.shape.md)
                            .background(if (on) c.accentSoft else Color.Transparent)
                            .kelvinPress({ accent = o })
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KelvinTheme(dark = dark, accent = o, glass = glass) { Box(Modifier.size(22.dp).clip(Kelvin.shape.full).background(Kelvin.colors.accent)) }
                        KText(o.name(), Modifier.weight(1f), Kelvin.type.rowTitle, if (on) c.onAccentSoft else c.ink)
                        if (on) KIcon(KelvinIcons.Check, null, size = 20.dp, color = c.accent)
                    }
                }
            }

            KelvinDialog(
                dialog, { dialog = false }, "Sign out of Koofr?",
                "Photos stay in Koofr. This phone stops showing them until you sign in again.",
                "Sign out", { dialog = false }, "Cancel", page, destructive = true,
            )
        }
        }
    }
}

/** A detail screen to push onto the catalog's stack: swipe from either edge to go back. */
@Composable
private fun SearchDetail(onBack: () -> Unit) {
    val c = Kelvin.colors
    Column(Modifier.fillMaxSize().background(c.canvas).verticalScroll(rememberScrollState())) {
        KelvinDetailHeader("Smart search", onBack, "Back", summary = "On-device, 1,204 photos indexed")
        KelvinSectionHeader("Index")
        KelvinGroup {
            KelvinRow("Photos indexed", value = "1,204")
            KelvinRow("Last run", value = "18:42")
            KelvinRow("Search text in photos", trailing = { KelvinSwitch(true, {}) })
        }
        KelvinSectionHeader("Recent")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i -> Photo(i, Modifier.weight(1f).aspectRatio(1f)) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i -> Photo(i + 3, Modifier.weight(1f).aspectRatio(1f)) }
        }
        KText("Swipe in from either edge and hold: this screen lifts into a card and follows your finger.", Modifier.padding(24.dp), Kelvin.type.summary, c.ink2)
    }
}

private fun Accent.name(): String = when (this) {
    Accent.Ember -> "Ember"
    Accent.Graphite -> "Graphite"
    is Accent.Preset -> preset.name
}
