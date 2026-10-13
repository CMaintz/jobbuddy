# PROTOTYPE: check the SVG preview and the PDF place every glyph at the same spot on the same page.
import re, xml.etree.ElementTree as ET, pymupdf, sys

def svg_glyphs(path):
    out = []
    def walk(el, tx, ty, sy):
        t = el.get("transform", "")
        m = re.match(r"translate\(([-\d.e]+)(?:[ ,]+([-\d.e]+))?\)", t)
        if m: tx, ty = tx + float(m[1]), ty + sy * float(m[2] or 0)
        m = re.match(r"matrix\(([-\d.e ]+)\)", t)
        if m:
            a, b, c, d, e, f = map(float, m[1].split())
            tx, ty, sy = tx + e, ty + sy * f, sy * d
        if el.tag.endswith("use") and el.get("x") is not None:
            out.append((round(tx + float(el.get("x")), 1), round(ty, 1)))
        for ch in el: walk(ch, tx, ty, sy)
    walk(ET.parse(path).getroot(), 0.0, 0.0, 1.0)
    return out

doc = pymupdf.open(sys.argv[1])
ok = True
for i, page in enumerate(doc, start=1):
    pdf = [(round(c["origin"][0], 1), round(c["origin"][1], 1))
           for b in page.get_text("rawdict")["blocks"] for l in b.get("lines", []) for s in l["spans"] for c in s["chars"] if not c["c"].isspace()]
    svg = svg_glyphs(f"out/cv-{i}.svg")
    # Icons are <use> too (images), so compare the PDF text glyphs as a subset of SVG placements.
    svg_set = set(svg)
    missing = [g for g in pdf if g not in svg_set and not any(abs(g[0]-x) <= 0.2 and abs(g[1]-y) <= 0.2 for x, y in svg)]
    lines_pdf = sorted({y for _, y in pdf})
    print(f"page {i}: pdf text glyphs {len(pdf)}, svg placements {len(svg)}, pdf glyphs without an svg match (±0.2pt): {len(missing)}, text lines {len(lines_pdf)}")
    ok &= not missing
print("IDENTICAL LAYOUT" if ok else "LAYOUT DIFFERS")
