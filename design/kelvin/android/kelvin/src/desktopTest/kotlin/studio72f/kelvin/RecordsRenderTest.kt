package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Draws the records pieces as an app would lay them out: a phone's "Check these" page (two stat cards, a record to
 * check, an overdue one) beside a desk's ledger pane of record lines, in both finishes, light and dark, in the
 * azure accent 72F Mail uses. With
 * `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class RecordsRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    @Test
    fun `the records pieces draw in every finish and theme`() {
        for (finish in KelvinFinish.entries) {
            for (dark in listOf(false, true)) {
                draw("records-${finish.name.lowercase()}-${if (dark) "dark" else "light"}", finish, dark)
            }
        }
    }

    private fun draw(name: String, finish: KelvinFinish, dark: Boolean) {
        val scene = ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, accent = Accent.Custom(KelvinAzure.light, KelvinAzure.dark), finish = finish, corners = KelvinCorners.Firm) {
                Row(
                    Modifier.fillMaxSize().background(Kelvin.colors.canvas).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KelvinStatRow {
                            KelvinStatCard("To pay", "₹13,229", Modifier.weight(1f), detail = "4 bills, 1 overdue", tone = Tone.Caution)
                            KelvinStatCard("Owed to you", "₹48,000", Modifier.weight(1f), detail = "2 invoices")
                        }
                        KelvinSection("Check these", start = 16.dp) {
                            KelvinRecordCard(
                                kind = "Invoice",
                                amount = "₹1,250.00",
                                title = "Studio Example",
                                facts = listOf("Due 1/12", "INV-114"),
                                source = "From “Invoice INV-114”, 13 Nov",
                                onOpenSource = {},
                                reason = "Read from the email's wording. Check the amount and the date.",
                                confirmText = "Confirm",
                                onConfirm = {},
                                editText = "Edit",
                                onEdit = {},
                                dismissText = "Not a bill",
                                onDismiss = {},
                            )
                            KelvinRecordCard(
                                kind = "Bill · due soon",
                                amount = "₹1,499",
                                title = "Tata Power",
                                facts = listOf("Due 16/11"),
                                source = "From “Electricity bill”, 9 Nov",
                                onOpenSource = {},
                                confirmText = "Confirm",
                                onConfirm = {},
                                editText = "Edit",
                                onEdit = {},
                                dismissText = "Not a bill",
                                onDismiss = {},
                                tone = Tone.Caution,
                            )
                            KelvinRecordCard(
                                kind = "Bill · overdue",
                                amount = "₹749",
                                title = "Airtel postpaid",
                                facts = listOf("Was due 10/11"),
                                source = "From “Your bill is ready”, 3 Nov",
                                onOpenSource = {},
                                confirmText = "Confirm",
                                onConfirm = {},
                                editText = "Edit",
                                onEdit = {},
                                dismissText = "Not a bill",
                                onDismiss = {},
                                tone = Tone.Critical,
                            )
                        }
                    }
                    Column(Modifier.weight(1f).background(Kelvin.colors.group, KelvinShapes.lg).padding(8.dp)) {
                        KelvinLineHeading("This month", top = 4.dp)
                        KelvinRecordLine("Studio Example", "₹1,250.00", "To check", {}, facts = listOf("Invoice", "Due 1/12"), statusTone = Tone.Info, selected = true)
                        KelvinRecordLine("Airtel postpaid", "₹749", "Overdue", {}, facts = listOf("Bill", "Was due 10/11"), statusTone = Tone.Critical)
                        KelvinRecordLine("Northwind Traders", "₹40,000", "Owed", {}, facts = listOf("Invoice you sent", "Due 30/11"), statusTone = Tone.Caution)
                        KelvinRecordLine("Google Play", "₹130", "Paid", {}, facts = listOf("Subscription", "Renews 2/12"), statusTone = Tone.Positive)
                        KelvinLineHeading("October")
                        KelvinRecordLine("Harbor Coffee", "₹420.50", "Paid", {}, facts = listOf("Receipt", "28/10"), statusTone = Tone.Positive)
                        KelvinRecordLine("Lumen Labs", "$12.50", "Refunded", {}, facts = listOf("Refund", "19/10"))
                        KelvinLineHeading("Development")
                        KelvinRecordLine("CI on main", null, "Open", {}, facts = listOf("72F-Studio/72F-mail", "a1b2c3d", "Today"), statusTone = Tone.Critical)
                        KelvinRecordLine("Records in the Money tab", null, "Open", {}, facts = listOf("72F-Studio/72F-mail", "#482"), statusTone = Tone.Caution)
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
        const val WIDTH = 980
        const val HEIGHT = 1020
        const val MIN_BYTES = 10_000
    }
}
