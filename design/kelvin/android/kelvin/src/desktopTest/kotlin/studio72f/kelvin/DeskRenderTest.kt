package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draws the desk-sized pieces (the rail and the one-line lists) without a window. With `-Dkelvin.screenshots=<dir>`
 * the pictures are saved there.
 */
class DeskRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `a rail beside a list of bundles and one-line messages draws in light and dark`() {
        draw("desk-light", dark = false)
        draw("desk-dark", dark = true)
    }

    private fun draw(name: String, dark: Boolean) {
        val scene = ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, finish = KelvinFinish.Flat, corners = KelvinCorners.Firm) {
                Row(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
                    KelvinRail {
                        KelvinRailItem(KelvinIcons.Edit, "Write", {}, primary = true)
                        Spacer(Modifier.size(12.dp))
                        KelvinRailItem(KelvinIcons.Inbox, "Inbox", {}, selected = true, dot = true)
                        KelvinRailItem(KelvinIcons.Send, "Sent", {})
                        KelvinRailItem(KelvinIcons.Trash, "Trash", {})
                        Spacer(Modifier.weight(1f))
                        KelvinAvatar("Sam Kapoor", Modifier.size(36.dp))
                    }
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KelvinBundleLine("Money due", KelvinIcons.Receipt, 2, "Brightline Energy  ·  Nimbus Broadband", {}, unread = true)
                        KelvinBundleLine("Updates", KelvinIcons.Bell, 3, "Parcelwise  ·  Tessera Cloud", {}, expanded = true)
                        KelvinLineHeading("Today")
                        KelvinMailLine("Ananya Rao", "9:40 AM", "Final cover", "Could you approve the final cover by Wednesday?", unread = true, onClick = {})
                        KelvinMailLine(
                            "Harbor Payments", "7:30 AM", "Verify your identity", "Your payouts are on hold.", unread = false, onClick = {},
                            selected = true, tag = MessageTag("Account", Tone.Caution, KelvinIcons.Shield),
                        )
                        KelvinLineHeading("Earlier this week")
                        KelvinMailLine("Lena Fischer", "Sun", "Proofs are in", "The printer sent the proofs.", unread = false, onClick = {}, compact = true)
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
        const val HEIGHT = 420
        const val MIN_BYTES = 10_000
    }
}
