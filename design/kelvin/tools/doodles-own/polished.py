"""The polished set: objects only, an even line and one accent each, for apps that need to look professional
(72F Mail first). Same scene names as the playful set, plus the states only Mail has."""
import math
from core import Scene, INK, ACCENT, PAPER, WASH, ellipse, rrect, rot, smooth, f

W = 6       # the one line weight
CX, CY = 512, 384


def pl(pts, closed=False):
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return d + (" Z" if closed else "")


def arc(cx, cy, r, a0, a1, n=None):
    n = n or max(6, int(abs(a1 - a0) / 8))
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
            for i in range(n + 1)]


class Clean(Scene):
    fit_args = {"margin": 70, "most": 1.25}

    def __init__(self, name, backdrop=True):
        super().__init__(name)
        if backdrop:
            self.fill(ellipse(CX, CY + 10, 236), WASH)

    # primitives ---------------------------------------------------------------------------
    def out(self, d, fill=PAPER, w=W):
        if fill:
            self.fill(d, fill)
        self.stroke(d, INK, w)

    def ln(self, pts, w=W, colour=INK):
        self.stroke(pl(pts), colour, w)

    def card(self, cx, cy, w, h, r=20, deg=0, fill=PAPER):
        self.out(rrect(cx, cy, w, h, r, deg), fill)

    def bars(self, x, y, widths, gap=26, deg=0, about=None, colour=WASH, w=12):
        """Skeleton text: soft rounded bars."""
        for i, bw in enumerate(widths):
            p = [(x, y + i * gap), (x + bw, y + i * gap)]
            if deg:
                p = rot(p, *(about or (x, y)), deg)
            self.stroke(pl(p), colour, w)

    def badge(self, cx, cy, r=46, glyph="check", fill=ACCENT):
        self.out(ellipse(cx, cy, r), fill)
        k = r / 46
        g = {"check": [(cx - 18 * k, cy + 1 * k), (cx - 5 * k, cy + 14 * k), (cx + 19 * k, cy - 12 * k)],
             "plus": None, "bang": None, "x": None}
        if glyph == "check":
            self.stroke(pl(g["check"]), PAPER, 8 * k)
        elif glyph == "bang":
            self.stroke(pl([(cx, cy - 20 * k), (cx, cy + 4 * k)]), PAPER, 9 * k)
            self.fill(ellipse(cx, cy + 20 * k, 5.5 * k), PAPER)
        elif glyph == "x":
            self.stroke(pl([(cx - 14 * k, cy - 14 * k), (cx + 14 * k, cy + 14 * k)]), PAPER, 8 * k)
            self.stroke(pl([(cx + 14 * k, cy - 14 * k), (cx - 14 * k, cy + 14 * k)]), PAPER, 8 * k)
        elif glyph == "plus":
            self.stroke(pl([(cx - 16 * k, cy), (cx + 16 * k, cy)]), PAPER, 8 * k)
            self.stroke(pl([(cx, cy - 16 * k), (cx, cy + 16 * k)]), PAPER, 8 * k)
        elif glyph == "up":
            self.stroke(pl([(cx, cy + 18 * k), (cx, cy - 16 * k)]), PAPER, 8 * k)
            self.stroke(pl([(cx - 15 * k, cy - 2 * k), (cx, cy - 18 * k), (cx + 15 * k, cy - 2 * k)]), PAPER, 8 * k)
        elif glyph == "heart":
            self.heart(cx, cy + 4 * k, 46 * k, PAPER, outline=False)
        elif glyph == "star":
            self.star(cx, cy, 24 * k, PAPER, line=False)
        elif glyph == "q":
            self.stroke(smooth([(cx - 12 * k, cy - 10 * k), (cx - 6 * k, cy - 22 * k), (cx + 10 * k, cy - 22 * k),
                                (cx + 14 * k, cy - 8 * k), (cx, cy + 2 * k), (cx, cy + 8 * k)]), PAPER, 8 * k)
            self.fill(ellipse(cx, cy + 22 * k, 5.5 * k), PAPER)

    def star(self, cx, cy, r, fill=ACCENT, line=True):
        pts = []
        for i in range(10):
            a = math.radians(-90 + i * 36)
            rr = r if i % 2 == 0 else r * 0.46
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        d = pl(pts, True)
        if line:
            self.out(d, fill, W)
        else:
            self.fill(d, fill)

    def decor(self, *items):
        """Small accents round the object: ("dot", x, y), ("ring", x, y), ("plus", x, y), ("spark", x, y)."""
        for kind, x, y in items:
            if kind == "dot":
                self.fill(ellipse(x, y, 9), ACCENT)
            elif kind == "ring":
                self.stroke(ellipse(x, y, 12), ACCENT, 5)
            elif kind == "plus":
                self.stroke(pl([(x - 13, y), (x + 13, y)]), ACCENT, 6)
                self.stroke(pl([(x, y - 13), (x, y + 13)]), ACCENT, 6)
            elif kind == "spark":
                self.fill("M{0},{1} C{0},{3} {4},{5} {6},{5} C{4},{5} {0},{7} {0},{8} C{0},{7} {9},{5} {10},{5} C{9},{5} {0},{3} {0},{1} Z"
                          .format(f(x), f(y - 22), 0, f(y - 6), f(x + 6), f(y), f(x + 22), f(y + 6), f(y + 22), f(x - 6), f(x - 22)), ACCENT)

    # objects ------------------------------------------------------------------------------
    def envelope(self, cx, cy, w, h, deg=0, open_=False, fill=PAPER):
        box = rot([(cx - w / 2, cy - h / 2), (cx + w / 2, cy - h / 2), (cx + w / 2, cy + h / 2), (cx - w / 2, cy + h / 2)], cx, cy, deg)
        if open_:
            flap = rot([(cx - w / 2, cy - h / 2), (cx, cy - h * 1.05), (cx + w / 2, cy - h / 2)], cx, cy, deg)
            self.out(pl(flap, True), fill)
            return box
        self.out(rrect(cx, cy, w, h, 12, deg), fill)
        self.ln(rot([(cx - w / 2 + 14, cy - h / 2 + 14), (cx, cy + h * 0.08), (cx + w / 2 - 14, cy - h / 2 + 14)], cx, cy, deg))
        return box

    def envelope_front(self, cx, cy, w, h, fill=PAPER):
        self.out(pl([(cx - w / 2, cy - h / 2), (cx, cy + h * 0.06), (cx + w / 2, cy - h / 2), (cx + w / 2, cy + h / 2), (cx - w / 2, cy + h / 2)], True), fill)
        self.ln([(cx - w / 2, cy + h / 2), (cx - w * 0.1, cy + h * 0.02)])
        self.ln([(cx + w / 2, cy + h / 2), (cx + w * 0.1, cy + h * 0.02)])

    def tray(self, cx, cy, w=360, h=120, fill=PAPER):
        """An inbox tray seen a little from above."""
        self.out(pl([(cx - w / 2 + 30, cy - h / 2), (cx + w / 2 - 30, cy - h / 2), (cx + w / 2, cy + 6), (cx - w / 2, cy + 6)], True), WASH)
        self.out(pl([(cx - w / 2, cy + 6), (cx - w * 0.18, cy + 6), (cx - w * 0.12, cy + 34), (cx + w * 0.12, cy + 34), (cx + w * 0.18, cy + 6),
                     (cx + w / 2, cy + 6), (cx + w / 2, cy + h / 2 + 30), (cx - w / 2, cy + h / 2 + 30)], True), fill)

    def magnifier(self, cx, cy, r=70, deg=45, handle=110, lens=WASH):
        a = math.radians(deg)
        p0 = (cx + math.cos(a) * (r + 4), cy + math.sin(a) * (r + 4))
        p1 = (cx + math.cos(a) * (r + handle), cy + math.sin(a) * (r + handle))
        self.stroke(pl([p0, p1]), INK, 30)
        self.stroke(pl([p0, p1]), ACCENT, 18)
        self.out(ellipse(cx, cy, r), lens, 12)
        self.stroke(smooth(arc(cx, cy, r * 0.66, 200, 250)), PAPER if lens != PAPER else WASH, 8)

    def cylinder(self, cx, cy, w, h, fill=PAPER):
        rx, ry = w / 2, w * 0.14
        self.out(f"M{f(cx - rx)},{f(cy)} L{f(cx - rx)},{f(cy + h)} A{f(rx)},{f(ry)} 0 0,0 {f(cx + rx)},{f(cy + h)} "
                 f"L{f(cx + rx)},{f(cy)} A{f(rx)},{f(ry)} 0 0,0 {f(cx - rx)},{f(cy)} Z", fill)
        self.out(ellipse(cx, cy, rx, ry), fill)

    def cloud(self, cx, cy, w=300, fill=PAPER):
        k = w / 300
        P = lambda x, y: (cx + x * k, cy + y * k)
        d = (f"M{f(P(-110, 60)[0])},{f(P(-110, 60)[1])} "
             f"A{f(56 * k)},{f(56 * k)} 0 0,1 {f(P(-96, -46)[0])},{f(P(-96, -46)[1])} "
             f"A{f(84 * k)},{f(84 * k)} 0 0,1 {f(P(54, -70)[0])},{f(P(54, -70)[1])} "
             f"A{f(64 * k)},{f(64 * k)} 0 0,1 {f(P(118, 60)[0])},{f(P(118, 60)[1])} Z")
        self.out(d, fill)


# scenes -----------------------------------------------------------------------------------

def loving():
    """Favourites: a saved card with a heart."""
    s = Clean("loving")
    s.card(470, 400, 250, 300, 22, -6)
    s.out(rrect(470, 352, 196, 150, 12, -6), WASH, W)
    s.ln(rot([(388, 410), (440, 352), (478, 392), (510, 364), (552, 412)], 470, 352, -6))
    s.bars(380, 460, [150, 100], deg=-6, about=(470, 400))
    s.badge(600, 262, 50, "heart")
    s.decor(("dot", 330, 210), ("plus", 720, 420), ("ring", 690, 560))
    return s


def reading():
    """A reading list with nothing in it: an open book with a ribbon."""
    s = Clean("reading")
    s.out("M512,300 C470,276 360,276 310,300 L310,500 C360,478 470,480 512,508 Z")
    s.out("M512,300 C554,276 664,276 714,300 L714,500 C664,478 554,480 512,508 Z")
    s.ln([(512, 300), (512, 508)])
    s.bars(346, 340, [120, 130, 90, 120])
    s.bars(552, 340, [120, 100, 130, 80])
    s.out(pl([(640, 270), (676, 270), (676, 360), (658, 344), (640, 360)], True), ACCENT)
    s.decor(("spark", 270, 230), ("dot", 760, 230), ("plus", 760, 520))
    return s


def unboxing():
    """An empty collection: an open box, flaps folded out on their hinges."""
    s = Clean("unboxing")
    x0, x1, top, h = 352, 672, 340, 210
    d, e = 38, 46
    bl, br = (x0 + d, top - e), (x1 - d, top - e)
    s.out(pl([bl, br, (x1 - d - 20, top - e - 76), (x0 + d + 20, top - e - 76)], True))
    s.out(pl([(x0, top), bl, (bl[0] - 86, bl[1] - 28), (x0 - 86, top - 28)], True))
    s.out(pl([(x1, top), br, (br[0] + 86, br[1] - 28), (x1 + 86, top - 28)], True))
    s.out(pl([(x0, top), (x1, top), br, bl], True), WASH)
    s.out(pl([(x0, top), (x1, top), (x1, top + h), (x0, top + h)], True))
    s.fill(pl([(490, top + 3), (534, top + 3), (534, top + 90), (490, top + 90)], True), ACCENT)
    s.bars(388, top + h - 46, [90])
    s.decor(("spark", 512, 160), ("dot", 380, 200), ("plus", 650, 190))
    return s


def clumsy():
    """Something went wrong: a card with a warning."""
    s = Clean("clumsy")
    s.card(500, 404, 300, 230, 22, 0)
    s.bars(390, 360, [180, 130, 160])
    s.out(pl([(640, 214), (712, 344), (568, 344)], True), ACCENT)
    s.stroke(pl([(640, 260), (640, 300)]), PAPER, 9)
    s.fill(ellipse(640, 324, 6), PAPER)
    s.decor(("dot", 330, 230), ("ring", 330, 580), ("plus", 760, 480))
    return s


def sitting_reading():
    """Still getting ready (memories developing, a shelf filling): a fanned stack of cards, one half done."""
    s = Clean("sitting_reading")
    s.card(470, 400, 230, 280, 20, -12)
    s.card(530, 396, 230, 280, 20, 8)
    s.card(500, 390, 240, 290, 20, 0)
    s.out(rrect(500, 336, 190, 140, 12), WASH)
    s.stroke(pl([(410, 468), (590, 468)]), WASH, 14)
    s.stroke(pl([(410, 468), (500, 468)]), ACCENT, 14)
    s.bars(410, 500, [120])
    s.decor(("spark", 700, 220), ("dot", 300, 260), ("plus", 330, 560))
    return s


def chilling():
    """Nothing to do: a cup of coffee."""
    s = Clean("chilling")
    s.out(ellipse(512, 540, 180, 30))
    s.out("M392,330 L412,500 C416,530 440,544 470,544 L554,544 C584,544 608,530 612,500 L632,330 Z", ACCENT)
    s.stroke("M624,380 C690,372 700,460 610,468", INK, 14)
    s.stroke("M624,380 C690,372 700,460 610,468", PAPER, 2)
    s.out(ellipse(512, 330, 120, 22), PAPER)
    for x in (470, 512, 554):
        s.stroke(smooth([(x, 290), (x - 12, 262), (x + 8, 236), (x - 4, 206)]), INK, 5)
    s.decor(("dot", 330, 230), ("plus", 720, 250))
    return s


def meditating():
    """All caught up: a big check over two cards."""
    s = Clean("meditating")
    s.card(470, 420, 260, 170, 20, -8)
    s.card(540, 400, 260, 170, 20, 4)
    s.bars(440, 380, [150, 110], deg=4, about=(540, 400))
    s.badge(512, 290, 74, "check")
    s.decor(("spark", 330, 240), ("dot", 700, 220), ("ring", 330, 540), ("plus", 730, 560))
    return s


def nothing_found():
    s = Clean("nothing_found")
    s.card(460, 400, 260, 300, 22, -4)
    s.bars(370, 300, [170, 120, 150, 100])
    s.magnifier(590, 400, 82, 45, 120, PAPER)
    s.badge(590, 400, 40, "q", WASH)
    s.decor(("dot", 320, 220), ("plus", 740, 260))
    return s


def search_start():
    s = Clean("search_start")
    s.magnifier(480, 360, 100, 45, 150, PAPER)
    for x, y, w in ((380, 340, 120), (420, 380, 90)):
        s.stroke(pl([(x, y), (x + w, y)]), WASH, 14)
    s.decor(("spark", 650, 230), ("spark", 712, 300), ("dot", 320, 230), ("ring", 700, 520))
    return s


def waiting():
    s = Clean("waiting")
    s.out(rrect(512, 226, 220, 30, 12), INK)
    s.out(rrect(512, 542, 220, 30, 12), INK)
    s.out("M430,240 L594,240 C594,320 540,360 528,384 C540,408 594,448 594,528 L430,528 C430,448 484,408 496,384 "
          "C484,360 430,320 430,240 Z")
    s.fill("M456,300 L568,300 C556,340 524,358 512,376 C500,358 468,340 456,300 Z", ACCENT)
    s.fill("M454,520 C470,470 500,470 512,454 C524,470 554,470 570,520 Z", ACCENT)
    s.stroke(pl([(512, 384), (512, 452)]), ACCENT, 4)
    s.decor(("dot", 340, 260), ("plus", 690, 290), ("ring", 680, 520))
    return s


def working():
    """Working on it: a card with a progress ring."""
    s = Clean("working")
    s.card(512, 390, 320, 260, 24)
    s.stroke(ellipse(512, 360, 64), WASH, 16)
    s.stroke(smooth(arc(512, 360, 64, -90, 150)), ACCENT, 16)
    s.bars(422, 470, [180])
    s.bars(462, 496, [100])
    s.decor(("spark", 720, 240), ("dot", 310, 250), ("plus", 320, 560))
    return s


def offline():
    s = Clean("offline")
    s.cloud(512, 390, 360)
    s.stroke(pl([(380, 250), (650, 520)]), PAPER, 26)
    s.stroke(pl([(380, 250), (650, 520)]), ACCENT, 12)
    s.decor(("dot", 330, 520), ("plus", 720, 250))
    return s


def not_found():
    s = Clean("not_found")
    s.out(pl([(400, 220), (570, 220), (630, 280), (630, 560), (400, 560)], True))
    s.out(pl([(570, 220), (570, 280), (630, 280)], True), WASH)
    s.bars(440, 290, [90, 140])
    s.badge(515, 440, 56, "q")
    s.decor(("spark", 320, 260), ("dot", 700, 240), ("ring", 700, 540))
    return s


def locked():
    s = Clean("locked")
    s.stroke("M440,370 L440,300 C440,256 476,226 512,226 C548,226 584,256 584,300 L584,370", INK, 34)
    s.stroke("M440,370 L440,300 C440,256 476,226 512,226 C548,226 584,256 584,300 L584,370", PAPER, 20)
    s.out(rrect(512, 450, 260, 200, 28), ACCENT)
    s.fill(ellipse(512, 432, 20), PAPER)
    s.stroke(pl([(512, 440), (512, 486)]), PAPER, 14)
    s.decor(("spark", 680, 240), ("dot", 330, 260), ("plus", 340, 560))
    return s


def permission():
    """Needs your permission: a card with its switch turned on."""
    s = Clean("permission")
    s.card(512, 390, 340, 230, 26)
    s.bars(380, 340, [150, 100])
    s.out(rrect(610, 340, 104, 56, 28), ACCENT)
    s.out(ellipse(632, 340, 20), PAPER, 4)
    s.bars(380, 440, [190])
    s.decor(("spark", 730, 230), ("dot", 300, 250), ("ring", 320, 560))
    return s


def quiet():
    s = Clean("quiet")
    s.out("M512,230 C450,230 420,280 420,340 L420,420 L384,470 L640,470 L604,420 L604,340 C604,280 574,230 512,230 Z", ACCENT)
    s.out(ellipse(512, 214, 16))
    s.out("M480,488 C480,520 544,520 544,488 Z", PAPER)
    s.ln([(470, 470), (554, 470)])
    for x, y, k in ((650, 260, 1), (700, 210, 0.75)):
        s.stroke(pl([(x - 16 * k, y - 16 * k), (x + 16 * k, y - 16 * k), (x - 16 * k, y + 16 * k), (x + 16 * k, y + 16 * k)]), INK, 6 * k)
    s.decor(("dot", 330, 260), ("plus", 340, 540))
    return s


def trash_empty():
    s = Clean("trash_empty")
    s.out("M410,300 L614,300 L594,560 C592,576 580,586 564,586 L460,586 C444,586 432,576 430,560 Z")
    for x in (474, 512, 550):
        s.stroke(pl([(x, 350), (x, 536)]), WASH, 14)
    s.out(rrect(512, 286, 260, 30, 12, -8), ACCENT)
    s.out(rrect(512, 262, 64, 24, 10, -8))
    s.decor(("spark", 700, 300), ("dot", 330, 260), ("plus", 690, 540))
    return s


def downloads():
    s = Clean("downloads")
    s.tray(512, 470, 360, 110)
    s.stroke(pl([(512, 210), (512, 400)]), ACCENT, 22)
    s.stroke(pl([(450, 340), (512, 404), (574, 340)]), ACCENT, 22)
    s.decor(("dot", 340, 260), ("plus", 690, 250))
    return s


def sync():
    s = Clean("sync")
    s.cloud(512, 400, 300)
    for a0 in (200, 20):
        pts = arc(512, 384, 210, a0, a0 + 120)
        s.stroke(smooth(pts), ACCENT, 10)
        end, prev = pts[-1], pts[-2]
        ang = math.atan2(end[1] - prev[1], end[0] - prev[0])
        s.fill(pl([(end[0] + 24 * math.cos(ang), end[1] + 24 * math.sin(ang)),
                   (end[0] + 22 * math.cos(ang + 2.3), end[1] + 22 * math.sin(ang + 2.3)),
                   (end[0] + 22 * math.cos(ang - 2.3), end[1] + 22 * math.sin(ang - 2.3))], True), ACCENT)
    return s


def welcome():
    """Hello: a card rising out of an open envelope."""
    s = Clean("welcome")
    s.envelope(512, 470, 340, 200, open_=True)
    s.card(512, 360, 270, 230, 18)
    s.star(512, 320, 40)
    s.bars(442, 390, [140])
    s.envelope_front(512, 470, 340, 200)
    s.decor(("spark", 330, 230), ("spark", 700, 210), ("dot", 740, 330))
    return s


def sent():
    s = Clean("sent")
    for i in range(4):
        x = 250 + i * 46
        s.stroke(pl([(x, 470 - i * 14), (x + 22, 464 - i * 14)]), ACCENT, 7)
    s.out(pl([(420, 400), (740, 250), (560, 470)], True))
    s.out(pl([(560, 470), (740, 250), (600, 540)], True), ACCENT)
    s.ln([(560, 470), (580, 520)])
    s.decor(("dot", 380, 260), ("plus", 760, 480))
    return s


def storage_full():
    s = Clean("storage_full")
    s.cylinder(512, 470, 280, 70)
    s.cylinder(512, 380, 280, 70)
    s.cylinder(512, 290, 280, 70, ACCENT)
    s.badge(660, 230, 40, "bang", INK)
    s.decor(("dot", 330, 250), ("plus", 330, 560))
    return s


def inbox_empty():
    s = Clean("inbox_empty")
    s.tray(512, 450, 380, 120)
    s.badge(512, 290, 58, "check")
    s.decor(("spark", 320, 250), ("dot", 700, 240), ("plus", 720, 560))
    return s


def update():
    s = Clean("update")
    s.out(rrect(512, 400, 230, 230, 56), PAPER)
    s.out(rrect(512, 400, 150, 150, 36), WASH, 4)
    s.badge(512, 400, 50, "up")
    s.decor(("spark", 680, 250), ("spark", 340, 270), ("dot", 700, 520))
    return s


# Mail only --------------------------------------------------------------------------------

def drafts_empty():
    s = Clean("drafts_empty")
    s.card(480, 400, 260, 310, 20, -4)
    s.bars(390, 300, [160, 120, 170, 90])
    p = rot([(470, 520), (690, 300), (720, 330), (500, 550), (462, 560)], 590, 430, 0)
    s.out(pl(p, True), ACCENT)
    s.ln([(690, 300), (720, 330)])
    s.out(pl([(470, 520), (500, 550), (462, 560)], True), INK)
    s.decor(("dot", 320, 240), ("plus", 740, 480))
    return s


def spam_empty():
    s = Clean("spam_empty")
    s.out("M512,220 L640,266 L640,380 C640,470 584,530 512,560 C440,530 384,470 384,380 L384,266 Z", ACCENT)
    s.stroke(pl([(460, 390), (500, 430), (570, 350)]), PAPER, 16)
    s.decor(("spark", 700, 250), ("dot", 330, 250), ("ring", 690, 540))
    return s


def archive_empty():
    s = Clean("archive_empty")
    s.out(rrect(512, 440, 300, 220, 18))
    s.out(rrect(512, 300, 340, 70, 18), ACCENT)
    s.out(rrect(512, 400, 100, 30, 15), WASH)
    s.decor(("spark", 720, 240), ("dot", 320, 250), ("plus", 330, 560))
    return s


def attachments_empty():
    s = Clean("attachments_empty")
    s.card(500, 410, 280, 300, 22, 4)
    s.bars(410, 400, [170, 120, 150])
    clip = "M560,160 L560,330 C560,370 500,370 500,330 L500,200 C500,176 534,176 534,200 L534,320"
    s.stroke(clip, INK, 22)
    s.stroke(clip, ACCENT, 10)
    s.decor(("dot", 330, 240), ("plus", 740, 500))
    return s


def snoozed_empty():
    s = Clean("snoozed_empty")
    s.out(ellipse(440, 254, 34), ACCENT)
    s.out(ellipse(584, 254, 34), ACCENT)
    s.out(ellipse(512, 400, 140))
    s.stroke(ellipse(512, 400, 112), WASH, 8)
    s.ln([(512, 400), (512, 330)], 10)
    s.ln([(512, 400), (566, 430)], 10)
    s.fill(ellipse(512, 400, 10), ACCENT)
    s.ln([(430, 520), (410, 556)], 10)
    s.ln([(594, 520), (614, 556)], 10)
    s.stroke(pl([(670, 250), (706, 250), (670, 290), (706, 290)]), INK, 6)
    s.decor(("dot", 320, 520))
    return s


def scheduled_empty():
    s = Clean("scheduled_empty")
    s.out(rrect(490, 410, 300, 280, 24))
    s.out("M340,320 L340,294 C340,280 352,270 364,270 L616,270 C628,270 640,280 640,294 L640,320 Z", ACCENT)
    for x in (420, 560):
        s.ln([(x, 250), (x, 292)], 12)
    for r in range(2):
        for c in range(4):
            s.fill(rrect(400 + c * 60, 380 + r * 60, 30, 30, 8), WASH)
    s.out(ellipse(630, 520, 64), PAPER)
    s.ln([(630, 520), (630, 486)], 8)
    s.ln([(630, 520), (656, 536)], 8)
    s.decor(("spark", 730, 250), ("dot", 300, 250))
    return s


def contacts_empty():
    s = Clean("contacts_empty")
    s.card(512, 410, 340, 230, 26)
    s.out(ellipse(430, 380, 46), WASH)
    s.fill(ellipse(430, 368, 16), ACCENT)
    s.fill("M402,410 C406,390 454,390 458,410 Z", ACCENT)
    s.bars(500, 360, [130, 90])
    s.bars(380, 470, [250])
    s.badge(660, 292, 36, "plus")
    s.decor(("dot", 320, 250), ("ring", 330, 560))
    return s


# podcasts ----------------------------------------------------------------------------------

def podcasts_empty():
    """No shows yet: a pair of headphones."""
    s = Clean("podcasts_empty")
    band = "M392,420 L392,360 C392,290 448,240 512,240 C576,240 632,290 632,360 L632,420"
    s.stroke(band, INK, 34)
    s.stroke(band, PAPER, 20)
    for x in (392, 632):
        s.out(rrect(x, 452, 70, 120, 26), ACCENT)
    s.decor(("spark", 700, 250), ("dot", 330, 260), ("plus", 330, 560))
    return s


def queue_empty():
    """Up next is empty: a list with a play button waiting at the top."""
    s = Clean("queue_empty")
    s.card(512, 400, 340, 280, 26)
    for i, y in enumerate((330, 400, 470)):
        s.out(rrect(400, y, 46, 46, 12), WASH if i else ACCENT)
        s.bars(444, y - 8, [150 if i else 180, 90], gap=20, w=10)
    s.fill(pl([(392, 318), (392, 342), (412, 330)], True), PAPER)
    s.decor(("spark", 720, 240), ("dot", 300, 250), ("ring", 320, 570))
    return s


def nothing_playing():
    """Nothing playing: a podcast microphone on its stand."""
    s = Clean("nothing_playing")
    s.out(ellipse(512, 590, 90, 18))
    s.stroke(pl([(512, 470), (512, 586)]), INK, 14)
    s.stroke("M432,380 L432,400 C432,446 468,476 512,476 C556,476 592,446 592,400 L592,380", INK, W + 2)
    s.out(rrect(512, 320, 120, 220, 60), PAPER)
    for y in (260, 290, 320):
        s.stroke(pl([(476, y), (548, y)]), WASH, 8)
    s.out(rrect(512, 372, 120, 26, 4), ACCENT)
    s.decor(("spark", 680, 230), ("dot", 340, 260), ("plus", 680, 540))
    return s


SCENES = [loving, reading, unboxing, clumsy, sitting_reading, chilling, meditating, nothing_found, search_start,
          waiting, working, offline, not_found, locked, permission, quiet, trash_empty, downloads, sync, welcome,
          sent, storage_full, inbox_empty, update,
          drafts_empty, spam_empty, archive_empty, attachments_empty, snoozed_empty, scheduled_empty, contacts_empty,
          podcasts_empty, queue_empty, nothing_playing]


# password manager ----------------------------------------------------------------------------

def _safe(s, cx, cy, w=260, h=240, open_=False):
    x0, x1, top, bot = cx - w / 2, cx + w / 2, cy - h / 2, cy + h / 2
    for t in (-1, 1):
        s.fill(rrect(cx + t * w * 0.32, bot + 12, 34, 22, 6), INK)
    s.out(rrect(cx, cy, w, h, 22))
    if open_:
        s.out(rrect(cx, cy, w - 44, h - 44, 12), WASH)
        s.out(pl([(x1, top + 14), (x1 + w * 0.4, top - 16), (x1 + w * 0.4, bot + 12), (x1, bot - 14)], True))
        s.fill(ellipse(x1 + w * 0.2, cy, 18, 24), ACCENT)
        return
    s.stroke(rrect(cx, cy, w - 44, h - 44, 12), INK, 4)
    s.out(ellipse(cx - 20, cy, 46), ACCENT)
    s.fill(ellipse(cx - 20, cy, 12), PAPER)
    s.ln([(x1 - 46, cy - 36), (x1 - 46, cy + 36)], 12)


def database_none():
    """No database open: a safe standing open and empty."""
    s = Clean("database_none")
    _safe(s, 470, 400, open_=True)
    s.decor(("spark", 720, 250), ("dot", 320, 240), ("plus", 330, 570))
    return s


def unlock():
    """Unlock the vault: a closed safe with its key."""
    s = Clean("unlock")
    _safe(s, 490, 400)
    s.out(ellipse(650, 270, 34), ACCENT)
    s.fill(ellipse(650, 270, 12), PAPER)
    s.stroke(pl([(676, 294), (740, 358)]), ACCENT, 18)
    s.stroke(pl([(716, 334), (700, 350)]), ACCENT, 14)
    s.decor(("dot", 320, 240), ("plus", 330, 570))
    return s


def wrong_password():
    """Wrong master password: a password field with a cross."""
    s = Clean("wrong_password")
    s.card(500, 400, 340, 150, 26)
    for i in range(6):
        s.fill(ellipse(390 + i * 36, 400, 11), INK)
    s.badge(660, 320, 46, "x")
    s.decor(("spark", 330, 260), ("dot", 700, 520), ("ring", 330, 540))
    return s


def all_secure():
    """No weak or reused passwords: a list of checks under a shield."""
    s = Clean("all_secure")
    s.card(490, 420, 300, 250, 24)
    for y in (350, 420, 490):
        s.out(ellipse(392, y, 18), ACCENT)
        s.stroke(pl([(384, y), (390, y + 6), (400, y - 6)]), PAPER, 5)
        s.bars(424, y, [140], w=10)
    s.out("M662,220 L726,244 L726,300 C726,344 698,372 662,388 C626,372 598,344 598,300 L598,244 Z", ACCENT)
    s.stroke(pl([(638, 300), (656, 318), (688, 284)]), PAPER, 9)
    s.decor(("dot", 320, 250), ("plus", 330, 580))
    return s


def expiring_none():
    """Nothing expiring soon: an hourglass with a check."""
    s = Clean("expiring_none")
    s.out(rrect(500, 226, 220, 30, 12), INK)
    s.out(rrect(500, 542, 220, 30, 12), INK)
    s.out("M418,240 L582,240 C582,320 528,360 516,384 C528,408 582,448 582,528 L418,528 C418,448 472,408 484,384 "
          "C472,360 418,320 418,240 Z")
    s.fill("M442,520 C458,470 488,470 500,454 C512,470 542,470 558,520 Z", ACCENT)
    s.badge(650, 300, 46, "check")
    s.decor(("dot", 330, 260), ("plus", 340, 560))
    return s


def fingerprint():
    """Unlock with a fingerprint: a fingerprint on its pad."""
    s = Clean("fingerprint")
    s.out(rrect(512, 390, 260, 260, 64))
    for r, a0, a1 in ((26, 190, 470), (50, 200, 500), (74, 210, 470), (98, 220, 430)):
        s.stroke(smooth(arc(512, 400, r, a0, a1)), ACCENT, 10)
    s.decor(("spark", 720, 240), ("dot", 310, 250), ("ring", 320, 570))
    return s


SCENES = SCENES + [database_none, unlock, wrong_password, all_secure, expiring_none, fingerprint]
