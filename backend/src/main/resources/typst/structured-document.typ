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

#header(doc.identity)
#if doc.kind == "cv" {
  for s in doc.sections { section(s) }
} else {
  for p in doc.paragraphs { par(p) }
}
