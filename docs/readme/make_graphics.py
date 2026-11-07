"""Draws the README's graphics in the app's own look (the top of frontend/src/styles.css says it in full).

GitHub shows an SVG as an image, so it cannot load a web font: every word is drawn as IBM Plex Sans outlines. To change
a word, change it here and run this again from the repository root, after `npm install` in frontend/ and
`pip install fonttools brotli`:

    python docs/readme/make_graphics.py
"""
import base64
import math
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
FONT = ROOT / "frontend/node_modules/@fontsource-variable/ibm-plex-sans/files/ibm-plex-sans-latin-wght-normal.woff2"
LOGO = ROOT / "frontend/public/eye-logo.png"

# The app's colours (styles.css, and CHAIN_COLOURS in pages/supplyChain.ts).
NIGHT, LIFT = "#05070d", "#0b1220"
TEXT, SOFT, MUTED = "#e8edf5", "#c2cbd8", "#8f9bb0"
TEAL, LIME, AMBER, HIGH, MEDIUM = "#2dd4bf", "#c4f36a", "#f5b73b", "#ff7272", "#ff8a3d"
TICK = "#cbd5e1"
GREEN, BLUE, VIOLET = "#62cf77", "#4ba3f7", "#8a59da"

_fonts = {}


def font(weight):
    if weight not in _fonts:
        instance = instantiateVariableFont(TTFont(FONT), {"wght": weight})
        _fonts[weight] = (instance, instance.getGlyphSet(), instance.getBestCmap(), instance["head"].unitsPerEm)
    return _fonts[weight]


def width(words, size, weight=400):
    instance, glyphs, cmap, em = font(weight)
    return sum(glyphs[cmap[ord(c)]].width for c in words) * size / em


def text(words, x, y, size, fill=TEXT, weight=400, anchor="start"):
    """Words as one path. y is the baseline."""
    _, glyphs, cmap, em = font(weight)
    scale = size / em
    if anchor != "start":
        x -= width(words, size, weight) / (2 if anchor == "middle" else 1)
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}".rstrip("0").rstrip("."))
    for c in words:
        glyph = glyphs[cmap[ord(c)]]
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, x, y)))
        x += glyph.width * scale
    return f'<path fill="{fill}" d="{pen.getCommands()}"/>'


def ticks(x, y, w, h, arm=12, colour=TICK, opacity=0.55):
    """The registration ticks on a panel's four corners."""
    corners = [(x, y, 1, 1), (x + w, y, -1, 1), (x, y + h, 1, -1), (x + w, y + h, -1, -1)]
    lines = "".join(
        f'<path d="M{cx + sx * arm} {cy}H{cx}V{cy + sy * arm}"/>' for cx, cy, sx, sy in corners
    )
    return f'<g fill="none" stroke="{colour}" stroke-opacity="{opacity}" stroke-width="1.5">{lines}</g>'


def sheet(w, h):
    """The night-blue sheet with its grid of small crosses."""
    crosses = "".join(
        f"M{x - 4} {y}h8M{x} {y - 4}v8" for x in range(40, w, 80) for y in range(40, h, 80)
    )
    return (
        f'<rect width="{w}" height="{h}" fill="{NIGHT}"/>'
        f'<rect width="{w}" height="{h}" fill="url(#lift)"/>'
        f'<path d="{crosses}" stroke="#fff" stroke-opacity=".07" stroke-width="1"/>'
        f'<rect x=".5" y=".5" width="{w - 1}" height="{h - 1}" fill="none" stroke="#fff" stroke-opacity=".13"/>'
        + ticks(14, 14, w - 28, h - 28, arm=18)
    )


def eye(x, y, side):
    """The pixel-art logo, cell by cell, so it stays crisp at any size."""
    image = Image.open(LOGO).convert("RGBA")
    cell = 40  # The logo is 13 cells of 40 pixels.
    n = image.width // cell
    unit = side / n
    rects = []
    for row in range(n):
        for col in range(n):
            r, g, b, a = image.getpixel((col * cell + cell // 2, row * cell + cell // 2))
            if a > 128:
                rects.append(
                    f'<rect x="{x + col * unit:.2f}" y="{y + row * unit:.2f}" '
                    f'width="{unit + 0.3:.2f}" height="{unit + 0.3:.2f}" fill="#{r:02x}{g:02x}{b:02x}"/>'
                )
    return "<g>" + "".join(rects) + "</g>"


def chamfered(x, y, side, cut):
    """A square with its top right corner cut: the shape that means a company."""
    return f"M{x} {y}H{x + side - cut}L{x + side} {y + cut}V{y + side}H{x}Z"


# How much of a tile a mark on a clear background fills, as in drawTile in frontend/src/graph/render.ts.
MARK_ROOM = 0.64


def logo(key, x, y, side, shape, room=1):
    """A company's logo on its own background, cut to its tile's shape, as the graph draws it. room is how much of the
    tile it fills: all of it on a background of its own, 0.64 for a mark on a clear background (MARK_ROOM)."""
    data = base64.b64encode((OUT / "logos" / f"{key}.png").read_bytes()).decode()
    return (
        f'<clipPath id="tile-{key}"><path d="{shape}"/></clipPath><path d="{shape}" fill="{LIFT}"/>'
        f'<image clip-path="url(#tile-{key})" x="{x + side * (1 - room) / 2}" y="{y + side * (1 - room) / 2}" '
        f'width="{side * room}" height="{side * room}" href="data:image/png;base64,{data}"/>'
    )


def supplier(cx, cy, key, name, shared=None, room=1):
    side = 60
    x, y = cx - side / 2, cy - side / 2
    out = (
        logo(key, x, y, side, f"M{x} {y}h{side}v{side}h-{side}Z", room)
        + f'<rect x="{x}" y="{y}" width="{side}" height="{side}" fill="none" stroke="#fff" stroke-opacity=".22"/>'
        + text(name, cx, cy + side / 2 + 28, 18, SOFT, 500, "middle")
    )
    if shared:
        out += (
            f'<rect x="{x - 10}" y="{y - 10}" width="24" height="24" fill="{AMBER}"/>'
            + text(str(shared), x + 2, y + 8, 18, NIGHT, 700, "middle")
        )
    return out


def holding(cx, cy, key, name, colour):
    ring, side = 76, 60
    return (
        f'<path d="{chamfered(cx - ring / 2, cy - ring / 2, ring, 14)}" fill="none" stroke="{colour}" stroke-width="5" opacity=".55" filter="url(#bloom)"/>'
        f'<path d="{chamfered(cx - ring / 2, cy - ring / 2, ring, 14)}" fill="none" stroke="{colour}" stroke-width="2"/>'
        + logo(key, cx - side / 2, cy - side / 2, side, chamfered(cx - side / 2, cy - side / 2, side, 11))
        + text(name, cx, cy + ring / 2 + 28, 18, TEXT, 600, "middle")
    )


def mix(a, b, amount):
    channels = [int(a[i:i + 2], 16) * (1 - amount) + int(b[i:i + 2], 16) * amount for i in (1, 3, 5)]
    return "#" + "".join(f"{round(c):02x}" for c in channels)


# The arrow's measures, from frontend/src/graph/render.ts, and the graph's pace: 60 units a second.
SEGMENT_PITCH, SEGMENT_LENGTH, TRACK_WIDTH = 10, 5, 3
RAIL_FROM, RAIL_TO, GLOW_WIDTH, CHEVRON = 3.4, 1.6, 16, 10
WAVE_GAP, WAVE_TRAIL, ARRIVAL, SPEED = 130, 52, 34, 60
PERIOD = round(WAVE_GAP / SPEED, 3)
_arrows = 0


def wave(colour):
    """A wave of light with its fading trail, repeating along an arrow. All arrows of a colour share one."""
    head = mix(colour, "#ffffff", 0.5)
    start = 1 - WAVE_TRAIL / WAVE_GAP
    return (
        f'<linearGradient id="wave{colour[1:]}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="{WAVE_GAP}" y2="0" spreadMethod="repeat">'
        f'<stop offset="0" stop-color="{head}" stop-opacity="0"/><stop offset="{start:.2f}" stop-color="{head}" stop-opacity="0"/>'
        f'<stop offset="{start + (1 - start) * 0.6:.2f}" stop-color="{head}" stop-opacity=".3"/><stop offset="1" stop-color="{head}"/>'
        f'<animate attributeName="x1" from="0" to="{WAVE_GAP}" dur="{PERIOD}s" repeatCount="indefinite"/>'
        f'<animate attributeName="x2" from="{WAVE_GAP}" to="{2 * WAVE_GAP}" dur="{PERIOD}s" repeatCount="indefinite"/>'
        "</linearGradient>"
    )


def arrow(start, end, colour, stalled=False):
    """The graph's arrow, layer for layer: its glow, its tapering rail, its conveyor of still segments that light up as
    each wave passes, and its chevron, flashing as a wave arrives. Stalled, as on a disruption, every segment pulses."""
    global _arrows
    _arrows += 1
    key = f"a{_arrows}"
    (x1, y1), (x2, y2) = start, end
    length = ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5
    angle = math.degrees(math.atan2(y2 - y1, x2 - x1))
    dim = mix(colour, NIGHT, 0.45 if stalled else 0.6)
    bright = mix(colour, "#ffffff", 0.3)
    rail = (
        f'<linearGradient id="{key}r" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="{length:.1f}" y2="0">'
        f'<stop offset="0" stop-color="{dim}" stop-opacity=".16"/><stop offset="1" stop-color="{colour}" stop-opacity=".38"/></linearGradient>'
        f'<linearGradient id="{key}g" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="{length:.1f}" y2="0">'
        f'<stop offset="0" stop-color="{colour}" stop-opacity="0"/><stop offset=".3" stop-color="{colour}" stop-opacity=".55"/>'
        f'<stop offset=".85" stop-color="{colour}"/><stop offset="1" stop-color="{colour}" stop-opacity="0"/></linearGradient>'
    )
    breathe = (
        '<animate attributeName="opacity" values=".35;1;.35" dur="1.3s" repeatCount="indefinite"/>' if stalled else ""
    )
    a, b = RAIL_FROM / 2, RAIL_TO / 2
    dash = f'stroke-dasharray="{SEGMENT_LENGTH} {SEGMENT_PITCH - SEGMENT_LENGTH}" stroke-dashoffset="-{SEGMENT_PITCH / 2}"'
    run = length - 4
    body = (
        f'<g transform="translate({x1} {y1}) rotate({angle:.2f})">'
        f'<rect y="-{GLOW_WIDTH / 2}" width="{length:.1f}" height="{GLOW_WIDTH}" fill="url(#{key}g)" opacity=".3" filter="url(#soft)">{breathe}</rect>'
        f'<path d="M0 {-a}L{length:.1f} {-b}V{b}L0 {a}Z" fill="url(#{key}r)"/>'
    )
    if stalled:
        body += (
            f'<line x2="{run:.1f}" stroke="{mix(colour, "#ffffff", 0.2)}" stroke-width="{TRACK_WIDTH}" {dash}>{breathe}</line>'
        )
    else:
        body += (
            f'<line x2="{run:.1f}" stroke="{mix(dim, colour, 0.5)}" stroke-opacity=".5" stroke-width="{TRACK_WIDTH}" {dash}/>'
            f'<g class="pulse"><rect y="-6" width="{run:.1f}" height="12" fill="url(#wave{colour[1:]})" opacity=".55" filter="url(#soft)"/>'
            f'<line x2="{run:.1f}" stroke="url(#wave{colour[1:]})" stroke-width="{TRACK_WIDTH}" {dash}/></g>'
        )
    # The chevron, over everything: a dark edge, a flash of light as a wave arrives, then the chevron itself.
    tip = length
    back, side = tip - CHEVRON, CHEVRON * 0.62
    shape = f"M{back:.1f} {-side:.1f}L{tip:.1f} 0L{back:.1f} {side:.1f}"
    arrives = (length % WAVE_GAP) / SPEED - PERIOD
    body += f'<path d="{shape}" fill="none" stroke="{NIGHT}" stroke-opacity=".75" stroke-width="5" stroke-linecap="square"/>'
    if not stalled:
        body += (
            f'<path class="pulse" d="{shape}" fill="none" stroke="{bright}" stroke-width="9" stroke-linecap="square" opacity="0" filter="url(#soft)">'
            f'<animate attributeName="opacity" values="1;0;0" keyTimes="0;{ARRIVAL / WAVE_GAP:.2f};1" dur="{PERIOD}s" begin="{arrives:.2f}s" repeatCount="indefinite"/></path>'
        )
    body += f'<path d="{shape}" fill="none" stroke="{bright}" stroke-width="2.4" stroke-linecap="square"/></g>'
    # The small square port where it leaves its tile.
    body += f'<rect x="{x1 - 3}" y="{y1 - 3}" width="6" height="6" fill="{bright}"/>'
    return rail, body


def arrows(*wanted):
    """Several arrows at once: their gradients, for <defs>, and their drawing."""
    drawn = [arrow(*one) for one in wanted]
    return "".join(d for d, _ in drawn), "".join(b for _, b in drawn)


def svg(w, h, body, glow=""):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">'
        f'<defs><filter id="soft" x="-20%" y="-200%" width="140%" height="500%"><feGaussianBlur stdDeviation="4"/></filter>'
        f'<filter id="bloom" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="14"/></filter>{glow}</defs>'
        "<style>@media (prefers-reduced-motion: reduce){.pulse{display:none}}</style>"
        f"{body}</svg>"
    )


def hero():
    w, h = 1280, 480
    defs = (
        '<radialGradient id="lift" cx=".72" cy=".5" r=".6">'
        '<stop offset="0" stop-color="#0f3a3e" stop-opacity=".55"/>'
        f'<stop offset="1" stop-color="{NIGHT}" stop-opacity="0"/></radialGradient>'
        + wave(GREEN) + wave(BLUE) + wave(VIOLET)
    )
    body = sheet(w, h)
    body += eye(70, 128, 84)
    body += text("TrueSight", 172, 196, 84, TEXT, 600)
    body += text("Portfolio Supply-Chain Risk,", 72, 278, 34, SOFT, 400)
    body += text("Traced to the Source", 72, 324, 34, LIME, 500)

    # Real links, from the reports of NVIDIA, AMD and Qualcomm (docs/examples: relationships.json, gemini-nvidia.json and
    # gemini-amd.json).
    sx, hx = 790, 1130
    ys = [100, 240, 380]
    suppliers = {"Micron": ys[0], "Samsung": ys[1], "GF": ys[2]}
    holdings = {"NVDA": (ys[0], GREEN), "AMD": (ys[1], BLUE), "QCOM": (ys[2], VIOLET)}
    links = [
        ("Micron", "NVDA"), ("Samsung", "NVDA"),
        ("Samsung", "AMD"), ("GF", "AMD"),
        ("Samsung", "QCOM"), ("GF", "QCOM"),
    ]
    # Where each link leaves its supplier and reaches its holding, in the order of links above. A link between companies
    # on the same row runs through both tiles' centres, so it is truly flat.
    spread = {"NVDA": [0, 18], "AMD": [0, 18], "QCOM": [-18, 0]}
    leaving = {"Micron": [0], "Samsung": [-16, 0, 16], "GF": [-14, 0]}
    seen, left = {}, {}
    wanted = []
    for s, c in links:
        y, colour = holdings[c]
        into = spread[c][seen.get(c, 0)]
        out = leaving[s][left.get(s, 0)]
        seen[c] = seen.get(c, 0) + 1
        left[s] = left.get(s, 0) + 1
        wanted.append(((sx + 34, suppliers[s] + out), (hx - 46, y + into), colour))
    gradients, drawn = arrows(*wanted)
    body += supplier(sx, ys[0], "micron", "Micron")
    body += supplier(sx, ys[1], "samsung", "Samsung", shared=3)
    body += supplier(sx, ys[2], "globalfoundries", "GlobalFoundries", shared=2, room=MARK_ROOM)
    body += holding(hx, ys[0], "nvidia", "NVIDIA", GREEN)
    body += holding(hx, ys[1], "amd", "AMD", BLUE)
    body += holding(hx, ys[2], "qualcomm", "Qualcomm", VIOLET)
    return svg(w, h, body + drawn, defs + gradients)


def step(x, y, w, h, title, lines, accent, lit=False):
    """One step of the flow: a panel with its ticks, its marker, its title and a few words."""
    out = ""
    if lit:
        out += (
            f'<rect x="{x - 6}" y="{y - 6}" width="{w + 12}" height="{h + 12}" fill="{accent}" opacity=".16" filter="url(#bloom)">'
            '<animate attributeName="opacity" values=".1;.26;.1" dur="4s" repeatCount="indefinite"/></rect>'
        )
    out += (
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{LIFT}" stroke="{accent if lit else "#fff"}" '
        f'stroke-opacity="{".6" if lit else ".13"}"/>'
        + ticks(x, y, w, h, arm=10, colour=accent if lit else TICK, opacity=0.9 if lit else 0.5)
        + f'<rect x="{x + 20}" y="{y + 26}" width="12" height="12" fill="{accent}"/>'
        + text(title, x + 42, y + 42, 30, TEXT, 600)
    )
    for n, line in enumerate(lines):
        assert width(line, 20) <= w - 36, line
        out += text(line, x + 20, y + 84 + n * 28, 20, SOFT, 400)
    return out


def pipeline():
    w, h = 1280, 610
    defs = (
        '<radialGradient id="lift" cx=".8" cy=".5" r=".75">'
        '<stop offset="0" stop-color="#0f3a3e" stop-opacity=".6"/>'
        f'<stop offset="1" stop-color="{NIGHT}" stop-opacity="0"/></radialGradient>'
        + wave(TEAL) + wave(LIME) + wave(MEDIUM) + wave(GREEN)
    )
    pw, ph, gap, x0 = 222, 180, 56, 40
    xs = [x0 + i * (pw + gap) for i in range(3)]
    reports, news = 58, 372
    box_x, box_y, box_w, box_h = xs[2] + pw + gap, 40, 0, 530
    box_w = w - 40 - box_x

    body = sheet(w, h)
    body += step(xs[0], reports, pw, ph, "Read", ["The latest 10-K or", "20-F, straight from", "SEC EDGAR."], TEAL)
    body += step(xs[1], reports, pw, ph, "Extract", ["Gemini names who", "supplies and buys,", "each with a quote."], TEAL)
    body += step(xs[2], reports, pw, ph, "Verify", ["A quote must match", "the report word for", "word, or it is cut."], LIME, lit=True)
    body += step(xs[0], news, pw, ph, "Watch", ["Google News, for", "the companies on", "your chain."], MEDIUM)
    body += step(xs[1], news, pw, ph, "Screen", ["Gemini splits real", "disruptions from", "the noise."], MEDIUM)
    body += step(xs[2], news, pw, ph, "Trace", ["Follows the links", "to the holdings an", "event reaches."], MEDIUM)

    # What fails the check leaves the line here, and never reaches the graph.
    vx = xs[2] + pw / 2
    body += text("Not in Report = Never Shown", vx, reports + ph + 88, 18, HIGH, 500, "middle")

    # The workspace, where both lines arrive.
    bx, by, bw, bh = box_x, box_y, box_w, box_h
    body += (
        f'<rect x="{bx - 8}" y="{by - 8}" width="{bw + 16}" height="{bh + 16}" fill="{TEAL}" opacity=".12" filter="url(#bloom)"/>'
        f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="{LIFT}" stroke="{TEAL}" stroke-opacity=".5"/>'
        + ticks(bx, by, bw, bh, arm=14, colour=TEAL, opacity=0.95)
        + f'<rect x="{bx + 24}" y="{by + 28}" width="12" height="12" fill="{TEAL}"/>'
        + text("Your Workspace", bx + 46, by + 44, 30, TEXT, 600)
    )
    # A real relationship: Tesla's 10-K, filed 29 January 2026 (docs/examples/relationships.json).
    ay = by + 118
    px, tx = bx + 64, bx + bw - 66
    body += supplier(px, ay, "panasonic", "Panasonic")
    body += holding(tx, ay, "tesla", "Tesla", GREEN)
    qx, qy = bx + 24, by + 196
    body += f'<rect x="{qx}" y="{qy}" width="3" height="136" fill="{TEAL}"/>'
    quote = ["Currently, we rely on suppliers such", "as Panasonic and Contemporary", "Amperex Technology Co. Limited", "(CATL) for these cells."]
    for n, line in enumerate(quote):
        assert width(line, 19) <= bw - 60, line
        body += text(line, qx + 18, qy + 22 + n * 26, 19, TEXT, 400)
    body += text("From Tesla's 10-K, Filed Jan 29, 2026", qx + 18, qy + 130, 16, MUTED, 400)
    # An alert, in the disruption's colour: the beacon sends its slow signal.
    dy = by + 422
    dx = bx + 52
    diamond = f"M{dx} {dy - 22}L{dx + 22} {dy}L{dx} {dy + 22}L{dx - 22} {dy}Z"
    body += (
        f'<rect x="{bx + 24}" y="{dy - 54}" width="{bw - 48}" height="1" fill="#fff" fill-opacity=".13"/>'
        f'<path d="{diamond}" fill="{HIGH}" opacity=".5" filter="url(#bloom)">'
        '<animate attributeName="opacity" values=".2;.8;.2" dur="1.3s" repeatCount="indefinite"/></path>'
        f'<path d="{diamond}" fill="{mix(HIGH, NIGHT, 0.6)}" stroke="{HIGH}" stroke-width="2"/>'
        f'<path d="M{dx} {dy - 10}V{dy + 3}M{dx} {dy + 8}V{dy + 10}" stroke="{TEXT}" stroke-width="3"/>'
        + text("An Alert on the Holding", dx + 38, dy + 8, 22, TEXT, 600)
    )
    # The one severity set, as everywhere in the app.
    severities = (("High", HIGH), ("Medium", MEDIUM), ("Low", TICK))
    used = sum(20 + width(name, 18, 500) for name, _ in severities) + 2 * 32
    sx = bx + (bw - used) / 2
    for name, colour in severities:
        body += f'<rect x="{sx:.1f}" y="{dy + 60}" width="12" height="12" fill="{colour}"/>'
        body += text(name, sx + 20, dy + 72, 18, SOFT, 500)
        sx += 20 + width(name, 18, 500) + 32

    gradients, drawn = arrows(
        ((xs[0] + pw, reports + ph / 2), (xs[1] - 3, reports + ph / 2), TEAL),
        ((xs[1] + pw, reports + ph / 2), (xs[2] - 3, reports + ph / 2), TEAL),
        ((xs[2] + pw, reports + ph / 2), (bx - 3, reports + ph / 2), LIME),
        ((vx, reports + ph), (vx, reports + ph + 60), HIGH, True),
        ((xs[0] + pw, news + ph / 2), (xs[1] - 3, news + ph / 2), MEDIUM),
        ((xs[1] + pw, news + ph / 2), (xs[2] - 3, news + ph / 2), MEDIUM),
        ((xs[2] + pw, news + ph / 2), (bx - 3, news + ph / 2), MEDIUM),
        ((px + 34, ay), (tx - 46, ay), GREEN),
    )
    return svg(w, h, body + drawn, defs + gradients)


def button(words, primary):
    h, pad = 88, 44
    size = 30
    w = round(width(words, size, 600)) + 2 * pad
    edge = TEAL if primary else "#fff"
    fill = "#0c3b3b" if primary else LIFT
    ink = "#d9fff8" if primary else TEXT
    opacity = "1" if primary else ".3"
    body = (
        f'<rect x="1" y="1" width="{w - 2}" height="{h - 2}" fill="{fill}" stroke="{edge}" stroke-opacity="{opacity}" stroke-width="2"/>'
        + text(words, w / 2, h / 2 + 11, size, ink, 600, "middle")
    )
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w // 2}" height="{h // 2}">{body}</svg>'


if __name__ == "__main__":
    (OUT / "hero.svg").write_text(hero(), encoding="utf-8")
    (OUT / "pipeline.svg").write_text(pipeline(), encoding="utf-8")
    (OUT / "button-live-site.svg").write_text(button("Open Live Site", True), encoding="utf-8")
    (OUT / "button-api-docs.svg").write_text(button("API Docs", False), encoding="utf-8")
