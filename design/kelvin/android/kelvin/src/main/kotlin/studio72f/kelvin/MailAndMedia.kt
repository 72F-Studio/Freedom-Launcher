package studio72f.kelvin

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * Mail and media: the pieces an email client and a podcast app need beyond the core set.
 * Recipients and attachments for mail; episodes, a mini player, a scrubber and a wheel picker
 * for listening. They follow Kelvin's rules: figures in the figure grammar, unread by the time,
 * pebble presses, state never by colour alone.
 */

/**
 * The To / Cc line of a compose screen: [label] as an eyebrow, recipients as input chips with a
 * remove x, and a text field that turns what is typed into a chip on Enter, comma or space.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KelvinRecipientField(
    label: String,
    recipients: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    removeLabel: (String) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val c = Kelvin.colors
    var draft by remember { mutableStateOf("") }
    val add by rememberUpdatedState(onAdd)
    fun commit() { val t = draft.trim().trimEnd(',', ';'); if (t.isNotEmpty()) add(t); draft = "" }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KelvinEyebrow(label, Modifier.widthIn(min = 28.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recipients.forEachIndexed { i, r ->
                KelvinChip(r, onRemove = { onRemove(i) }, removeLabel = removeLabel(r))
            }
            BasicTextField(
                draft,
                { v -> if (v.endsWith(",") || v.endsWith(";") || v.endsWith(" ")) { draft = v; commit() } else draft = v },
                Modifier.widthIn(min = 120.dp).heightIn(min = 36.dp).padding(top = 7.dp),
                textStyle = Kelvin.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { commit() }, onDone = { commit() }),
                decorationBox = { inner -> Box { if (draft.isEmpty() && recipients.isEmpty()) KText(placeholder, style = Kelvin.type.body, color = c.ink2, maxLines = 1); inner() } },
            )
        }
    }
}

/**
 * An attachment: a glass tile carrying the file's kind, its name, and its size as a figure
 * ("2.4 MB"). Pressed it opens; an optional [onRemove] adds an x for a draft's attachments.
 */
@Composable
fun KelvinAttachmentChip(
    name: String,
    size: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = KelvinIcons.Paperclip,
    tile: androidx.compose.ui.graphics.Color = KelvinTiles.Blue,
    onRemove: (() -> Unit)? = null,
    removeLabel: String? = null,
) {
    val c = Kelvin.colors
    Row(
        modifier
            .heightIn(min = 56.dp)
            .kelvinPull(onClick, onClickLabel = name, shape = KelvinShapes.md, wash = false)
            .background(c.sunken, KelvinShapes.md)
            .padding(start = 10.dp, end = if (onRemove != null) 4.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KelvinGlassTile(icon, tile)
        Column(Modifier.widthIn(max = 180.dp)) {
            KText(name, style = Kelvin.type.label, maxLines = 1)
            KelvinFigure(size, style = Kelvin.type.figure.copy(fontSize = Kelvin.type.caption.fontSize), color = c.ink2, unit = c.ink2, rolling = false)
        }
        if (onRemove != null) Box(
            Modifier.size(36.dp).kelvinPull(onRemove, onClickLabel = removeLabel).semantics { if (removeLabel != null) contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) { KIcon(KelvinIcons.X, null, size = 16.dp, color = c.ink2) }
    }
}

/**
 * An episode in a feed or the queue: artwork, the show as an eyebrow, the title, then date and
 * length as figures with the unplayed dot after them (as unread mail has it), a thin progress
 * line once it has been started, and the download control at the end. The row plays on [onPlay]
 * from its play pebble and opens on [onClick].
 */
@Composable
fun KelvinEpisodeRow(
    show: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    playLabel: String,
    modifier: Modifier = Modifier,
    unplayed: Boolean = false,
    progress: Float? = null,
    download: DownloadState? = null,
    onDownload: (() -> Unit)? = null,
    downloadLabel: String = "",
    artwork: @Composable BoxScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    Row(
        modifier.groupDividerInset(16.dp + 64.dp + 12.dp).fillMaxWidth().kelvinPress(onClick).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 4.dp).size(64.dp).clip(KelvinShapes.sm).background(c.sunken), content = artwork)
        // The title has the row's full width; the controls sit on the date line beneath it.
        Column(Modifier.weight(1f)) {
            KelvinEyebrow(show)
            KText(title, Modifier.padding(top = 2.dp, end = 8.dp), Kelvin.type.name.copy(fontWeight = if (unplayed) FontWeight.Bold else FontWeight.SemiBold), maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // A figure never wraps; with large text the line is set as words so it can.
                        if (largeText()) KText(meta, Modifier.weight(1f, fill = false), Kelvin.type.summary, c.ink2)
                        else KelvinFigure(meta, style = Kelvin.type.figure.copy(fontSize = Kelvin.type.summary.fontSize), color = c.ink2, unitWord = c.ink2, rolling = false)
                        if (unplayed) Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(c.accent))
                    }
                    if (progress != null && progress > 0f) KelvinProgressBar(progress, Modifier.padding(top = 6.dp).widthIn(max = 160.dp), height = 3.dp)
                }
                if (download != null && onDownload != null) KelvinDownloadButton(download, onDownload, downloadLabel)
                KelvinIconButton(KelvinIcons.Play, playLabel, onPlay, kind = IconButtonKind.Accent)
            }
        }
    }
}

/**
 * The mini player above the dock: a glass pill with the artwork, what is playing, and play/pause,
 * its progress a thin accent line along its foot (none when [progress] is null, as for a stream or
 * another app's session). The whole pill is a pebble and opens the full player on [onClick];
 * play/pause takes its own tap. Give [previous] and [next] for music, where skipping a track is
 * the common move; they flank play as plain glyphs and dim in place when they cannot act.
 * [onClose] adds a plain x at the end, for sound that plays without a player to open (pass null
 * [onClick] then).
 */
@Composable
fun KelvinMiniPlayer(
    title: String,
    subtitle: String,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onClick: (() -> Unit)?,
    progress: Float?,
    playLabel: String,
    pauseLabel: String,
    backdrop: KelvinBackdrop?,
    modifier: Modifier = Modifier,
    previous: TransportControl? = null,
    next: TransportControl? = null,
    onClose: (() -> Unit)? = null,
    closeLabel: String = "",
    artwork: @Composable BoxScope.() -> Unit = {},
) {
    val c = Kelvin.colors
    val press = rememberGlassPress()
    val accent = c.accent
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp.withTextScale(Kelvin.type.label.lineHeight))
            .then(if (onClick != null) Modifier.kelvinClick(onClick, onClickLabel = title) else Modifier)
            .then(
                if (backdrop != null) Modifier.glass(backdrop, CircleShape, GlassContent.Pane, elevated = true, motion = press.layerBlock, pebble = press).then(press.gestures)
                else Modifier.pebbleMotion(press).pebble(CircleShape, c.raised, press),
            )
            .drawBehind {
                if (progress == null) return@drawBehind
                // Progress along the foot of the pill, inside its curve.
                val inset = size.height / 2f
                val y = size.height - 2.dp.toPx()
                drawLine(accent, Offset(inset, y), Offset(inset + (size.width - inset * 2f) * progress.coerceIn(0f, 1f), y), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.sunken), content = artwork)
        Column(Modifier.weight(1f)) {
            KText(title, style = Kelvin.type.label, maxLines = 1)
            KText(subtitle, style = Kelvin.type.caption, color = c.ink2, maxLines = 1)
        }
        @Composable
        fun skip(control: TransportControl?) {
            if (control == null) return
            KelvinIconButton(
                control.icon, control.label, control.onClick,
                Modifier.graphicsLayer { alpha = if (control.enabled) 1f else 0.38f },
                kind = IconButtonKind.Plain, enabled = control.enabled,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            skip(previous)
            KelvinIconButton(if (playing) KelvinIcons.Pause else KelvinIcons.Play, if (playing) pauseLabel else playLabel, onPlayPause, kind = IconButtonKind.Accent)
            skip(next)
            if (onClose != null) KelvinIconButton(KelvinIcons.X, closeLabel, onClose, kind = IconButtonKind.Plain)
        }
    }
}

/**
 * The player's scrubber: Kelvin's slider over the episode, elapsed time at the start and time
 * left at the end as figures (accent colons), both rolling as it plays.
 */
@Composable
fun KelvinScrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onSeekFinished: (() -> Unit)? = null,
) {
    val c = Kelvin.colors
    val d = durationMs.coerceAtLeast(1L)
    fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
    Column(modifier.fillMaxWidth()) {
        KelvinSlider(positionMs.toFloat() / d, { onSeek((it * d).toLong()) }, contentDescription, valueText = null, onValueChangeFinished = onSeekFinished)
        Row(Modifier.fillMaxWidth()) {
            val style = Kelvin.type.ticking(Kelvin.type.figure.copy(fontSize = Kelvin.type.caption.fontSize))
            KelvinFigure(clock(positionMs), style = style, color = c.ink2)
            Box(Modifier.weight(1f))
            KelvinFigure("−" + clock(d - positionMs), style = style, color = c.ink2, unit = c.ink2)
        }
    }
}

/**
 * A wheel for one value from a list (a sleep timer, a playback speed, an hour): items scroll
 * through a band in the middle and snap to it, ticking as each one passes. The one in the band
 * is ink and full size; the rest fade and shrink with their distance from it, following the
 * finger. [onSelect] reports the item that settled.
 */
@Composable
fun KelvinWheelPicker(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    visible: Int = 5,
    itemHeight: Dp = 44.dp,
) {
    val c = Kelvin.colors
    val haptics = rememberKelvinHaptics()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val fling = rememberSnapFlingBehavior(state)
    val pick by rememberUpdatedState(onSelect)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    // The fractional index at the band's centre, read from the scroll position.
    val centre by remember { derivedStateOf { state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx } }
    LaunchedEffect(state) {
        var last = selected
        snapshotFlow { kotlin.math.round(centre).toInt() }.collect { i -> if (i != last) { last = i; haptics.tick() } }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving -> if (!moving) pick(kotlin.math.round(centre).toInt().coerceIn(0, items.lastIndex)) }
    }
    Box(
        modifier.fillMaxWidth().height(itemHeight * visible)
            .semantics { this.contentDescription = contentDescription; stateDescription = items.getOrElse(kotlin.math.round(centre).toInt()) { "" } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(itemHeight).background(c.sunken, KelvinShapes.md))
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(items) { i, item ->
                val distance = abs(i - centre)
                val near = (1f - distance / (visible / 2f + 0.5f)).coerceIn(0f, 1f)
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KelvinFigure(
                        item,
                        Modifier.graphicsLayer { val s = 0.8f + 0.2f * (1f - distance.coerceAtMost(1f)); scaleX = s; scaleY = s; alpha = 0.25f + 0.75f * near },
                        style = Kelvin.type.title,
                        color = lerp(c.ink2, c.ink, (1f - distance).coerceIn(0f, 1f)),
                        rolling = false,
                    )
                }
            }
        }
    }
}

/**
 * A voice note or audio attachment in a message, a memo or a mail: the accent play pebble beside
 * Kelvin's scrubber, elapsed time and time left as figures under it, all in a group well. Dragging
 * the scrubber seeks; TalkBack reads play or pause and the position.
 */
@Composable
fun KelvinVoiceNote(
    playing: Boolean,
    onPlayPause: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    playLabel: String,
    pauseLabel: String,
    positionLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Kelvin.colors.group, KelvinShapes.md).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KelvinIconButton(if (playing) KelvinIcons.Pause else KelvinIcons.Play, if (playing) pauseLabel else playLabel, onPlayPause, kind = IconButtonKind.Accent)
        KelvinScrubber(positionMs, durationMs, onSeek, positionLabel, Modifier.weight(1f))
    }
}
