"""Draws the README graphics from the game itself.

Run from the project root:  python3 art/readme/draw.py     (writes docs/img/*.png)

Everything comes from the game: tiles, face, crusher and sky from assets/, the slime's shape from
art/readme/frames.txt (particle positions printed by the real physics), the zone pictures from
art/readme/shots/ (real screenshots). Only the diagrams and the lettering are drawn here.
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "docs", "img")
os.makedirs(OUT, exist_ok=True)


def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


INK = hx("1a1424")
PANEL = hx("2a2236")
PANEL2 = hx("3a2f45")
SKY = hx("8cc1e4")
WHITE = hx("f4fff4")
SLIME_L, SLIME_M, SLIME_D, SLIME_O = hx("9be37a"), hx("62c45c"), hx("3f9a53"), hx("1d5a3a")
RED, RED_D = hx("e9453c"), hx("7a1b22")
GOLD, ORANGE = hx("ffd36b"), hx("e0702f")
BLUE = hx("62b4ff")
GREY = hx("8791a8")

# ---------------------------------------------------------------- 5 x 7 pixel font
GLYPHS = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01110 10001 10000 10000 10000 10001 01110", "D": "11110 10001 10001 10001 10001 10001 11110",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01110 10001 10000 10111 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "01110 00100 00100 00100 00100 00100 01110", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 10101 10011 10001 10001 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 10001 01010 00100",
    "W": "10001 10001 10001 10101 10101 11011 10001", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110", "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00110 01000 10000 11111", "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010", "5": "11111 10000 11110 00001 00001 10001 01110",
    "6": "00110 01000 10000 11110 10001 10001 01110", "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110", "9": "01110 10001 10001 01111 00001 00010 01100",
    " ": "00000 00000 00000 00000 00000 00000 00000", ".": "00000 00000 00000 00000 00000 01100 01100",
    ",": "00000 00000 00000 00000 01100 00100 01000", "-": "00000 00000 00000 11111 00000 00000 00000",
    ":": "00000 01100 01100 00000 01100 01100 00000", "!": "00100 00100 00100 00100 00100 00000 00100",
    "?": "01110 10001 00001 00110 00100 00000 00100", "/": "00001 00001 00010 00100 01000 10000 10000",
    "+": "00000 00100 00100 11111 00100 00100 00000", "=": "00000 00000 11111 00000 11111 00000 00000",
    ">": "10000 01000 00100 00010 00100 01000 10000", "<": "00001 00010 00100 01000 00100 00010 00001",
    "(": "00010 00100 01000 01000 01000 00100 00010", ")": "01000 00100 00010 00010 00010 00100 01000",
    "*": "00000 10101 01110 11111 01110 10101 00000", "%": "11001 11010 00010 00100 01000 01011 10011",
    "'": "00100 00100 01000 00000 00000 00000 00000", "#": "01010 01010 11111 01010 11111 01010 01010",
}


def text_width(s, k):
    return len(s) * 6 * k - k


def text(img, x, y, s, k=3, color=WHITE, shadow=INK, anchor="l"):
    """Draws [s] in capitals at pixel size [k]; anchor 'l' left, 'c' centred on x, 'r' right-aligned to x."""
    s = s.upper()
    if anchor == "c":
        x -= text_width(s, k) // 2
    elif anchor == "r":
        x -= text_width(s, k)
    d = ImageDraw.Draw(img)
    for layer, (ox, oy, col) in enumerate(([(k, k, shadow)] if shadow else []) + [(0, 0, color)]):
        cx = x
        for ch in s:
            rows = GLYPHS.get(ch, GLYPHS["?"]).split()
            for ry, row in enumerate(rows):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        px, py = cx + ox + rx * k, y + oy + ry * k
                        d.rectangle([px, py, px + k - 1, py + k - 1], fill=col)
            cx += 6 * k


# ---------------------------------------------------------------- the game's own art
ASSETS = os.path.join(ROOT, "assets")
TILES = Image.open(os.path.join(ASSETS, "tiles.png")).convert("RGBA")
GRASS_T, DIRT_T, STUB_T, PAD_T = (TILES.crop((i * 16, 0, i * 16 + 16, 16)) for i in range(4))
SLOPE_UP_T, SLOPE_DOWN_T = (TILES.crop((i * 16, 0, i * 16 + 16, 16)) for i in (4, 5))
FACE = Image.open(os.path.join(ASSETS, "face.png")).convert("RGBA")
SKY_IMG = Image.open(os.path.join(ASSETS, "bg.png")).convert("RGBA")
CRUSHER = Image.open(os.path.join(ASSETS, "crusher.png")).convert("RGBA")


def up(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def winding_mask(size, pts, ring=22):
    """Inside the outline by winding number (not even-odd), as the game paints it, so a folded outline stays solid."""
    mask = Image.new("L", size, 0)
    px = mask.load()
    verts = [(pts[2 * i], pts[2 * i + 1]) for i in range(ring)]
    for y in range(size[1]):
        yc = y + 0.5
        hits = []
        for i in range(ring):
            (x0, y0), (x1, y1) = verts[i], verts[(i + 1) % ring]
            if (y0 <= yc) != (y1 <= yc):
                hits.append((x0 + (yc - y0) / (y1 - y0) * (x1 - x0), 1 if y1 > y0 else -1))
        hits.sort()
        winding, start = 0, 0.0
        for x, d in hits:
            was = winding != 0
            winding += d
            if not was and winding != 0:
                start = x
            elif was and winding == 0:
                for xx in range(int(math.floor(start)), int(math.ceil(x))):
                    if 0 <= xx < size[0]:
                        px[xx, y] = 255
    return mask


def draw_slime(img, pts, ring=22):
    """Draws the slime as the game does, 1 world unit = 1 pixel: banded fill, outline, face."""
    ring_pts = [(round(pts[2 * i]), round(pts[2 * i + 1])) for i in range(ring)]
    mask = winding_mask(img.size, pts, ring)
    ys = [p[1] for p in ring_pts]
    top, bottom = min(ys), max(ys)
    for y in range(top, bottom + 1):
        shade = (y - top) / max(1, bottom - top + 1)
        color = SLIME_L if shade < 0.25 else SLIME_M if shade < 0.7 else SLIME_D
        img.paste(color, (0, y, img.width, y + 1), mask.crop((0, y, img.width, y + 1)))
    d = ImageDraw.Draw(img)
    d.line(ring_pts + [ring_pts[0]], fill=SLIME_O, width=1)
    cx = sum(p[0] for p in ring_pts) / ring
    cy = sum(p[1] for p in ring_pts) / ring
    img.alpha_composite(FACE, (round(cx + 3 - 6), round(cy - 1 - 4)))


def tile_for(rows, c, r):
    ch = rows[r][c]
    if ch == "#":
        above = rows[r - 1][c] if r > 0 else "."
        return DIRT_T if above == "#" else GRASS_T
    if ch == "v":
        return STUB_T
    return None


def level_image(rows, bg=SKY):
    img = Image.new("RGBA", (len(rows[0]) * 16, len(rows) * 16), bg)
    for r in range(len(rows)):
        for c in range(len(rows[0])):
            t = tile_for(rows, c, r)
            if t is not None:
                img.alpha_composite(t, (c * 16, r * 16))
    return img


# ---------------------------------------------------------------- the real physics frames
def load_frames():
    grids, frames = {}, {}
    cur = None
    for line in open(os.path.join(ROOT, "art", "readme", "frames.txt")):
        p = line.split()
        if not p:
            continue
        if p[0] == "GRID":
            cur = p[1]
            grids[cur] = []
        elif p[0] == "ROW":
            grids[cur].append(p[1])
        elif p[0] == "FRAME":
            frames.setdefault(p[1], []).append((int(p[2]), [float(v) for v in p[3:]]))
    return grids, frames


GRIDS, FRAMES = load_frames()


def panel(scene, pts, window, k, caption=None):
    """One framed picture of the slime in [scene] (a level_image), cropped to window = (x, y, w, h) in world units."""
    img = scene.copy()
    draw_slime(img, pts)
    x, y, w, h = [int(v) for v in window]
    crop = img.crop((x, y, x + w, y + h))
    big = up(crop, k)
    d = ImageDraw.Draw(big)
    d.rectangle([0, 0, big.width - 1, big.height - 1], outline=INK, width=k)
    if caption:
        text(big, 6 * k // 2, 5, caption, k=2)
    return big


def strip(panels, gap=10, bg=PANEL):
    w = sum(p.width for p in panels) + gap * (len(panels) + 1)
    h = max(p.height for p in panels) + gap * 2
    img = Image.new("RGBA", (w, h), bg)
    x = gap
    for p in panels:
        img.alpha_composite(p, (x, gap))
        x += p.width + gap
    return img


def centre(pts, ring=22):
    return sum(pts[2 * i] for i in range(ring)) / ring, sum(pts[2 * i + 1] for i in range(ring)) / ring


# ---------------------------------------------------------------- 1. hero
def hero():
    k = 3
    W, H = 320, 150
    img = Image.new("RGBA", (W, H), SKY)
    img.alpha_composite(SKY_IMG.crop((60, 60, 380, 60 + H)), (0, 0))
    floor_row = 8
    for c in range(W // 16 + 1):
        for r in range(floor_row, H // 16 + 1):
            img.alpha_composite(GRASS_T if r == floor_row else DIRT_T, (c * 16, r * 16))
    # a hill, a pad, and a curtain hanging from a ledge, all from the real tiles
    for i, c in enumerate(range(15, 19)):
        img.alpha_composite(SLOPE_UP_T, (c * 16, (floor_row - 1 - i) * 16))
        for r in range(floor_row - i, floor_row):
            img.alpha_composite(DIRT_T, (c * 16, r * 16))
    img.alpha_composite(PAD_T, (11 * 16, floor_row * 16))
    ledge_y = 58
    for c in range(4, 9):
        img.alpha_composite(GRASS_T, (c * 16, ledge_y))
        img.alpha_composite(DIRT_T, (c * 16, ledge_y + 16))
    d = ImageDraw.Draw(img)
    top = ledge_y + 32
    for sx in range(64, 144 + 1, 16):
        d.line([(sx, top), (sx, top + 34)], fill=RED_D, width=3)
        d.line([(sx, top), (sx, top + 34)], fill=RED, width=1)
        d.ellipse([sx - 3, top + 31, sx + 3, top + 37], fill=RED, outline=RED_D)
    # the slime mid-jump, from the real simulation
    jump = dict(FRAMES["jump"])
    pts = jump[20]
    cx, cy = centre(pts)
    shifted = [pts[i] + (232 - cx if i % 2 == 0 else (86 - cy)) for i in range(len(pts))]
    draw_slime(img, shifted)
    big = up(img, k)
    band = Image.new("RGBA", (big.width, 128), hx("1a1424", 200))
    big.alpha_composite(band, (0, 0))
    text(big, W * k // 2, 18, "SLIMY THE BLOB", k=7, color=GOLD, anchor="c")
    text(big, W * k // 2, 18 + 7 * 9 + 8, "A SLIME MADE OF 22 DOTS AND ONE HEAVY BALL", k=3, color=WHITE, anchor="c")
    big.save(os.path.join(OUT, "hero.png"))


# ---------------------------------------------------------------- 2. anatomy
def anatomy():
    S = 13
    r, hs = 13.3, 1.3
    n_arc, n_base = 14, 8
    ring = []
    for i in range(n_arc + 1):
        a = math.pi + math.pi * i / n_arc
        ring.append((math.cos(a) * r, math.sin(a) * r * hs))
    for j in range(1, n_base):
        ring.append((r - 2 * r * j / n_base, 0.0))
    core = (0.0, -4 * r / (3 * math.pi) * hs)
    W, H = 980, 470
    img = Image.new("RGBA", (W, H), PANEL)
    ox, oy = 250, 410
    P = lambda p: (ox + p[0] * S, oy + p[1] * S)
    d = ImageDraw.Draw(img)
    d.rectangle([0, oy, W, H], fill=hx("6b4a3c"))
    d.rectangle([0, oy, W, oy + 6], fill=hx("4aa13c"))
    spoke = hx("7d8bb8")
    for p in ring:
        d.line([P(core), P(p)], fill=spoke, width=2)
    pts = [P(p) for p in ring]
    d.polygon(pts, fill=hx("3f9a53", 90))
    d.line(pts + [pts[0]], fill=SLIME_M, width=4)
    for x, y in pts:
        d.rectangle([x - 6, y - 6, x + 6, y + 6], fill=WHITE, outline=INK, width=2)
    cx, cy = P(core)
    d.ellipse([cx - 24, cy - 24, cx + 24, cy + 24], fill=RED, outline=INK, width=3)
    text(img, cx, cy - 7, "34X", k=3, color=WHITE, shadow=None, anchor="c")

    def label(x, y, head, body, color, target):
        d.line([(x - 8, y + 10), target], fill=color, width=2)
        d.ellipse([target[0] - 5, target[1] - 5, target[0] + 5, target[1] + 5], fill=color, outline=INK, width=2)
        d.rectangle([x, y + 2, x + 16, y + 18], fill=color, outline=INK, width=2)
        text(img, x + 28, y, head, k=3, color=WHITE)
        for i, line in enumerate(body):
            text(img, x + 28, y + 28 + i * 22, line, k=2, color=GREY, shadow=None)

    mid = lambda a, b: ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    label(560, 20, "22 SKIN DOTS", ["JOINED BY STIFF LINKS", "SO THE SKIN WONT TEAR"], WHITE, pts[5])
    label(560, 120, "AREA RULE", ["SQUASH IT HERE, IT BULGES THERE", "THE VOLUME STAYS THE SAME"], SLIME_M, (ox + 9.3 * S, oy - 6.8 * S))
    label(560, 226, "1 HEAVY CORE", ["STANDS FOR THE GOO INSIDE", "ITS WEIGHT DRAGS THE BODY", "THROUGH TIGHT GAPS"], RED, (cx + 24, cy))
    label(560, 346, "SOFT SPOKES", ["REMEMBER THE SHAPE", "BUT LET IT STRETCH"], spoke, mid(P(core), P(ring[19])))
    text(img, 20, 16, "SLIME ANATOMY", k=4, color=GOLD)
    text(img, 20, 56, "ONE FRAME OF THE REAL SIM", k=2, color=GREY, shadow=None)
    img.save(os.path.join(OUT, "anatomy.png"))


# ---------------------------------------------------------------- 3. shaft squeeze
def shaft():
    rows = GRIDS["shaft"]
    scene = level_image(rows)
    seq = dict(FRAMES["shaft"])
    steps = sorted(seq)
    rim = 5 * 16
    def first(pred):
        return next(s for s in steps if pred(seq[s]))
    top = lambda p: min(p[2 * i + 1] for i in range(22))
    bottom = lambda p: max(p[2 * i + 1] for i in range(22))
    picks = [
        steps[0],
        first(lambda p: bottom(p) > rim + 4),
        first(lambda p: centre(p)[1] > rim + 16),
        first(lambda p: top(p) > rim + 6),
        first(lambda p: bottom(p) > 15 * 16 + 6),
        first(lambda p: top(p) > 15 * 16 + 2),
    ]
    caps = ["DROP", "TOUCH", "SQUISH", "STRETCH", "POP OUT", "FREE"]
    panels = []
    for i, s in enumerate(picks):
        cx, cy = centre(seq[s])
        wy = int(min(max(cy - 36, 0), len(rows) * 16 - 88))
        panels.append(panel(scene, seq[s], (int(11.5 * 16 - 28), wy, 56, 88), 4, caps[i]))
    strip(panels).save(os.path.join(OUT, "squeeze_shaft.png"))
    return picks


# ---------------------------------------------------------------- 4. tunnel squeeze
def tunnel():
    rows = GRIDS["tunnel"]
    scene = level_image(rows)
    seq = dict(FRAMES["tunnel"])
    steps = sorted(seq)
    def first(pred):
        return next((s for s in steps if pred(seq[s])), steps[-1])
    xs = lambda p: centre(p)[0]
    left = lambda p: min(p[2 * i] for i in range(22))
    right = lambda p: max(p[2 * i] for i in range(22))
    out = first(lambda p: left(p) > 18 * 16 + 2)
    picks = [
        steps[0],
        first(lambda p: right(p) > 10 * 16 + 2),
        first(lambda p: xs(p) > 12.5 * 16),
        first(lambda p: xs(p) > 15.5 * 16),
        out,
        steps[min(len(steps) - 1, steps.index(out) + 4)],
    ]
    caps = ["READY", "SQUASH", "FLATTENED", "SQUEEZE", "POP OUT", "BOUNCE BACK"]
    panels = []
    for i, s in enumerate(picks):
        cx, _ = centre(seq[s])
        wx = int(min(max(cx - 44, 0), len(rows[0]) * 16 - 88))
        panels.append(panel(scene, seq[s], (wx, 4 * 16 + 6, 88, 7 * 16 - 6), 3, caps[i]))
    strip(panels).save(os.path.join(OUT, "squeeze_tunnel.png"))
    return picks


# ---------------------------------------------------------------- 5. jump
def jump():
    rows = GRIDS["jump"]
    scene = level_image(rows)
    seq = dict(FRAMES["jump"])
    steps = sorted(seq)
    ys = {s: centre(seq[s])[1] for s in steps}
    apex = min(steps, key=lambda s: ys[s])
    height = lambda s: max(seq[s][2 * i + 1] for i in range(22)) - min(seq[s][2 * i + 1] for i in range(22))
    after = [s for s in steps if s >= apex + 14]
    land = min(after, key=height) if after else steps[-1]
    picks = [steps[0], steps[3], apex, min(steps, key=lambda s: abs(s - (apex + 10))), land, steps[-1]]
    caps = ["READY", "LAUNCH", "TOP", "FALL", "SPLAT", "SETTLE"]
    panels = []
    for i, s in enumerate(picks):
        panels.append(panel(scene, seq[s], (int(7 * 16 - 24), 9 * 16 - 104, 48, 112), 3, caps[i]))
    strip(panels).save(os.path.join(OUT, "squash_stretch.png"))
    return picks


# ---------------------------------------------------------------- 6. the four zones, from real screenshots
def zones():
    names = {"A": "THE CALIBRATION CHAMBER", "B": "THE VERTICAL ASCENT", "C": "THE SQUEEZE CORRIDOR", "D": "THE KINETIC GAUNTLET"}
    tests = {"A": "RUNNING, FRICTION, HOPS", "B": "PITS, STEPS, AIR CONTROL", "C": "TUNNELS AND A TIGHT SHAFT", "D": "SLOPES, PADS, A CRUSHER"}
    tiles = []
    for z in "ABCD":
        shot = Image.open(os.path.join(ROOT, "art", "readme", "shots", f"zone_{z}.png")).convert("RGBA")
        shot = shot.crop((0, 130, shot.width, 1290)).resize((640, 290), Image.NEAREST)
        d = ImageDraw.Draw(shot)
        d.rectangle([0, 0, 639, 289], outline=INK, width=3)
        d.rectangle([0, 238, 639, 289], fill=hx("1a1424", 215))
        text(shot, 12, 244, f"{z}  {names[z]}", k=3, color=GOLD, shadow=None)
        text(shot, 12, 268, tests[z], k=2, color=WHITE, shadow=None)
        tiles.append(shot)
    img = Image.new("RGBA", (640 * 2 + 30, 290 * 2 + 30), PANEL)
    for i, t in enumerate(tiles):
        img.alpha_composite(t, (10 + (i % 2) * 650, 10 + (i // 2) * 300))
    img.save(os.path.join(OUT, "zones.png"))


# ---------------------------------------------------------------- 7. how a frame flows
def box(img, x, y, w, h, title, lines, color, tcolor=INK):
    for line in [title] + lines:
        assert text_width(line, 2 if line is not title else 3) + 24 <= w, f"'{line}' does not fit a box {w} wide"
    d = ImageDraw.Draw(img)
    d.rectangle([x + 4, y + 4, x + w + 4, y + h + 4], fill=INK)
    d.rectangle([x, y, x + w, y + h], fill=color, outline=INK, width=3)
    text(img, x + 12, y + 10, title, k=3, color=tcolor, shadow=None)
    for i, line in enumerate(lines):
        text(img, x + 12, y + 44 + i * 20, line, k=2, color=tcolor, shadow=None)


def arrow(img, a, b, color=WHITE, label=None):
    d = ImageDraw.Draw(img)
    d.line([a, b], fill=color, width=4)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    for da in (2.6, -2.6):
        d.line([b, (b[0] + 14 * math.cos(ang + da), b[1] + 14 * math.sin(ang + da))], fill=color, width=4)
    if label:
        text(img, (a[0] + b[0]) // 2, min(a[1], b[1]) - 22, label, k=2, color=color, anchor="c")


def flow():
    W, H = 1180, 640
    img = Image.new("RGBA", (W, H), PANEL)
    text(img, 20, 14, "HOW A FRAME WORKS", k=4, color=GOLD)
    text(img, 20, 54, "60 TIMES A SECOND, FOR THE SLIME", k=2, color=GREY, shadow=None)
    box(img, 20, 110, 240, 90, "KEYBOARD", ["A D  SPACE  1-4"], BLUE)
    box(img, 340, 110, 260, 90, "SLIME.STEP", ["MOVE, JUMP, COYOTE"], SLIME_M)
    arrow(img, (262, 155), (338, 155))
    d = ImageDraw.Draw(img)
    d.rectangle([300, 250, 1160, 440], fill=PANEL2, outline=INK, width=3)
    text(img, 316, 258, "4 SUBSTEPS EVERY STEP", k=3, color=WHITE, shadow=None)
    box(img, 320, 300, 230, 110, "1 MOVE", ["GRAVITY AND", "MOMENTUM"], GOLD)
    box(img, 590, 300, 260, 110, "2 RELAX", ["SKIN, SPOKES, AREA", "6 PASSES"], ORANGE)
    box(img, 890, 300, 250, 110, "3 COLLIDE", ["PUSH DOTS OUT OF", "FLOOR AND WALLS"], hx("9be37a"))
    arrow(img, (552, 355), (588, 355))
    arrow(img, (852, 355), (888, 355))
    arrow(img, (470, 202), (470, 248))
    box(img, 20, 300, 240, 110, "LEVEL", ["MAP IN, SOLIDS OUT", "AND TILES"], hx("a88056"))
    arrow(img, (262, 355), (298, 355))
    box(img, 340, 500, 240, 100, "CURTAIN", ["RED SLIME CLOTH", "GETS PUSHED"], RED, WHITE)
    box(img, 620, 500, 300, 100, "PAINTER", ["FILL, OUTLINE, FACE", "ONE PIXEL ROW AT A TIME"], hx("62c45c"))
    arrow(img, (582, 550), (618, 550))
    box(img, 960, 500, 200, 100, "SCREEN", ["KLYPH + SDL3"], WHITE)
    arrow(img, (922, 550), (958, 550))
    arrow(img, (1000, 442), (800, 498), color=GREY)
    box(img, 20, 500, 280, 100, "63 TESTS", ["INCLUDING A BOT THAT", "PLAYS EVERY ZONE"], hx("ff8aa0"))
    arrow(img, (140, 498), (140, 412), color=GREY)
    img.save(os.path.join(OUT, "flow.png"))


# ---------------------------------------------------------------- 8. the curtain
def curtain():
    shot = Image.open(os.path.join(ROOT, "art", "readme", "shots", "curtain.png")).convert("RGBA")
    crop = shot.crop((640, 600, 1700, 1300)).resize((530, 350), Image.NEAREST)
    d = ImageDraw.Draw(crop)
    d.rectangle([0, 0, 529, 349], outline=INK, width=3)
    crop.save(os.path.join(OUT, "curtain.png"))


if __name__ == "__main__":
    hero()
    anatomy()
    print("shaft frames", shaft())
    print("tunnel frames", tunnel())
    print("jump frames", jump())
    zones()
    flow()
    curtain()
    print(sorted(os.listdir(OUT)))
