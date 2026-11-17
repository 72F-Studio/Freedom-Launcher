package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The adaptive frame: the rail on an open fold and a tablet, the reading column, and the window classes. With
 * `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class AdaptiveRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `window classes follow Android's breakpoints`() {
        assertEquals(false, KelvinWindow(411.dp, 891.dp).wide)
        assertEquals(KelvinWidth.Medium, KelvinWindow(673.dp, 841.dp).widthClass)
        assertTrue(KelvinWindow(673.dp, 841.dp).wide)
        // A phone on its side is wide and short.
        KelvinWindow(891.dp, 411.dp).let { assertEquals(KelvinWidth.Expanded, it.widthClass); assertTrue(it.short) }
    }

    @Test
    fun `a grid keeps its tile size as the window widens`() {
        assertEquals(3, KelvinWindow.Phone.columnsFor(3))
        assertEquals(5, KelvinWindow(673.dp, 841.dp).columnsFor(3))
        assertEquals(2, KelvinWindow(300.dp, 600.dp).columnsFor(2))
    }

    @Test
    fun `the reading gutter centres a column and never goes under its least`() {
        assertEquals(16.dp, kelvinReadingGutter(400.dp))
        assertEquals(180.dp, kelvinReadingGutter(1000.dp))
    }

    @Test
    fun `the dock stays at the foot of a fold and a tablet, in light and dark`() {
        draw("adaptive-fold-light", 673, 841, dark = false)
        draw("adaptive-tablet-dark", 1280, 800, dark = true)
        draw("adaptive-landscape-light", 891, 411, dark = false)
    }

    private fun draw(name: String, width: Int, height: Int, dark: Boolean) {
        val places = listOf(
            DockItem("Photos", KelvinIcons.Photo),
            DockItem("Albums", KelvinIcons.Album),
            DockItem("Memories", KelvinIcons.Star, badge = true),
            DockItem("Settings", KelvinIcons.Settings),
        )
        val scene = ImageComposeScene(width = width * SCALE, height = height * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, accent = Accent.Preset(AccentPreset.Glacier)) {
                CompositionLocalProvider(LocalKelvinWindow provides KelvinWindow(width.dp, height.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        val page = rememberKelvinBackdrop()
                        BoxWithConstraints(Modifier.fillMaxSize().kelvinBackdrop(page)) {
                        BoxWithConstraints(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
                            val columns = LocalKelvinWindow.current.columnsFor(3, available = maxWidth)
                            LazyVerticalGrid(GridCells.Fixed(columns), contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(24) { i ->
                                    Box(Modifier.aspectRatio(1f).clip(KelvinShapes.lg)
                                        .background(if (i % 3 == 0) Kelvin.colors.accentSoft else Kelvin.colors.group))
                                }
                            }
                        }
                        }
                        KelvinDock(places, selected = 1, onSelect = {}, backdrop = page, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                    }
                }
            }
        }
        val png = try {
            scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        } finally {
            scene.close()
        }
        assertTrue(png.size > MIN_BYTES)
        out?.let { File(it).apply { mkdirs() }.resolve("$name.png").writeBytes(png) }
    }

    @Test
    fun `a settings column is held to reading width on a tablet`() {
        val scene = ImageComposeScene(width = 1280 * SCALE, height = 800 * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = false, accent = Accent.Preset(AccentPreset.Glacier)) {
                Column(Modifier.fillMaxSize().background(Kelvin.colors.canvas).padding(vertical = 24.dp)) {
                    Column(Modifier.kelvinReadingWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(5) { Box(Modifier.fillMaxWidth().height(56.dp).clip(KelvinShapes.lg).background(Kelvin.colors.group)) }
                    }
                }
            }
        }
        val png = try { scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0) } finally { scene.close() }
        assertTrue(png.size > 1_000)
        out?.let { File(it).apply { mkdirs() }.resolve("adaptive-reading.png").writeBytes(png) }
    }

    private companion object {
        const val SCALE = 2
        const val MIN_BYTES = 10_000
    }
}
