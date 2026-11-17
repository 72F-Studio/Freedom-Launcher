package studio72f.kelvin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/** Draws the tray mark at the sizes a tray uses, with and without its dot, on a light and a dark panel. */
class DeskTrayRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the tray mark reads on light and dark panels, at tray sizes`() {
        val scene = ImageComposeScene(width = 360, height = 200, density = Density(1f)) {
            KelvinTheme(dark = false) {
                Column {
                    for (panel in listOf(Color(0xFFF2F2F2), Color(0xFF1E1E1E))) {
                        Row(
                            Modifier.background(panel).padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            for (size in listOf(16, 22, 32, 64)) {
                                for (badge in listOf(false, true)) {
                                    Image(
                                        rememberKelvinTrayMark(KelvinIcons.Mail, Kelvin.colors.accent, badge),
                                        null,
                                        Modifier.size(size.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                                    )
                                }
                            }
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
        assertTrue(png.size > 1_000)
        out?.let { File(it).apply { mkdirs() }.resolve("desk-tray-mark.png").writeBytes(png) }
    }
}
