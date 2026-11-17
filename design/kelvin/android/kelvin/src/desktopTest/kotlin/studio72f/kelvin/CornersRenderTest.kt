package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draws one specimen screen with each corner scale, side by side, so the two can be compared without a device.
 * With `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class CornersRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `soft and firm corners draw side by side in light and dark`() {
        draw("corners-light", dark = false)
        draw("corners-dark", dark = true)
    }

    private fun draw(name: String, dark: Boolean) {
        val scene = ImageComposeScene(width = WIDTH * 2 * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            Row {
                for (corners in KelvinCorners.entries) {
                    KelvinTheme(dark = dark, finish = KelvinFinish.Flat, corners = corners) {
                        Box(Modifier.width(WIDTH.dp).fillMaxHeight().background(Kelvin.colors.canvas)) { Specimen(corners) }
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

    @Composable
    private fun Specimen(corners: KelvinCorners) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KText(corners.name, Modifier.padding(horizontal = 16.dp), Kelvin.type.display)
            Box(Modifier.padding(horizontal = 16.dp)) { KelvinSearchField("", {}, "Search mail", "Clear") }
            KelvinTabs(listOf(KelvinTab("All", count = 12), KelvinTab("Unread", count = 3, alert = true), KelvinTab("Starred")), 0, {})
            KelvinGroup {
                KelvinMessageRow("Harbor Payments", "09:41", "Invoice 2291 is ready", "Your November invoice is attached.", unread = true, onClick = {})
                KelvinMessageRow("Lumen Labs", "Tue", "Design review moved", "We moved Thursday's review to 3pm.", unread = false, onClick = {})
                KelvinMessageRow("Sam Ortiz", "Mon", "Re: Q4 plan", "Looks good to me, one note on the budget.", unread = false, onClick = {})
            }
            KelvinNotice("Sync paused", "The server did not answer. Mail will retry in a minute.", tone = Tone.Info, actionText = "Retry", onAction = {})
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KelvinStatCard("Unread", "12", Modifier.weight(1f))
                KelvinStatCard("Due today", "3", Modifier.weight(1f), tone = Tone.Caution)
            }
            Box(Modifier.padding(horizontal = 16.dp)) { KelvinSegmented(listOf("Day", "Week", "Month"), 1, {}) }
            Box(Modifier.padding(horizontal = 16.dp)) { KelvinTextField("sam@lumenlabs.example", {}, "Email") }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KelvinPill("Verified", tone = Tone.Positive)
                KelvinPill("2 attachments")
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KelvinButton("Cancel", {}, Modifier.weight(1f), kind = ButtonKind.Secondary)
                KelvinButton("Send", {}, Modifier.weight(1f))
            }
            // A sheet's top edge, as it rises over the screen.
            Column(
                Modifier.padding(top = 4.dp).fillMaxWidth().height(96.dp).background(Kelvin.colors.group, KelvinShapes.sheet).padding(20.dp),
            ) {
                KText("Move to folder", style = Kelvin.type.title)
            }
        }
    }

    private companion object {
        const val SCALE = 2
        const val WIDTH = 412
        const val HEIGHT = 1080
        const val MIN_BYTES = 10_000
    }
}
