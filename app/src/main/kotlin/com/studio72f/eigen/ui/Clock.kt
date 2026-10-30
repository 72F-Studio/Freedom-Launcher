package com.studio72f.eigen.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import studio72f.kelvin.Kelvin
import studio72f.kelvin.KText
import studio72f.kelvin.Onest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** The home clock's styles, shared with the Java settings menus. */
object ClockStyles {
    @JvmField val IDS = arrayOf("ratio", "thin", "stacked", "outline", "kesar", "binary")
    @JvmField val NAMES = arrayOf("Heavy", "Thin", "Stacked", "Outline", "Kesar split", "Binary")
}

/** Centred clock and date. Tapping the time opens alarms, the date opens the calendar; holding either offers a style change. */
@Composable
fun HomeClock(style: String, onTime: () -> Unit, onDate: () -> Unit, modifier: Modifier = Modifier, scale: Float = 1f, onLongPress: (() -> Unit)? = null) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50) // wake on the minute
        }
    }
    val context = LocalContext.current
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val is24 = DateFormat.is24HourFormat(context)
    val h = cal.get(if (is24) Calendar.HOUR_OF_DAY else Calendar.HOUR).let { if (!is24 && it == 0) 12 else it }
    val hours = if (is24) "%02d".format(h) else h.toString()
    val minutes = "%02d".format(cal.get(Calendar.MINUTE))
    val c = Kelvin.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val tap = Modifier.eigenPress(onTime, onLongPress, label = "Open alarms").semantics { contentDescription = "$hours:$minutes" }
        when (style) {
            "binary" -> BinaryClock(cal, tap.padding(vertical = 12.dp), scale)
            "stacked" -> Column(tap, horizontalAlignment = Alignment.CenterHorizontally) {
                Rolling(hours, face((120 * scale).toInt(), FontWeight.Bold).copy(color = c.ink, lineHeight = (104 * scale).sp))
                Rolling(minutes, face((120 * scale).toInt(), FontWeight.Bold).copy(color = c.ink.copy(alpha = 0.55f), lineHeight = (104 * scale).sp))
            }
            else -> {
                val weight = if (style == "thin") FontWeight.Light else FontWeight.Bold
                val base = face(((if (style == "thin") 112 else 96) * scale).toInt(), weight).let { if (style == "outline") it.copy(drawStyle = Stroke(width = 5f)) else it }
                // Hours and minutes roll up on their own as they change; the colon stays put.
                Row(tap, verticalAlignment = Alignment.CenterVertically) {
                    Rolling(hours, base.copy(color = c.ink))
                    BasicText(":", style = base.copy(color = c.ink.copy(alpha = 0.5f)))
                    Rolling(minutes, base.copy(color = if (style == "kesar") c.accent else c.ink))
                }
            }
        }
        KText(
            SimpleDateFormat("EEEE, d MMMM", LocalConfiguration.current.locales[0]).format(Date(now)),
            Modifier.eigenPress(onDate, onLongPress, label = "Open calendar").padding(horizontal = 12.dp, vertical = 6.dp),
            Kelvin.type.rowTitle, c.ink.copy(alpha = 0.78f), maxLines = 1, align = TextAlign.Center,
        )
    }
}

/** Text that rolls up into its new value on the spatial spring when it changes (clock digits). */
@Composable
private fun Rolling(text: String, style: TextStyle) {
    val spatial = Kelvin.motion.spatial<androidx.compose.ui.unit.IntOffset>()
    val gentle = Kelvin.motion.gentle<Float>()
    androidx.compose.animation.AnimatedContent(
        text,
        transitionSpec = {
            ((androidx.compose.animation.slideInVertically(spatial) { it / 2 } + androidx.compose.animation.fadeIn(gentle)) togetherWith
                (androidx.compose.animation.slideOutVertically(spatial) { -it / 2 } + androidx.compose.animation.fadeOut(gentle)))
                .using(androidx.compose.animation.SizeTransform(clip = false))
        },
        label = "rolling digits",
    ) { BasicText(it, style = style) }
}

private fun face(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = Onest, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.02).sp,
    letterSpacing = if (weight == FontWeight.Light) (-0.02).em else (-0.05).em, textAlign = TextAlign.Center,
)

/** Binary-coded decimal: four columns (hour tens, hour ones, minute tens, minute ones), lit dots in the accent. */
@Composable
private fun BinaryClock(cal: Calendar, modifier: Modifier, scale: Float = 1f) {
    val c = Kelvin.colors
    val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE)
    val digits = intArrayOf(h / 10, h % 10, m / 10, m % 10)
    val rows = intArrayOf(2, 4, 3, 4)
    Canvas(modifier.size(232.dp * scale, 112.dp * scale)) {
        val r = 11.dp.toPx() * scale; val gapX = size.width / 4f; val gapY = size.height / 4f
        for (col in 0 until 4) for (bit in 0 until rows[col]) {
            val on = digits[col] shr bit and 1 == 1
            val center = Offset(gapX * col + gapX / 2 + (if (col >= 2) 8.dp.toPx() else -8.dp.toPx()), size.height - gapY * bit - gapY / 2)
            if (on) drawCircle(c.accent, r, center) else drawCircle(c.ink.copy(alpha = 0.14f), r, center)
        }
    }
}
