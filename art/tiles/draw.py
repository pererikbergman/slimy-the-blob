"""Draws the art for Slimy the Blob: tile sheet, crusher, slime face and background.

Run from the project root:  python3 art/tiles/draw.py
Writes assets/*.png, art/<set>/v<N>_*.png renders and 8x previews, and assets/tiles.json.
Light comes from the top left everywhere. Every material is a 5-tone ramp with hue-shifted shadows.
"""
import json
import math
import os
from PIL import Image, ImageDraw

VERSION = 1
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


OUTLINE = hx("1a1424")
DIRT = [hx(c) for c in ("2d1f2b", "4a3038", "6b4a3c", "8a6347", "a88056")]
GRASS = [hx(c) for c in ("1f4d2b", "2f7a35", "4aa13c", "7dc94b", "b5e86a")]
STONE = [hx(c) for c in ("2a2f3d", "434a5e", "626b82", "8791a8", "b0bad0")]
ORANGE = [hx(c) for c in ("7a2c1b", "b5452a", "e0702f", "f5a03c", "ffd36b")]
CLEAR = (0, 0, 0, 0)


def h(x, y, salt=0):
    """Small integer hash, 0..255."""
    n = (x * 374761393 + y * 668265263 + salt * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFF


def dirt_pixel(x, y):
    """Seamless dirt: periodic in 16 px both ways, strata plus speckles."""
    px, py = x % 16, y % 16
    v = h(px, py, 1)
    tone = 2
    if (py // 4 + h(0, py // 4, 7)) % 5 == 0:
        tone = 1 if v < 150 else 2
    if v < 22:
        tone = 1
    elif v > 238:
        tone = 3
    elif 118 < v < 126:
        tone = 3
    if v == 77 or v == 178:
        tone = 0
    return DIRT[tone]


def grass_depth(x):
    """How many pixels of grass hang below the top edge at column x (periodic)."""
    return 3 + (h(x % 16, 0, 3) % 3)


def tile_grass_top():
    im = Image.new("RGBA", (16, 16), CLEAR)
    for x in range(16):
        depth = grass_depth(x)
        for y in range(16):
            if y == 0:
                im.putpixel((x, y), GRASS[0])
            elif y == 1:
                im.putpixel((x, y), GRASS[4])
            elif y < depth:
                im.putpixel((x, y), GRASS[3] if y == 2 else GRASS[2])
            elif y == depth:
                im.putpixel((x, y), GRASS[1] if h(x, y, 5) < 140 else GRASS[0])
            else:
                im.putpixel((x, y), dirt_pixel(x, y))
    return im


def tile_dirt():
    im = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), dirt_pixel(x, y))
    return im


def tile_stub():
    """Top half of the tile is rock; the bottom half stays clear because the stub is only 8 units thick."""
    im = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(8):
        for x in range(16):
            brick = (y // 4) % 2
            offset = 8 if brick else 0
            seam_x = (x + offset) % 8 == 0
            seam_y = y % 4 == 0
            tone = 2
            v = h(x, y, 11)
            if v < 30:
                tone = 1
            elif v > 225:
                tone = 3
            if seam_x or seam_y:
                tone = 1
            if y == 0:
                tone = 3 if not seam_x else 2
            im.putpixel((x, y), STONE[tone])
    for x in range(16):
        im.putpixel((x, 7), OUTLINE)
        im.putpixel((x, 6), STONE[0] if h(x, 6, 2) < 200 else STONE[1])
    return im


def tile_pad():
    im = Image.new("RGBA", (16, 16), CLEAR)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=hx("2a2236"))
    # plate on top
    d.rectangle([0, 0, 15, 3], fill=ORANGE[2])
    d.line([(0, 0), (15, 0)], fill=ORANGE[4])
    d.line([(0, 1), (15, 1)], fill=ORANGE[3])
    d.line([(0, 3), (15, 3)], fill=ORANGE[0])
    d.line([(0, 4), (15, 4)], fill=OUTLINE)
    # spring: a zig-zag in light metal
    pts = [(3, 5), (12, 6), (3, 8), (12, 9), (3, 11), (12, 12)]
    d.line(pts, fill=STONE[3])
    d.line([(x, y + 1) for x, y in pts], fill=STONE[1])
    # base
    d.rectangle([0, 13, 15, 15], fill=STONE[2])
    d.line([(0, 13), (15, 13)], fill=STONE[4])
    d.line([(0, 15), (15, 15)], fill=OUTLINE)
    for x in (0, 15):
        d.line([(x, 0), (x, 15)], fill=OUTLINE)
    return im


def tile_slope(rising):
    im = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            # depth below the surface line, in pixels; negative means air
            depth = (x + y - 15) if rising else (y - x)
            if depth < 0:
                continue
            if depth == 0:
                im.putpixel((x, y), GRASS[4])
            elif depth == 1:
                im.putpixel((x, y), GRASS[3])
            elif depth == 2:
                im.putpixel((x, y), GRASS[2])
            elif depth == 3:
                im.putpixel((x, y), GRASS[1])
            else:
                im.putpixel((x, y), dirt_pixel(x, y))
    return im


def crusher():
    im = Image.new("RGBA", (32, 32), CLEAR)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], fill=STONE[2])
    # panel shading from the top left
    d.line([(1, 1), (30, 1)], fill=STONE[4])
    d.line([(1, 1), (1, 25)], fill=STONE[4])
    d.line([(2, 2), (29, 2)], fill=STONE[3])
    d.line([(30, 2), (30, 25)], fill=STONE[1])
    d.line([(2, 25), (30, 25)], fill=STONE[1])
    # mount bar at the top
    d.rectangle([0, 0, 31, 4], fill=STONE[1])
    d.line([(0, 4), (31, 4)], fill=STONE[0])
    # hazard band along the bottom edge
    for x in range(32):
        for y in range(26, 32):
            stripe = ((x + y) // 4) % 2 == 0
            d.point((x, y), fill=ORANGE[3] if stripe else OUTLINE)
    # rivets
    for rx, ry in ((4, 8), (27, 8), (4, 20), (27, 20)):
        d.point((rx, ry), fill=STONE[4])
        d.point((rx + 1, ry + 1), fill=STONE[0])
    # vertical panel seam
    d.line([(15, 6), (15, 24)], fill=STONE[1])
    d.line([(16, 6), (16, 24)], fill=STONE[3])
    d.rectangle([0, 0, 31, 31], outline=OUTLINE)
    return im


def face():
    im = Image.new("RGBA", (12, 8), CLEAR)
    dark = hx("1d3a2a")
    white = hx("f4fff4")
    for ex in (2, 8):
        for dy in range(3):
            for dx in range(2):
                im.putpixel((ex + dx, 1 + dy), dark)
        im.putpixel((ex, 1), white)
    for x in (3, 8):
        im.putpixel((x, 5), dark)
    for x in range(4, 8):
        im.putpixel((x, 6), dark)
    return im


SKY = [hx(c) for c in ("5e9ad6", "74aedd", "8cc1e4", "a6d3ea", "c0e2f0", "d8eef5")]
BAYER = [[0, 2], [3, 1]]


def background():
    w, hgt = 480, 240
    im = Image.new("RGBA", (w, hgt), SKY[0])
    px = im.load()
    bands = len(SKY)
    for y in range(hgt):
        t = y / hgt * (bands - 1)
        lo = int(t)
        frac = t - lo
        for x in range(w):
            threshold = (BAYER[y % 2][x % 2] + 0.5) / 4
            idx = lo + (1 if frac > threshold else 0)
            px[x, y] = SKY[min(idx, bands - 1)]
    layers = [
        # color, base y, (cycles, amplitude, phase) terms: whole cycles so the image tiles sideways
        (hx("9bc2e0"), 150, [(2, 18, 0.4), (5, 7, 1.1)]),
        (hx("7fa6cf"), 175, [(3, 16, 2.0), (7, 6, 0.3)]),
        (hx("5f88b8"), 200, [(4, 14, 4.1), (9, 5, 2.2)]),
    ]
    for color, base, terms in layers:
        for x in range(w):
            top = base
            for cycles, amp, phase in terms:
                top += amp * math.sin(2 * math.pi * cycles * x / w + phase)
            for y in range(int(top), hgt):
                px[x, y] = color
            # rim light on the lit edge
            px[x, int(top)] = tuple(min(255, c + 24) for c in color[:3]) + (255,)
    # clouds: flat-bottomed puffs placed away from the edges
    cloud = hx("f2fbff")
    shade = hx("cfe6f2")
    d = ImageDraw.Draw(im)
    for cx, cy, s in ((70, 40, 1.0), (200, 70, 0.8), (330, 32, 1.2), (430, 85, 0.7)):
        for dx, dy, r in ((0, 0, 10), (12, -4, 12), (26, 0, 9), (-12, 2, 8)):
            rr = int(r * s)
            d.ellipse([cx + int(dx * s) - rr, cy + int(dy * s) - rr, cx + int(dx * s) + rr, cy + int(dy * s) + rr], fill=cloud)
        d.rectangle([cx - int(22 * s), cy + int(4 * s), cx + int(34 * s), cy + int(12 * s)], fill=shade)
        d.rectangle([cx - int(22 * s), cy + int(4 * s), cx + int(34 * s), cy + int(8 * s)], fill=cloud)
    return im


def checker(w, hgt, cell=8):
    im = Image.new("RGBA", (w, hgt))
    d = ImageDraw.Draw(im)
    for y in range(0, hgt, cell):
        for x in range(0, w, cell):
            d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(70, 70, 80, 255) if (x // cell + y // cell) % 2 else (90, 90, 100, 255))
    return im


def zoom(im, k, bg=True):
    big = im.resize((im.width * k, im.height * k), Image.NEAREST)
    if not bg:
        return big
    base = checker(big.width, big.height, 8 * k // 2)
    base.alpha_composite(big)
    return base


def scene_preview(sheet, crush):
    """A little level built from the tiles, to judge seams and the diagonal surfaces."""
    rows = [
        "......................",
        "......................",
        "......................",
        "..........M...........",
        "..........vvv.........",
        "................/.....",
        ".............../#\\....",
        "J.v...J.../....#.#\\...",
        "##############.#####.#",
    ]
    # build with a plain 22 x 9 layout
    cols = 22
    out = Image.new("RGBA", (cols * 16, len(rows) * 16), hx("8cc1e4"))
    tiles = [sheet.crop((i * 16, 0, i * 16 + 16, 16)) for i in range(6)]
    for r, row in enumerate(rows):
        for c, ch in enumerate(row.ljust(cols, ".")):
            if ch == "#":
                above = rows[r - 1][c] if r > 0 and c < len(rows[r - 1]) else "."
                out.alpha_composite(tiles[1] if above == "#" else tiles[0], (c * 16, r * 16))
            elif ch == "v":
                out.alpha_composite(tiles[2], (c * 16, r * 16))
            elif ch == "J":
                out.alpha_composite(tiles[3], (c * 16, r * 16))
            elif ch == "/":
                out.alpha_composite(tiles[4], (c * 16, r * 16))
            elif ch == "\\":
                out.alpha_composite(tiles[5], (c * 16, r * 16))
            elif ch == "M":
                out.alpha_composite(crush, (c * 16, r * 16))
    return out


def save(im, folder, name, preview_zoom):
    os.makedirs(os.path.join(ROOT, "art", folder), exist_ok=True)
    im.save(os.path.join(ROOT, "art", folder, f"v{VERSION}_{name}.png"))
    zoom(im, preview_zoom).save(os.path.join(ROOT, "art", folder, f"v{VERSION}_{name}_preview.png"))
    im.save(os.path.join(ROOT, "assets", f"{name}.png"))


def main():
    os.makedirs(os.path.join(ROOT, "assets"), exist_ok=True)
    tiles = [tile_grass_top(), tile_dirt(), tile_stub(), tile_pad(), tile_slope(True), tile_slope(False)]
    sheet = Image.new("RGBA", (16 * len(tiles), 16), CLEAR)
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 16, 0))
    save(sheet, "tiles", "tiles", 8)
    crush = crusher()
    save(crush, "tiles", "crusher", 8)
    zoom(scene_preview(sheet, crush), 4).save(os.path.join(ROOT, "art", "tiles", f"v{VERSION}_scene_preview.png"))
    save(face(), "face", "face", 16)
    save(background(), "bg", "bg", 2)
    manifest = {
        "cell": 16,
        "sheet": "tiles.png",
        "tiles": {
            "0": {"name": "grass_top", "type": "solid"},
            "1": {"name": "dirt", "type": "solid"},
            "2": {"name": "ceiling_stub", "type": "solid", "note": "only the top 8 px are solid"},
            "3": {"name": "bounce_pad", "type": "bounce"},
            "4": {"name": "slope_up", "type": "slope", "note": "rises to the right; solid where x + y >= 15"},
            "5": {"name": "slope_down", "type": "slope", "note": "falls to the right; solid where x <= y"},
        },
        "crusher": {"file": "crusher.png", "size": [32, 32]},
        "face": {"file": "face.png", "size": [12, 8], "pivot": [6, 4]},
        "background": {"file": "bg.png", "size": [480, 240], "tiles_horizontally": True},
    }
    with open(os.path.join(ROOT, "assets", "tiles.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("drew", len(tiles), "tiles")


if __name__ == "__main__":
    main()
