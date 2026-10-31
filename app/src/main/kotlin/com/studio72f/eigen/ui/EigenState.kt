package com.studio72f.eigen.ui

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.studio72f.eigen.MainActivity

/** One line in the Assistant tab. [source] is set on Eigen's finished answers. */
data class ChatLine(val mine: Boolean, val text: String, val source: String? = null, val failed: Boolean = false)

/** Pill actions anchored at a point (a long-press): optional lifted app card and its bounds in the window. */
data class PillMenu(val x: Float, val y: Float, val items: List<Pair<String, () -> Unit>>, val app: MainActivity.App? = null, val cardBounds: androidx.compose.ui.geometry.Rect? = null)

/** A drawer on home: its name and the apps in it, in order. */
data class Drawer(val name: String, val apps: List<MainActivity.App>)

/** What the Compose UI asks of the activity, which still owns apps, widgets, settings and dialogs. */
interface EigenHost {
    fun launch(app: MainActivity.App)
    /** Launches with a clip-reveal from where the card sits (window pixels). */
    fun launchFrom(app: MainActivity.App, left: Int, top: Int, width: Int, height: Int)
    fun appMenu(app: MainActivity.App)
    fun homeMenu()
    fun openSettings()
    fun addWidget()
    fun openAlarms()
    fun media(action: Int)
    fun openNotificationAccess()
    fun setGlassLevel(level: Float)
    fun setFrost(on: Boolean)
    fun setFrostLevel(level: Float)
    fun isDefaultHome(): Boolean
    fun makeDefaultHome()
    fun setColour(index: Int)
    fun setClock(style: String)
    fun chooseWallpaper()
    fun finishTour()
    fun contexts(): com.studio72f.eigen.ContextsHost
    fun isFavorite(app: MainActivity.App): Boolean
    fun toggleFavorite(app: MainActivity.App)
    fun moveApp(app: MainActivity.App)
    fun addDrawer()
    fun openWallpaper()
    fun appInfo(app: MainActivity.App)
    fun uninstall(app: MainActivity.App)
    fun setCardStyle(app: MainActivity.App, style: String)
    fun setWide(app: MainActivity.App, wide: Boolean)
    fun setConversationApps(packages: Set<String>)
    fun requestUsageAccess()
    fun toggleDock(app: MainActivity.App)
    fun openCalendar()
    fun drawerOf(app: MainActivity.App): String
    fun useWallpaper(bitmap: android.graphics.Bitmap)
    fun setDim(dim: Float)
    /** Saves the widgets page's order (Eigen widget ids and "widget:<id>" app widgets). */
    fun setWidgetOrder(order: List<String>)
    /** Removes a widget from the page: an Eigen widget is hidden, an app widget is unbound. */
    fun removeWidget(id: String)
    fun hasDot(app: MainActivity.App): Boolean
    fun buyPlus()
    fun restorePlus()
}

/**
 * Everything the Compose UI shows, set by MainActivity (Java) and read as Compose state. UI-only state that the
 * activity must be able to reset (search, the open drawer, the current page) lives here too.
 */
class EigenState {
    /** Work that must outlive any one screen (an assistant answer still streaming when the UI is rebuilt). */
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    // Eigen Plus
    var plus by mutableStateOf(false)
    /** Play's price for Plus once known, in the local currency. */
    var plusPrice by mutableStateOf("₹349")
    /** null: closed; "": opened from Settings; otherwise what was tapped ("Binary"), named at the top. */
    var plusSheet by mutableStateOf<String?>(null)

    // Apps
    var drawers by mutableStateOf(emptyList<Drawer>())
    var allApps by mutableStateOf(emptyList<MainActivity.App>())
    /** Bumped when notification dots change so cards redraw. */
    var dotsVersion by mutableIntStateOf(0)

    // Look
    var accent by mutableIntStateOf(0xFFFF8A1F.toInt())
    var monoIcons by mutableStateOf(false)
    var glass by mutableFloatStateOf(0.3f)
    /** Kelvin's Frost texture (deep ground glass) instead of Liquid glass; Eigen's default look. */
    var frost by mutableStateOf(true)
    /** Frost intensity, 0 Opaque … 1 Sheer; kept apart from the Liquid glass level. */
    var frostLevel by mutableFloatStateOf(0.75f)
    var reduceMotion by mutableStateOf(false)
    var clockStyle by mutableStateOf("ratio")

    // Wallpaper drawn inside Eigen (blurred, dimmed, greyscale in Greyscale mode). "system" shows Android's.
    var wallpaperMode by mutableStateOf("solid")
    var wallpaper by mutableStateOf<Bitmap?>(null)
    /** Pre-blurred copy for Android 11 and older, which have no RenderEffect blur. */
    var softWallpaper by mutableStateOf<Bitmap?>(null)
    var dim by mutableFloatStateOf(0.75f)
    var blur by mutableIntStateOf(32)

    // Widgets page
    /** App widgets hosted by the activity, by "widget:<id>". */
    var hostedWidgets by mutableStateOf(emptyMap<String, View>())
    /** What the widgets page shows, top to bottom; it stacks from the bottom, by the thumb. */
    var widgetOrder by mutableStateOf(emptyList<String>())
    /** The focus timer: when it ends (running) or how long is left (paused). */
    var focusEndsAt by mutableLongStateOf(0L)
    var focusLeftMs by mutableLongStateOf(25 * 60_000L)
    var alarm by mutableStateOf<String?>(null)
    var agenda by mutableStateOf<String?>(null)
    var mediaTitle by mutableStateOf<String?>(null)
    var mediaArtist by mutableStateOf<String?>(null)
    var mediaPlaying by mutableStateOf(false)
    var notificationAccess by mutableStateOf(false)

    // UI state the activity can reset
    var page by mutableIntStateOf(PAGE_HOME)
    var pageRequest by mutableIntStateOf(0)
    /** Drawers open on home; several can be open at once. */
    var openDrawers by mutableStateOf(emptySet<String>())
    /** True while open/close all runs: drawers glide open without each card's own entrance. */
    var bulkOpening by mutableStateOf(false)
    var appDrawerOpen by mutableStateOf(false)
    /** How far the app drawer is open, 0 … 1, live while the finger pulls; the wallpaper frosts in step. */
    var drawerProgress by mutableFloatStateOf(0f)
    var pillMenu by mutableStateOf<PillMenu?>(null)
    var dashboardApp by mutableStateOf<MainActivity.App?>(null)
    /** How a collapsed drawer shows unread notifications: dash, dot, colour or off. */
    var dotStyle by mutableStateOf("dash")
    /** Per-app card fill (classic, marker, bright) and two-column cards, by app id. */
    var cardStyles by mutableStateOf(emptyMap<String, String>())
    var wideCards by mutableStateOf(emptySet<String>())
    /** Up to four app ids pinned to the dock above the search capsule. */
    var dockIds by mutableStateOf(emptyList<String>())
    /** The clock style sheet, opened from a long-press on the clock. */
    var clockSheet by mutableStateOf(false)
    /** Eigen's shader textures, opened from the wallpaper menu. */
    var textureSheet by mutableStateOf(false)
    /** Cards per row in drawers: 4 (roomy), 5 or 6 (compact). */
    var columns by mutableIntStateOf(4)
    /** How much the home wallpaper is blurred: 0 sharp, 0.5 soft, 1 frosted. */
    var homeBlur by mutableFloatStateOf(0f)
    /** Black and white over everything, set by the activity on its window. */
    var monochrome by mutableStateOf(false)
    /** Minutes in the foreground today, by package; empty without usage access. */
    var usageMinutes by mutableStateOf(emptyMap<String, Int>())
    var usageAccess by mutableStateOf(false)
    /** Bumped on every resume so usage and similar live data reload. */
    var resumeCount by mutableIntStateOf(0)
    /** Bumped when home comes back from an app, so it settles in. */
    var returnPulse by mutableIntStateOf(0)
    /** Packages whose notifications feed Conversations; empty means all. */
    var conversationApps by mutableStateOf(emptySet<String>())
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var leftTab by mutableIntStateOf(TAB_CONVERSATIONS)
    /** A question handed to the Assistant tab (from search or the home menu), consumed once. */
    var pendingQuestion by mutableStateOf<String?>(null)
    /** The Assistant conversation. Kept here so it survives the page leaving the screen; never written to disk. */
    val chat = mutableStateListOf<ChatLine>()
    var thinking by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    /** null: closed; "": the contexts list; otherwise the id of the context being edited. */
    var contextsPage by mutableStateOf<String?>(null)
    /** Bumped whenever a context changes, so pages that read the (plain Java) rules redraw. */
    var contextsVersion by mutableIntStateOf(0)
    /** The first-run tour's page, or -1 when it is not showing. */
    var tourPage by mutableIntStateOf(-1)
    var settings by mutableStateOf(emptyList<SettingsSection>())
    /** Set to put the cursor in the Assistant's composer (opening it from search or the menu); cleared once used. */
    var composerFocus by mutableStateOf(false)

    val greyscale: Boolean get() {
        val r = (accent shr 16) and 0xFF; val g = (accent shr 8) and 0xFF; val b = accent and 0xFF
        return kotlin.math.abs(r - g) < 12 && kotlin.math.abs(g - b) < 12
    }

    /** Opens one drawer on its own (a context's drawer when Home is pressed). */
    fun openOnly(name: String) { openDrawers = setOf(name) }

    fun showPage(target: Int) { page = target; pageRequest++ }

    fun openAssistant(question: String?) {
        searching = false; query = ""
        leftTab = TAB_ASSISTANT
        if (!question.isNullOrBlank()) pendingQuestion = question else composerFocus = true
        showPage(PAGE_LEFT)
    }

    /** The Home button: back to a closed, centred home. */
    fun goHome() { plusSheet = null; contextsPage = null; settingsOpen = false; searching = false; query = ""; openDrawers = emptySet(); appDrawerOpen = false; pillMenu = null; dashboardApp = null; composerFocus = false; showPage(PAGE_HOME) }

    /** Back, innermost first. Returns false when there was nothing to close. */
    fun handleBack(): Boolean = when {
        tourPage > 0 -> { tourPage--; true }
        plusSheet != null -> { plusSheet = null; true }
        pillMenu != null -> { pillMenu = null; true }
        clockSheet -> { clockSheet = false; true }
        textureSheet -> { textureSheet = false; true }
        dashboardApp != null -> { dashboardApp = null; true }
        contextsPage != null -> { contextsPage = if (contextsPage!!.isEmpty()) null else ""; true }
        settingsOpen -> { settingsOpen = false; true }
        searching -> { searching = false; query = ""; true }
        page != PAGE_HOME -> { showPage(PAGE_HOME); true }
        appDrawerOpen -> { appDrawerOpen = false; true }
        openDrawers.isNotEmpty() -> { openDrawers = emptySet(); true }
        else -> false
    }

    companion object {
        const val PAGE_LEFT = 0
        const val PAGE_HOME = 1
        const val PAGE_WIDGETS = 2
        const val TAB_CONVERSATIONS = 0
        const val TAB_ASSISTANT = 1
    }
}
