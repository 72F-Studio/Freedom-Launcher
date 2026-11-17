package studio72f.kelvin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draws a desk app's opening ([KelvinDeskIntro] with [KelvinDeskPoint]s, keys and choice cards) in every style
 * Kelvin has: both finishes, both corner scales, light and dark. With `-Dkelvin.screenshots=<dir>` the pictures are
 * saved there.
 */
class DeskIntroRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the desk opening draws in every finish, corner scale and theme`() {
        for (finish in KelvinFinish.entries) {
            for (corners in KelvinCorners.entries) {
                for (dark in listOf(false, true)) {
                    val style = "${finish.name.lowercase()}-${corners.name.lowercase()}-${if (dark) "dark" else "light"}"
                    draw("desk-intro-points-$style", finish, corners, dark, step = 0)
                    draw("desk-intro-choices-$style", finish, corners, dark, step = 2)
                }
            }
        }
    }

    private fun draw(name: String, finish: KelvinFinish, corners: KelvinCorners, dark: Boolean, step: Int) {
        val scene = ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            KelvinTheme(dark = dark, finish = finish, corners = corners) {
                if (step == 0) Points() else Choices()
            }
        }
        val png = try {
            // The step arrives over a moment: draw it once it has.
            for (frame in 0 until FRAMES) scene.render(frame * FRAME_NANOS)
            scene.render(FRAMES * FRAME_NANOS).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
        } finally {
            scene.close()
        }
        assertTrue(png.size > MIN_BYTES)
        out?.let { File(it).apply { mkdirs() }.resolve("$name.png").writeBytes(png) }
    }

    @androidx.compose.runtime.Composable
    private fun Points() = KelvinDeskIntro(
        title = "Welcome to Lumen",
        summary = "Your notes, calm and quick, on this computer.",
        step = 0,
        steps = 3,
        onSkip = {},
        skipText = "Skip",
        actions = { KelvinButton("Continue", {}) },
        content = {
            KelvinDeskPoint(KelvinIcons.Cloud, "Works offline", summary = "Everything you open stays here.")
            KelvinDeskPoint(KelvinIcons.Lock, "Yours alone", summary = "No ads and no tracking.")
            KelvinDeskPoint(KelvinIcons.Plus, "A new note", summary = "From anywhere.", trailing = { KelvinKbd("Ctrl N") })
        },
    ) { KelvinGlassTile(KelvinIcons.Book, Kelvin.colors.accent, size = DeskIntroArt) }

    @androidx.compose.runtime.Composable
    private fun Choices() = KelvinDeskIntro(
        title = "Add your account",
        summary = "Pick where your notes live.",
        step = 2,
        steps = 3,
        onBack = {},
        backText = "Back",
        actions = { KelvinButton("Look around first", {}, kind = ButtonKind.Secondary) },
        content = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KelvinChoiceCard("Cloud", KelvinIcons.Cloud, Kelvin.colors.accent, {}, Modifier.weight(1f), "Sign in with your browser")
                KelvinChoiceCard("Folder", KelvinIcons.Folder, Kelvin.colors.accent, {}, Modifier.weight(1f), "Any folder on this computer")
            }
        },
    ) { KelvinGlassTile(KelvinIcons.User, Kelvin.colors.accent, size = DeskIntroArt) }

    private companion object {
        const val WIDTH = 1100
        const val HEIGHT = 760
        const val MIN_BYTES = 10_000
        const val FRAMES = 30L
        const val FRAME_NANOS = 16_000_000L
    }
}
