package studio72f.kelvin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The words a [KelvinFullPlayer] gives TalkBack, so apps can translate them. */
@Immutable
data class PlayerLabels(
    val play: String,
    val pause: String,
    val back: String,
    val forward: String,
    val position: String,
    val queue: String,
)

/** Kelvin's own [PlayerLabels], from its string resources so they translate. */
@Composable
fun playerLabels(): PlayerLabels = PlayerLabels(
    play = kelvinString(KelvinString.PlayerPlay),
    pause = kelvinString(KelvinString.PlayerPause),
    back = kelvinString(KelvinString.PlayerBack),
    forward = kelvinString(KelvinString.PlayerForward),
    position = kelvinString(KelvinString.PlayerPosition),
    queue = kelvinString(KelvinString.PlayerQueue),
)

/** One control in a [KelvinTransport]: its glyph, its spoken name, and whether it can act now. */
@Immutable
data class TransportControl(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * A player's transport on one line, for anything that plays: episodes, tracks, a book read aloud,
 * words flashed one at a time. Play is the large accent pebble in the middle with a white glyph;
 * [back] and [forward] (seek: 15 seconds, a sentence) sit beside it and [previous] and [next]
 * (a chapter, a track) at the ends, all plain glyphs that move like pebbles. A control that cannot
 * act now ([TransportControl.enabled]) stays in place at 38% so the row never shifts. Play gives a
 * confirm haptic, the others a tick. [size] is play's diameter: 80dp on a full player, 64dp in a
 * panel.
 */
@Composable
fun KelvinTransport(
    playing: Boolean,
    onPlayPause: () -> Unit,
    playLabel: String,
    pauseLabel: String,
    modifier: Modifier = Modifier,
    back: TransportControl? = null,
    forward: TransportControl? = null,
    previous: TransportControl? = null,
    next: TransportControl? = null,
    size: Dp = 80.dp,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    @Composable
    fun side(control: TransportControl?) {
        if (control == null) return
        KelvinIconButton(
            control.icon, control.label, { haptics.tick(); control.onClick() },
            Modifier.graphicsLayer { alpha = if (control.enabled) 1f else 0.38f },
            kind = IconButtonKind.Plain, enabled = control.enabled,
        )
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        side(previous)
        side(back)
        val press = rememberGlassPress()
        val label = if (playing) pauseLabel else playLabel
        Box(
            Modifier.size(size).kelvinClick({ haptics.confirm(); onPlayPause() }, onClickLabel = label)
                .pebbleMotion(press).pebble(CircleShape, c.accent, press)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { KIcon(if (playing) KelvinIcons.Pause else KelvinIcons.Play, null, size = size * 0.42f, color = c.accent.iconInk()) }
        side(forward)
        side(next)
    }
}

/**
 * The full player of a podcast app: the artwork large on the aurora, the title and show, the
 * scrubber, then back 15, play and forward 30 within the thumb's reach, play a large accent
 * pebble with a white glyph. Under them, [speed] and [sleep] as chips that open their pickers and
 * the queue. Everything is a pebble; the figures roll.
 */
@Composable
fun KelvinFullPlayer(
    title: String,
    show: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    speed: String = "1×",
    onSpeed: () -> Unit = {},
    sleep: String? = null,
    onSleep: () -> Unit = {},
    onQueue: () -> Unit = {},
    labels: PlayerLabels = playerLabels(),
    previous: TransportControl? = null,
    next: TransportControl? = null,
    artwork: @Composable BoxScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    val swell by animateFloatAsState(if (playing) 1f else 0.9f, Kelvin.motion.wobble(), label = "artwork")
    Column(
        modifier.fillMaxWidth().horizon(0.8f).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The artwork rests a little smaller while paused and swells as it plays.
        Box(
            Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                .graphicsLayer { scaleX = swell; scaleY = swell }
                .shadow(24.dp, KelvinShapes.lg, ambientColor = c.accent.copy(alpha = 0.3f), spotColor = c.accent.copy(alpha = 0.3f))
                .clip(KelvinShapes.lg).background(c.sunken),
            content = artwork,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KText(title, Modifier.semantics { heading() }, Kelvin.type.headline, align = TextAlign.Center, maxLines = 2)
            KelvinEyebrow(show, Modifier.padding(top = 4.dp))
        }
        KelvinScrubber(positionMs, durationMs, onSeek, labels.position)
        KelvinTransport(
            playing, onPlayPause, labels.play, labels.pause,
            back = TransportControl(KelvinIcons.Back15, labels.back, onBack),
            forward = TransportControl(KelvinIcons.Forward30, labels.forward, onForward),
            previous = previous, next = next,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KelvinChip(speed, icon = KelvinIcons.Speed, onClick = onSpeed)
            KelvinChip(sleep ?: "Sleep", icon = KelvinIcons.Sleep, onClick = onSleep, selected = if (sleep != null) true else null)
            KelvinIconButton(KelvinIcons.Queue, labels.queue, onQueue, kind = IconButtonKind.Plain)
        }
    }
}

/**
 * A ring that shows how full something is (storage, a listening goal, a download): the accent arc
 * on the sunken track with a [figure] in the middle in the figure grammar ("86%", "4.2 GB") and a
 * [caption] under it. The arc moves on the gentle spring.
 */
@Composable
fun KelvinRingMeter(
    fraction: Float,
    figure: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    size: Dp = 140.dp,
    stroke: Dp = 12.dp,
    color: Color = Kelvin.colors.accent,
) {
    val c = Kelvin.colors
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), Kelvin.motion.gentle(), label = "meter")
    val track = c.sunken
    Box(
        // The ring grows with large text so the figure and caption stay inside it.
        modifier.size(size.withTextScale(Kelvin.type.caption.lineHeight * 3))
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
            .drawBehind {
                val w = stroke.toPx()
                val arc = Size(this.size.width - w, this.size.height - w)
                drawArc(track, 0f, 360f, false, Offset(w / 2f, w / 2f), arc, style = Stroke(w))
                drawArc(color, -90f, 360f * shown, false, Offset(w / 2f, w / 2f), arc, style = Stroke(w, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) {
        // Inside the ring's hole: the caption wraps rather than running into the arc.
        Column(Modifier.padding(horizontal = stroke + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KelvinFigure(figure, style = Kelvin.type.numeral.copy(fontSize = Kelvin.type.display.fontSize), heat = true)
            if (caption != null) KText(caption, style = Kelvin.type.caption, color = c.ink2, maxLines = 2, align = TextAlign.Center)
        }
    }
}

/**
 * A quick toggle tile, as in a control centre (Wi-Fi only, Do not disturb, auto-download): a glass
 * tile, the name and its state. On, the tile fills with its own colour and its glass tile flips to
 * white with the glyph in that colour, a little of it showing through; the
 * state is also written ("On", "Off"), never colour alone. The tile is a pebble.
 */
@Composable
fun KelvinQuickTile(
    icon: ImageVector,
    title: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    state: String = if (on) "On" else "Off",
    tile: Color = KelvinTiles.Blue,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    // On, the tile fills with its own colour (blue for Wi-Fi, violet for quiet hours), not the
    // app's accent: each toggle stays recognisable by colour as well as by glyph.
    val fill by animateColorAsState(if (on) tile else c.group, Kelvin.motion.gentle(), label = "tile")
    val ink = if (on) tile.iconInk() else c.ink
    val sub = if (on) tile.iconInk().copy(alpha = 0.85f) else c.ink2
    val press = rememberGlassPress()
    Row(
        modifier.heightIn(min = 72.dp.withTextScale(Kelvin.type.label.lineHeight))
            .kelvinClick({ haptics.toggle(!on); onToggle() }, onClickLabel = title)
            .semantics { stateDescription = state; contentDescription = title }
            .pebbleMotion(press).pebble(KelvinShapes.lg, fill, press, glow = on)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // On, the colours flip: the inner tile turns white and its glyph takes the accent.
        KelvinGlassTile(icon, if (on) Color.White else tile, size = 36.dp, glyph = if (on) tile.legibleOn(Color.White, 3f) else Color.White, backAlpha = if (on) 0.82f else 1f, frontAlpha = if (on) 1f else 0.92f)
        Column {
            KText(title, style = Kelvin.type.name.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = if (largeText()) 2 else 1)
            KText(state, style = Kelvin.type.caption, color = sub, maxLines = 1)
        }
    }
}

/** One choice in a [KelvinActionList]; [summary] says what it does when the label alone cannot. */
@Immutable
data class ListAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tile: Color = KelvinTiles.Blue,
    val destructive: Boolean = false,
    val summary: String? = null,
)

/**
 * A list of actions for a sheet or a long-press (Reply, Forward, Move, Delete; Add to queue,
 * Share, Mark played): each a row with its glass tile and words, the destructive one in red.
 */
@Composable
fun KelvinActionList(actions: List<ListAction>, modifier: Modifier = Modifier) {
    val c = Kelvin.colors
    KelvinGroup(modifier) {
        actions.forEach { a ->
            Row(
                Modifier.groupDividerInset(60.dp).fillMaxWidth().heightIn(min = 56.dp)
                    .kelvinPress(a.onClick, role = Role.Button)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KelvinGlassTile(a.icon, if (a.destructive) c.critical else a.tile)
                Column(Modifier.weight(1f)) {
                    KText(a.label, style = Kelvin.type.rowTitle.copy(fontWeight = if (a.destructive) FontWeight.SemiBold else FontWeight.Medium), color = if (a.destructive) c.critical.legibleOn(c.group) else c.ink)
                    if (a.summary != null) KText(a.summary, style = Kelvin.type.summary, color = c.ink2)
                }
            }
        }
    }
}
