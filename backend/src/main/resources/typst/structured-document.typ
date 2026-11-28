// Built-in layout for every StructuredDocument (CV and letter, ATS and designed).
// All content arrives as data (sys.inputs.data names a JSON file in the root), never as
// Typst source, so user text cannot inject markup. The backend has already validated the
// colours and mapped the font to a bundled family before writing that file.

#let doc = json(sys.inputs.at("data"))
#let theme = doc.theme
#let primary = rgb(theme.primary)
#let accent = rgb(theme.accent)
#let muted = rgb("#5b6875")
#let base = theme.baseSize * 1pt

#set document(title: doc.identity.name, author: doc.identity.name)
#set page(paper: "a4", margin: (x: 18mm, y: 16mm))
// The Noto face is the fallback for scripts the chosen family lacks (CJK names), so they
// render instead of showing as missing-glyph boxes.
#set text(font: (theme.font, "Noto Sans SC"), size: base, fill: rgb("#1f2933"))
// Typst would otherwise curl straight quotes in user text; ATS parsers read the text layer.
#set smartquote(enabled: false)
#set par(leading: 0.55em, spacing: 0.8em, justify: false)
#set list(marker: [•], indent: 2pt, body-indent: 5pt, spacing: 0.45em)

#let present(value) = value != none and value != ""

// Rich text (editor HTML parsed by the backend into paragraphs and lists of styled runs).
#let run(r) = {
  if r.at("br", default: false) { return linebreak() }
  let body = r.at("text", default: "")
  if r.at("underline", default: false) { body = underline(body) }
  if r.at("strike", default: false) { body = strike(body) }
  let args = (:)
  if r.at("bold", default: false) { args.insert("weight", "bold") }
  if r.at("italic", default: false) { args.insert("style", "italic") }
  if r.at("color", default: none) != none { args.insert("fill", rgb(r.color)) }
  if r.at("size", default: none) != none { args.insert("size", r.size * 0.75pt) }
  text(..args, body)
}
#let runs(rs) = for r in rs { run(r) }
#let rich(blocks) = for b in blocks {
  if b.kind == "list" {
    if b.ordered { enum(..b.items.map(runs)) } else { list(..b.items.map(runs)) }
  } else { par(runs(b.runs)) }
}

// The letter screen's three sheets, in its CSS pixels (an A4 page is 794px wide, 1px = 0.75pt).
#let letter-styles = (
  editorial: (bg: "#f6f3ec", fg: "#1a1714", muted: "#6b6660", divider: "#d9d2c4", font: "Inter",
    size: 12.5, line: 1.65, header: "columns", rail: false),
  classic: (bg: "#fbfaf6", fg: "#1c1a16", muted: "#5a554e", divider: "#cfc8b9", font: "Liberation Serif",
    size: 13, line: 1.78, header: "centered", rail: false),
  bold: (bg: "#ffffff", fg: "#0e0e0e", muted: "#5a5a5a", divider: "#e5e0d4", font: "Inter",
    size: 12.5, line: 1.62, header: "rail", rail: true),
)

#let designed-letter(style) = {
  let px(n) = n * 0.75pt
  let s = letter-styles.at(style)
  let muted = rgb(s.muted)
  set page(fill: rgb(s.bg), margin: (x: px(64), y: px(56)),
    background: if s.rail { place(left + top, dy: px(56), rect(width: px(4), height: 100% - px(112), fill: rgb("#f5a623"))) })
  set text(font: (s.font, "Noto Sans SC"), size: px(s.size), fill: rgb(s.fg), top-edge: "ascender", bottom-edge: "descender")
  set par(leading: (s.line - 1.2) * 1em, spacing: px(14))
  set list(spacing: (s.line - 1.2) * 1em)
  let id = doc.identity
  let sub = (id.headline, id.email).filter(present).join(" - ")
  if present(id.name) {
    if s.header == "centered" {
      block(width: 100%, below: px(32), inset: (bottom: px(16)), stroke: (bottom: 0.75pt + rgb(s.divider)), align(center, {
        text(size: px(22), weight: "semibold", tracking: -0.01em, id.name)
        if present(sub) { linebreak(); text(size: px(11.5), fill: muted, sub) }
      }))
    } else {
      block(below: px(32), {
        text(size: px(18), weight: "medium", tracking: -0.01em, id.name)
        if present(sub) { linebreak(); text(size: px(11.5), fill: muted, sub) }
      })
    }
  }
  rich(doc.body)
}

#let photo(identity) = {
  if present(identity.photo) {
    box(clip: true, radius: 50%, width: 22mm, height: 22mm,
      image(identity.photo, width: 22mm, height: 22mm, fit: "cover"))
  } else {
    box(width: 22mm, height: 22mm, radius: 50%, fill: accent,
      align(center + horizon, text(size: 1.6 * base, weight: "bold", fill: primary, identity.initials)))
  }
}

#let identity-block(identity, fill) = {
  block(spacing: 0.3em, text(size: theme.h1Size * 1pt, weight: "bold", fill: fill, identity.name))
  if present(identity.headline) {
    block(spacing: 0.5em, text(size: 1.15 * base, fill: fill, identity.headline))
  }
  if identity.contacts.len() > 0 {
    text(size: 0.9 * base, fill: fill, identity.contacts.join("  |  "))
  }
}

#let header(identity) = {
  let show-photo = doc.designed and doc.showPhoto
  if doc.designed {
    block(width: 100%, fill: primary, inset: 12pt, radius: 3pt, below: 14pt,
      grid(columns: if show-photo { (auto, 1fr) } else { (1fr,) }, column-gutter: 12pt, align: horizon,
        ..if show-photo { (photo(identity),) } else { () },
        identity-block(identity, white)))
  } else {
    block(below: 12pt, identity-block(identity, primary))
  }
}

#let heading-rule(title) = block(above: 1.1em, below: 0.6em, sticky: true, {
  text(size: 1.15 * base, weight: "bold", fill: primary, title)
  if doc.designed { v(-0.5em); line(length: 100%, stroke: 1pt + accent) }
})

#let entry(item) = block(spacing: 0.9em, breakable: true, {
  grid(columns: (1fr, auto), column-gutter: 8pt,
    {
      text(weight: "bold", item.title)
      if present(item.subtitle) { linebreak(); text(fill: muted, item.subtitle) }
    },
    if present(item.dateRange) { text(fill: muted, item.dateRange) })
  if present(item.description) { block(above: 0.5em, item.description) }
  if item.bullets.len() > 0 { block(above: 0.5em, list(..item.bullets)) }
  if item.technologies.len() > 0 {
    block(above: 0.4em, text(size: 0.9 * base, fill: muted, item.technologies.join(" · ")))
  }
})

#let section(s) = {
  heading-rule(s.heading)
  if present(s.body) { block(spacing: 0.7em, s.body) }
  if s.compact {
    s.items.map(i => i.title).join(" · ")
  } else {
    for item in s.items { entry(item) }
  }
}

#if doc.kind == "letter" and doc.letterStyle != none {
  designed-letter(doc.letterStyle)
} else {
  header(doc.identity)
  if doc.kind == "cv" {
    for s in doc.sections { section(s) }
  } else {
    rich(doc.body)
  }
}
