package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The type scale, and the Flat finish's strokeless buttons, drawn without a window. With
 * `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class ScaleRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the scale has ten sizes and no two a point apart`() {
        val t = KelvinType.of(KelvinFonts.Kelvin)
        val m = t.material
        val sizes = listOf(
            t.hero, t.masthead, t.numeral, t.display, t.tab, t.headline, t.title, t.name, t.action, t.figure, t.section,
            t.eyebrow, t.rowTitle, t.body, t.label, t.summary, t.caption, t.micro,
            m.displayLarge, m.displayMedium, m.displaySmall, m.headlineLarge, m.headlineMedium, m.headlineSmall,
            m.titleLarge, m.titleMedium, m.titleSmall, m.bodyLarge, m.bodyMedium, m.bodySmall,
            m.labelLarge, m.labelMedium, m.labelSmall,
        ).map { it.fontSize.value.toInt() }.distinct().sorted()

        assertEquals(listOf(11, 12, 14, 16, 20, 24, 28, 34, 44, 56), sizes)
    }

    @Test
    fun `the scale and the flat buttons draw in light and dark`() {
        draw("scale-light", dark = false)
        draw("scale-dark", dark = true)
    }

    private fun draw(name: String, dark: Boolean) {
        val scene = ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, finish = KelvinFinish.Flat, corners = KelvinCorners.Firm) {
                val t = Kelvin.type
                Row(Modifier.fillMaxSize().background(Kelvin.colors.canvas).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KText("display 28", style = t.display)
                        KText("tab 24", style = t.tab)
                        KText("headline 20", style = t.headline)
                        KText("title 16", style = t.title)
                        KText("name 14, a sender or a notice", style = t.name)
                        KText("row-title 14, the first line of a row", style = t.rowTitle)
                        KText("body 14, what a person reads in sentences.", style = t.body)
                        KText("action 14", style = t.action)
                        KText("summary 12, what explains the line above it.", style = t.summary, color = Kelvin.colors.ink2)
                        KText("label 12", style = t.label)
                        KText("section 12", style = t.section, color = Kelvin.colors.ink2)
                        KText("caption 12", style = t.caption, color = Kelvin.colors.ink2)
                        KelvinEyebrow("eyebrow 11")
                        KText("micro 11", style = t.micro)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            KelvinButton("Reply", {}, icon = KelvinIcons.Reply)
                            KelvinButton("Archive", {}, kind = ButtonKind.Secondary, icon = KelvinIcons.Archive)
                            KelvinIconButton(KelvinIcons.Search, "Search", {})
                        }
                        KelvinGroup(gutter = 0.dp) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KelvinButton("In a group", {}, kind = ButtonKind.Secondary)
                                KelvinButton("Tinted", {}, kind = ButtonKind.Tinted)
                            }
                        }
                        KelvinSegmented(listOf("All", "Unread", "Starred"), 0, {})
                        KelvinMessageRow("Lena Fischer", "Sun", "Proofs are in", "The printer sent the proofs for the Bruges book.", unread = true, onClick = {})
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

    private companion object {
        const val SCALE = 2
        const val WIDTH = 1000
        const val HEIGHT = 520
        const val MIN_BYTES = 10_000
    }
}
