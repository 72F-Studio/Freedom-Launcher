package com.studio72f.eigen.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import studio72f.kelvin.Accent
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KelvinBackdrop
import studio72f.kelvin.KelvinTheme
import studio72f.kelvin.kelvinBackdrop
import studio72f.kelvin.rememberKelvinBackdrop

/** Entry point for MainActivity: a ComposeView showing the three pages over the wallpaper. */
object EigenUi {
    @JvmStatic
    fun create(activity: Activity, state: EigenState, host: EigenHost): ComposeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { EigenApp(state, host) }
    }
}

/**
 * Kelvin, with Eigen's accent. Kesar and its warm siblings are set straight into the palette (Kelvin's dial has
 * no saffron); Greyscale uses Kelvin's own Graphite. Eigen is always dark: it sits on a wallpaper.
 */
@Composable
fun EigenTheme(state: EigenState, content: @Composable () -> Unit) {
    val accent = Color(state.accent)
    val grey = state.greyscale
    KelvinTheme(
        dark = true,
        accent = if (grey) Accent.Graphite else Accent.Ember,
        glass = state.glass,
        reduceMotion = state.reduceMotion,
        adjust = { c ->
            if (grey) c else c.copy(
                accent = accent,
                onAccent = Color(0xFF140C05),
                accentSoft = lerp(c.group, accent, 0.22f),
                onAccentSoft = accent,
                horizon = accent.copy(alpha = c.horizon.alpha),
                glassHeat = lerp(accent, Color.White, 0.3f),
            )
        },
        content = content,
    )
}

@Composable
fun EigenApp(state: EigenState, host: EigenHost) {
    LaunchedEffect(Unit) { com.studio72f.eigen.ai.EigenAssistant.refreshStatus() }
    EigenTheme(state) {
        // Glass refracts the wallpaper layer only; the pages themselves are never recorded (no glass on glass).
        val backdrop = rememberKelvinBackdrop(ground = if (state.wallpaperMode == "system") Color.Black.copy(alpha = 0.3f) else Color.Black)
        Box(Modifier.fillMaxSize()) {
            Wallpaper(state, Modifier.fillMaxSize().kelvinBackdrop(backdrop))
            Pages(state, host, backdrop)
            SettingsOverlay(state, host)
        }
    }
}

@Composable
private fun Pages(state: EigenState, host: EigenHost, backdrop: KelvinBackdrop) {
    val pager = rememberPagerState(initialPage = state.page) { 3 }
    val spatial = Kelvin.motion.spatial<Float>()
    // The activity (Back, Home, menus) asks for a page; a swipe tells the state where we are.
    LaunchedEffect(state.pageRequest) { if (pager.currentPage != state.page) pager.animateScrollToPage(state.page, animationSpec = spatial) }
    // A rebuilt view can restore the pager's old saved page; the state's page wins.
    LaunchedEffect(pager) {
        if (pager.currentPage != state.page) pager.scrollToPage(state.page)
        snapshotFlow { pager.settledPage }.collect { state.page = it }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
        when (page) {
            EigenState.PAGE_LEFT -> LeftPage(state, host, backdrop)
            EigenState.PAGE_HOME -> HomePage(state, host, backdrop)
            else -> WidgetsPage(state, host, backdrop)
        }
    }
}

/**
 * The wallpaper as Eigen shows it: HyperOS-strength blur with a touch of extra saturation so colour survives it,
 * dimmed for legibility and a little more toward the bottom, or fully desaturated in Greyscale. Solid mode gets a
 * faint warm glow from the top in the accent. System mode stays transparent: Android draws it, unblurred.
 */
@Composable
private fun Wallpaper(state: EigenState, modifier: Modifier) {
    val accent = Kelvin.colors.accent
    val modern = Build.VERSION.SDK_INT >= 31
    val photo: Bitmap? = if (state.wallpaperMode == "photo") (if (modern) state.wallpaper else state.softWallpaper ?: state.wallpaper) else null
    Box(modifier) {
        when {
            photo != null -> {
                val image = remember(photo) { photo.asImageBitmap() }
                val matrix = remember(state.greyscale) { ColorMatrix().apply { setToSaturation(if (state.greyscale) 0f else 1.15f) } }
                Image(
                    image, null,
                    Modifier.fillMaxSize().then(if (modern && state.blur > 0) Modifier.blur(state.blur.dp, BlurredEdgeTreatment.Rectangle) else Modifier),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(matrix),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    Color.Black.copy(alpha = state.dim * 0.8f), Color.Black.copy(alpha = state.dim), Color.Black.copy(alpha = (state.dim + 0.18f).coerceAtMost(0.95f)),
                ))))
            }
            state.wallpaperMode == "system" -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dim * 0.6f)))
            else -> Box(
                Modifier.fillMaxSize().background(Color.Black).drawBehind {
                    drawRect(Brush.radialGradient(
                        listOf(accent.copy(alpha = if (state.greyscale) 0.08f else 0.20f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f), radius = size.width * 1.2f,
                    ))
                },
            )
        }
    }
}
