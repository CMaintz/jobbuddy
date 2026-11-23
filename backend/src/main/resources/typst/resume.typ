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
#let tone(pct) = color.mix((ink, pct * 1%), (white, (100 - pct) * 1%))
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
  text(..args, body)
}
#let runs(rs) = for r in rs { run(r) }
#let rich(blocks) = for b in blocks {
  if b.kind == "list" {
    if b.ordered { enum(..b.items.map(runs)) } else { list(..b.items.map(runs)) }
  } else { par(runs(b.runs)) }
}

#let contact-icons = (
  email: "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z",
  phone: "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z",
  location: "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
)
#let icon(name, fill, size: 12) = box(baseline: 15%, image(bytes(
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='" + fill.to-hex()
    + "' d='" + contact-icons.at(name) + "'/></svg>"), format: "svg", width: px(size)))

#let contacts(fill, icon-fill, gap: 12, socials: true, k: 1) = {
  let parts = ()
  for key in ("email", "phone", "location") {
    if present(person.at(key)) {
      parts.push(box[#icon(key, icon-fill) #h(px(3)) #text(size: fs(12, k: k), fill: fill, person.at(key))])
    }
  }
  if socials { for s in doc.socials { parts.push(text(size: fs(12, k: k), fill: fill, s.label)) } }
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
    text(size: fs(12, k: k), fill: tone(62), s))).join(h(px(4)))
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
    if g.category != none { rows.push(text(size: fs(10, k: k), weight: "semibold", fill: cat-fill, upper(g.category))) }
    for s in g.skills {
      rows.push(row(text(size: fs(12, k: k), fill: name-fill, s.name),
        if doc.showSkillLevel and s.level != none { dots(s.level, dot-on, dot-off, size: dot-size) }))
    }
    stack(spacing: px(6), ..rows)
  }))

#let skill-chips(k, fill, border, chip-fill: none, radius: 999pt, category: auto) = stack(spacing: px(8),
  ..doc.skillGroups.map(g => {
    let parts = ()
    let category-fill = if category == auto { border } else { category }
    if g.category != none { parts.push(text(size: fs(10, k: k), weight: "semibold", fill: category-fill, upper(g.category))) }
    parts.push(g.skills.map(s => box(inset: (x: px(8), y: px(2)), radius: radius, stroke: 0.75pt + border,
      fill: chip-fill, text(size: fs(12, k: k), fill: fill, s.name))).join(h(px(8))))
    stack(spacing: px(6), ..parts)
  }))

#let custom-sections(heading-fn) = for cs in doc.custom {
  let k = k-of("custom")
  block(above: gap, below: 0pt, styled("custom", {
    block(sticky: true, heading-fn(cs.heading))
    if cs.body.len() > 0 { text(size: fs(14, k: k), fill: tone(92), rich(cs.body)) }
    if cs.items.len() > 0 { set text(size: fs(12, k: k), fill: tone(92)); list(..cs.items) }
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
        text(size: fs(12, k: k), fill: tone(62), e.subtitle + if present(e.at("location", default: none)) { " · " + e.location } else { "" })),
      text(size: fs(12, k: k), fill: tone(48), dates(e)))
    if e.at("description", default: ()).len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(78), rich(e.description)) }
    chips(e.skills, k: k)
  })
  let main(id) = {
    let k = k-of(id)
    if id == "summary" and doc.summary.len() > 0 { styled(id, { h2(heading-text("summary"), k: k); text(size: fs(14, k: k), fill: tone(92), rich(doc.summary)) }) }
    else if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => entry(e, k))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => entry(e, k))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      row(text(size: fs(14, k: k), weight: "semibold", p.title), text(size: fs(12, k: k), fill: tone(48), p.date))
      if p.description.len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(78), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
  }
  let side(id) = {
    let k = k-of(id)
    if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { side-h(heading-text("skills"), k: k); skill-list(k, tone(92), tone(62), accent, rgb("#d1d5db")) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { side-h(heading-text("languages"), k: k); stack(spacing: px(4), ..doc.languages.map(l => row(text(size: fs(12, k: k), fill: tone(92), l.name), text(size: fs(12, k: k), fill: tone(48), l.proficiency)))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { side-h(heading-text("certifications"), k: k); stack(spacing: px(8), ..doc.certifications.map(c => stack(spacing: px(2),
      text(size: fs(12, k: k), weight: "medium", fill: tone(92), c.name),
      ..if present(c.issuer) { (text(size: fs(12, k: k), fill: tone(48), c.issuer),) } else { () },
      ..if present(c.date) { (text(size: fs(12, k: k), fill: tone(48), c.date),) } else { () }))) }) }
    else if id == "strengths" and doc.strengths.len() > 0 { styled(id, { side-h(heading-text("strengths"), k: k); stack(spacing: px(8), ..doc.strengths.map(s => stack(spacing: px(2),
      text(size: fs(12, k: k), weight: "semibold", fill: tone(92))[#box(baseline: -20%, circle(radius: px(2.5), fill: accent)) #s.title],
      ..if present(s.description) { (text(size: fs(12, k: k), fill: tone(48), s.description),) } else { () }))) }) }
    else if id == "socials" and doc.socials.len() > 0 { styled(id, { side-h(heading-text("links"), k: k); stack(spacing: px(6), ..doc.socials.map(s => text(size: fs(12, k: k), fill: tone(78), s.label))) }) }
  }
  set page(background: page-fill(accent.transparentize(92%), 35%, dx: 65%))
  grid(columns: (65%, 35%),
    block(inset: px(32), width: 100%, {
      block(width: 100%, stroke: (bottom: 1.5pt + accent), inset: (bottom: px(16)), below: gap, {
        text(size: fs(30), weight: "bold", fill: accent, person.name)
        if present(person.title) { linebreak(); text(size: fs(18), fill: tone(62), person.title) }
        v(px(8)); contacts(tone(62), accent, socials: false)
      })
      column(("summary", "experience", "education", "projects"), main)
      custom-sections(t => block(below: px(8), stroke: (bottom: 0.75pt + accent), inset: (bottom: px(3)), width: 100%,
        text(size: fs(12.8), weight: "bold", fill: accent, tracking: 0.08em, upper(t))))
    }),
    block(inset: px(24), width: 100%, {
      if doc.photo.path != none { align(center, photo(96)); v(gap) }
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
      row(stack(spacing: px(2), text(size: fs(14, k: k), weight: "semibold", e.title), text(size: fs(12, k: k), fill: tone(62), e.subtitle)), text(size: fs(12, k: k), fill: tone(48), dates(e)))
      if e.description.len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(78), rich(e.description)) }
      chips(e.skills, k: k)
    }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => block({
      row(text(size: fs(14, k: k), weight: "semibold", e.title), text(size: fs(12, k: k), fill: tone(48), e.start))
      text(size: fs(12, k: k), fill: tone(62), e.subtitle)
      chips(e.skills, k: k)
    }))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      row(text(size: fs(14, k: k), weight: "semibold", p.title), text(size: fs(12, k: k), fill: tone(48), p.date))
      if p.description.len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(78), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { h2(heading-text("certifications"), k: k); stack(spacing: px(6), ..doc.certifications.map(c => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", c.name),
      ..if present(c.issuer) { (text(size: fs(12, k: k), fill: tone(62), c.issuer + " · " + c.date),) } else { () }))) }) }
  }
  set page(background: page-fill(accent, 35%))
  grid(columns: (35%, 65%),
    block(inset: px(28), width: 100%, {
      set text(fill: white)
      let parts = ()
      if doc.photo.path != none { parts.push(align(center, photo(96, extra: 1.5pt + white.transparentize(70%)))) }
      parts.push({ text(size: fs(24), weight: "bold", person.name); if present(person.title) { linebreak(); text(size: fs(14), fill: white.transparentize(25%), person.title) } })
      if present(person.email) or present(person.phone) or present(person.location) {
        parts.push({ side-h(heading-text("contact")); stack(spacing: px(6), ..("email", "phone", "location").filter(c => present(person.at(c))).map(c =>
          text(size: fs(12), fill: on)[#icon(c, white.transparentize(30%)) #h(px(4)) #person.at(c)])) })
      }
      if doc.skillGroups.len() > 0 { parts.push({ side-h(heading-text("skills")); skill-list(1, on, white.transparentize(30%), white.transparentize(10%), white.transparentize(75%), dot-size: 6) }) }
      if doc.languages.len() > 0 { parts.push({ side-h(heading-text("languages")); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12), fill: on, l.name), text(size: fs(12), fill: white.transparentize(40%), l.proficiency)))) }) }
      if doc.strengths.len() > 0 { parts.push({ side-h(heading-text("strengths")); stack(spacing: px(6), ..doc.strengths.map(s => stack(spacing: px(2), text(size: fs(12), weight: "semibold", s.title),
        ..if present(s.description) { (text(size: fs(12), fill: white.transparentize(40%), s.description),) } else { () }))) }) }
      if doc.socials.len() > 0 { parts.push({ side-h(heading-text("links")); stack(spacing: px(4), ..doc.socials.map(s => text(size: fs(12), fill: on, s.label))) }) }
      stack(spacing: px(20), ..parts)
    }),
    block(inset: px(32), width: 100%, {
      if doc.summary.len() > 0 { block(below: gap, text(size: fs(14), fill: tone(78), rich(doc.summary))) }
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
    row(text(size: fs(14, k: k), weight: "semibold", e.title), text(size: fs(12, k: k), fill: tone(48), dates(e)))
    text(size: fs(12, k: k), fill: tone(62), e.subtitle + if present(e.at("location", default: none)) { " · " + e.location } else { "" })
    if e.at("description", default: ()).len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(78), rich(e.description)) }
    chips(e.skills, k: k)
  })
  let sec(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => entry(e, k))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(12), ..doc.education.map(e => entry(e, k))) }) }
    else if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("skills"), k: k); skill-chips(k, accent, accent) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { small-h(heading-text("languages"), k: k); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12, k: k), l.name), text(size: fs(12, k: k), fill: tone(48), l.proficiency)))) }) }
    else if id == "certifications" and doc.certifications.len() > 0 { styled(id, { small-h(heading-text("certifications"), k: k); stack(spacing: px(2), ..doc.certifications.map(c => text(size: fs(12, k: k), weight: "medium", c.name))) }) }
  }
  block(inset: px(40), width: 100%, {
    block(width: 100%, stroke: (bottom: 2.25pt + accent), inset: (bottom: px(24)), below: px(24),
      {
        let identity = { text(size: fs(30), weight: "bold", fill: accent, person.name)
          if present(person.title) { linebreak(); text(size: fs(16), fill: tone(62), person.title) }
          v(px(8)); contacts(tone(62), accent, gap: 16) }
        if doc.photo.path == none { identity }
        else if doc.photo.placement == "right" { grid(columns: (1fr, auto), column-gutter: px(24), align: horizon, identity, photo(80)) }
        else { grid(columns: (auto, 1fr), column-gutter: px(24), align: horizon, photo(80), identity) }
      })
    if doc.summary.len() > 0 { block(below: gap, text(size: fs(14), fill: tone(92), rich(doc.summary))) }
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
  let h2(t, k: 1) = block(sticky: true, below: px(16), text(size: fs(12, k: k), fill: tone(48), tracking: 0.1em, upper(t)))
  let sec(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => block(
      grid(columns: (px(96), 1fr), column-gutter: px(16), align(right, text(size: fs(12, k: k), fill: tone(34), e.start)), {
        text(size: fs(14, k: k), weight: "medium", e.title); linebreak(); text(size: fs(12, k: k), fill: tone(48), e.subtitle)
        if e.description.len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(62), rich(e.description)) }
        chips(e.skills, k: k)
      })))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e =>
      grid(columns: (px(96), 1fr), column-gutter: px(16), align(right, text(size: fs(12, k: k), fill: tone(34), e.start)), {
        text(size: fs(14, k: k), weight: "medium", e.title); linebreak(); text(size: fs(12, k: k), fill: tone(48), e.subtitle) }))) }) }
    else if id == "skills" and doc.skillGroups.len() > 0 { styled(id, { h2(heading-text("skills"), k: k); skill-chips(k, tone(78), rgb("#e5e7eb"), chip-fill: rgb("#f9fafb"), radius: px(4), category: tone(48)) }) }
  }
  block(inset: px(48), width: 100%, {
    set text(fill: tone(100))
    block(below: px(28), {
      text(size: fs(36), weight: "light", tracking: -0.02em, person.name)
      if present(person.title) { linebreak(); text(size: fs(16), weight: "light", fill: tone(48), person.title) }
      v(px(12)); contacts(tone(48), tone(48), gap: 16, socials: false)
    })
    if doc.summary.len() > 0 { block(below: gap, stroke: (left: 1.5pt + accent), inset: (left: px(16)), text(size: fs(14), weight: "light", fill: tone(78), rich(doc.summary))) }
    stack(spacing: gap, ..arrange(("experience", "education", "skills")).map(sec).filter(b => b != none))
    custom-sections(t => block(below: px(12), text(size: fs(12), fill: tone(48), tracking: 0.1em, upper(t))))
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
          text(size: fs(12, k: k), fill: tone(62), e.subtitle + if present(e.location) { " · " + e.location } else { "" })),
        text(size: fs(12, k: k), fill: tone(48), dates(e)))
      if e.description.len() > 0 { v(px(6)); text(size: fs(12, k: k), fill: tone(78), rich(e.description)) }
      chips(e.skills, k: k)
    }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", e.title), text(size: fs(12, k: k), fill: tone(62), e.subtitle)))) }) }
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
    if doc.summary.len() > 0 { block(below: gap, stroke: (left: 3pt + accent), inset: (left: px(16)), text(size: fs(14), fill: tone(78), rich(doc.summary))) }
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
      ..g.skills.map(s => stack(spacing: px(2), text(size: fs(12, k: k), fill: tone(92), s.name),
        ..if doc.showSkillLevel and s.level != none { (grid(columns: (1fr,) * 5, column-gutter: px(2),
          ..range(1, 6).map(d => rect(width: 100%, height: px(4), radius: 99pt, fill: if d <= s.level { accent } else { rgb("#e5e7eb") }))),) } else { () }))))) }) }
    else if id == "languages" and doc.languages.len() > 0 { styled(id, { h2(heading-text("languages"), k: k); stack(spacing: px(2), ..doc.languages.map(l => row(text(size: fs(12, k: k), l.name), text(size: fs(12, k: k), fill: tone(48), l.proficiency)))) }) }
    else if id == "socials" and doc.socials.len() > 0 { styled(id, { h2(heading-text("links"), k: k); stack(spacing: px(4), ..doc.socials.map(s => text(size: fs(12, k: k), fill: tone(78), s.label))) }) }
  }
  let main(id) = {
    let k = k-of(id)
    if id == "experience" and doc.experience.len() > 0 { styled(id, { h2(heading-text("experience"), k: k); stack(spacing: px(16), ..doc.experience.map(e => block(
      stroke: (left: 1.5pt + accent.transparentize(62%)), inset: (left: px(12)), {
        row(text(size: fs(14, k: k), weight: "semibold", e.title), text(size: fs(12, k: k), fill: tone(48), dates(e)))
        text(size: fs(12, k: k), fill: tone(48), e.subtitle)
        if e.description.len() > 0 { v(px(4)); text(size: fs(12, k: k), fill: tone(62), rich(e.description)) }
        chips(e.skills, k: k)
      }))) }) }
    else if id == "education" and doc.education.len() > 0 { styled(id, { h2(heading-text("education"), k: k); stack(spacing: px(8), ..doc.education.map(e => stack(spacing: px(2),
      text(size: fs(14, k: k), weight: "medium", e.title), text(size: fs(12, k: k), fill: tone(48), e.subtitle)))) }) }
    else if id == "projects" and doc.projects.len() > 0 { styled(id, { h2(heading-text("projects"), k: k); stack(spacing: px(12), ..doc.projects.map(p => block({
      text(size: fs(14, k: k), weight: "semibold", p.title)
      if p.description.len() > 0 { text(size: fs(12, k: k), fill: tone(62), rich(p.description)) }
      chips(p.skills, k: k)
    }))) }) }
    else if id == "strengths" and doc.strengths.len() > 0 { styled(id, { h2(heading-text("strengths"), k: k); grid(columns: (1fr, 1fr), gutter: px(8),
      ..doc.strengths.map(s => block(width: 100%, inset: px(8), radius: px(8), stroke: 0.75pt + rgb("#f3f4f6"), fill: rgb("#f9fafb"), {
        text(size: fs(12, k: k), weight: "semibold", fill: tone(92))[#box(baseline: -20%, circle(radius: px(2.5), fill: accent)) #s.title]
        if present(s.description) { linebreak(); text(size: fs(12, k: k), fill: tone(48), s.description) }
      }))) }) }
  }
  set page(background: { place(top + left, rect(width: 100%, height: px(8), fill: accent))
    place(top + left, dx: 40%, line(start: (0pt, px(8)), end: (0pt, 100%), stroke: 0.75pt + rgb("#f3f4f6"))) })
  grid(columns: (40%, 60%),
    block(inset: (x: px(32), top: px(40), bottom: px(32)), width: 100%, {
      align(center, {
        if doc.photo.path != none { photo(112); v(px(12)) }
        text(size: fs(24), weight: "bold", fill: accent, person.name)
        if present(person.title) { linebreak(); text(size: fs(14), fill: tone(48), person.title) }
      })
      v(px(16)); line(length: 100%, stroke: 0.75pt + rgb("#f3f4f6")); v(px(16))
      styled("header", stack(spacing: px(6), ..("email", "phone", "location").filter(c => present(person.at(c))).map(c =>
        text(size: fs(12, k: k-of("header")), fill: tone(62))[#icon(c, accent) #h(px(4)) #person.at(c)])))
      v(gap)
      column(("skills", "languages", "socials"), side)
    }),
    block(inset: (x: px(32), top: px(40), bottom: px(32)), width: 100%, {
      if doc.summary.len() > 0 { block(below: gap, text(size: fs(14), fill: tone(78), rich(doc.summary))) }
      column(("experience", "education", "projects", "strengths"), main)
      custom-sections(t => block(below: px(10), text(size: fs(12), weight: "bold", fill: accent, tracking: 0.1em, upper(t))))
    }))
}

#let layouts = (classic: classic, modern: modern-1col, "modern-2col": modern-2col, minimal: minimal,
  executive: executive, creative: creative)
#layouts.at(doc.layout, default: classic)()
