"""Petits outils pour dessiner les figures du guide en SVG (sans dépendance externe)."""
from html import escape

INK = "#1e293b"
MUTED = "#64748b"

PALETTE = {
    "main": ("#dbeafe", "#2563eb"),
    "sched": ("#ede9fe", "#7c3aed"),
    "agent": ("#d1fae5", "#059669"),
    "infra": ("#e2e8f0", "#475569"),
    "user": ("#fef3c7", "#d97706"),
    "ok": ("#dcfce7", "#16a34a"),
    "bad": ("#fee2e2", "#dc2626"),
    "note": ("#fffbeb", "#d97706"),
    "white": ("#ffffff", "#94a3b8"),
    "ws": ("#dbeafe", "#2563eb"),
    "api": ("#dcfce7", "#16a34a"),
    "impl": ("#bbf7d0", "#15803d"),
    "dal": ("#fef3c7", "#b45309"),
    "inf": ("#f1f5f9", "#475569"),
    "cli": ("#fae8ff", "#a21caf"),
}

COLORS = {"ink": INK, "green": "#16a34a", "red": "#dc2626", "blue": "#2563eb", "violet": "#7c3aed",
          "orange": "#d97706", "gray": "#64748b", "teal": "#059669"}


def defs():
    markers = "".join(
        f'<marker id="ar-{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
        f'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{color}"/></marker>'
        for name, color in COLORS.items())
    return f"<defs>{markers}</defs>"


def svg(width, height, body, max_width=None):
    style = f' style="max-width:{max_width}px"' if max_width else ""
    return (f'<svg class="fig" viewBox="0 0 {width} {height}" width="100%"{style} '
            f'xmlns="http://www.w3.org/2000/svg" font-family="Segoe UI, Arial, sans-serif">'
            f'{defs()}{body}</svg>')


def text(x, y, s, size=12, anchor="middle", color=INK, weight="normal", italic=False):
    st = ' font-style="italic"' if italic else ""
    return (f'<text x="{x}" y="{y}" font-size="{size}" text-anchor="{anchor}" fill="{color}" '
            f'font-weight="{weight}"{st}>{escape(s)}</text>')


def box(x, y, w, h, lines, kind="white", size=12.5, rx=8, bold_first=True, dash=False, align="middle"):
    fill, stroke = PALETTE[kind]
    d = ' stroke-dasharray="5 4"' if dash else ""
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" '
           f'stroke-width="1.6"{d}/>']
    if isinstance(lines, str):
        lines = [lines]
    lh = size * 1.3
    total = lh * len(lines)
    y0 = y + h / 2 - total / 2 + size * 0.95
    tx = x + w / 2 if align == "middle" else x + 10
    for i, line in enumerate(lines):
        weight = "bold" if (i == 0 and bold_first) else "normal"
        color = INK if i == 0 else "#334155"
        sz = size if i == 0 else size - 1
        out.append(text(tx, y0 + i * lh, line, sz, align if align == "middle" else "start", color, weight))
    return "".join(out)


def arrow(x1, y1, x2, y2, color="ink", label=None, lx=None, ly=None, dash=False, width=1.6, size=11.5,
          both=False, label_color=None, anchor="middle"):
    c = COLORS[color]
    d = ' stroke-dasharray="6 4"' if dash else ""
    start = f' marker-start="url(#ar-{color})"' if both else ""
    out = (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{c}" stroke-width="{width}"{d} '
           f'marker-end="url(#ar-{color})"{start}/>')
    if label:
        if lx is None:
            lx = (x1 + x2) / 2
        if ly is None:
            ly = (y1 + y2) / 2 - 6
        out += text(lx, ly, label, size, anchor, label_color or c)
    return out


def path(d, color="ink", dash=False, width=1.6, end=True):
    c = COLORS[color]
    da = ' stroke-dasharray="6 4"' if dash else ""
    me = f' marker-end="url(#ar-{color})"' if end else ""
    return f'<path d="{d}" fill="none" stroke="{c}" stroke-width="{width}"{da}{me}/>'


def cross(x, y, r=9):
    c = COLORS["red"]
    return (f'<circle cx="{x}" cy="{y}" r="{r + 3}" fill="#fff" stroke="{c}" stroke-width="1.6"/>'
            f'<line x1="{x - r * .6}" y1="{y - r * .6}" x2="{x + r * .6}" y2="{y + r * .6}" stroke="{c}" stroke-width="2.4"/>'
            f'<line x1="{x - r * .6}" y1="{y + r * .6}" x2="{x + r * .6}" y2="{y - r * .6}" stroke="{c}" stroke-width="2.4"/>')


def check(x, y, r=9):
    c = COLORS["green"]
    return (f'<circle cx="{x}" cy="{y}" r="{r + 3}" fill="#fff" stroke="{c}" stroke-width="1.6"/>'
            f'<path d="M{x - r * .6},{y} L{x - r * .1},{y + r * .5} L{x + r * .7},{y - r * .5}" fill="none" '
            f'stroke="{c}" stroke-width="2.4"/>')


def group(x, y, w, h, title, kind="white", size=13):
    fill, stroke = PALETTE[kind]
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" fill-opacity="0.45" '
            f'stroke="{stroke}" stroke-width="1.8" stroke-dasharray="7 4"/>'
            + text(x + 12, y + 20, title, size, "start", stroke, "bold"))


def sequence(participants, steps, width=900, col_label=12.5):
    """Diagramme de séquence. participants : [(clé, [lignes], genre)] ; steps : liste de tuples
    ("msg", de, vers, libellé[, "reply"|"bad"|"ok"]) | ("note", de, vers, texte) | ("gap", hauteur)."""
    n = len(participants)
    margin = 75
    step_x = (width - 2 * margin) / (n - 1)
    xs = {key: margin + i * step_x for i, (key, _, _) in enumerate(participants)}
    head_h = 46
    body = []
    y = head_h + 34
    rows = []
    for st in steps:
        kind = st[0]
        if kind == "msg":
            _, a, b, label, *style = st
            style = style[0] if style else ""
            if a == b:
                x = xs[a]
                rows.append(path(f"M{x},{y - 8} h42 v18 h-40", "violet" if style == "" else
                                 {"bad": "red", "ok": "green", "reply": "gray"}[style]))
                rows.append(text(x + 50, y + 5, label, 11.5, "start", INK))
                y += 40
            else:
                color = {"": "ink", "reply": "gray", "bad": "red", "ok": "green", "event": "orange"}[style]
                x1, x2 = xs[a], xs[b]
                pad = 4 if x2 > x1 else -4
                rows.append(arrow(x1 + pad, y, x2 - pad, y, color, dash=(style in ("reply", "event"))))
                anchor_x = (x1 + x2) / 2
                rows.append(f'<rect x="{anchor_x - len(label) * 3.1 - 4}" y="{y - 19}" width="{len(label) * 6.2 + 8}" '
                            f'height="15" fill="#ffffff" fill-opacity="0.85"/>')
                rows.append(text(anchor_x, y - 7, label, 11.5, "middle",
                                 COLORS["red"] if style == "bad" else INK))
                y += 34
        elif kind == "note":
            _, a, b, label = st
            x1, x2 = min(xs[a], xs[b]) - 62, max(xs[a], xs[b]) + 62
            lines = label if isinstance(label, list) else [label]
            h = 10 + 15 * len(lines)
            fill, stroke = PALETTE["note"]
            rows.append(f'<rect x="{x1}" y="{y - 14}" width="{x2 - x1}" height="{h}" rx="5" fill="{fill}" '
                        f'stroke="{stroke}" stroke-width="1.2"/>')
            for i, line in enumerate(lines):
                rows.append(text((x1 + x2) / 2, y + 1 + i * 15, line, 11.5, "middle", "#78350f", italic=True))
            y += h + 18
        elif kind == "gap":
            y += st[1]
    end = y
    for key, lines, kind in participants:
        x = xs[key]
        body.append(f'<line x1="{x}" y1="{head_h + 10}" x2="{x}" y2="{end}" stroke="#94a3b8" '
                    f'stroke-width="1.2" stroke-dasharray="4 4"/>')
    for key, lines, kind in participants:
        x = xs[key]
        w = min(step_x - 12, 150)
        body.append(box(x - w / 2, 8, w, head_h, lines, kind, size=col_label))
    return svg(width, end + 12, "".join(body + rows))
