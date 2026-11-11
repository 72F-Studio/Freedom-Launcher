#!/usr/bin/env python3
"""Draw an app's Material icons as Kelvin's Tabler glyphs (MIT, https://tabler.io/icons).

Compose, scanning a source tree for Icons.<Style>.<Name> uses (unchanged from the first version):
  material_to_tabler.py <tabler icons dir> <source root> <package> <out.kt> [--standalone]
  Emits a drop-in `Icons` object with only the glyphs used, mapped by MAP. It builds them with Kelvin's `tablerIcon`;
  --standalone emits its own builder instead, for code that cannot link Kelvin (a Kotlin Multiplatform module with a
  desktop target), and names the glyphs with their style (`OutlinedArchive`) in a flat `TablerIcons` object.

Compose, from a list of facade names (for a design system that keeps its own `Icons` facade):
  material_to_tabler.py facade <tabler icons dir> <names file> <package> <out.kt>
  Each line of <names file> is `Style.Name` (`Outlined.Archive`); emits a standalone `TablerIcons` object.

Views, overriding an app's Material vector drawables by name:
  material_to_tabler.py drawables <tabler icons dir> <out res dir> <res dir> [<res dir> ...]
  For every ic_<name>.xml in the res dirs whose name maps (ic_mark_email_read -> MarkEmailRead -> mail-opened), writes
  a Tabler vector drawable of the same name to <out res dir>/drawable, keeping the original's tint, so an app module
  can override a library's glyphs without touching the library.

Kelvin's own glyphs for Views:
  material_to_tabler.py kelvin-drawables <tabler icons dir> <kelvin source dir> <out res dir>
  Writes kelvin_ic_<tabler-name>.xml for every glyph Kelvin draws (KelvinIcons and KelvinIconPaths).

Add --outline to any command for the stroke set (Tabler outline, 1.5); only state glyphs (a starred star) stay filled.

Every output follows Kelvin's icon rule (GUIDELINES.md, Iconography): the filled glyph wherever Tabler draws one,
otherwise the outline at Kelvin's 1.5 stroke; a glyph whose meaning is an empty state (an unstarred star) stays an
outline, so state is never colour alone.
"""
import pathlib, re, sys

MAP = {
 # First set (HyperMind, Gallery, Rediom).
 "AccessTime":"clock","AccountCircle":"user-circle","Add":"plus","ArrowBack":"arrow-left","ArrowForward":"arrow-right",
 "Autorenew":"refresh","AutoStories":"book-2","Bedtime":"moon","Bookmark":"bookmark","BookmarkAdd":"bookmark-plus",
 "BookmarkAdded":"bookmark","BookmarkBorder":"bookmark","Check":"check","CheckCircle":"circle-check",
 "ChevronRight":"chevron-right","Close":"x","CloudDownload":"cloud-download","CloudSync":"cloud-up","Code":"code",
 "ContentCopy":"copy","Create":"pencil","Delete":"trash","DeleteOutline":"trash","Download":"download","Edit":"pencil",
 "Email":"mail","ErrorOutline":"alert-circle","ExpandLess":"chevron-up","ExpandMore":"chevron-down",
 "FastForward":"player-track-next","Favorite":"heart","FileDownload":"file-download","FileUpload":"file-upload",
 "FontDownload":"typography","Fullscreen":"maximize","GroupAdd":"users-plus","Headphones":"headphones","Headset":"headset",
 "Language":"world","LocalFireDepartment":"flame","Lock":"lock","Menu":"menu-2","MenuBook":"book","MoreVert":"dots-vertical",
 "MusicNote":"music","NavigateNext":"chevron-right","NotificationsOff":"bell-off","OpenInBrowser":"external-link",
 "OpenInNew":"external-link","Pause":"player-pause","Person":"user","PersonRemove":"user-minus","PlayArrow":"player-play",
 "RadioButtonUnchecked":"circle","Refresh":"refresh","Remove":"minus","Replay":"rotate","Restore":"history",
 "SaveAlt":"download","ScreenRotation":"device-mobile-rotated","Search":"search","Security":"shield-lock","Share":"share",
 "SkipNext":"player-skip-forward","SkipPrevious":"player-skip-back","Speed":"gauge","Star":"star","Storage":"database",
 "TextFormat":"text-size","TextSnippet":"file-text","Tune":"adjustments-horizontal","Undo":"arrow-back-up",
 "Visibility":"eye","VolumeOff":"volume-off","VolumeUp":"volume","Warning":"alert-triangle",
 # Mail (72F Mail, on Thunderbird's Material Symbols and its Bolt facade).
 "Account":"user-circle","AddCircle":"circle-plus","Adjust":"adjustments-horizontal","AllInbox":"inbox",
 "Analytics":"chart-bar","Archive":"archive","Attachment":"paperclip","Badge":"id-badge-2","Bank":"building-bank",
 "Block":"ban","Bolt":"bolt","Book":"book","BugReport":"bug","Cancel":"circle-x","CompareArrows":"arrows-left-right",
 "Description":"file-text","DoNotDisturbOn":"circle-minus","Dot":"point","Draft":"file-pencil","Drafts":"file-pencil",
 "DragHandle":"grip-horizontal","DriveFileMove":"folder-symlink","Encrypted":"shield-lock","Error":"alert-circle",
 "FavoriteFolder":"folder-star","FilterList":"filter","Fingerprint":"fingerprint","Flower":"flower","Folder":"folder",
 "FolderManaged":"folder-cog","Forum":"messages","Forward":"arrow-forward-up","Game":"device-gamepad-2","Group":"users",
 "Healing":"bandage","Hearth":"heart","Help":"help-circle","Image":"photo","Inbox":"inbox","Info":"info-circle",
 "Key":"key","KeyboardArrowDown":"chevron-down","Link":"link","Login":"login-2","Mail":"mail",
 "MarkEmailRead":"mail-opened","MarkEmailUnread":"mail","Monitor":"device-desktop","NoEncryption":"lock-open-off",
 "Notifications":"bell","Outbox":"mail-up","PersonAdd":"user-plus","Pets":"paw","QrCode":"qrcode",
 "RemoteFeatureFlag":"flag","Reply":"arrow-back-up","ReplyAll":"arrow-back-up-double","Report":"alert-octagon",
 "Rocket":"rocket","Save":"device-floppy","School":"school","SearchRemote":"world-search","SelectAll":"list-check",
 "Send":"send","Settings":"settings","Smile":"mood-smile","Sort":"arrows-sort","Spa":"leaf","StarFilled":"star",
 "SwapVert":"arrows-up-down","Sync":"refresh","TouchApp":"hand-finger","Upload":"upload","VisibilityOff":"eye-off",
 "Work":"briefcase",
}

# `--outline` on any command draws the stroke set (Tabler outline, 1.5) instead of the filled one.
OUTLINE = "--outline" in sys.argv
if OUTLINE:
    sys.argv.remove("--outline")

# Names whose meaning is an empty state: they stay outlines even where Tabler has a filled glyph.
OUTLINE_STATES = {"StarBorder", "BookmarkBorder", "FavoriteBorder", "RadioButtonUnchecked"}
# Names whose meaning is a state Tabler shows as a filled glyph.
FORCE_FILLED = {"BookmarkAdded", "StarFilled"}

# Tabler names that stay filled in the stroke set because their filled form is a state.
STATE_FILLED = {"star"}

# Drawables that follow the reading direction.
MIRRORED = {"arrow-left", "arrow-right", "arrow-back-up", "arrow-back-up-double", "arrow-forward-up", "send",
            "chevron-right", "chevron-left", "login-2", "logout"}


def shapes(svg):
    """Every drawn element of a Tabler SVG as path data (Tabler draws a few circles, lines and rects)."""
    out = []
    for m in re.finditer(r'<(path|circle|ellipse|line|polyline|polygon|rect)\b([^>]*)/?>', svg, re.S):
        tag, a = m.group(1), dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(2)))
        if a.get("d", "").strip() == "M0 0h24v24H0z" or (a.get("stroke") == "none" and a.get("fill") == "none"):
            continue
        f = lambda k, d=0.0: float(a.get(k, d))
        if tag == "path":
            out.append(a["d"])
        elif tag in ("circle", "ellipse"):
            cx, cy = f("cx"), f("cy")
            rx, ry = (f("r"), f("r")) if tag == "circle" else (f("rx"), f("ry"))
            out.append(f"M{cx - rx:g} {cy:g}a{rx:g} {ry:g} 0 1 0 {2 * rx:g} 0a{rx:g} {ry:g} 0 1 0 {-2 * rx:g} 0")
        elif tag == "line":
            out.append(f"M{f('x1'):g} {f('y1'):g}L{f('x2'):g} {f('y2'):g}")
        elif tag in ("polyline", "polygon"):
            pts = a["points"].replace(",", " ").split()
            pairs = [f"{pts[i]} {pts[i + 1]}" for i in range(0, len(pts), 2)]
            out.append("M" + "L".join(pairs) + ("z" if tag == "polygon" else ""))
        else:
            x, y, w, h, r = f("x"), f("y"), f("width"), f("height"), f("rx", a.get("ry", 0))
            if r:
                out.append(f"M{x + r:g} {y:g}h{w - 2 * r:g}a{r:g} {r:g} 0 0 1 {r:g} {r:g}v{h - 2 * r:g}a{r:g} {r:g} 0 0 1 {-r:g} {r:g}"
                           f"h{-(w - 2 * r):g}a{r:g} {r:g} 0 0 1 {-r:g} {-r:g}v{-(h - 2 * r):g}a{r:g} {r:g} 0 0 1 {r:g} {-r:g}z")
            else:
                out.append(f"M{x:g} {y:g}h{w:g}v{h:g}h{-w:g}z")
    return out


def glyph(tabler, t, filled):
    """(paths, filled) for Tabler glyph t: the filled form if asked for and Tabler draws one, else the outline."""
    tabler = pathlib.Path(tabler)
    if filled and (tabler / "filled" / f"{t}.svg").exists():
        return shapes((tabler / "filled" / f"{t}.svg").read_text()), True
    f = tabler / "outline" / f"{t}.svg"
    if not f.exists():
        raise SystemExit(f"missing tabler icon {t}")
    return shapes(f.read_text()), False


def kelvin_filled(name):
    """Kelvin's rule for a Material name: filled wherever Tabler draws one, except an empty state.
    With --outline (the stroke set) only the state glyphs that must differ in form (a starred star) stay filled."""
    if OUTLINE:
        return name in FORCE_FILLED
    return name in FORCE_FILLED or name not in OUTLINE_STATES


STANDALONE_BUILDER = '''import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val TABLER_GRID = 24f
private const val KELVIN_STROKE = 1.5f

// Kelvin's tablerIcon(), for code that cannot link Kelvin: outlines at Kelvin's 1.5 stroke, filled glyphs solid.
private fun tablerIcon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = TABLER_GRID.dp,
        defaultHeight = TABLER_GRID.dp,
        viewportWidth = TABLER_GRID,
        viewportHeight = TABLER_GRID,
    ).apply {
        for (d in paths) {
            if (filled) {
                addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
            } else {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = KELVIN_STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()
'''


def kt_entry(tabler, ident, name, filled_wanted, indent="        "):
    t = MAP[name]
    ps, filled = glyph(tabler, t, filled_wanted)
    args = ",\n".join(f'{indent}        "{d}"' for d in ps)
    return f'{indent}val {ident}: ImageVector by lazy {{\n{indent}    tablerIcon(\n{indent}        "{t}",\n{args},\n{indent}        filled = {"true" if filled else "false"},\n{indent}    )\n{indent}}}\n'


def compose_scan(tabler, root, pkg, out, standalone):
    uses = set()
    for p in pathlib.Path(root).rglob("*.kt"):
        for m in re.finditer(r"Icons\.(AutoMirrored\.)?(Outlined|Filled|Rounded|Default|TwoTone|Sharp)\.([A-Za-z]+)", p.read_text()):
            uses.add(((m.group(1) or "").rstrip("."), m.group(2), m.group(3)))
    if standalone:
        facade(tabler, sorted({(s, n) for _, s, n in uses}), pkg, out)
        return
    groups = {}
    for mirrored, style, name in sorted(uses):
        groups.setdefault((mirrored, style), set()).add(name)

    def line(style, name):
        filled = (style == "Filled" or name in FORCE_FILLED)
        ps, f = glyph(tabler, MAP[name], filled)
        args = ", ".join('"%s"' % d for d in ps)
        # KIcon fills a glyph by its Tabler name, so an empty state says it must stay a line.
        keep = ", keepOutline = true" if name in OUTLINE_STATES and not f else ""
        return f'        val {name}: ImageVector by lazy {{ tablerIcon("{MAP[name]}", {args}{", filled = true" if f else ""}{keep}) }}\n'

    body = ""
    for style in ["Outlined", "Filled", "Rounded", "Default", "TwoTone", "Sharp"]:
        names = groups.get(("", style))
        if names:
            body += f"    object {style} {{\n" + "".join(line(style, n) for n in sorted(names)) + "    }\n"
    mir = [s for (m, s) in groups if m == "AutoMirrored"]
    if mir:
        body += "    object AutoMirrored {\n"
        for style in sorted(mir):
            body += f"        object {style} {{\n" + "".join("    " + line(style, n) for n in sorted(groups[("AutoMirrored", style)])) + "        }\n"
        body += "    }\n"
    pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(out).write_text(f"""// Generated by material_to_tabler.py. Edit the map there, not this file.
package {pkg}

import androidx.compose.ui.graphics.vector.ImageVector
import studio72f.kelvin.tablerIcon

/**
 * Material's icon names, drawn as Tabler glyphs (MIT, https://tabler.io/icons) through Kelvin,
 * so every icon on screen is one family. Import this in place of Material's `Icons`.
 */
object Icons {{
{body}}}
""")
    print(f"{len(uses)} uses -> {out}")


def facade(tabler, names, pkg, out):
    """A standalone `TablerIcons` object: `<Style><Name>` for each (style, name), by Kelvin's icon rule."""
    body = ""
    for style, name in sorted(set(names)):
        if name not in MAP:
            raise SystemExit(f"no Tabler glyph mapped for {style}.{name}: add it to MAP")
        body += kt_entry(tabler, style + name, name, style == "Filled" or kelvin_filled(name), indent="    ")
    pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(out).write_text(f"""// Generated by Kelvin's tools/material_to_tabler.py. Edit the map there, not this file.
// Tabler Icons (MIT, https://tabler.io/icons), drawn by Kelvin's icon rule: the filled glyph wherever Tabler draws
// one, otherwise the outline at Kelvin's 1.5 stroke; an empty state (an unstarred star) stays an outline.
@file:Suppress("MaxLineLength", "LargeClass")

package {pkg}

{STANDALONE_BUILDER}
internal object TablerIcons {{
{body}}}
""")
    print(f"{len(names)} glyphs -> {out}")


def vector_xml(t, ps, filled, tint, mirrored, note):
    body = []
    for d in ps:
        if filled:
            body.append(f'    <path\n        android:fillColor="@android:color/white"\n        android:pathData="{d}"\n        />')
        else:
            body.append(f'    <path\n        android:pathData="{d}"\n        android:strokeWidth="1.5"\n        android:strokeColor="@android:color/white"\n'
                        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n        />')
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {note} Tabler Icons "{t}" ({"filled" if filled else "outline, 1.5 stroke"}), MIT. -->
<vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="{"true" if mirrored else "false"}"
    android:tint="{tint}"
    android:viewportWidth="24"
    android:viewportHeight="24"
    >
''' + "\n".join(body) + "\n</vector>\n"


def drawables(tabler, out, res_dirs):
    out = pathlib.Path(out) / "drawable"
    out.mkdir(parents=True, exist_ok=True)
    done = []
    for res in res_dirs:
        for f in sorted(pathlib.Path(res).glob("drawable/ic_*.xml")):
            src = f.read_text()
            if "<vector" not in src:
                continue
            name = "".join(w.capitalize() for w in f.stem[3:].split("_"))
            if name not in MAP:
                continue
            tint = re.search(r'android:tint="([^"]+)"', src)
            t = MAP[name]
            ps, filled = glyph(tabler, t, kelvin_filled(name))
            (out / f.name).write_text(vector_xml(t, ps, filled, tint.group(1) if tint else "?attr/colorControlNormal",
                                                 t in MIRRORED, f"Kelvin glyph for {f.stem}, overriding the Material one by name."))
            done.append(f.stem)
    print(f"{len(done)} drawables -> {out}")


def kelvin_drawables(tabler, src, out):
    """kelvin_ic_<name> for every Tabler glyph Kelvin draws, so View screens use the same set."""
    names = set()
    for p in pathlib.Path(src).rglob("*.kt"):
        text = p.read_text()
        names |= set(re.findall(r'\b(?:icon|set)\("([a-z0-9-]+)"', text))
        if p.name == "KelvinIconPaths.kt":
            names |= set(re.findall(r'^\s+"([a-z0-9-]+)" to listOf', text, re.M))
    out = pathlib.Path(out) / "drawable"
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for t in sorted(names):
        if not (pathlib.Path(tabler) / "outline" / f"{t}.svg").exists():
            continue
        ps, filled = glyph(tabler, t, not OUTLINE or t in STATE_FILLED)
        (out / f"kelvin_ic_{t.replace('-', '_')}.xml").write_text(
            vector_xml(t, ps, filled, "?attr/colorControlNormal", t in MIRRORED, "Generated by tools/material_to_tabler.py."))
        n += 1
    print(f"{n} Kelvin glyphs -> {out}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a and a[0] == "facade":
        names = [tuple(l.strip().split(".", 1)) for l in pathlib.Path(a[2]).read_text().splitlines() if l.strip()]
        facade(a[1], names, a[3], a[4])
    elif a and a[0] == "drawables":
        drawables(a[1], a[2], a[3:])
    elif a and a[0] == "kelvin-drawables":
        kelvin_drawables(a[1], a[2], a[3])
    else:
        compose_scan(a[0], a[1], a[2], a[3], "--standalone" in a)
