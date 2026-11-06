#!/usr/bin/env python3
"""Kelvin's own doodles: the shared drawing core. playful.py and polished.py draw the scenes, build.py writes them.

For each scene build.py writes an SVG (open it anywhere; the colours are role markers) and the path list
KelvinDoodle reads. Each line of the list is:  <role> <how> 0 0 <pathData>
  role: ink | accent | paper | wash        (paper is the page colour, wash is the accent at low alpha)
  how:  0 / 1   fill, nonzero / even-odd
        s<w>    stroke of width w, round caps and joins
"""
import math, os, sys

INK, ACCENT, PAPER, WASH = "#000000", "#ff5678", "#ffffff", "#ffd3dc"
ROLE = {INK: "ink", ACCENT: "accent", PAPER: "paper", WASH: "wash"}
LINE = 7          # the one outline weight
FINE = 5          # faces, text lines, sparkles


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def smooth(pts, closed=False):
    """Catmull-Rom through pts as cubic Béziers."""
    if len(pts) == 2:
        (a, b), (c, d) = pts
        return f"M{f(a)},{f(b)} L{f(c)},{f(d)}"
    p = pts[:]
    if closed:
        p = [pts[-1]] + pts + pts[:2]
    else:
        p = [pts[0]] + pts + [pts[-1]]
    out = [f"M{f(p[1][0])},{f(p[1][1])}"]
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out.append(f"C{f(c1[0])},{f(c1[1])} {f(c2[0])},{f(c2[1])} {f(p2[0])},{f(p2[1])}")
    return " ".join(out) + (" Z" if closed else "")


def ellipse(cx, cy, rx, ry=None):
    ry = rx if ry is None else ry
    return (f"M{f(cx - rx)},{f(cy)} A{f(rx)},{f(ry)} 0 1,0 {f(cx + rx)},{f(cy)} "
            f"A{f(rx)},{f(ry)} 0 1,0 {f(cx - rx)},{f(cy)} Z")


def rot(pts, cx, cy, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def rrect(cx, cy, w, h, r, deg=0):
    """A rounded rectangle as a closed path, rotated about its centre."""
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    k = 0.5523 * r
    segs = [((x0 + r, y0), None), ((x1 - r, y0), None), ((x1, y0 + r), ((x1 - r + k, y0), (x1, y0 + r - k))),
            ((x1, y1 - r), None), ((x1 - r, y1), ((x1, y1 - r + k), (x1 - r + k, y1))),
            ((x0 + r, y1), None), ((x0, y1 - r), ((x0 + r - k, y1), (x0, y1 - r + k))),
            ((x0, y0 + r), None), ((x0 + r, y0), ((x0, y0 + r - k), (x0 + r - k, y0)))]
    R = lambda p: rot([p], cx, cy, deg)[0]
    out = [f"M{f(R(segs[0][0])[0])},{f(R(segs[0][0])[1])}"]
    for pt, ctrl in segs[1:]:
        q = R(pt)
        if ctrl:
            a, b = R(ctrl[0]), R(ctrl[1])
            out.append(f"C{f(a[0])},{f(a[1])} {f(b[0])},{f(b[1])} {f(q[0])},{f(q[1])}")
        else:
            out.append(f"L{f(q[0])},{f(q[1])}")
    return " ".join(out) + " Z"


class Scene:
    def __init__(self, name):
        self.name, self.parts = name, []

    # primitives ---------------------------------------------------------------------------
    def fill(self, d, colour, evenodd=False):
        self.parts.append((colour, "1" if evenodd else "0", d))

    def stroke(self, d, colour=INK, w=LINE):
        self.parts.append((colour, f"s{f(w)}", d))

    def shape(self, d, colour=PAPER, w=LINE):
        """A filled shape with the ink outline."""
        self.fill(d, colour)
        self.stroke(d, INK, w)

    def tube(self, pts, w, colour=PAPER):
        """A soft limb: an ink stroke with a narrower stroke of colour on top, so it reads outlined."""
        d = smooth(pts)
        self.stroke(d, INK, w + 2 * LINE)
        self.stroke(d, colour, w)

    def line(self, pts, w=LINE, colour=INK):
        self.stroke(smooth(pts), colour, w)

    def dot(self, x, y, r=5.5, colour=INK):
        self.fill(ellipse(x, y, r), colour)

    def pebble(self, cx, cy, rx, ry):
        """The heat-lit pebble every scene rests on: a soft wash in the accent."""
        self.fill(ellipse(cx, cy, rx, ry), WASH)

    def sparkle(self, x, y, s=16, colour=ACCENT):
        self.stroke(f"M{f(x - s)},{f(y)} L{f(x + s)},{f(y)} M{f(x)},{f(y - s)} L{f(x)},{f(y + s)}", colour, FINE + 1)

    def heart(self, cx, cy, s, colour=ACCENT, outline=True, deg=0):
        pts = [(0, 0.35), (-0.5, -0.15), (-0.5, -0.45), (-0.25, -0.62), (0, -0.42),
               (0.25, -0.62), (0.5, -0.45), (0.5, -0.15)]
        p = rot([(cx + x * s, cy + y * s) for x, y in pts], cx, cy, deg)
        d = (f"M{f(p[0][0])},{f(p[0][1])} C{f(p[1][0])},{f(p[1][1])} {f(p[2][0])},{f(p[2][1])} {f(p[3][0])},{f(p[3][1])} "
             f"C{f(p[3][0] + 0.12 * s)},{f(p[3][1] - 0.06 * s)} {f(p[4][0] - 0.05 * s)},{f(p[4][1] - 0.05 * s)} {f(p[4][0])},{f(p[4][1])} "
             f"C{f(p[4][0] + 0.05 * s)},{f(p[4][1] - 0.05 * s)} {f(p[5][0] - 0.12 * s)},{f(p[5][1] - 0.06 * s)} {f(p[5][0])},{f(p[5][1])} "
             f"C{f(p[6][0])},{f(p[6][1])} {f(p[7][0])},{f(p[7][1])} {f(p[0][0])},{f(p[0][1])} Z")
        if outline:
            self.shape(d, colour)
        else:
            self.fill(d, colour)

    def fit(self, margin=36, most=1.4):
        """Scale the scene about its own bounds to fill the 1024 × 768 frame, as Open Doodles do."""
        xs, ys = [], []
        for _, how, d in self.parts:
            pad = float(how[1:]) / 2 if how.startswith("s") else 0
            for x, y in points(d):
                xs += [x - pad, x + pad]; ys += [y - pad, y + pad]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        k = min((1024 - 2 * margin) / (x1 - x0), (768 - 2 * margin) / (y1 - y0), most)
        ox, oy = 512 - k * (x0 + x1) / 2, 384 - k * (y0 + y1) / 2
        self.parts = [(c, f"s{f(float(how[1:]) * k)}" if how.startswith("s") else how, transform(d, k, ox, oy))
                      for c, how, d in self.parts]
        return self

    def svg(self):
        body = []
        for colour, how, d in self.parts:
            if how.startswith("s"):
                body.append(f'<path d="{d}" fill="none" stroke="{colour}" stroke-width="{how[1:]}" '
                            f'stroke-linecap="round" stroke-linejoin="round"/>')
            else:
                rule = ' fill-rule="evenodd"' if how == "1" else ""
                body.append(f'<path d="{d}" fill="{colour}"{rule}/>')
        return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 768">\n' + "\n".join(body) + "\n</svg>\n")

    def txt(self):
        return "".join(f"{ROLE[c]} {how} 0 0 {d}\n" for c, how, d in self.parts)


def _walk(d, k=1.0, ox=0.0, oy=0.0):
    """Yield path tokens with coordinates scaled; arc flags and rotation are left alone.
    Only the absolute commands this file writes occur: M L C A Z."""
    import re
    toks = re.findall(r"[MLCAZ]|-?\d+(?:\.\d+)?", d)
    out, pts, cmd, i, n = [], [], None, 0, 0
    while i < len(toks):
        t = toks[i]
        if t in "MLCAZ":
            cmd, n = t, 0
            out.append(t); i += 1; continue
        if cmd == "A":
            rx, ry, rot_, la, sw, x, y = (float(v) for v in toks[i:i + 7])
            x, y = x * k + ox, y * k + oy
            pts.append((x, y))
            out.append(f"{f(rx * k)},{f(ry * k)} {f(rot_)} {int(la)},{int(sw)} {f(x)},{f(y)}")
            i += 7
        else:
            x, y = float(toks[i]) * k + ox, float(toks[i + 1]) * k + oy
            pts.append((x, y))
            out.append(f"{f(x)},{f(y)}")
            i += 2
    return out, pts


def points(d):
    return _walk(d)[1]


def transform(d, k, ox, oy):
    return " ".join(_walk(d, k, ox, oy)[0]).replace("M ", "M").replace("L ", "L").replace("C ", "C").replace("A ", "A")




# the hand ---------------------------------------------------------------------------------
# Animals are drawn the way a pen draws them: every line is a brush stroke that swells and
# tapers (written out as a filled ink shape), every shape wobbles a little off true, and an
# outline runs a touch past where it started instead of closing neatly.

import random


def catmull(pts, closed=False, step=6.0):
    """Points along a Catmull-Rom curve through pts, about `step` units apart."""
    n = len(pts)
    p = ([pts[-1]] + pts + pts[:2]) if closed else ([pts[0]] + pts + [pts[-1]])
    out = []
    segs = n if closed else n - 1
    for i in range(1, segs + 1):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        L = math.dist(p1, p2)
        m = max(2, int(L / step))
        for j in range(m):
            t = j / m
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    if not closed:
        out.append(pts[-1])
    return out


def poly(pts, closed=True):
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return d + (" Z" if closed else "")


class Hand(Scene):
    STYLES = {
        # casual: a loose pen; outlines overshoot where they close, shapes sit a touch off true
        "casual": dict(swell=0.07, jit=1.0, over=True, lo=0.0, lw=LINE),
        # semi: the same animals drawn steady; the line still swells and tapers, nothing wobbles
        "semi": dict(swell=0.035, jit=0.0, over=False, lo=0.42, lw=6.2),
    }

    def __init__(self, name, style="casual"):
        super().__init__(name)
        self.style = style
        self.rng = random.Random(name)
        st = self.STYLES[style]
        self.swell, self.jit, self.over, self.lo_floor, self.lw = st["swell"], st["jit"], st["over"], st["lo"], st["lw"]

    def jitter(self, pts, amt=1.0):
        amt *= self.jit
        return [(x + self.rng.uniform(-amt, amt), y + self.rng.uniform(-amt, amt)) for x, y in pts]

    def _wave(self):
        a, b = self.rng.uniform(1.2, 2.6), self.rng.uniform(3.0, 5.5)
        pa, pb = self.rng.random(), self.rng.random()
        return lambda t: 0.7 * math.sin(math.tau * (a * t + pa)) + 0.3 * math.sin(math.tau * (b * t + pb))

    def brush(self, pts, w=None, colour=INK, closed=False, taper=0.16, lo=0.3):
        """A pen stroke along pts: tapered ends, a slight swell, drawn as a filled shape."""
        w = self.lw if w is None else w
        lo = max(lo, self.lo_floor)
        if closed and not self.over:
            # a steady hand: the outline closes on itself, an even line all round
            line = catmull(pts, closed=True)
            line = line + line[:2]
            taper, lo = 1e-9, 1.0
        elif closed:
            # start somewhere along the loop and run a little past the start
            k = self.rng.randrange(len(pts))
            loop = pts[k:] + pts[:k]
            line = catmull(loop, closed=True)
            over = max(2, len(line) // 24)
            line = line + line[:over]
        else:
            line = catmull(pts)
        if len(line) < 3:
            return
        wave = self._wave()
        total = sum(math.dist(line[i], line[i + 1]) for i in range(len(line) - 1)) or 1
        left, right, acc = [], [], 0.0
        for i, (x, y) in enumerate(line):
            if i:
                acc += math.dist(line[i - 1], line[i])
            t = acc / total
            a, b = line[max(i - 1, 0)], line[min(i + 1, len(line) - 1)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            L = math.hypot(dx, dy) or 1
            nx, ny = -dy / L, dx / L
            edge = min(t / taper, (1 - t) / taper, 1)
            hw = w / 2 * (lo + (1 - lo) * (edge * edge * (3 - 2 * edge))) * (1 + self.swell * wave(t))
            left.append((x + nx * hw, y + ny * hw))
            right.append((x - nx * hw, y - ny * hw))
        outline = left + right[::-1]
        stride = max(1, int(round(len(outline) / max(8, total / 7))))
        outline = outline[::stride] if stride > 1 else outline
        self.fill(smooth(outline, closed=True), colour)

    def blob(self, pts, colour=PAPER, w=None, line=True, wobble=0.8, sharp=False):
        """A hand-drawn shape: filled, with a pen outline that overshoots where it closes.
        sharp keeps the corners (boxes, tables, books) by pinning points close to each one."""
        pts = self.jitter(pts, wobble)
        if sharp:
            dense = []
            for i, a in enumerate(pts):
                b = pts[(i + 1) % len(pts)]
                for t in (0.0, 0.07, 0.5, 0.93):
                    dense.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            pts = dense
        self.fill(smooth(pts, closed=True), colour)
        if line:
            self.brush(pts, w, closed=True)
        return pts

    def ribbon(self, centre, w0, w1, colour=PAPER, line=True, w=None, open_base=True):
        """A tail or a leg: a shape along a centre line, w0 wide at its start and w1 at its rounded tip.
        open_base leaves the outline off the start, so a tail or leg grows out of the body it overlaps."""
        c = catmull(centre, step=10)
        if len(c) < 2:
            return
        total = sum(math.dist(c[i], c[i + 1]) for i in range(len(c) - 1)) or 1
        left, right, acc = [], [], 0.0
        for i, (x, y) in enumerate(c):
            if i:
                acc += math.dist(c[i - 1], c[i])
            t = acc / total
            a, b = c[max(i - 1, 0)], c[min(i + 1, len(c) - 1)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            L = math.hypot(dx, dy) or 1
            nx, ny = -dy / L, dx / L
            hw = (w0 + (w1 - w0) * t) / 2
            left.append((x + nx * hw, y + ny * hw))
            right.append((x - nx * hw, y - ny * hw))
        def cap(p, q, centre_pt, r, forward):
            # a round end: half a circle from p to q around centre_pt
            a0 = math.atan2(p[1] - centre_pt[1], p[0] - centre_pt[0])
            out = []
            for j in range(1, 6):
                a = a0 + (math.pi * j / 6) * (-1 if forward else 1)
                out.append((centre_pt[0] + r * math.cos(a), centre_pt[1] + r * math.sin(a)))
            return out
        tip = cap(left[-1], right[-1], c[-1], w1 / 2, True)
        base = cap(right[0], left[0], c[0], w0 / 2, True)
        loop = left[::2] + [left[-1]] + tip + [right[-1]] + right[::-2] + [right[0]] + base
        if not open_base:
            return self.blob(loop, colour, w=w, line=line, wobble=0)
        self.fill(smooth(loop, closed=True), colour)
        if line:
            side = left[::2] + [left[-1]] + tip + [right[-1]] + right[::-2] + [right[0]]
            self.brush(side, w, taper=0.04, lo=0.55)
        return loop

    def oval(self, cx, cy, rx, ry=None, n=None, deg=0):
        ry = rx if ry is None else ry
        n = n or max(8, int((rx + ry) / 9))
        o = self.rng.uniform(0, math.tau)
        pts = [(cx + rx * math.cos(o + math.tau * i / n), cy + ry * math.sin(o + math.tau * i / n)) for i in range(n)]
        return rot(pts, cx, cy, deg) if deg else pts

    def at(self, shape, cx, cy, sx, sy=None, deg=0):
        """Place a unit shape (a list of points around 0,0) at cx, cy."""
        sy = sx if sy is None else sy
        pts = [(cx + x * sx, cy + y * sy) for x, y in shape]
        return rot(pts, cx, cy, deg) if deg else pts

    def ground(self, cx, cy, rx, ry=26):
        self.fill(smooth(self.jitter(self.oval(cx, cy, rx, ry, n=14), 4), closed=True), WASH)

    def hand_heart(self, cx, cy, s, colour=ACCENT, line=True, deg=0):
        unit = [(0, 0.42), (-0.3, 0.12), (-0.5, -0.16), (-0.46, -0.42), (-0.24, -0.54), (-0.04, -0.4),
                (0, -0.34), (0.06, -0.42), (0.26, -0.55), (0.48, -0.4), (0.5, -0.14), (0.3, 0.13)]
        return self.blob(self.at(unit, cx, cy, s, s, deg), colour, line=line, wobble=s * 0.012)

    def spark(self, x, y, s=14, colour=ACCENT):
        self.brush([(x - s, y + 2), (x + s, y - 2)], 6, colour, lo=0.5)
        self.brush([(x + 2, y - s), (x - 2, y + s)], 6, colour, lo=0.5)

    # faces --------------------------------------------------------------------------------
    def eyes(self, cx, cy, gap, kind="open", r=7.5, colour=INK):
        for ex in (cx - gap, cx + gap):
            if kind == "open":
                self.blob(self.oval(ex, cy, r * 0.85, r * 1.15, n=7), colour, line=False, wobble=0.8)
            elif kind == "down":
                self.blob(self.oval(ex, cy + r * 0.5, r * 0.8, r * 0.7, n=7), colour, line=False, wobble=0.5)
                self.brush([(ex - r * 1.3, cy - r * 0.2), (ex + r * 1.3, cy - r * 0.2)], 4.5, colour, lo=0.3)
            elif kind == "meh":
                # half-closed, unimpressed: a flat lid over the lower half of the eye
                self.blob([(ex - r, cy), (ex - r * 0.7, cy + r * 0.8), (ex, cy + r * 1.05), (ex + r * 0.7, cy + r * 0.8), (ex + r, cy)],
                          colour, line=False, wobble=0.3)
                self.brush([(ex - r * 1.4, cy - 1), (ex + r * 1.4, cy - 1)], 5, colour, lo=0.5)
            elif kind == "closed":
                self.brush([(ex - r * 1.4, cy - 2), (ex, cy + r * 0.7), (ex + r * 1.4, cy - 2)], 5.5, colour)
            elif kind == "happy":
                self.brush([(ex - r * 1.4, cy + 3), (ex, cy - r * 0.8), (ex + r * 1.4, cy + 3)], 5.5, colour)
            elif kind == "wide":
                self.blob(self.oval(ex, cy, r * 1.6, r * 1.8, n=9), PAPER, w=4.5, wobble=0.8)
                self.blob(self.oval(ex + 1, cy + 1, r * 0.75, r * 0.9, n=7), colour, line=False, wobble=0.5)

    def cat_face(self, cx, cy, s, eyes="open", look=0, ink=INK, mouth="w", whiskers=True):
        fx = cx + look * s * 0.25
        self.eyes(fx, cy - s * 0.04, s * 0.34, eyes, r=s * 0.075, colour=ink)
        ny = cy + s * 0.2
        self.blob([(fx - s * 0.08, ny - s * 0.04), (fx + s * 0.08, ny - s * 0.04), (fx, ny + s * 0.05)], ACCENT, line=False, wobble=0.6)
        if mouth == "w":
            self.brush([(fx - s * 0.17, ny + s * 0.1), (fx - s * 0.08, ny + s * 0.16), (fx, ny + s * 0.07)], 4.5, ink)
            self.brush([(fx, ny + s * 0.07), (fx + s * 0.08, ny + s * 0.16), (fx + s * 0.17, ny + s * 0.1)], 4.5, ink)
        elif mouth == "flat":
            self.brush([(fx - s * 0.1, ny + s * 0.13), (fx + s * 0.1, ny + s * 0.13)], 4.5, ink, lo=0.5)
        elif mouth == "o":
            self.blob(self.oval(fx, ny + s * 0.16, s * 0.06, s * 0.08, n=7), ink, line=False, wobble=0.6)
        if whiskers:
            rows = (-0.04, 0.06) if self.style == "casual" else (0.01,)
            for side in (-1, 1):
                for k, dy in enumerate(rows):
                    x0 = cx + side * s * 0.62
                    self.brush([(x0, ny + s * dy), (x0 + side * s * 0.22, ny + s * (dy - 0.03 + 0.05 * k)),
                                (x0 + side * s * 0.42, ny + s * (dy - 0.02 + 0.1 * k))], 4, ink, lo=0.2)

    def cat_head(self, cx, cy, s, colour=PAPER, eyes="open", look=0, mouth="w", tilt=0, stripes=True, ear_in=True):
        """A round cat head with pointed ears, `s` about the head's half width."""
        unit = [(-0.98, 0.18), (-1.0, -0.22), (-0.86, -0.56), (-0.8, -0.86), (-0.76, -1.2), (-0.56, -0.98),
                (-0.36, -0.76), (0, -0.8), (0.36, -0.76), (0.56, -0.98), (0.77, -1.21), (0.82, -0.86),
                (0.87, -0.55), (1.0, -0.2), (0.96, 0.2), (0.7, 0.58), (0.32, 0.76), (0, 0.79), (-0.34, 0.76), (-0.72, 0.56)]
        pts = self.blob(self.at(unit, cx, cy, s, s * 0.86, tilt), colour, wobble=s * 0.006)
        ink = PAPER if colour == INK else INK
        if ear_in:
            for side in (-1, 1):
                ear = [(side * 0.74, -1.02), (side * 0.56, -0.84), (side * 0.78, -0.72)]
                self.blob(self.at(ear, cx, cy, s, s * 0.86, tilt), ACCENT, line=False, wobble=1)
        if stripes and colour != INK:
            for dx in (-0.16, 0, 0.16):
                top = rot([(cx + dx * s, cy - s * 0.66), (cx + dx * s * 1.1, cy - s * 0.48)], cx, cy, tilt)
                self.brush(top, 6, INK, lo=0.25)
        self.cat_face(cx, cy + s * 0.08, s, eyes, look, ink, mouth)
        return pts


