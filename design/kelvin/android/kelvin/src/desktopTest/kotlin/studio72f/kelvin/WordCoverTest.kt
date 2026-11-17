package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/** The word cover: which words it takes, how large, and a picture of it with `-Dkelvin.screenshots=<dir>`. */
class WordCoverTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the cover takes the first words, without markup`() {
        assertEquals("Vegetables", coverWords("Vegetables"))
        assertEquals("Stone Arts visit", coverWords("## Stone Arts visit, for cladding tiles"))
        assertEquals("Buy milk and bread", coverWords("**Buy** milk and bread before 8"))
        assertEquals("", coverWords("— · —"))
    }

    @Test
    fun `a key keeps its colour`() {
        assertEquals(KelvinTiles.forKey("Stone Arts"), KelvinTiles.forKey("  stone arts "))
    }

    @Test
    fun `covers draw in light and dark`() {
        for (dark in listOf(false, true)) {
            val scene = ImageComposeScene(width = 440 * 2, height = 140 * 2, density = Density(2f)) {
                KelvinTheme(dark = dark) {
                    Column(Modifier.fillMaxSize().background(Kelvin.colors.group).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KelvinWordCover("Vegetables", Modifier.size(60.dp, 80.dp), icon = KelvinIcons.Microphone)
                            KelvinWordCover("Stone Arts visit for cladding", Modifier.size(60.dp, 80.dp), icon = KelvinIcons.Microphone)
                            KelvinWordCover("Call the bank about the card", Modifier.size(60.dp, 80.dp))
                            KelvinWordCover("Interstellar", Modifier.size(60.dp, 80.dp))
                            KelvinWordCover("Go", Modifier.size(60.dp, 80.dp))
                            KelvinWordCover("Supercalifragilistic", Modifier.size(60.dp, 80.dp))
                        }
                    }
                }
            }
            val png = try { scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0) } finally { scene.close() }
            assertTrue(png.size > 2_000)
            out?.let { File(it).apply { mkdirs() }.resolve("word-cover-${if (dark) "dark" else "light"}.png").writeBytes(png) }
        }
    }
}
