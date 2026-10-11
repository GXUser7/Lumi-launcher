"""Draws Lumi Launcher's icon — a pixel lantern glowing on a night-blue tile — at every size it is used.

    python scripts/make-icon.py        (needs Pillow: pip install pillow)

Writes the window's icons (app/src/main/resources/icon/lumi-<size>.png), the .exe's and the
shortcut's icon (scripts/lumi.ico), the installer's small wizard images (installer/wizard-small-*.bmp)
and a vector copy for the site and the README (scripts/lumi.svg).

Every size is drawn on its own: the tile and its glow are smooth, but the lantern is laid on whole
screen pixels — scaled down from one big picture, a 16 px lantern would be a blur.
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The lantern on a 14×14 grid: c handle, I frame, i frame highlight, g glass edge, y light, w its core.
LANTERN = [
    ".....cccc.....",
    "....c....c....",
    "....c....c....",
    "....IIIIII....",
    "...IiiiiiiI...",
    "...IIIIIIII...",
    "...IgyyyygI...",
    "...IyywwyyI...",
    "...IywwwwyI...",
    "...IywwwwyI...",
    "...IyywwyyI...",
    "...IgyyyygI...",
    "...IIIIIIII...",
    "....iiiiii....",
]
COLORS = {'c': '#6A7389', 'I': '#474F66', 'i': '#6B7490', 'g': '#F2A23A', 'y': '#FFC85A', 'w': '#FFF2B8'}
TOP, BOTTOM = (0x22, 0x30, 0x5A), (0x13, 0x1A, 0x33)
GLOW = [(0.0, (0xFF, 0xC8, 0x5A), 0.75), (0.45, (0xFF, 0x9F, 0x43), 0.22), (1.0, (0xFF, 0x9F, 0x43), 0.0)]

SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]
WIZARD = [(55, 58), (69, 72), (83, 87), (110, 116), (138, 145)]


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


def glow_at(t):
    """The glow's colour and opacity at [t] of its radius, as the SVG radial gradient had them."""
    for (t0, c0, a0), (t1, c1, a1) in zip(GLOW, GLOW[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0)
            return tuple(c0[i] + (c1[i] - c0[i]) * k for i in range(3)), a0 + (a1 - a0) * k
    return GLOW[-1][1], 0.0


def tile(size):
    """The rounded night tile with the lantern's glow on it, smooth: drawn 4× larger and scaled down."""
    big = size * 4
    s = big / 256
    pixels = []
    cx, cy, radius = 128 * s, (8 + 240 * 0.56) * s, 240 * 0.5 * s
    for y in range(big):
        t = (y - 8 * s) / (240 * s)
        base = tuple(TOP[i] + (BOTTOM[i] - TOP[i]) * min(max(t, 0), 1) for i in range(3))
        for x in range(big):
            c, a = glow_at(min(math.hypot(x - cx, y - cy) / radius, 1.0))
            pixels.append(tuple(round(base[i] * (1 - a) + c[i] * a) for i in range(3)) + (255,))
    image = Image.new('RGBA', (big, big))
    image.putdata(pixels)
    mask = Image.new('L', (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((8 * s, 8 * s, 248 * s - 1, 248 * s - 1), radius=72 * s, fill=255)
    # The colour stays under the transparent corners, and the box filter only averages: no fringe at the edge.
    image.putalpha(mask)
    return image.reduce(4)


def icon(size):
    image = tile(size)
    # 13 px a lantern pixel at 256, as drawn; never less than one screen pixel.
    unit = max(1, round(size * 13 / 256))
    left = round((size - 14 * unit) / 2)
    top = round((size - 14 * unit) / 2 - size * 3 / 256)
    draw = ImageDraw.Draw(image)
    for r, row in enumerate(LANTERN):
        for c, ch in enumerate(row):
            if ch != '.':
                x, y = left + c * unit, top + r * unit
                draw.rectangle((x, y, x + unit - 1, y + unit - 1), fill=rgb(COLORS[ch]) + (255,))
    return image


def svg():
    """The same icon as a vector, for the site and the README: smooth tile, lantern on sharp pixels."""
    stops = ''.join(f'<stop offset="{t}" stop-color="#{c[0]:02X}{c[1]:02X}{c[2]:02X}" stop-opacity="{a}"/>' for t, c, a in GLOW)
    paths = {}
    for r, row in enumerate(LANTERN):
        for c, ch in enumerate(row):
            if ch != '.':
                paths.setdefault(ch, []).append(f'M{37 + c * 13} {34 + r * 13}h13v13h-13z')
    lantern = ''.join(f'<path fill="{COLORS[ch]}" d="{"".join(d)}"/>' for ch, d in paths.items())
    top, bottom = ('#%02X%02X%02X' % c for c in (TOP, BOTTOM))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">'
        f'<defs><linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/>'
        f'<stop offset="1" stop-color="{bottom}"/></linearGradient>'
        f'<radialGradient id="g" cx="0.5" cy="0.56" r="0.5">{stops}</radialGradient></defs>'
        '<rect x="8" y="8" width="240" height="240" rx="72" fill="url(#t)"/>'
        '<rect x="8" y="8" width="240" height="240" rx="72" fill="url(#g)"/>'
        f'<g shape-rendering="crispEdges">{lantern}</g></svg>\n'
    )


def main():
    icons = {size: icon(size) for size in SIZES}
    res = os.path.join(ROOT, 'app', 'src', 'main', 'resources', 'icon')
    os.makedirs(res, exist_ok=True)
    for size, image in icons.items():
        image.save(os.path.join(res, f'lumi-{size}.png'))
    biggest = icons[SIZES[-1]]
    biggest.save(os.path.join(ROOT, 'scripts', 'lumi.ico'), sizes=[(s, s) for s in SIZES],
                 append_images=[icons[s] for s in SIZES[:-1]])
    # The wizard's corner picture: the icon on the page's white, a little inside the frame.
    for w, h in WIZARD:
        side = round(min(w, h) * 0.94)
        page = Image.new('RGB', (w, h), (255, 255, 255))
        image = icon(side)
        page.paste(image, ((w - side) // 2, (h - side) // 2), image)
        page.save(os.path.join(ROOT, 'installer', f'wizard-small-{w}.bmp'))
    with open(os.path.join(ROOT, 'scripts', 'lumi.svg'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(svg())
    print('icons written')


if __name__ == '__main__':
    main()
