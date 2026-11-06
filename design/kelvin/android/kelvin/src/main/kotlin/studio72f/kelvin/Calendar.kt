package studio72f.kelvin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A mark on a day of a [KelvinMonthCalendar]: its [tone]'s soft fill and ink, and what it means for TalkBack. */
@Immutable
data class DayMark(val tone: Tone, val label: String)

/**
 * A month: the month and year as a figure with the accent slash ("7/2026"), previous and next
 * circles, weekday initials, then the days as circles. Dates are (year, month, day). The chosen day
 * ([selected]) is solid accent with a white
 * figure; marked days ([marks], for meetings, a period, new episodes) carry their tone's soft fill
 * and ink; today is underlined. Swipe sideways or use the circles to change month; the grid
 * slides the way it went. [month] is 1 to 12; [firstDayOfWeek] 1 is Sunday, 2 Monday.
 */
@Composable
fun KelvinMonthCalendar(
    year: Int,
    month: Int,
    selected: Triple<Int, Int, Int>?,
    onSelect: (year: Int, month: Int, day: Int) -> Unit,
    onMonthChange: (year: Int, month: Int) -> Unit,
    weekdayLabels: List<String>,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
    firstDayOfWeek: Int = 2,
    today: Triple<Int, Int, Int>? = null,
    marks: (year: Int, month: Int, day: Int) -> DayMark? = { _, _, _ -> null },
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val change by rememberUpdatedState(onMonthChange)
    val spec = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
    fun shift(by: Int) {
        val m0 = (month - 1 + by)
        val y = year + Math.floorDiv(m0, 12)
        val m = Math.floorMod(m0, 12) + 1
        haptics.tick(); change(y, m)
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            KelvinFigure("$month/$year", style = Kelvin.type.display, rolling = false)
            Spacer(Modifier.weight(1f))
            KelvinIconButton(KelvinIcons.ChevronLeft, previousLabel, { shift(-1) }, kind = IconButtonKind.Plain)
            KelvinIconButton(KelvinIcons.ChevronRight, nextLabel, { shift(1) }, kind = IconButtonKind.Plain)
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) KText(weekdayLabels[(firstDayOfWeek - 1 + i) % 7], Modifier.weight(1f), Kelvin.type.caption.copy(fontWeight = FontWeight.SemiBold), c.ink2, maxLines = 1, align = TextAlign.Center)
        }
        AnimatedContent(
            targetState = year * 12 + month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(spec) { w -> if (forward) w else -w }) togetherWith
                    (slideOutHorizontally(spec) { w -> if (forward) -w else w })
            },
            modifier = Modifier.pointerInput(year, month) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -80.dp.toPx()) shift(1) else if (dx > 80.dp.toPx()) shift(-1) },
                    onHorizontalDrag = { ch, d -> ch.consume(); dx += d },
                )
            },
            label = "month",
        ) { key ->
            val y = (key - 1) / 12
            val m = (key - 1) % 12 + 1
            val cal = java.util.Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
            val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val lead = Math.floorMod(cal.get(java.util.Calendar.DAY_OF_WEEK) - firstDayOfWeek, 7)
            val rows = (lead + days + 6) / 7
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = r * 7 + col - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (day in 1..days) DayCell(
                                day,
                                selected = selected == Triple(y, m, day),
                                today = today == Triple(y, m, day),
                                mark = marks(y, m, day),
                                onClick = { haptics.tick(); onSelect(y, m, day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, today: Boolean, mark: DayMark?, onClick: () -> Unit) {
    val c = Kelvin.colors
    val t = mark?.tone?.colors()
    val fill = when { selected -> c.accent; t != null -> t.fill; else -> androidx.compose.ui.graphics.Color.Transparent }
    val ink = when { selected -> c.accent.iconInk(); t != null -> t.ink; else -> c.ink }
    val underline = c.ink2
    Box(
        Modifier.size(42.dp)
            .kelvinPull(onClick, role = Role.Button, shape = CircleShape, wash = !selected && t == null)
            .background(fill, CircleShape)
            .semantics {
                contentDescription = listOfNotNull(day.toString(), mark?.label, if (today) "today" else null).joinToString(", ")
                this.selected = selected
            }
            .drawBehind {
                if (today && !selected) {
                    val y = size.height * 0.8f
                    drawLine(underline, Offset(size.width * 0.34f, y), Offset(size.width * 0.66f, y), 2.dp.toPx())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        KelvinFigure(day.toString(), style = Kelvin.type.figure.copy(fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Medium), color = ink, rolling = false)
    }
}
