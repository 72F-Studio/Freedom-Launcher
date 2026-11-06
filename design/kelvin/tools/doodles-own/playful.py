"""The playful set: cats, a dog and birds in a calm pen line, for Gallery, Rediom and the other everyday apps."""
import math
from core import Hand, INK, ACCENT, PAPER, WASH, LINE, rot, smooth, catmull


class Pen(Hand):
    """Hand plus the animals and props the scenes share. Positions are (cx, base) and a scale k."""

    # bits ---------------------------------------------------------------------------------
    def paw(self, x, y, k=1.0, deg=0, colour=PAPER):
        self.blob(self.oval(x, y, 27 * k, 18 * k, deg=deg), colour)
        ink = PAPER if colour == INK else INK
        for dx in (-8, 8):
            p = rot([(x + dx * k, y - 4 * k), (x + dx * k, y + 8 * k)], x, y, deg)
            self.brush(p, 4, ink, lo=0.3)

    def tail(self, pts, colour=PAPER, w0=32, w1=20):
        self.ribbon(pts, w0, w1, colour)

    def zzz(self, x, y, k=1.0):
        for i, (dx, dy, kk) in enumerate(((0, 0, 1.0), (52, -58, 0.78), (94, -106, 0.58))):
            q = k * kk
            cx, cy = x + dx * k, y + dy * k
            self.brush([(cx - 16 * q, cy - 16 * q), (cx + 16 * q, cy - 17 * q), (cx - 15 * q, cy + 16 * q), (cx + 17 * q, cy + 15 * q)],
                       6 * q + 1.5, ACCENT, lo=0.5)

    def stripes(self, pts, w=6):
        if self.style != "casual":
            return
        for a, b in pts:
            self.brush([a, b], w, lo=0.2)

    def ground(self, cx, cy, rx, ry=26):
        if self.style == "casual":
            super().ground(cx, cy, rx, ry)

    def backdrop(self):
        """The steady set sits on a soft round backdrop, like the corporate set, in place of a ground."""
        from core import points
        xs, ys = [], []
        for _, how, d in self.parts:
            for x, y in points(d):
                xs.append(x); ys.append(y)
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        r = 0.44 * max(max(xs) - min(xs), max(ys) - min(ys))
        n = 28
        circle = [(cx + r * math.cos(math.tau * i / n), cy + r * 0.04 + r * math.sin(math.tau * i / n)) for i in range(n)]
        self.parts.insert(0, (WASH, "0", smooth(circle, closed=True)))

    def spark(self, x, y, s=14, colour=ACCENT):
        if self.style == "casual":
            return super().spark(x, y, s, colour)
        q = s * 0.95
        self.fill(f"M{x:.1f},{y - q:.1f} C{x:.1f},{y - q * 0.3:.1f} {x + q * 0.3:.1f},{y:.1f} {x + q:.1f},{y:.1f} "
                  f"C{x + q * 0.3:.1f},{y:.1f} {x:.1f},{y + q * 0.3:.1f} {x:.1f},{y + q:.1f} "
                  f"C{x:.1f},{y + q * 0.3:.1f} {x - q * 0.3:.1f},{y:.1f} {x - q:.1f},{y:.1f} "
                  f"C{x - q * 0.3:.1f},{y:.1f} {x:.1f},{y - q * 0.3:.1f} {x:.1f},{y - q:.1f} Z", colour)

    # cats ---------------------------------------------------------------------------------
    def cat_sit(self, cx, by, k=1.0, colour=PAPER, eyes="open", look=0, mouth="w", tail=1, front="down",
                between=None, head_tilt=0, wave=False):
        """A cat sitting, facing us: a short chest under the chin, haunches bulging at the sides, front legs that part
        below the chest. front: "down", "hug" (legs round whatever `between` draws) or "none". Returns the head's centre."""
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        ink = PAPER if colour == INK else INK
        if tail:
            t = tail
            self.ribbon([P(96 * t, -26), P(168 * t, -22), P(210 * t, -64), P(214 * t, -128), P(192 * t, -168)], 30 * k, 15 * k, colour)
        self.blob([P(-50, -290), P(-72, -246), P(-88, -200), P(-102, -160), P(-134, -118), P(-144, -64), P(-126, -14),
                   P(-70, 0), P(0, 2), P(70, 0), P(126, -14), P(144, -64), P(134, -118), P(102, -160), P(88, -200),
                   P(72, -246), P(50, -290)], colour)
        for t in (-1, 1):
            # the folded back leg: a thigh curve on the haunch, the back foot out at the side
            self.brush([P(132 * t, -116), P(104 * t, -136), P(84 * t, -100), P(84 * t, -50), P(96 * t, -14)], None, ink, lo=0.3)
            self.blob(self.oval(*P(110 * t, -8), 32 * k, 14 * k), colour)
        self.stripes([(P(-84, -214), P(-62, -208)), (P(-94, -184), P(-72, -178)), (P(84, -214), P(62, -208)),
                      (P(94, -184), P(72, -178))])
        if front == "hug":
            for t in (-1, 1):
                self.ribbon([P(56 * t, -262), P(112 * t, -216), P(118 * t, -150), P(80 * t, -110)], 36 * k, 32 * k, colour)
        if between:
            between()
        if front == "hug":
            self.paw(*P(-74, -104), k, 24, colour)
            self.paw(*P(74, -104), k, -24, colour)
        elif front == "down":
            self.ribbon([P(-46, -196), P(-46, -110), P(-44, -34)], 38 * k, 35 * k, colour)
            self.paw(*P(-44, -24), k, 0, colour)
            if wave:
                self.ribbon([P(58, -262), P(112, -300), P(140, -380)], 36 * k, 32 * k, colour)
                self.paw(*P(144, -394), k, -72, colour)
            else:
                self.ribbon([P(46, -196), P(46, -110), P(44, -34)], 38 * k, 35 * k, colour)
                self.paw(*P(44, -24), k, 0, colour)
        hx, hy = P(0, -350)
        self.cat_head(hx, hy, 96 * k, colour, eyes, look, mouth, tilt=head_tilt)
        return hx, hy

    def cat_loaf(self, cx, by, k=1.0, colour=PAPER, eyes="closed", look=0, mouth="w", tail=True, paws=True):
        """A cat lying on its tummy, front paws tucked forward."""
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        if tail:
            self.tail([P(150, -170), P(214, -228), P(214, -290), P(180, -312)], colour, 30 * k, 20 * k)
        self.blob([P(-172, -46), P(-180, -128), P(-130, -190), P(-40, -212), P(70, -212), P(150, -190), P(194, -128),
                   P(188, -46), P(110, -6), P(0, 0), P(-110, -6)], colour)
        if colour != INK:
            self.stripes([(P(90, -192), P(80, -160)), (P(122, -180), P(110, -150)), (P(150, -160), P(136, -132))])
        hx, hy = P(0, -256)
        self.cat_head(hx, hy, 100 * k, colour, eyes, look, mouth)
        if paws:
            self.paw(*P(-60, -14), k, 0, colour)
            self.paw(*P(60, -14), k, 0, colour)
        return hx, hy

    def cat_curl(self, cx, cy, k=1.0, colour=PAPER):
        """A cat curled up asleep, its tail growing out of the rump and wrapping round the front."""
        P = lambda dx, dy: (cx + dx * k, cy + dy * k)
        self.blob([P(-182, 30), P(-172, -50), P(-102, -110), P(0, -130), P(102, -110), P(174, -50), P(184, 30),
                   P(128, 82), P(0, 98), P(-128, 84)], colour)
        self.stripes([(P(-12, -106), P(-18, -76)), (P(44, -100), P(36, -70)), (P(98, -80), P(86, -52))], 7)
        self.ribbon([P(178, 16), P(160, 66), P(90, 94), P(0, 100), P(-70, 92)], 34 * k, 13 * k, colour)
        self.cat_head(*P(-100, 0), 86 * k, colour, "closed", 0, None, tilt=-14)

    # the dog ------------------------------------------------------------------------------
    def dog_head(self, hx, hy, k=1.0, eyes="open", tongue=False, look=0):
        P = lambda dx, dy: (hx + dx * k, hy + dy * k)
        self.blob([P(-76, -30), P(-70, -76), P(-20, -96), P(36, -92), P(78, -58), P(82, 0), P(60, 52), P(0, 70),
                   P(-58, 54), P(-82, 10)])
        self.blob([P(-62, -72), P(-100, -44), P(-110, 26), P(-92, 54), P(-70, 18), P(-56, -30)], INK)
        self.blob([P(54, -78), P(96, -50), P(110, 22), P(92, 52), P(70, 12), P(48, -34)], INK)
        mx = hx + look * 16 * k
        self.blob(self.oval(mx, hy + 32 * k, 40 * k, 28 * k))
        if tongue:
            self.blob([(mx - 12 * k, hy + 50 * k), (mx + 12 * k, hy + 50 * k), (mx + 12 * k, hy + 74 * k), (mx, hy + 82 * k),
                       (mx - 12 * k, hy + 74 * k)], ACCENT)
        self.blob(self.oval(mx, hy + 18 * k, 15 * k, 10 * k, n=7), INK, line=False, wobble=0.4)
        self.brush([(mx, hy + 28 * k), (mx, hy + 42 * k)], 4.5)
        self.brush([(mx - 16 * k, hy + 46 * k), (mx, hy + 42 * k), (mx + 16 * k, hy + 46 * k)], 4.5)
        self.eyes(mx, hy - 16 * k, 26 * k, eyes, r=7 * k)

    def dog_sit(self, cx, by, k=1.0, eyes="open", tongue=True, look=0, wag=True, between=None):
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        if wag:
            self.ribbon([P(104, -30), P(166, -60), P(194, -122)], 28 * k, 15 * k)
            self.brush([P(214, -146), P(228, -164)], 5, lo=0.3)
            self.brush([P(226, -114), P(248, -120)], 5, lo=0.3)
        self.blob([P(-58, -272), P(-82, -226), P(-98, -180), P(-112, -146), P(-142, -104), P(-150, -56), P(-130, -12),
                   P(-72, 0), P(0, 2), P(72, 0), P(130, -12), P(150, -56), P(142, -104), P(112, -146), P(98, -180),
                   P(82, -226), P(58, -272)])
        self.blob(self.oval(*P(-114, -84), 24 * k, 30 * k, deg=10), INK, line=False, wobble=0.6)
        for t in (-1, 1):
            self.brush([P(140 * t, -104), P(112 * t, -126), P(90 * t, -92), P(90 * t, -46), P(100 * t, -12)], None, INK, lo=0.3)
            self.blob(self.oval(*P(116 * t, -8), 36 * k, 15 * k))
        if between:
            between()
        for t in (-1, 1):
            self.ribbon([P(48 * t, -190), P(48 * t, -110), P(46 * t, -34)], 42 * k, 38 * k)
            self.paw(*P(46 * t, -24), k * 1.1)
        hx, hy = P(0, -322)
        self.dog_head(hx, hy, k, eyes, tongue, look)
        return hx, hy

    def dog_lie(self, cx, by, k=1.0, eyes="open", head_dy=0, tongue=False, between=None):
        """The dog lying down, facing us, front paws forward. between draws over the paws' base, under the head."""
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        self.tail([P(-260, -90), P(-300, -128), P(-316, -176)], PAPER, 28 * k, 18 * k)
        self.blob([P(-270, -40), P(-290, -100), P(-250, -158), P(-160, -180), P(-40, -176), P(30, -140), P(40, -60),
                   P(0, -16), P(-140, -6), P(-240, -14)])
        self.blob(self.oval(*P(-170, -110), 30 * k, 22 * k, deg=-12), INK, line=False, wobble=0.8)
        self.blob(self.oval(*P(-96, -140), 18 * k, 13 * k), INK, line=False, wobble=0.6)
        self.blob(self.oval(*P(-226, -26), 44 * k, 22 * k))
        if between:
            between()
        self.ribbon([P(-30, -60), P(20, -26), P(70, -18)], 38 * k, 36 * k)
        self.ribbon([P(40, -70), P(110, -30), P(160, -18)], 38 * k, 36 * k)
        self.paw(*P(78, -18), k * 1.1, -6)
        self.paw(*P(168, -18), k * 1.1, -6)
        hx, hy = P(60, -170 + head_dy)
        self.dog_head(hx, hy, k, eyes, tongue)
        return hx, hy

    # birds --------------------------------------------------------------------------------
    def bird(self, cx, cy, k=1.0, colour=ACCENT, eyes="open", wings="fold", feet=True, crest=True, beak="closed"):
        """A round bird facing us. wings: "fold" or "up" (flying)."""
        P = lambda dx, dy: (cx + dx * k, cy + dy * k)
        if wings == "up":
            for t in (-1, 1):
                self.blob([P(100 * t, -10), P(170 * t, -70), P(260 * t, -96), P(300 * t, -80), P(270 * t, -44), P(290 * t, -20),
                           P(250 * t, 6), P(266 * t, 30), P(200 * t, 40), P(120 * t, 40)], PAPER)
                self.brush([P(190 * t, -40), P(240 * t, -38)], 4.5, lo=0.2)
                self.brush([P(170 * t, 4), P(220 * t, 12)], 4.5, lo=0.2)
        if feet:
            self.brush([P(-30, 140), P(-36, 176)], 6)
            self.brush([P(30, 140), P(36, 176)], 6)
            self.brush([P(-54, 178), P(-36, 176), P(-20, 182)], 5)
            self.brush([P(20, 182), P(36, 176), P(54, 178)], 5)
        self.blob([P(0, -150), P(-86, -124), P(-140, -40), P(-136, 60), P(-86, 136), P(0, 164), P(86, 136), P(136, 60),
                   P(140, -40), P(86, -124)], colour)
        self.blob([P(-72, 40), P(-60, 116), P(0, 144), P(60, 116), P(72, 40), P(36, 12), P(-36, 12)])
        for y in (50, 80):
            for x in (-28, 12):
                self.brush([P(x - 8, y), P(x + 4, y + 10), P(x + 16, y)], 4.5, lo=0.2)
        if wings == "fold":
            for t in (-1, 1):
                self.blob([P(108 * t, -30), P(150 * t, 10), P(150 * t, 80), P(118 * t, 120), P(92 * t, 86), P(90 * t, 10)], PAPER)
                self.brush([P(118 * t, 30), P(130 * t, 70)], 4.5, lo=0.2)
        if crest:
            self.brush([P(-6, -150), P(-16, -186), P(8, -174), P(16, -206)], 6, lo=0.3)
        self.eyes(cx, cy - 66 * k, 36 * k, eyes, r=9 * k)
        if beak == "open":
            self.blob([P(-22, -38), P(22, -38), P(0, -8)], INK, line=False, wobble=0.3)
            self.blob([P(-18, -30), P(18, -30), P(0, -18)], ACCENT, line=False, wobble=0.2)
        else:
            self.blob([P(-22, -40), P(22, -40), P(0, -8)], INK, line=False, wobble=0.3)

    def headphones(self, hx, hy, s, band=True):
        """Big headphones on a head of half-width s centred at hx, hy."""
        if band:
            self.ribbon([(hx - s * 0.98, hy + s * 0.02), (hx - s * 0.7, hy - s * 0.98), (hx, hy - s * 1.22),
                         (hx + s * 0.7, hy - s * 0.98), (hx + s * 0.98, hy + s * 0.02)], s * 0.16, s * 0.16, INK, open_base=False)
        for t in (-1, 1):
            self.blob(self.oval(hx + t * s * 0.98, hy + s * 0.08, s * 0.2, s * 0.32), ACCENT)

    def mic(self, cx, by, k=1.0):
        """A podcast microphone on a desk stand."""
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        self.blob(self.oval(*P(0, -10), 70 * k, 16 * k))
        self.brush([P(0, -20), P(0, -150)], 14 * k, lo=1)
        self.ribbon([P(0, -150), P(0, -330)], 92 * k, 92 * k, PAPER, open_base=False)
        for y in (-300, -270, -240):
            self.brush([P(-26, y), P(26, y)], 5, lo=0.4)
        self.blob([P(-50, -200), P(50, -200), P(50, -176), P(-50, -176)], ACCENT, sharp=True)

    # props --------------------------------------------------------------------------------
    def book_open(self, cx, cy, w=130, lines=True, deg=0):
        L = [(cx, cy - 16), (cx - w * 0.33, cy - 30), (cx - w * 0.78, cy - 30), (cx - w, cy - 20), (cx - w * 0.95, cy + 80),
             (cx - w * 0.6, cy + 72), (cx - w * 0.25, cy + 76), (cx, cy + 92)]
        R = [(2 * cx - x, y) for x, y in L]
        if deg:
            L, R = rot(L, cx, cy, deg), rot(R, cx, cy, deg)
        self.blob(L)
        self.blob(R)
        self.brush(rot([(cx, cy - 14), (cx, cy + 90)], cx, cy, deg), 5)
        if lines:
            for i, y in enumerate((0, 22, 44)):
                for side in (-1, 1):
                    a = [(cx + side * w * 0.2, cy + y + 4 - i), (cx + side * w * 0.5, cy + y - 4), (cx + side * w * 0.8, cy + y + 2)]
                    self.brush(rot(a, cx, cy, deg) if deg else a, 4.5, lo=0.2)

    def book_back(self, cx, by, w=140, h=110):
        """An open book standing toward the reader, seen from behind: two covers meeting at the spine, the page
        edges showing over the top."""
        for t in (-1, 1):
            self.blob([(cx, by - 8), (cx, by - h - 10), (cx + t * w * 0.5, by - h - 22), (cx + t * (w - 12), by - h - 12),
                       (cx + t * (w - 12), by - 18)], PAPER, sharp=True)
            self.brush([(cx + t * 14, by - h - 4), (cx + t * w * 0.5, by - h - 12), (cx + t * (w - 26), by - h - 6)], 4, lo=0.3)
        for t in (-1, 1):
            self.blob([(cx, by), (cx, by - h), (cx + t * w * 0.5, by - h - 10), (cx + t * w, by - h + 2), (cx + t * w, by - 8)],
                      ACCENT, sharp=True)
        self.blob([(cx - 9, by + 2), (cx + 9, by + 2), (cx + 9, by - h - 2), (cx - 9, by - h - 2)], INK, line=False, sharp=True)

    def cup(self, cx, by, k=1.0, deg=0, steam=True):
        P = lambda dx, dy: (cx + dx * k, by + dy * k)
        body = [P(-44, -92), P(-40, -14), P(-24, 0), P(24, 0), P(40, -14), P(44, -92)]
        handle = [P(44, -72), P(70, -66), P(66, -34), P(42, -30)]
        if deg:
            body, handle = rot(body, cx, by - 46 * k, deg), rot(handle, cx, by - 46 * k, deg)
        self.blob(body, ACCENT, sharp=False)
        self.brush(handle, 7)
        if steam:
            for dx in (-14, 16):
                self.brush([P(dx, -110), P(dx - 8, -136), P(dx + 6, -158), P(dx - 2, -186)], 5, lo=0.15)

    def cushion(self, cx, by, w=260, h=70, colour=ACCENT):
        self.blob([(cx - w, by - 14), (cx - w + 10, by - h), (cx - w * 0.6, by - h - 20), (cx, by - h - 26), (cx + w * 0.6, by - h - 20),
                   (cx + w - 10, by - h), (cx + w, by - 14), (cx + w * 0.6, by + 10), (cx - w * 0.6, by + 10)], colour)
        for x in (-0.7, 0.7):
            self.brush([(cx + w * x - 14, by - 10), (cx + w * x + 2, by - 24)], 5, lo=0.2)

    def box(self, cx, top, w=280, h=196, colour=PAPER, part="all", tape=True, inside=WASH):
        """An open cardboard box seen a little from above: the opening, a back flap standing up and the two side flaps
        folded outward on their hinges. Draw part="back", then whatever sits inside, then part="front"."""
        x0, x1, d, e = cx - w / 2, cx + w / 2, w * 0.12, h * 0.22
        bl, br = (x0 + d, top - e), (x1 - d, top - e)
        if part in ("back", "all"):
            self.blob([bl, br, (x1 - d - w * 0.06, top - e - h * 0.36), (x0 + d + w * 0.06, top - e - h * 0.36)], colour, sharp=True)
            ox, oy = -w * 0.27, -h * 0.13
            self.blob([(x0, top), bl, (bl[0] + ox, bl[1] + oy), (x0 + ox, top + oy)], colour, sharp=True)
            self.blob([(x1, top), br, (br[0] - ox, br[1] + oy), (x1 - ox, top + oy)], colour, sharp=True)
            self.blob([(x0, top), (x1, top), br, bl], inside, sharp=True)
        if part in ("front", "all"):
            self.blob([(x0, top), (x1, top), (x1 - 6, top + h), (x0 + 6, top + h)], colour, sharp=True)
            if tape:
                self.blob([(cx - 22, top + 4), (cx + 22, top + 4), (cx + 21, top + h * 0.42), (cx - 21, top + h * 0.42)],
                          ACCENT, line=False, sharp=True)
            self.brush([(x0 + w * 0.18, top + h * 0.7), (x0 + w * 0.42, top + h * 0.72)], 5, lo=0.2)
            self.brush([(x0 + w * 0.18, top + h * 0.82), (x0 + w * 0.32, top + h * 0.83)], 5, lo=0.2)

    def magnifier(self, cx, cy, r=62, deg=40, handle=110):
        hx, hy = cx + math.cos(math.radians(deg)) * r, cy + math.sin(math.radians(deg)) * r
        ex, ey = cx + math.cos(math.radians(deg)) * (r + handle), cy + math.sin(math.radians(deg)) * (r + handle)
        self.ribbon([(hx, hy), (ex, ey)], 24, 26, INK)
        self.blob(self.oval(cx, cy, r + 12, r + 12, n=16), INK, line=False, wobble=0.3)
        self.blob(self.oval(cx, cy, r, r, n=16), PAPER, line=False, wobble=0.2)
        self.brush([(cx - r * 0.55, cy - r * 0.2), (cx - r * 0.4, cy - r * 0.5), (cx - r * 0.1, cy - r * 0.62)], 6, ACCENT, lo=0.3)

    def hourglass(self, cx, cy, h=170):
        w = h * 0.36
        self.blob([(cx - w * 1.2, cy - h / 2 - 12), (cx + w * 1.2, cy - h / 2 - 12), (cx + w * 1.2, cy - h / 2 + 6), (cx - w * 1.2, cy - h / 2 + 6)], INK, line=False, sharp=True)
        self.blob([(cx - w * 1.2, cy + h / 2 - 6), (cx + w * 1.2, cy + h / 2 - 6), (cx + w * 1.2, cy + h / 2 + 12), (cx - w * 1.2, cy + h / 2 + 12)], INK, line=False, sharp=True)
        self.blob([(cx - w, cy - h / 2 + 6), (cx + w, cy - h / 2 + 6), (cx + w * 0.9, cy - h * 0.2), (cx + 8, cy), (cx + w * 0.9, cy + h * 0.2),
                   (cx + w, cy + h / 2 - 6), (cx - w, cy + h / 2 - 6), (cx - w * 0.9, cy + h * 0.2), (cx - 8, cy), (cx - w * 0.9, cy - h * 0.2)])
        self.blob([(cx - w * 0.62, cy - h * 0.2), (cx + w * 0.62, cy - h * 0.2), (cx, cy - 6)], ACCENT, line=False, wobble=0.4)
        self.blob([(cx - w * 0.9, cy + h / 2 - 8), (cx, cy + h * 0.2), (cx + w * 0.9, cy + h / 2 - 8)], ACCENT, line=False, wobble=0.4)
        self.brush([(cx, cy - 4), (cx, cy + h * 0.22)], 4, ACCENT, lo=0.6)

    def cloud(self, cx, cy, w=180, colour=PAPER):
        k = w / 180
        P = lambda dx, dy: (cx + dx * k, cy + dy * k)
        self.blob([P(-170, 40), P(-176, -6), P(-130, -40), P(-90, -36), P(-70, -92), P(0, -110), P(60, -80), P(84, -50),
                   P(140, -54), P(178, -10), P(170, 40), P(120, 58), P(-120, 58)], colour)

    def bin(self, cx, by, w=200, h=230):
        x0, x1 = cx - w / 2, cx + w / 2
        self.blob([(x0 - 6, by - h), (x1 + 6, by - h), (x1 - 18, by), (x0 + 18, by)], PAPER, sharp=True)
        for t in (-0.25, 0, 0.25):
            self.brush([(cx + t * w, by - h + 36), (cx + t * w * 0.85, by - 26)], 5, lo=0.3)

    def bin_rim(self, cx, by, w=200, h=230):
        self.blob([(cx - w / 2 - 22, by - h - 18), (cx + w / 2 + 22, by - h - 18), (cx + w / 2 + 22, by - h + 8), (cx - w / 2 - 22, by - h + 8)], ACCENT, sharp=True)

    def envelope(self, cx, cy, w=150, h=100, deg=0, colour=PAPER):
        box = [(cx - w / 2, cy - h / 2), (cx + w / 2, cy - h / 2), (cx + w / 2, cy + h / 2), (cx - w / 2, cy + h / 2)]
        flap = [(cx - w / 2 + 6, cy - h / 2 + 6), (cx, cy + h * 0.12), (cx + w / 2 - 6, cy - h / 2 + 6)]
        if deg:
            box, flap = rot(box, cx, cy, deg), rot(flap, cx, cy, deg)
        self.blob(box, colour, sharp=True)
        self.brush(flap, 5.5, lo=0.6)

    def padlock(self, cx, cy, s=1.0):
        self.ribbon([(cx - 34 * s, cy - 10 * s), (cx - 36 * s, cy - 60 * s), (cx, cy - 86 * s), (cx + 36 * s, cy - 60 * s), (cx + 34 * s, cy - 10 * s)],
                    18 * s, 18 * s, PAPER)
        self.blob([(cx - 60 * s, cy - 18 * s), (cx + 60 * s, cy - 18 * s), (cx + 58 * s, cy + 70 * s), (cx - 58 * s, cy + 70 * s)], ACCENT, sharp=True)
        self.blob(self.oval(cx, cy + 18 * s, 11 * s, 11 * s, n=7), INK, line=False, wobble=0.3)
        self.brush([(cx, cy + 24 * s), (cx, cy + 46 * s)], 8 * s, lo=0.6)

    def bell(self, cx, cy, s=1.0, colour=ACCENT):
        P = lambda dx, dy: (cx + dx * s, cy + dy * s)
        self.blob([P(0, -110), P(-50, -90), P(-70, -30), P(-76, 30), P(-104, 70), P(104, 70), P(76, 30), P(70, -30), P(50, -90)], colour)
        self.blob(self.oval(*P(0, 86), 22 * s, 16 * s), INK, line=False, wobble=0.4)
        self.blob(self.oval(*P(0, -118), 14 * s, 12 * s))

    def yarn(self, cx, cy, r=70):
        self.blob(self.oval(cx, cy, r, r, n=14), ACCENT)
        for a in (-40, 0, 40):
            p = rot([(cx - r * 0.8, cy - r * 0.3), (cx, cy - r * 0.05), (cx + r * 0.8, cy - r * 0.35)], cx, cy, a)
            self.brush(p, 4.5, lo=0.3)

    def paper_plane(self, cx, cy, s=1.0, deg=-12):
        P = [(cx - 150 * s, cy), (cx + 160 * s, cy - 70 * s), (cx - 40 * s, cy + 40 * s)]
        Q = [(cx - 40 * s, cy + 40 * s), (cx + 160 * s, cy - 70 * s), (cx - 10 * s, cy + 92 * s)]
        self.blob(rot(P, cx, cy, deg), PAPER, sharp=True)
        self.blob(rot(Q, cx, cy, deg), ACCENT, sharp=True)

    def window(self, cx, cy, w=300, h=250, sill=True):
        x0, y0 = cx - w / 2, cy - h / 2
        self.blob([(x0, y0), (x0 + w, y0), (x0 + w, y0 + h), (x0, y0 + h)], WASH, sharp=True)

    def sill(self, cx, y, w=360):
        self.blob([(cx - w / 2, y), (cx + w / 2, y), (cx + w / 2 + 10, y + 26), (cx - w / 2 - 10, y + 26)], PAPER, sharp=True)

    def key(self, cx, cy, s=1.0, deg=0):
        ring = self.oval(cx - 46 * s, cy, 28 * s, 28 * s, n=10)
        shaft = [(cx - 20 * s, cy - 8 * s), (cx + 70 * s, cy - 8 * s), (cx + 70 * s, cy + 8 * s), (cx - 20 * s, cy + 8 * s)]
        bits = [(cx + 40 * s, cy + 6 * s), (cx + 40 * s, cy + 28 * s), (cx + 56 * s, cy + 28 * s), (cx + 56 * s, cy + 6 * s)]
        if deg:
            ring, shaft, bits = rot(ring, cx, cy, deg), rot(shaft, cx, cy, deg), rot(bits, cx, cy, deg)
        self.blob(bits, ACCENT, sharp=True)
        self.blob(shaft, ACCENT, sharp=True)
        self.blob(ring, ACCENT)
        hc = rot([(cx - 46 * s, cy)], cx, cy, deg)[0]
        self.blob(self.oval(hc[0], hc[1], 10 * s, 10 * s, n=7), PAPER)

    def question(self, cx, cy, s=1.0, colour=ACCENT):
        P = lambda dx, dy: (cx + dx * s, cy + dy * s)
        self.brush([P(-30, -40), P(-20, -70), P(14, -76), P(36, -54), P(26, -24), P(2, -6), P(0, 22)], 14 * s, colour, lo=0.7)
        self.blob(self.oval(*P(0, 52), 9 * s, 9 * s, n=7), colour, line=False, wobble=0.3)

    def arrows_round(self, cx, cy, r=110):
        for a0 in (200, 20):
            arc = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(a0, a0 + 130, 13)]
            self.brush(arc, 9, ACCENT, lo=0.6)
            end, prev = arc[-1], arc[-2]
            ang = math.atan2(end[1] - prev[1], end[0] - prev[0])
            head = [(end[0] + 22 * math.cos(ang), end[1] + 22 * math.sin(ang)),
                    (end[0] + 18 * math.cos(ang + 2.2), end[1] + 18 * math.sin(ang + 2.2)),
                    (end[0] + 18 * math.cos(ang - 2.2), end[1] + 18 * math.sin(ang - 2.2))]
            self.blob(head, ACCENT, line=False, wobble=0.2)

    def leaf(self, x, y, deg=0, s=1.0):
        leaf = rot([(x - 26 * s, y), (x - 8 * s, y - 14 * s), (x + 12 * s, y - 12 * s), (x + 26 * s, y), (x + 8 * s, y + 14 * s), (x - 12 * s, y + 12 * s)], x, y, deg)
        self.blob(leaf, PAPER, w=5)
        self.brush(rot([(x - 22 * s, y), (x + 20 * s, y)], x, y, deg), 4, lo=0.2)

    def drops(self, pts):
        for x, y, r in pts:
            self.blob(self.oval(x, y, r, r * 1.3, n=7), ACCENT, line=False, wobble=0.3)

    def notes(self, x, y, k=1.0):
        for dx, dy, q in ((0, 0, 1.0), (60, -60, 0.8)):
            cx, cy = x + dx * k, y + dy * k
            self.blob(self.oval(cx, cy, 14 * q, 11 * q, n=7, deg=-20), ACCENT, line=False, wobble=0.3)
            self.brush([(cx + 12 * q, cy - 2), (cx + 12 * q, cy - 52 * q), (cx + 32 * q, cy - 42 * q)], 5, lo=0.5)


# scenes -----------------------------------------------------------------------------------

def loving(style="casual"):
    s = Pen("loving", style)
    s.ground(512, 650, 230)
    s.cat_sit(512, 646, front="hug", eyes="happy", between=lambda: s.hand_heart(512, 470, 196))
    s.hand_heart(694, 260, 56, line=False, deg=14)
    s.hand_heart(332, 214, 40, line=False, deg=-12)
    s.hand_heart(734, 356, 30, line=False, deg=8)
    return s


def reading(style="casual"):
    """A cat on its tummy reading a book propped open in front of it (we see the covers), tea beside: a library still to fill."""
    s = Pen("reading", style)
    s.ground(512, 646, 330)
    s.cup(816, 640)
    s.cat_loaf(512, 600, eyes="down", paws=False)
    s.book_back(512, 642, 150, 112)
    s.paw(386, 528, 1, -12)
    s.paw(638, 528, 1, 12)
    return s

def unboxing(style="casual"):
    """A cat popping out of a box: new arrivals, nothing new yet."""
    s = Pen("unboxing", style)
    s.ground(512, 652, 290)
    s.box(512, 452, part="back")
    s.cat_head(512, 346, 108, eyes="wide", mouth="o")
    s.box(512, 452, part="front")
    s.paw(448, 452, 1.05, -8)
    s.paw(576, 452, 1.05, 8)
    s.spark(352, 240, 18); s.spark(672, 214, 14); s.spark(712, 300, 10)
    return s

def clumsy(style="casual"):
    s = Pen("clumsy", style)
    s.ground(512, 668, 300)
    s.blob([(250, 470), (690, 462), (694, 494), (252, 500)], PAPER, sharp=True)
    s.brush([(300, 500), (296, 656)], 9, lo=0.7)
    s.brush([(640, 494), (648, 656)], 9, lo=0.7)
    s.cat_sit(450, 470, 0.62, INK, eyes="wide", look=0.6, mouth="o", tail=-1, front="none")
    s.ribbon([(470, 330), (540, 330), (600, 316), (632, 300)], 24, 22, INK)
    s.paw(640, 298, 0.62, -20, INK)
    s.cup(740, 430, 1, deg=40, steam=False)
    s.drops([(812, 330, 12), (842, 364, 8), (790, 300, 7), (862, 312, 6)])
    s.brush([(660, 262), (676, 244)], 5, lo=0.3)
    s.brush([(684, 286), (706, 278)], 5, lo=0.3)
    return s


def sitting_reading(style="casual"):
    s = Pen("sitting_reading", style)
    s.ground(500, 660, 340)
    s.cushion(450, 640, 300)
    s.dog_lie(520, 600, eyes="down", head_dy=10, between=lambda: s.book_back(640, 596, 84, 74))
    s.blob([(800, 640), (796, 606), (936, 600), (944, 636)], PAPER, sharp=True)
    s.blob([(808, 604), (812, 570), (928, 566), (924, 600)], ACCENT, sharp=True)
    s.blob([(818, 568), (826, 536), (912, 538), (916, 568)], PAPER, sharp=True)
    s.brush([(816, 620), (920, 616)], 4.5, lo=0.2)
    s.bird(866, 470, 0.36, INK, crest=False)
    return s


def chilling(style="casual"):
    s = Pen("chilling", style)
    s.ground(512, 650, 330)
    s.cushion(512, 640, 270)
    s.cat_curl(512, 510)
    s.zzz(530, 330)
    return s


def meditating(style="casual"):
    s = Pen("meditating", style)
    s.ground(512, 668, 220)
    s.fill(smooth(s.jitter(s.oval(512, 380, 236, 236, n=18), 1.5), closed=True), WASH)
    s.blob([(380, 640), (392, 584), (470, 566), (580, 568), (648, 590), (650, 642), (560, 664), (450, 664)], PAPER)
    s.bird(512, 400, 1.0, eyes="closed")
    for x, y, d in ((282, 300, -30), (746, 250, 24), (770, 430, -12)):
        s.leaf(x, y, d)
    return s


def nothing_found(style="casual"):
    """Search found nothing: a cat with a magnifier, eye big behind the glass."""
    s = Pen("nothing_found", style)
    s.ground(512, 650, 240)
    hx, hy = s.cat_sit(512, 646, look=-0.3, front="down", tail=1)
    s.magnifier(hx - 30, hy + 2, 52, 40, 120)
    s.eyes(hx - 30, hy + 2, 0, "open", r=18)
    s.question(700, 230, 1.0)
    return s


def search_start(style="casual"):
    """Ready to search: the dog sniffing a trail of paw prints."""
    s = Pen("search_start", style)
    s.ground(520, 650, 340)
    for i, (x, y) in enumerate(((800, 620), (870, 590), (930, 625), (990, 596))):
        s.blob(s.oval(x, y, 14, 10, n=7), ACCENT, line=False, wobble=0.3)
        for dx in (-14, 0, 14):
            s.blob(s.oval(x + dx, y - 16, 5, 5, n=6), ACCENT, line=False, wobble=0.2)
    s.dog_lie(520, 630, eyes="closed", head_dy=40)
    s.brush([(690, 560), (712, 552)], 5, lo=0.3)
    s.brush([(694, 584), (718, 588)], 5, lo=0.3)
    return s


def waiting(style="casual"):
    """Loading: a cat hanging off a loading bar, waiting for it to fill."""
    s = Pen("waiting", style)
    s.ground(512, 760, 200)
    s.ribbon([(548, 600), (590, 650), (594, 716), (566, 744)], 26, 13)
    for t in (-1, 1):
        s.ribbon([(512 + 30 * t, 590), (512 + 34 * t, 652), (512 + 36 * t, 690)], 32, 30)
        s.paw(512 + 36 * t, 698, 0.95)
    s.blob([(466, 376), (446, 440), (440, 520), (452, 590), (488, 620), (536, 620), (572, 590), (584, 520), (578, 440), (558, 376)])
    s.stripes([((446, 470), (468, 476)), ((444, 500), (466, 506)), ((578, 470), (556, 476)), ((580, 500), (558, 506))])
    for t in (-1, 1):
        s.ribbon([(512 + 52 * t, 392), (512 + 92 * t, 320), (512 + 100 * t, 238)], 32, 30)
    s.cat_head(512, 330, 88, eyes="meh", mouth="flat")
    s.ribbon([(300, 226), (724, 226)], 44, 44, open_base=False)
    s.ribbon([(312, 226), (452, 226)], 26, 26, ACCENT, line=False)
    for t in (-1, 1):
        s.paw(512 + 100 * t, 216, 1.0)
    return s

def working(style="casual"):
    """Working on it (developing, processing): a cat on its tummy batting a ball of yarn."""
    s = Pen("working", style)
    s.ground(512, 650, 320)
    s.cat_loaf(460, 630, eyes="open", look=0.6, paws=False)
    s.brush([(640, 600), (700, 640), (780, 630), (820, 600), (870, 620)], 5, ACCENT, lo=0.6)
    s.yarn(640, 580, 62)
    s.paw(560, 600, 1, -20)
    s.paw(600, 540, 1, -40)
    return s


def offline(style="casual"):
    """No connection: a cat looking at the plug it pulled out of the wall."""
    s = Pen("offline", style)
    s.ground(512, 650, 320)
    s.blob([(744, 470), (822, 470), (822, 570), (744, 570)], PAPER, sharp=True)
    for x in (770, 796):
        s.blob([(x - 5, 500), (x + 5, 500), (x + 5, 530), (x - 5, 530)], INK, line=False, sharp=True)
    s.brush([(260, 640), (330, 632), (420, 644), (560, 640), (650, 632)], 9, INK, lo=1)
    s.blob([(650, 616), (700, 612), (702, 650), (652, 652)], ACCENT, sharp=True)
    s.brush([(700, 622), (726, 620)], 7, lo=0.9)
    s.brush([(700, 640), (726, 640)], 7, lo=0.9)
    s.cat_sit(440, 646, eyes="wide", mouth="o", look=0.7, front="down", tail=-1)
    s.brush([(724, 586), (740, 600)], 5, ACCENT, lo=0.3)
    s.brush([(712, 596), (716, 616)], 5, ACCENT, lo=0.3)
    return s

def not_found(style="casual"):
    """It isn't there: the dog by an empty hole it dug."""
    s = Pen("not_found", style)
    s.ground(512, 660, 330)
    s.blob([(580, 650), (600, 610), (700, 596), (800, 610), (820, 650)], INK, line=False, wobble=1)
    for x, y in ((610, 590), (640, 570), (790, 582), (816, 600)):
        s.blob(s.oval(x, y, 10, 8, n=6), INK, line=False, wobble=0.6)
    s.dog_sit(390, 656, eyes="open", tongue=False, look=0.6)
    s.question(700, 450, 1.1)
    return s


def locked(style="casual"):
    """Locked or hidden: a cat asleep on a locked chest."""
    s = Pen("locked", style)
    s.ground(512, 660, 280)
    s.blob([(330, 480), (694, 480), (694, 652), (330, 652)], PAPER, sharp=True)
    s.brush([(330, 530), (694, 530)], 6, lo=0.8)
    s.padlock(512, 560, 0.7)
    s.cat_curl(512, 400, 0.72)
    s.zzz(650, 270, 0.7)
    return s


def permission(style="casual"):
    """Needs permission: the dog sitting nicely with a key in its mouth."""
    s = Pen("permission", style)
    s.ground(512, 650, 240)
    hx, hy = s.dog_sit(512, 646, tongue=False)
    s.key(hx + 30, hy + 54, 0.9, deg=-6)
    return s


def quiet(style="casual"):
    """No notifications: a bird asleep on top of a bell."""
    s = Pen("quiet", style)
    s.ground(512, 650, 220)
    s.bell(512, 520, 1.2)
    s.bird(512, 268, 0.62, eyes="closed", feet=True)
    s.zzz(620, 160, 0.7)
    return s


def trash_empty(style="casual"):
    """Trash is empty: a happy cat sitting in an empty bin."""
    s = Pen("trash_empty", style)
    s.ground(512, 650, 230)
    s.cat_head(512, 334, 104, eyes="happy")
    s.bin(512, 646)
    s.bin_rim(512, 646)
    s.paw(450, 410, 1.05, -10)
    s.paw(574, 410, 1.05, 10)
    s.spark(700, 260, 14); s.spark(330, 300, 11)
    return s


def downloads(style="casual"):
    """Nothing saved offline: a bird flying in with a small parcel."""
    s = Pen("downloads", style)
    s.ground(512, 668, 200)
    s.brush([(496, 392), (560, 540)], 4.5, lo=0.6)
    s.brush([(528, 392), (640, 540)], 4.5, lo=0.6)
    s.blob([(536, 540), (664, 540), (662, 640), (538, 640)], PAPER, sharp=True)
    s.blob([(584, 542), (616, 542), (616, 638), (584, 638)], ACCENT, line=False, sharp=True)
    s.bird(512, 280, 0.62, wings="up", feet=False, beak="open")
    for x, y in ((300, 260), (276, 320)):
        s.brush([(x, y), (x - 40, y + 4)], 5, ACCENT, lo=0.5)
    return s


def sync(style="casual"):
    """Syncing: two cats chasing each other's tails round a circle, the way the arrows go."""
    s = Pen("sync", style)
    cx, cy, R = 512, 384, 170
    pol = lambda r, a: (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
    arc_ = lambda a0, a1, r=R, n=8: [pol(r, a0 + (a1 - a0) * i / n) for i in range(n + 1)]
    rears = (210, 30)
    for a0 in rears:                       # tails trail back along the circle toward the other cat's nose
        s.ribbon(arc_(a0 + 4, a0 - 44), 24, 10)
    for a0 in rears:                       # legs mid-stride, reaching toward the middle
        for a, lean in ((a0 + 10, -10), (a0 + 74, 10)):
            p, q = pol(R - 14, a), pol(R - 66, a + lean)
            s.ribbon([p, q], 24, 22)
            s.paw(*q, 0.78, a + 90)
    for a0 in rears:                       # slim bodies, running clockwise
        s.ribbon(arc_(a0, a0 + 86), 74, 66, open_base=False)
        s.stripes([(pol(R + 24, a), pol(R + 8, a + 3)) for a in (a0 + 22, a0 + 40, a0 + 58)])
    for a0 in rears:
        # heads stay upright, faces turned the way each cat runs
        ha = a0 + 104
        hx, hy = pol(R + 2, ha)
        look = 0.65 if math.cos(math.radians(ha + 90)) > 0 else -0.65
        s.cat_head(hx, hy, 72, eyes="open", look=look, mouth="w", stripes=False)
    return s

def welcome(style="casual"):
    """Hello: a cat on the sill, waving through an open window."""
    s = Pen("welcome", style)
    s.window(512, 360, 420, 380)
    s.sill(512, 550, 490)
    s.cat_sit(512, 550, 0.8, front="down", eyes="happy", wave=True, tail=-1)
    s.spark(240, 220, 14); s.spark(790, 200, 12)
    return s

def sent(style="casual"):
    """Sent: a bird riding a paper plane."""
    s = Pen("sent", style)
    for x in (180, 230, 280):
        s.brush([(x, 470 + (x - 180) * 0.2), (x + 26, 466 + (x - 180) * 0.2)], 6, ACCENT, lo=0.6)
    s.bird(540, 300, 0.5, feet=False)
    s.paper_plane(540, 430, 1.4)
    return s


def storage_full(style="casual"):
    """Storage is full: a cat squeezed into a box far too small."""
    s = Pen("storage_full", style)
    s.ground(512, 660, 300)
    s.box(512, 530, 250, 130, part="back")
    s.cat_loaf(512, 576, 0.9, eyes="closed")
    s.box(512, 530, 250, 130, part="front")
    for a, b in (((300, 430), (268, 410)), ((294, 470), (258, 470)), ((724, 430), (756, 410)), ((730, 470), (766, 470))):
        s.brush([a, b], 6, lo=0.3)
    return s

def inbox_empty(style="casual"):
    """Inbox zero: a cat asleep in an empty letter tray."""
    s = Pen("inbox_empty", style)
    s.ground(512, 660, 300)
    s.blob([(300, 470), (724, 470), (700, 640), (324, 640)], WASH, line=False, sharp=True)
    s.cat_curl(512, 520, 0.8)
    s.blob([(270, 540), (754, 540), (720, 650), (304, 650)], PAPER, sharp=True)
    s.brush([(420, 590), (604, 590)], 6, lo=0.5)
    s.envelope(780, 330, 110, 74, deg=14)
    s.zzz(640, 330, 0.7)
    return s


def update(style="casual"):
    """Something new arrived: a chick hatching from its egg."""
    s = Pen("update", style)
    s.ground(512, 660, 200)
    s.bird(512, 420, 0.7, feet=False, eyes="happy", crest=True)
    s.blob([(390, 470), (412, 440), (440, 470), (470, 436), (500, 470), (530, 436), (560, 470), (590, 436), (614, 470),
            (636, 440), (640, 520), (610, 600), (512, 640), (414, 600), (384, 520)], PAPER)
    s.spark(360, 300, 14); s.spark(680, 280, 16); s.spark(700, 380, 10)
    return s


SCENES = [loving, reading, unboxing, clumsy, sitting_reading, chilling, meditating, nothing_found, search_start,
          waiting, working, offline, not_found, locked, permission, quiet, trash_empty, downloads, sync, welcome,
          sent, storage_full, inbox_empty, update]


# the states only Mail has ------------------------------------------------------------------

def drafts_empty(style="casual"):
    """No drafts: a cat behind a blank sheet, pencil in paw."""
    s = Pen("drafts_empty", style)
    s.ground(512, 646, 320)
    s.cat_loaf(512, 600, eyes="open", look=-0.3, paws=False)
    s.blob([(376, 500), (648, 492), (656, 640), (368, 646)], PAPER, sharp=True)
    s.ribbon([(640, 520), (722, 420)], 26, 26, ACCENT, open_base=False)
    s.blob([(626, 530), (640, 520), (652, 532), (632, 552)], INK, line=False, sharp=True)
    s.paw(400, 500, 1, -10)
    s.paw(640, 504, 1, 20)
    return s


def spam_empty(style="casual"):
    """No spam: the dog on guard, a shield on its collar."""
    s = Pen("spam_empty", style)
    s.ground(512, 650, 240)
    hx, hy = s.dog_sit(512, 646, tongue=False, wag=True)
    s.blob([(hx - 30, hy + 80), (hx + 30, hy + 80), (hx + 30, hy + 112), (hx, hy + 136), (hx - 30, hy + 112)], ACCENT)
    s.brush([(hx - 12, hy + 104), (hx - 2, hy + 114), (hx + 14, hy + 96)], 5, PAPER, lo=0.5)
    return s


def archive_empty(style="casual"):
    """The archive is empty: a cat asleep on the lid of an archive box."""
    s = Pen("archive_empty", style)
    s.ground(512, 660, 280)
    s.blob([(344, 500), (680, 500), (672, 652), (352, 652)], PAPER, sharp=True)
    s.blob([(330, 470), (694, 470), (694, 510), (330, 510)], ACCENT, sharp=True)
    s.blob([(472, 560), (552, 560), (552, 584), (472, 584)], WASH, sharp=True)
    s.cat_curl(512, 400, 0.72)
    s.zzz(650, 270, 0.7)
    return s


def attachments_empty(style="casual"):
    """No attachments: a cat on its tummy batting at a big paperclip."""
    s = Pen("attachments_empty", style)
    s.ground(512, 646, 330)
    s.cat_loaf(440, 620, eyes="open", look=0.6, paws=False)
    clip = [(650, 640), (650, 450), (690, 410), (730, 450), (730, 600), (704, 626), (678, 600), (678, 480)]
    s.brush(clip, 18, INK, lo=1)
    s.brush(clip, 8, ACCENT, lo=1)
    s.paw(560, 610, 1, -10)
    s.paw(620, 560, 1, -50)
    return s


def snoozed_empty(style="casual"):
    """Nothing snoozed: a cat asleep beside an alarm clock that never rang."""
    s = Pen("snoozed_empty", style)
    s.ground(512, 650, 330)
    s.cushion(440, 640, 230)
    s.cat_curl(440, 520, 0.84)
    cx, cy = 760, 520
    s.blob(s.oval(cx - 52, cy - 84, 24, 20), ACCENT)
    s.blob(s.oval(cx + 52, cy - 84, 24, 20), ACCENT)
    s.brush([(cx - 50, cy + 80), (cx - 64, cy + 108)], 9, lo=0.8)
    s.brush([(cx + 50, cy + 80), (cx + 64, cy + 108)], 9, lo=0.8)
    s.blob(s.oval(cx, cy, 94, 94, n=16))
    s.brush([(cx, cy), (cx, cy - 56)], 8, lo=0.7)
    s.brush([(cx, cy), (cx + 40, cy + 22)], 8, lo=0.7)
    s.blob(s.oval(cx, cy, 8, 8, n=7), ACCENT, line=False, wobble=0.2)
    s.zzz(540, 330, 0.7)
    return s


def scheduled_empty(style="casual"):
    """Nothing scheduled: a bird perched on top of a blank calendar page."""
    s = Pen("scheduled_empty", style)
    s.ground(512, 660, 260)
    s.blob([(340, 380), (684, 380), (684, 652), (340, 652)], PAPER, sharp=True)
    s.blob([(340, 380), (684, 380), (684, 440), (340, 440)], ACCENT, sharp=True)
    for x in (420, 604):
        s.ribbon([(x, 352), (x, 400)], 18, 18, PAPER, open_base=False)
    for r in range(3):
        for c in range(5):
            s.blob(s.oval(392 + c * 60, 494 + r * 54, 12, 10, n=7), WASH, line=False, wobble=0.2)
    s.bird(512, 236, 0.62, eyes="open")
    return s


def contacts_empty(style="casual"):
    """No contacts yet: the dog holding up a blank name card."""
    s = Pen("contacts_empty", style)
    s.ground(512, 650, 240)
    hx, hy = s.dog_sit(512, 646, tongue=False)
    s.blob([(hx - 78, hy + 46), (hx + 92, hy + 40), (hx + 96, hy + 140), (hx - 74, hy + 146)], PAPER, sharp=True)
    s.blob(s.oval(hx - 30, hy + 94, 24, 24, n=10), WASH)
    s.brush([(hx + 10, hy + 80), (hx + 70, hy + 78)], 6, lo=0.4)
    s.brush([(hx + 10, hy + 104), (hx + 52, hy + 103)], 6, lo=0.4)
    s.paw(hx - 76, hy + 120, 1, 70)
    s.paw(hx + 94, hy + 112, 1, -70)
    return s



# the states a podcast app needs ---------------------------------------------------------------

def podcasts_empty(style="casual"):
    """No shows yet: a cat on its tummy in big headphones, listening to nothing in particular."""
    s = Pen("podcasts_empty", style)
    s.ground(512, 646, 330)
    hx, hy = s.cat_loaf(512, 620, eyes="closed")
    s.headphones(hx, hy + 6, 100)
    s.notes(690, 320, 1.1)
    return s


def queue_empty(style="casual"):
    """Up next is empty: a bird perched on a microphone, waiting for something to say."""
    s = Pen("queue_empty", style)
    s.ground(512, 660, 200)
    s.mic(512, 656, 1.0)
    s.bird(512, 228, 0.5, eyes="open", beak="open")
    s.notes(640, 220, 0.8)
    return s


def nothing_playing(style="casual"):
    """Nothing playing: the dog asleep on a cushion, headphones round its neck."""
    s = Pen("nothing_playing", style)
    s.ground(500, 660, 340)
    s.cushion(450, 640, 300)
    hx, hy = s.dog_lie(520, 600, eyes="closed", head_dy=10)
    s.ribbon([(hx - 84, hy + 40), (hx - 40, hy + 92), (hx + 40, hy + 92), (hx + 84, hy + 40)], 14, 14, INK, open_base=False)
    for t in (-1, 1):
        s.blob(s.oval(hx + t * 88, hy + 46, 22, 30), ACCENT)
    s.zzz(hx + 120, hy - 120, 0.7)
    return s


SCENES = SCENES + [drafts_empty, spam_empty, archive_empty, attachments_empty, snoozed_empty, scheduled_empty, contacts_empty,
                   podcasts_empty, queue_empty, nothing_playing]


# the states a password manager needs ---------------------------------------------------------

def _safe(s, cx, by, w=250, h=230, open_=False, badge=None):
    """A wall safe on short feet. open_ swings the door out to the right; badge puts an accent mark on the door."""
    x0, x1, top = cx - w / 2, cx + w / 2, by - h
    for t in (-1, 1):
        s.blob([(cx + t * w * 0.34 - 16, by - 4), (cx + t * w * 0.34 + 16, by - 4), (cx + t * w * 0.34 + 14, by + 16),
                (cx + t * w * 0.34 - 14, by + 16)], INK, line=False, sharp=True)
    s.blob([(x0, top), (x1, top), (x1, by), (x0, by)], PAPER, sharp=True)
    if open_:
        s.blob([(x0 + 22, top + 22), (x1 - 22, top + 22), (x1 - 22, by - 22), (x0 + 22, by - 22)], WASH, sharp=True)
        s.brush([(x0 + 34, by - 90), (x1 - 34, by - 90)], 6, lo=0.8)
        s.blob([(x1, top + 14), (x1 + w * 0.42, top - 18), (x1 + w * 0.42, by + 10), (x1, by - 14)], PAPER, sharp=True)
        s.blob(s.oval(x1 + w * 0.22, top + h * 0.48, 24, 30, n=10), ACCENT)
        return
    s.brush([(x0 + 22, top + 22), (x1 - 22, top + 22), (x1 - 22, by - 22), (x0 + 22, by - 22), (x0 + 22, top + 18)], 5, lo=0.8)
    dx, dy = cx - w * 0.08, top + h * 0.5
    s.blob(s.oval(dx, dy, 44, 44, n=14), ACCENT)
    s.blob(s.oval(dx, dy, 13, 13, n=8), PAPER, w=5)
    for a in range(0, 360, 45):
        p = [(dx + 44 * math.cos(math.radians(a)), dy + 44 * math.sin(math.radians(a))),
             (dx + 56 * math.cos(math.radians(a)), dy + 56 * math.sin(math.radians(a)))]
        s.brush(p, 5, lo=0.6)
    s.brush([(x1 - 50, dy - 40), (x1 - 50, dy + 40)], 12, lo=0.9)
    if badge == "x":
        bx, by_ = x1 - 6, top + 6
        s.blob(s.oval(bx, by_, 34, 34, n=12), ACCENT)
        s.brush([(bx - 12, by_ - 12), (bx + 12, by_ + 12)], 7, PAPER, lo=0.8)
        s.brush([(bx + 12, by_ - 12), (bx - 12, by_ + 12)], 7, PAPER, lo=0.8)


def database_none(style="casual"):
    """No database open: a cat looking into an open, empty safe."""
    s = Pen("database_none", style)
    s.ground(512, 662, 330)
    _safe(s, 600, 646, open_=True)
    s.cat_sit(330, 650, 0.82, eyes="open", look=0.7, front="down", tail=-1)
    s.question(470, 250, 0.8)
    return s


def unlock(style="casual"):
    """Unlock the vault: the dog has brought the key to the safe."""
    s = Pen("unlock", style)
    s.ground(512, 662, 340)
    _safe(s, 680, 646, 230, 220)
    hx, hy = s.dog_sit(380, 650, 0.84, tongue=False, look=0.6)
    s.key(hx + 40, hy + 46, 0.8, deg=-6)
    return s


def wrong_password(style="casual"):
    """Wrong master password: the safe stays shut, and the cat is not impressed."""
    s = Pen("wrong_password", style)
    s.ground(512, 662, 340)
    _safe(s, 660, 646, 240, 230, badge="x")
    s.cat_sit(370, 650, 0.84, eyes="meh", mouth="flat", look=0.6, front="down", tail=-1)
    return s


def all_secure(style="casual"):
    """No weak or reused passwords: a cat sitting proudly beside a big shield."""
    s = Pen("all_secure", style)
    s.ground(512, 660, 320)
    s.cat_sit(380, 650, 0.86, eyes="happy", front="down", tail=-1)
    cx, cy = 660, 470
    s.blob([(cx, cy - 150), (cx + 120, cy - 110), (cx + 116, cy + 10), (cx + 80, cy + 90), (cx, cy + 150), (cx - 80, cy + 90),
            (cx - 116, cy + 10), (cx - 120, cy - 110)], ACCENT)
    s.brush([(cx - 50, cy + 4), (cx - 12, cy + 44), (cx + 56, cy - 40)], 16, PAPER, lo=0.8)
    s.spark(800, 300, 14); s.spark(530, 230, 10)
    return s


def expiring_none(style="casual"):
    """Nothing expiring soon: a bird asleep on top of an hourglass."""
    s = Pen("expiring_none", style)
    s.ground(512, 668, 200)
    s.hourglass(512, 500, 300)
    s.bird(512, 236, 0.56, eyes="closed")
    s.zzz(620, 140, 0.6)
    return s


def fingerprint(style="casual"):
    """Unlock with a fingerprint: a cat on its tummy pressing a paw to the pad."""
    s = Pen("fingerprint", style)
    s.ground(512, 650, 330)
    s.cat_loaf(430, 626, eyes="open", look=0.6, paws=False)
    cx, cy = 690, 520
    s.blob(s.oval(cx, cy, 104, 104, n=18))
    for r in (24, 44, 64, 84):
        a0, a1 = 200, 520 - (r // 20) * 14
        arc = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(a0, a1, 12)]
        s.brush(arc, 7, ACCENT, lo=0.5)
    s.ribbon([(540, 600), (600, 572), (640, 548)], 34, 32)
    s.paw(648, 544, 1.1, -30)
    s.paw(548, 612, 1, -10)
    return s


SCENES = SCENES + [database_none, unlock, wrong_password, all_secure, expiring_none, fingerprint]
