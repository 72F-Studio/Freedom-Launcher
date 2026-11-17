package studio72f.kelvin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draws the stat cards and an offer notice on a coloured backdrop like 72F Mail's Today page (soft glows of sky,
 * lilac and sun), light and dark, both finishes, so toned cards are judged on what they sit on rather than on a
 * plain page. With `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class BackdropRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the stat cards draw on a coloured backdrop in every finish and theme`() {
        for (finish in KelvinFinish.entries) {
            for (dark in listOf(false, true)) {
                draw("backdrop-${finish.name.lowercase()}-${if (dark) "dark" else "light"}", finish, dark)
            }
        }
    }

    private fun draw(name: String, finish: KelvinFinish, dark: Boolean) {
        val glows = if (dark) DARK_GLOWS else LIGHT_GLOWS
        val scene = ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, accent = Accent.Custom(KelvinAzure.light, KelvinAzure.dark), finish = finish, corners = KelvinCorners.Firm) {
                Box(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.width * 1.1f
                        glows.forEachIndexed { i, color ->
                            val center = Offset(size.width * HOMES_X[i], size.height * HOMES_Y[i])
                            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
                        }
                    }
                    val strongest = if (dark) glows.maxBy { it.luminanceOf() } else glows.minBy { it.luminanceOf() }
                    KelvinOnBackdrop(strongest) {
                        Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KelvinStatRow(Modifier.padding(horizontal = 16.dp)) {
                                KelvinStatCard("Needs you", "6", Modifier.weight(1f), detail = "17 unread", tone = Tone.Critical)
                                KelvinStatCard("Due soon", "₹4,018", Modifier.weight(1f), detail = "3 to pay", tone = Tone.Caution)
                            }
                            KelvinStatRow(Modifier.padding(horizontal = 16.dp)) {
                                KelvinStatCard("Needs you", "2", Modifier.weight(1f), detail = "5 unread", tone = Tone.Neutral)
                                KelvinStatCard("Paid", "₹1,250", Modifier.weight(1f), detail = "2 this month", tone = Tone.Positive)
                            }
                            KelvinNotice(
                                title = "Write Today on this phone",
                                message = "A one-time 557 MB download lets your phone summarise your day and each email.",
                                tone = Tone.Accent,
                                icon = KelvinIcons.Sparkles,
                                actionText = "Download",
                                onAction = {},
                            )
                        }
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

    private fun Color.luminanceOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private companion object {
        const val SCALE = 2
        const val WIDTH = 400
        const val HEIGHT = 520
        const val MIN_BYTES = 10_000
        // 72F Mail's Today backdrop: sky near the top, lilac across the middle, sun near the foot.
        val LIGHT_GLOWS = listOf(Color(0xFFC4DBFF), Color(0xFFE6D0FA), Color(0xFFFFE2B0))
        val DARK_GLOWS = listOf(Color(0xFF1A2C4A), Color(0xFF2E2040), Color(0xFF382E18))
        val HOMES_X = floatArrayOf(0.1f, 0.95f, 0.25f)
        val HOMES_Y = floatArrayOf(0.08f, 0.48f, 0.9f)
    }
}
