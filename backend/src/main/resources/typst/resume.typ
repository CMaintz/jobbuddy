// The resume builder's six layouts. The builder previews the SVG pages of this same compile,
// so what the user sees is the PDF. All content arrives as data (sys.inputs.data names a JSON
// file in the root), never as Typst source, so user text cannot inject markup. Colours, font
// and sizes were validated by the backend before they were written there.
//
// Sizes are given in the builder's CSS pixels: an A4 page is 794px wide, so 1px = 0.75pt.

#let doc = json(sys.inputs.at("data"))
#let theme = doc.theme
#let accent = rgb(theme.color)
#let ink = rgb(theme.text)
// A heading the client did not send prints as nothing rather than failing the compile.
#let heading-text(key) = doc.labels.at(key, default: "")
#let person = doc.person
#let styles = doc.sectionStyles

#let px(n) = n * 0.75pt
#let fs(n, k: 1) = px(n * theme.scale * k)
// Tailwind's gray-300..700 on the builder's text colour, as document-render.css mixes them.
#let shade(base, pct) = color.mix((base, pct * 1%), (white, (100 - pct) * 1%))
#let tone(pct) = shade(ink, pct)
// Detail text (subtitles, dates, descriptions) as a grey of the surrounding text colour, so a
// section given a colour of its own tints its greys too, as --rb-sec-text did in the builder.
#let muted(pct, ..args) = context text(fill: shade(text.fill, pct), ..args)
// Helpers take a fill as a colour, or as a muted grey step (an integer) of the surrounding text.
#let ink-text(fill, ..args) = if type(fill) == int { muted(fill, ..args) } else { text(fill: fill, ..args) }
#let present(v) = v != none and v != ""

#set document(title: person.name, author: person.name)
#set page(paper: if doc.page == "letter" { "us-letter" } else { "a4" }, margin: 0pt)
// The Noto face covers scripts the chosen family lacks (CJK names), so they render instead of
// missing-glyph boxes. Typst would curl straight quotes; ATS parsers read the text layer.
#set text(font: (theme.font, "Noto Sans SC"), size: fs(14), fill: ink, top-edge: "ascender",
  bottom-edge: "descender")
#set smartquote(enabled: false)
#set par(leading: (theme.line - 1.2) * 1em, spacing: (theme.line - 1.2) * 1em + px(4), justify: false)
#set list(marker: [•], indent: px(4), body-indent: px(6), spacing: (theme.line - 1.2) * 1em)
#set enum(indent: px(4), body-indent: px(6), spacing: (theme.line - 1.2) * 1em)

// Rich text (TipTap HTML parsed server-side into paragraphs and lists of styled runs).
#let run(r) = {
  if r.at("br", default: false) { return linebreak() }
  let body = r.at("text", default: "")
  if r.at("underline", default: false) { body = underline(body) }
  if r.at("strike", default: false) { body = strike(body) }
  let args = (:)
  if r.at("bold", default: false) { args.insert("weight", "bold") }
  if r.at("italic", default: false) { args.insert("style", "italic") }
  if r.at("color", default: none) != none { args.insert("fill", rgb(r.color)) }
  if r.at("size", default: none) != none { args.insert("size", fs(r.size)) }
  if r.at("font", default: none) != none { args.insert("font", (r.font, "Noto Sans SC")) }
  text(..args, body)
}
#let runs(rs) = for r in rs { run(r) }
#let alignments = (left: left, center: center, right: right)
#let paragraph(b) = {
  let a = b.at("align", default: none)
  if a == "justify" { par(justify: true, runs(b.runs)) }
  // Full width, or a block sized to its text (the summary blocks are) has nothing to align in.
  else if type(a) == str and a in alignments { block(width: 100%, align(alignments.at(a), par(runs(b.runs)))) }
  else { par(runs(b.runs)) }
}
#let rich(blocks) = for b in blocks {
  if b.kind == "list" {
    if b.ordered { enum(..b.items.map(runs)) } else { list(..b.items.map(runs)) }
  } else { paragraph(b) }
}

#let contact-icons = (
  email: "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z",
  phone: "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z",
  location: "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
)
#let icon(name, fill, size: 12) = box(baseline: 15%, image(bytes(
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='" + fill.to-hex()
    + "' d='" + contact-icons.at(name) + "'/></svg>"), format: "svg", width: px(size)))

// The builder's strength and link icons: Lucide's (ISC licence, https://lucide.dev/license)
// stroke shapes in a 24-unit box, keyed as the builder stores them. The brand marks come from
// lucide 0.400.0, the last release that shipped them; the rest from 1.51.0, as the builder uses.
#let line-icons = (
  zap: "<path d='M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z' />",
  star: "<path d='M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z' />",
  award: "<path d='m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526' /> <circle cx='12' cy='8' r='6' />",
  target: "<circle cx='12' cy='12' r='10' /> <circle cx='12' cy='12' r='6' /> <circle cx='12' cy='12' r='2' />",
  "trending-up": "<path d='M16 7h6v6' /> <path d='m22 7-8.5 8.5-5-5L2 17' />",
  lightbulb: "<path d='M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5' /> <path d='M9 18h6' /> <path d='M10 22h4' />",
  "check-circle": "<circle cx='12' cy='12' r='10' /> <path d='m16 9-5.5 5.5L8 12' />",
  shield: "<path d='M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z' />",
  users: "<path d='M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2' /> <path d='M16 3.128a4 4 0 0 1 0 7.744' /> <path d='M22 21v-2a4 4 0 0 0-3-3.87' /> <circle cx='9' cy='7' r='4' />",
  "message-circle": "<path d='M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719' />",
  code: "<path d='m16 18 6-6-6-6' /> <path d='m8 6-6 6 6 6' />",
  cpu: "<path d='M12 20v2' /> <path d='M12 2v2' /> <path d='M17 20v2' /> <path d='M17 2v2' /> <path d='M2 12h2' /> <path d='M2 17h2' /> <path d='M2 7h2' /> <path d='M20 12h2' /> <path d='M20 17h2' /> <path d='M20 7h2' /> <path d='M7 20v2' /> <path d='M7 2v2' /> <rect x='4' y='4' width='16' height='16' rx='2' /> <rect x='8' y='8' width='8' height='8' rx='1' />",
  database: "<ellipse cx='12' cy='5' rx='9' ry='3' /> <path d='M3 5V19A9 3 0 0 0 21 19V5' /> <path d='M3 12A9 3 0 0 0 21 12' />",
  layout: "<rect width='7' height='9' x='3' y='3' rx='1' /> <rect width='7' height='5' x='14' y='3' rx='1' /> <rect width='7' height='9' x='14' y='12' rx='1' /> <rect width='7' height='5' x='3' y='16' rx='1' />",
  smartphone: "<rect width='14' height='20' x='5' y='2' rx='2' ry='2' /> <path d='M12 18h.01' />",
  globe: "<circle cx='12' cy='12' r='10' /> <path d='M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20' /> <path d='M2 12h20' />",
  mail: "<path d='m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7' /> <rect x='2' y='4' width='20' height='16' rx='2' />",
  phone: "<path d='M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384' />",
  "map-pin": "<path d='M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0' /> <circle cx='12' cy='10' r='3' />",
  briefcase: "<path d='M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16' /> <rect width='20' height='14' x='2' y='6' rx='2' />",
  link: "<path d='M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71' /> <path d='M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71' />",
  "external-link": "<path d='M15 3h6v6' /> <path d='M10 14 21 3' /> <path d='M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6' />",
  terminal: "<path d='M12 19h8' /> <path d='m4 17 6-6-6-6' />",
  "pen-tool": "<path d='M15.707 21.293a1 1 0 0 1-1.414 0l-1.586-1.586a1 1 0 0 1 0-1.414l5.586-5.586a1 1 0 0 1 1.414 0l1.586 1.586a1 1 0 0 1 0 1.414z' /> <path d='m18 13-1.375-6.874a1 1 0 0 0-.746-.776L3.235 2.028a1 1 0 0 0-1.207 1.207L5.35 15.879a1 1 0 0 0 .776.746L13 18' /> <path d='m2.3 2.3 7.286 7.286' /> <circle cx='11' cy='11' r='2' />",
  "gamepad-2": "<line x1='6' x2='10' y1='11' y2='11' /> <line x1='8' x2='8' y1='9' y2='13' /> <line x1='15' x2='15.01' y1='12' y2='12' /> <line x1='18' x2='18.01' y1='10' y2='10' /> <path d='M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z' />",
  hash: "<line x1='4' x2='20' y1='9' y2='9' /> <line x1='4' x2='20' y1='15' y2='15' /> <line x1='10' x2='8' y1='3' y2='21' /> <line x1='16' x2='14' y1='3' y2='21' />",
  layers: "<path d='M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z' /> <path d='M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12' /> <path d='M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17' />",
  github: "<path d='M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4' /> <path d='M9 18c-4.51 2-5-2-7-2' />",
  linkedin: "<path d='M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z' /> <rect width='4' height='12' x='2' y='9' /> <circle cx='4' cy='4' r='2' />",
  twitter: "<path d='M22 4s-.7 2.1-2 3.4c1.6 10-9.4 17.3-18 11.6 2.2.1 4.4-.6 6-2C3 15.5.5 9.6 3 5c2.2 2.6 5.6 4.1 9 4-.9-4.2 4-6.6 7-3.8 1.1 0 3-1.2 3-1.2z' />",
  facebook: "<path d='M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z' />",
  instagram: "<rect width='20' height='20' x='2' y='2' rx='5' ry='5' /> <path d='M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z' /> <line x1='17.5' x2='17.51' y1='6.5' y2='6.5' />",
  youtube: "<path d='M2.5 17a24.12 24.12 0 0 1 0-10 2 2 0 0 1 1.4-1.4 49.56 49.56 0 0 1 16.2 0A2 2 0 0 1 21.5 7a24.12 24.12 0 0 1 0 10 2 2 0 0 1-1.4 1.4 49.55 49.55 0 0 1-16.2 0A2 2 0 0 1 2.5 17' /> <path d='m10 15 5-3-5-3z' />",
  twitch: "<path d='M21 2H3v16h5v4l4-4h5l4-4V2zm-10 9V7m5 4V7' />",
  gitlab: "<path d='m22 13.29-3.33-10a.42.42 0 0 0-.14-.18.38.38 0 0 0-.22-.11.39.39 0 0 0-.23.07.42.42 0 0 0-.14.18l-2.26 6.67H8.32L6.1 3.26a.42.42 0 0 0-.1-.18.38.38 0 0 0-.26-.08.39.39 0 0 0-.23.07.42.42 0 0 0-.14.18L2 13.29a.74.74 0 0 0 .27.83L12 21l9.69-6.88a.71.71 0 0 0 .31-.83Z' />",
  dribbble: "<circle cx='12' cy='12' r='10' /> <path d='M19.13 5.09C15.22 9.14 10 10.44 2.25 10.94' /> <path d='M21.75 12.84c-6.62-1.41-12.14 1-16.38 6.32' /> <path d='M8.56 2.75c4.37 6 6 9.42 8 17.72' />",
  figma: "<path d='M5 5.5A3.5 3.5 0 0 1 8.5 2H12v7H8.5A3.5 3.5 0 0 1 5 5.5z' /> <path d='M12 2h3.5a3.5 3.5 0 1 1 0 7H12V2z' /> <path d='M12 12.5a3.5 3.5 0 1 1 7 0 3.5 3.5 0 1 1-7 0z' /> <path d='M5 19.5A3.5 3.5 0 0 1 8.5 16H12v3.5a3.5 3.5 0 1 1-7 0z' /> <path d='M5 12.5A3.5 3.5 0 0 1 8.5 9H12v7H8.5A3.5 3.5 0 0 1 5 12.5z' />",
  codepen: "<polygon points='12 2 22 8.5 22 15.5 12 22 2 15.5 2 8.5 12 2' /> <line x1='12' x2='12' y1='22' y2='15.5' /> <polyline points='22 8.5 12 15.5 2 8.5' /> <polyline points='2 15.5 12 8.5 22 15.5' /> <line x1='12' x2='12' y1='2' y2='8.5' />",
  slack: "<rect width='3' height='8' x='13' y='2' rx='1.5' /> <path d='M19 8.5V10h1.5A1.5 1.5 0 1 0 19 8.5' /> <rect width='3' height='8' x='8' y='14' rx='1.5' /> <path d='M5 15.5V14H3.5A1.5 1.5 0 1 0 5 15.5' /> <rect width='8' height='3' x='14' y='13' rx='1.5' /> <path d='M15.5 19H14v1.5a1.5 1.5 0 1 0 1.5-1.5' /> <rect width='8' height='3' x='2' y='8' rx='1.5' /> <path d='M8.5 5H10V3.5A1.5 1.5 0 1 0 8.5 5' />",
)
// A key the set lacks (an old draft, crafted input) draws the default rather than nothing.
#let line-icon(key, fallback, fill, size: 11) = box(baseline: 15%, image(bytes(
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='" + fill.to-hex()
    + "' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'>"
    + line-icons.at(if type(key) == str and key in line-icons { key } else { fallback }) + "</svg>"),
  format: "svg", width: px(size)))
#let strength-icon(s, fill) = line-icon(s.icon, "star", fill)
#let social-icon(s, fill) = line-icon(s.icon, "link", fill)

#let contacts(fill, icon-fill, gap: 12, socials: true, k: 1) = {
  let parts = ()
  for key in ("email", "phone", "location") {
    if present(person.at(key)) {
      parts.push(box[#icon(key, icon-fill) #h(px(3)) #ink-text(fill, size: fs(12, k: k), person.at(key))])
    }
  }
  if socials {
    for s in doc.socials { parts.push(box[#social-icon(s, icon-fill) #h(px(3)) #ink-text(fill, size: fs(12, k: k), s.label)]) }
  }
  parts.join(h(px(gap)))
}

#let photo(size, extra: none) = {
  let p = doc.photo
  let edge = if p.size != none { px(p.size) } else { px(size) }
  let radius = if p.shape == "circle" { 50% } else if p.shape == "rounded" { px(12) } else { 0pt }
  if p.path != none {
    box(clip: true, radius: radius, width: edge, height: edge, stroke: extra,
      image(p.path, width: edge, height: edge, fit: "cover"))
  }
}

// Where a column layout puts the photo: the edge she picked, or the middle of the column.
#let photo-side = if doc.photo.placement == "left" { left } else if doc.photo.placement == "right" { right } else { center }

// Per-section overrides from the builder's layout tab: font, weight, style, colour, scale.
#let styled(id, body) = {
  let s = styles.at(id, default: (:))
  let args = (:)
  if s.at("font", default: none) != none { args.insert("font", (s.font, "Noto Sans SC")) }
  if s.at("bold", default: false) { args.insert("weight", "semibold") }
  if s.at("italic", default: false) { args.insert("style", "italic") }
  if s.at("color", default: none) != none { args.insert("fill", rgb(s.color)) }
  set text(..args)
  body
}
#let k-of(id) = styles.at(id, default: (:)).at("scale", default: 1)

// The builder orders sections inside each column by the user's column lists (a section in
// neither list keeps its place at the front) and hides the ones switched off; custom sections
// always come last.
#let arrange(ids) = {
  let rank(id) = if id == "custom" { 98 } else if id in doc.main { doc.main.position(x => x == id) + 1 } else if id in doc.side { doc.side.position(x => x == id) + 1 } else { 0 }
  ids.filter(id => id not in doc.hidden).sorted(key: rank)
}

#let gap = px(theme.sectionGap)
#let column(ids, render) = {
  let blocks = arrange(ids).map(id => render(id)).filter(b => b != none)
  stack(spacing: gap, ..blocks)
}

#let chips(skills, k: 1) = if skills.len() > 0 {
  v(px(4))
  skills.map(s => box(inset: (x: px(8), y: px(2)), radius: px(2), stroke: 0.75pt + tone(20), fill: rgb("#f4f5f7"),
    muted(62, size: fs(12, k: k), s))).join(h(px(4)))
}

#let dates(e) = if present(e.start) or present(e.end) {
  [#e.at("start", default: "") - #e.at("end", default: "")]
}

#let row(left, right) = grid(columns: (1fr, auto), column-gutter: px(8), left, right)

#let dots(level, on, off, size: 8, gap: 2) = box(baseline: 0%, stack(dir: ltr, spacing: px(gap),
  ..range(1, 6).map(d => circle(radius: px(size / 2), fill: if d <= level { on } else { off }))))

#let skill-list(k, name-fill, cat-fill, dot-on, dot-off, dot-size: 8) = stack(spacing: px(6),
  ..doc.skillGroups.map(g => {
    let rows = ()
    if g.category != none { rows.push(ink-text(cat-fill, size: fs(10, k: k), weight: "semibold", upper(g.category))) }
    for s in g.skills {
      rows.push(row(ink-text(name-fill, size: fs(12, k: k), s.name),
        if doc.showSkillLevel and s.level != none { dots(s.level, dot-on, dot-off, size: dot-size) }))
    }
    stack(spacing: px(6), ..rows)
  }))

#let skill-chips(k, fill, border, chip-fill: none, radius: 999pt, category: auto) = stack(spacing: px(8),
  ..doc.skillGroups.map(g => {
    let parts = ()
    let category-fill = if category == auto { border } else { category }
    if g.category != none { parts.push(ink-text(category-fill, size: fs(10, k: k), weight: "semibold", upper(g.category))) }
    parts.push(g.skills.map(s => box(inset: (x: px(8), y: px(2)), radius: radius, stroke: 0.75pt + border,
      fill: chip-fill, ink-text(fill, size: fs(12, k: k), s.name))).join(h(px(8))))
    stack(spacing: px(6), ..parts)
  }))

#let custom-sections(heading-fn) = for cs in doc.custom {
  let k = k-of("custom")
  block(above: gap, below: 0pt, styled("custom", {
    block(sticky: true, heading-fn(cs.heading))
    if cs.body.len() > 0 { muted(92, size: fs(14, k: k), rich(cs.body)) }
    if cs.items.len() > 0 { muted(92, size: fs(12, k: k), list(..cs.items)) }
  }))
}

#let page-fill(fill, width, height: 100%, dx: 0pt) = place(top + left, dx: dx, rect(width: width, height: height, fill: fill))

// classic: main column (65%) with a bordered header, tinted sidebar (35%) on the right.
#let classic() = {
  let h2(t, k: 1) = block(sticky: true, below: px(8), stroke: (bottom: 0.75pt + accent), inset: (bottom: px(3)), width: 100%,
    text(size: fs(12.8, k: k), weight: "bold", fill: accent, tracking: 0.08em, upper(t)))
  let side-h(t, k: 1) = block(sticky: true, below: px(6), text(size: fs(11.2, k: k), weight: "bold", fill: accent, tracking: 0.08em, upper(t)))
  let entry(e, k, title-key: "title") = block({
    row(stack(spacing: px(2), text(size: fs(14, k: k), weight: "semibold", e.title),
        muted(62, size: fs(12, k: k), e.subtitle + if present(e.at("location", default: none)) { " · " + e.location } else { "" })),
      muted(48, size: fs(12, k: k), dates(e)))
    if e.at("description", default: ()).len() > 0 { v(px(4)); muted(78, size: fs(12, k: k), rich(e.description)) }
    chips(e.skills, k: k)
  })
  let main(id) = {
    let k = k-of(id)
    if id == "summary" and doc.summary.len() > 0 { styled(id, { h2(heading-text("summary"), k: k); muted(92, size: fs(14, k: k), rich(doc.summary)) }) }
    else if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => entry(e, k))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => entry(e, k))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      row(text(size: fs(14, k: k), weight: "semibold", p.title), muted(48, size: fs(12, k: k), p.date))
      if p.description.len() > 0 { v(px(4)); muted(78, size: fs(12, k: k), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
  }
  let side(id) = {
    let k = k-of(id)
    if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { side-h(heading-text("skills"), k: k); skill-list(k, 92, 62, accent, rgb("#d1d5db")) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { side-h(heading-text("languages"), k: k); stack(spacing: px(4), ..doc.languages.map(l => row(muted(92, size: fs(12, k: k), l.name), muted(48, size: fs(12, k: k), l.proficiency)))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { side-h(heading-text("certifications"), k: k); stack(spacing: px(8), ..doc.certifications.map(c => stack(spacing: px(2),
      muted(92, size: fs(12, k: k), weight: "medium", c.name),
      ..if present(c.issuer) { (muted(48, size: fs(12, k: k), c.issuer),) } else { () },
      ..if present(c.date) { (muted(48, size: fs(12, k: k), c.date),) } else { () }))) }) }
    else if id == "strengths" and doc.strengths.len() > 0 { styled(id, { side-h(heading-text("strengths"), k: k); stack(spacing: px(8), ..doc.strengths.map(s => stack(spacing: px(2),
      muted(92, size: fs(12, k: k), weight: "semibold")[#strength-icon(s, accent) #s.title],
      ..if present(s.description) { (muted(48, size: fs(12, k: k), s.description),) } else { () }))) }) }
    else if id == "socials" and doc.socials.len() > 0 { styled(id, { side-h(heading-text("links"), k: k); stack(spacing: px(6), ..doc.socials.map(s => muted(78, size: fs(12, k: k))[#social-icon(s, accent) #s.label])) }) }
  }
  set page(background: page-fill(accent.transparentize(92%), 35%, dx: 65%))
  grid(columns: (65%, 35%),
    block(inset: px(32), width: 100%, {
      block(width: 100%, stroke: (bottom: 1.5pt + accent), inset: (bottom: px(16)), below: gap, {
        text(size: fs(30), weight: "bold", fill: accent, person.name)
        if present(person.title) { linebreak(); muted(62, size: fs(18), person.title) }
        v(px(8)); contacts(62, accent, socials: false)
      })
      column(("summary", "experience", "education", "projects"), main)
      custom-sections(t => block(below: px(8), stroke: (bottom: 0.75pt + accent), inset: (bottom: px(3)), width: 100%,
        text(size: fs(12.8), weight: "bold", fill: accent, tracking: 0.08em, upper(t))))
    }),
    block(inset: px(24), width: 100%, {
      if doc.photo.path != none { align(photo-side, photo(96)); v(gap) }
      column(("skills", "languages", "certifications", "strengths", "socials"), side)
    }))
}

// modern-2col: solid theme sidebar (35%) on the left, main column (65%).
#let modern-2col() = {
  let side-h(t) = block(sticky: true, below: px(8), text(size: fs(12), weight: "bold", fill: white.transparentize(40%), tracking: 0.1em, upper(t)))
  let h2(t, k: 1) = block(sticky: true, below: px(12), text(size: fs(12, k: k), weight: "bold", fill: accent, tracking: 0.1em, upper(t)))
  let on = white.transparentize(10%)
  let main(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => block({
      row(stack(spacing: px(2), text(size: fs(14, k: k), weight: "semibold", e.title), muted(62, size: fs(12, k: k), e.subtitle)), muted(48, size: fs(12, k: k), dates(e)))
      if e.description.len() > 0 { v(px(4)); muted(78, size: fs(12, k: k), rich(e.description)) }
      chips(e.skills, k: k)
    }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => block({
      row(text(size: fs(14, k: k), weight: "semibold", e.title), muted(48, size: fs(12, k: k), e.start))
      muted(62, size: fs(12, k: k), e.subtitle)
      chips(e.skills, k: k)
    }))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      row(text(size: fs(14, k: k), weight: "semibold", p.title), muted(48, size: fs(12, k: k), p.date))
      if p.description.len() > 0 { v(px(4)); muted(78, size: fs(12, k: k), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { h2(heading-text("certifications"), k: k); stack(spacing: px(6), ..doc.certifications.map(c => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", c.name),
      ..if present(c.issuer) { (muted(62, size: fs(12, k: k), c.issuer + " · " + c.date),) } else { () }))) }) }
  }
  set page(background: page-fill(accent, 35%))
  grid(columns: (35%, 65%),
    block(inset: px(28), width: 100%, {
      set text(fill: white)
      let parts = ()
      if doc.photo.path != none { parts.push(align(photo-side, photo(96, extra: 1.5pt + white.transparentize(70%)))) }
      parts.push({ text(size: fs(24), weight: "bold", person.name); if present(person.title) { linebreak(); text(size: fs(14), fill: white.transparentize(25%), person.title) } })
      if present(person.email) or present(person.phone) or present(person.location) {
        parts.push({ side-h(heading-text("contact")); stack(spacing: px(6), ..("email", "phone", "location").filter(c => present(person.at(c))).map(c =>
          text(size: fs(12), fill: on)[#icon(c, white.transparentize(30%)) #h(px(4)) #person.at(c)])) })
      }
      if doc.skillGroups.len() > 0 { parts.push({ side-h(heading-text("skills")); skill-list(1, on, white.transparentize(30%), white.transparentize(10%), white.transparentize(75%), dot-size: 6) }) }
      if doc.languages.len() > 0 { parts.push({ side-h(heading-text("languages")); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12), fill: on, l.name), text(size: fs(12), fill: white.transparentize(40%), l.proficiency)))) }) }
      if doc.strengths.len() > 0 { parts.push({ side-h(heading-text("strengths")); stack(spacing: px(6), ..doc.strengths.map(s => stack(spacing: px(2), text(size: fs(12), weight: "semibold")[#strength-icon(s, white.transparentize(20%)) #s.title],
        ..if present(s.description) { (text(size: fs(12), fill: white.transparentize(40%), s.description),) } else { () }))) }) }
      if doc.socials.len() > 0 { parts.push({ side-h(heading-text("links")); stack(spacing: px(4), ..doc.socials.map(s => text(size: fs(12), fill: on)[#social-icon(s, white.transparentize(20%)) #s.label])) }) }
      stack(spacing: px(20), ..parts)
    }),
    block(inset: px(32), width: 100%, {
      if doc.summary.len() > 0 { block(below: gap, muted(78, size: fs(14), rich(doc.summary))) }
      column(("experience", "education", "projects", "certifications"), main)
      custom-sections(t => block(below: px(12), text(size: fs(12), weight: "bold", fill: accent, tracking: 0.1em, upper(t))))
    }))
}

// modern (one column): bordered header with photo, ruled section headings, chip skills.
#let modern-1col() = {
  let h2(t, k: 1) = block(sticky: true, below: px(12), width: 100%, stroke: (bottom: 0.75pt + accent), inset: (bottom: px(4)),
    text(size: fs(12, k: k), weight: "bold", fill: accent, tracking: 0.1em, upper(t)))
  let small-h(t, k: 1) = block(sticky: true, below: px(8), text(size: fs(12, k: k), weight: "bold", fill: accent, tracking: 0.1em, upper(t)))
  let entry(e, k) = block({
    row(text(size: fs(14, k: k), weight: "semibold", e.title), muted(48, size: fs(12, k: k), dates(e)))
    muted(62, size: fs(12, k: k), e.subtitle + if present(e.at("location", default: none)) { " · " + e.location } else { "" })
    if e.at("description", default: ()).len() > 0 { v(px(4)); muted(78, size: fs(12, k: k), rich(e.description)) }
    chips(e.skills, k: k)
  })
  let sec(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => entry(e, k))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => entry(e, k))) }) }
    else if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("skills"), k: k); skill-chips(k, accent, accent) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { small-h(heading-text("languages"), k: k); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12, k: k), l.name), muted(48, size: fs(12, k: k), l.proficiency)))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { small-h(heading-text("certifications"), k: k); stack(spacing: px(2), ..doc.certifications.map(c => text(size: fs(12, k: k), weight: "medium", c.name))) }) }
  }
  block(inset: px(40), width: 100%, {
    block(width: 100%, stroke: (bottom: 2.25pt + accent), inset: (bottom: px(24)), below: px(24),
      {
        let identity = { text(size: fs(30), weight: "bold", fill: accent, person.name)
          if present(person.title) { linebreak(); muted(62, size: fs(16), person.title) }
          v(px(8)); contacts(62, accent, gap: 16) }
        if doc.photo.path == none { identity }
        else if doc.photo.placement == "right" { grid(columns: (1fr, auto), column-gutter: px(24), align: horizon, identity, photo(80)) }
        else { grid(columns: (auto, 1fr), column-gutter: px(24), align: horizon, photo(80), identity) }
      })
    if doc.summary.len() > 0 { block(below: gap, muted(92, size: fs(14), rich(doc.summary))) }
    stack(spacing: gap, ..arrange(("experience", "education", "skills")).map(sec).filter(b => b != none))
    let pair = arrange(("languages", "certifications")).map(sec).filter(b => b != none)
    if pair.len() > 0 { v(gap); grid(columns: (1fr, 1fr), column-gutter: px(24), ..pair) }
    custom-sections(t => block(below: px(12), width: 100%, stroke: (bottom: 0.75pt + accent), inset: (bottom: px(4)),
      text(size: fs(12), weight: "bold", fill: accent, tracking: 0.1em, upper(t))))
  })
}

// minimal: light type, a date gutter and a ruled summary. Headings keep the 0.1em tracking of
// the other layouts: wider letter-spacing makes PDF text extraction read them as single letters.
#let minimal() = {
  let h2(t, k: 1) = block(sticky: true, below: px(16), muted(48, size: fs(12, k: k), tracking: 0.1em, upper(t)))
  let sec(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => block(
      grid(columns: (px(96), 1fr), column-gutter: px(16), align(right, muted(34, size: fs(12, k: k), e.start)), {
        text(size: fs(14, k: k), weight: "medium", e.title); linebreak(); muted(48, size: fs(12, k: k), e.subtitle)
        if e.description.len() > 0 { v(px(4)); muted(62, size: fs(12, k: k), rich(e.description)) }
        chips(e.skills, k: k)
      })))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e =>
      grid(columns: (px(96), 1fr), column-gutter: px(16), align(right, muted(34, size: fs(12, k: k), e.start)), {
        text(size: fs(14, k: k), weight: "medium", e.title); linebreak(); muted(48, size: fs(12, k: k), e.subtitle) }))) }) }
    else if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("skills"), k: k); skill-chips(k, 78, rgb("#e5e7eb"), chip-fill: rgb("#f9fafb"), radius: px(4), category: 48) }) }
  }
  block(inset: px(48), width: 100%, {
    set text(fill: tone(100))
    block(below: px(28), {
      text(size: fs(36), weight: "light", tracking: -0.02em, person.name)
      if present(person.title) { linebreak(); muted(48, size: fs(16), weight: "light", person.title) }
      v(px(12)); contacts(48, tone(48), gap: 16, socials: false)
    })
    if doc.summary.len() > 0 { block(below: gap, stroke: (left: 1.5pt + accent), inset: (left: px(16)), muted(78, size: fs(14), weight: "light", rich(doc.summary))) }
    stack(spacing: gap, ..arrange(("experience", "education", "skills")).map(sec).filter(b => b != none))
    custom-sections(t => block(below: px(12), muted(48, size: fs(12), tracking: 0.1em, upper(t))))
  })
}

// executive: full-width theme band, ruled headings, education and skills side by side.
#let executive() = {
  let h2(t, k: 1, rule: 0.75pt, below: 12) = block(sticky: true, below: px(below), width: 100%, stroke: (bottom: rule + accent), inset: (bottom: px(8)),
    text(size: fs(14, k: k), weight: "semibold", fill: accent, tracking: 0.1em, upper(t)))
  let sec(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("professionalExperience"), k: k, rule: 1.5pt, below: 16); stack(spacing: px(20), ..doc.experience.map(e => block({
      row(stack(spacing: px(2), text(size: fs(14, k: k), weight: "semibold", e.title),
          muted(62, size: fs(12, k: k), e.subtitle + if present(e.location) { " · " + e.location } else { "" })),
        muted(48, size: fs(12, k: k), dates(e)))
      if e.description.len() > 0 { v(px(6)); muted(78, size: fs(12, k: k), rich(e.description)) }
      chips(e.skills, k: k)
    }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", e.title), muted(62, size: fs(12, k: k), e.subtitle)))) }) }
    else if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("coreSkills"), k: k); skill-chips(k, accent, accent, radius: px(4)) }) }
  }
  block(width: 100%, fill: accent, inset: px(32), styled("header", {
    set text(fill: white)
    let identity = stack(spacing: px(4), text(size: fs(30), weight: "semibold", tracking: 0.03em, person.name),
      text(size: fs(16), fill: white.transparentize(20%), person.title))
    if doc.photo.path == none { identity }
    else if doc.photo.placement == "left" { grid(columns: (auto, 1fr), column-gutter: px(24), align: bottom, photo(80, extra: 1.5pt + white.transparentize(70%)), identity) }
    else { grid(columns: (1fr, auto), align: bottom, identity, photo(80, extra: 1.5pt + white.transparentize(70%))) }
    v(px(16)); contacts(white.transparentize(25%), white, gap: 20)
  }))
  block(inset: (x: px(40), top: px(32), bottom: px(40)), width: 100%, {
    if doc.summary.len() > 0 { block(below: gap, stroke: (left: 3pt + accent), inset: (left: px(16)), muted(78, size: fs(14), rich(doc.summary))) }
    let order = arrange(("experience", "education", "skills"))
    if "experience" in order { block(below: gap, sec("experience")) }
    let pair = order.filter(id => id != "experience").map(sec).filter(b => b != none)
    if pair.len() > 0 { grid(columns: (1fr, 1fr), column-gutter: px(24), ..pair) }
    custom-sections(t => block(below: px(16), width: 100%, stroke: (bottom: 1.5pt + accent), inset: (bottom: px(8)),
      text(size: fs(14), weight: "semibold", fill: accent, tracking: 0.1em, upper(t))))
  })
}

// creative: top accent bar, centred identity in a left column (40%), ruled entries on the right.
#let creative() = {
  let h2(t, k: 1) = block(sticky: true, below: px(10), text(size: fs(12, k: k), weight: "bold", fill: accent, tracking: 0.1em, upper(t)))
  let side(id) = {
    let k = k-of(id)
    if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("skills"), k: k); stack(spacing: px(6), ..doc.skillGroups.map(g => stack(spacing: px(6),
      ..if g.category != none { (text(size: fs(10, k: k), weight: "semibold", fill: accent, upper(g.category)),) } else { () },
      ..g.skills.map(s => stack(spacing: px(2), muted(92, size: fs(12, k: k), s.name),
        ..if doc.showSkillLevel and s.level != none { (grid(columns: (1fr,) * 5, column-gutter: px(2),
          ..range(1, 6).map(d => rect(width: 100%, height: px(4), radius: 99pt, fill: if d <= s.level { accent } else { rgb("#e5e7eb") }))),) } else { () }))))) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { h2(heading-text("languages"), k: k); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12, k: k), l.name), muted(48, size: fs(12, k: k), l.proficiency)))) }) }
    else if id == "socials" and doc.socials.len() > 0 { styled(id, { h2(heading-text("links"), k: k); stack(spacing: px(4), ..doc.socials.map(s => muted(78, size: fs(12, k: k))[#social-icon(s, accent) #s.label])) }) }
  }
  let main(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => block(
      stroke: (left: 1.5pt + accent.transparentize(62%)), inset: (left: px(12)), {
        row(text(size: fs(14, k: k), weight: "semibold", e.title), muted(48, size: fs(12, k: k), dates(e)))
        muted(48, size: fs(12, k: k), e.subtitle)
        if e.description.len() > 0 { v(px(4)); muted(62, size: fs(12, k: k), rich(e.description)) }
        chips(e.skills, k: k)
      }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", e.title), muted(48, size: fs(12, k: k), e.subtitle)))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      text(size: fs(14, k: k), weight: "semibold", p.title)
      if p.description.len() > 0 { muted(62, size: fs(12, k: k), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
    else if id == "strengths" and doc.strengths.len() > 0 { styled(id, { h2(heading-text("strengths"), k: k); grid(columns: (1fr, 1fr), gutter: px(8),
      ..doc.strengths.map(s => block(width: 100%, inset: px(8), radius: px(8), stroke: 0.75pt + rgb("#f3f4f6"), fill: rgb("#f9fafb"), {
        muted(92, size: fs(12, k: k), weight: "semibold")[#strength-icon(s, accent) #s.title]
        if present(s.description) { linebreak(); muted(48, size: fs(12, k: k), s.description) }
      }))) }) }
  }
  set page(background: { place(top + left, rect(width: 100%, height: px(8), fill: accent))
    place(top + left, dx: 40%, line(start: (0pt, px(8)), end: (0pt, 100%), stroke: 0.75pt + rgb("#f3f4f6"))) })
  grid(columns: (40%, 60%),
    block(inset: (x: px(32), top: px(40), bottom: px(32)), width: 100%, {
      align(center, {
        if doc.photo.path != none { align(photo-side, photo(112)); v(px(12)) }
        text(size: fs(24), weight: "bold", fill: accent, person.name)
        if present(person.title) { linebreak(); muted(48, size: fs(14), person.title) }
      })
      v(px(16)); line(length: 100%, stroke: 0.75pt + rgb("#f3f4f6")); v(px(16))
      styled("header", stack(spacing: px(6), ..("email", "phone", "location").filter(c => present(person.at(c))).map(c =>
        muted(62, size: fs(12, k: k-of("header")))[#icon(c, accent) #h(px(4)) #person.at(c)])))
      v(gap)
      column(("skills", "languages", "socials"), side)
    }),
    block(inset: (x: px(32), top: px(40), bottom: px(32)), width: 100%, {
      if doc.summary.len() > 0 { block(below: gap, muted(78, size: fs(14), rich(doc.summary))) }
      column(("experience", "education", "projects", "strengths"), main)
      custom-sections(t => block(below: px(10), text(size: fs(12), weight: "bold", fill: accent, tracking: 0.1em, upper(t))))
    }))
}

#let layouts = (classic: classic, modern: modern-1col, "modern-2col": modern-2col, minimal: minimal,
  executive: executive, creative: creative)
#layouts.at(doc.layout, default: classic)()
