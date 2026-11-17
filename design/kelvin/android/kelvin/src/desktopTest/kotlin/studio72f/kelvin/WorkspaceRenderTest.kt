package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
 * Draws the work-app pieces (table, tree, banner, breadcrumbs, toolbar, keys, pagination, select, activity, row
 * actions) in every style Kelvin has: both finishes, both corner scales, light and dark. With
 * `-Dkelvin.screenshots=<dir>` the pictures are saved there.
 */
class WorkspaceRenderTest {
    private val out: String? = System.getProperty("kelvin.screenshots")

    private data class Seat(val name: String, val role: String, val seen: String)

    private val seats = listOf(
        Seat("Ananya Rao", "Owner", "Today"),
        Seat("Lena Fischer", "Admin", "Yesterday"),
        Seat("Sam Kapoor", "Member", "3 Nov"),
    )

    @Test
    fun `the work-app pieces draw in every finish, corner scale and theme`() {
        for (finish in KelvinFinish.entries) {
            for (corners in KelvinCorners.entries) {
                for (dark in listOf(false, true)) {
                    draw("workspace-${finish.name.lowercase()}-${corners.name.lowercase()}-${if (dark) "dark" else "light"}", finish, corners, dark)
                }
            }
        }
    }

    private fun draw(name: String, finish: KelvinFinish, corners: KelvinCorners, dark: Boolean) {
        val scene = ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
            KelvinTheme(dark = dark, finish = finish, corners = corners) {
                Column(Modifier.fillMaxSize().background(Kelvin.colors.canvas)) {
                    KelvinBanner("You are offline. Changes will be made when you are back.", tone = Tone.Caution, icon = KelvinIcons.Cloud, actionText = "Retry", onAction = {}, onDismiss = {}, dismissLabel = "Close")
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        KelvinRail(Modifier.height(420.dp)) {
                            KelvinRailItem(KelvinIcons.Edit, "Write", {}, primary = true)
                            KelvinRailItem(KelvinIcons.Inbox, "Inbox", {}, selected = true, dot = true)
                            KelvinRailItem(KelvinIcons.Send, "Sent", {})
                            KelvinRailItem(KelvinIcons.Archive, "Archive", {}, dropTarget = true)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            KelvinRailAccount("Sam Kapoor", "sam@lumenlabs.example", {}, selected = true)
                            KelvinRailAccount("Lena Fischer", "lena@harbor.example", {}, dot = true)
                            KelvinRailItem(KelvinIcons.Plus, "Add an account", {})
                        }
                        KelvinRail(Modifier.height(420.dp), expanded = true) {
                            KelvinRailToggle(expanded = true, {}, "Show names", "Hide names")
                            KelvinRailItem(KelvinIcons.Edit, "Write", {}, primary = true)
                            KelvinRailItem(KelvinIcons.Inbox, "Inbox", {}, selected = true, dot = true)
                            KelvinRailItem(KelvinIcons.Send, "Sent", {})
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            KelvinRailAccount("Sam Kapoor", "sam@lumenlabs.example", {}, selected = true)
                            KelvinRailAccount("Lena Fischer", "lena@harbor.example", {}, dot = true)
                            KelvinRailItem(KelvinIcons.Plus, "Add an account", {})
                        }
                        Column(Modifier.width(240.dp).background(Kelvin.colors.group, KelvinShapes.lg).padding(8.dp)) {
                            KelvinTreeItem("Inbox", {}, icon = KelvinIcons.Inbox, selected = true, count = 4)
                            KelvinTreeItem("Clients", {}, expanded = true, onToggle = {})
                            KelvinTreeItem("Northwind", {}, depth = 1, count = 2)
                            KelvinTreeItem("Harbor", {}, depth = 1)
                            KelvinTreeItem("Receipts", {}, expanded = false, onToggle = {})
                        }
                        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KelvinBreadcrumbs(listOf("Settings", "Team", "Seats"), {})
                            KelvinDragChip("Final cover for the Bruges book", icon = KelvinIcons.Mail)
                            KelvinToolbar {
                                KelvinRowActions(
                                    listOf(
                                        KelvinAction(KelvinIcons.Archive, "Archive", {}),
                                        KelvinAction(KelvinIcons.Clock, "Snooze", {}),
                                        KelvinAction(KelvinIcons.Trash, "Delete", {}, destructive = true),
                                    ),
                                )
                                KelvinToolbarGap()
                                KelvinKbd("Ctrl K")
                                KelvinKbd("?")
                            }
                            KelvinTable(
                                seats,
                                listOf(
                                    KelvinColumn("Name", { it.name }, weight = 2f),
                                    KelvinColumn("Role", { it.role }),
                                    KelvinColumn("Last seen", { it.seen }, end = true),
                                ),
                                sortedBy = 0,
                                onSort = {},
                                selected = { it.role == "Admin" },
                                onRow = {},
                                checked = { it.role == "Admin" },
                                onCheck = { _, _ -> },
                            )
                            KelvinPagination("1–3 of 3", null, null, "Previous page", "Next page")
                            KelvinMailLine(
                                "Ananya Rao", "9:40 AM", "Final cover", "Could you approve it?", unread = true, onClick = {},
                                actions = listOf(KelvinAction(KelvinIcons.Archive, "Archive", {})),
                            )
                            KelvinActivityItem("Lena Fischer", "changed Sam's role to Member", "2 min")
                            KelvinActivityItem("Sign-in", "from a new computer", "1 h", tone = Tone.Caution, detail = "Linux, Firefox", last = true)
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

    private companion object {
        const val SCALE = 1
        const val WIDTH = 940
        const val HEIGHT = 620
        const val MIN_BYTES = 10_000
    }
}
