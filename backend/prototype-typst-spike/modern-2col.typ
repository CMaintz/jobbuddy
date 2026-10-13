// PROTOTYPE - throwaway Typst port of frontend resume-builder modern-2col layout.
// Data comes from a JSON file passed as --input data=<path relative to --root>.

#let doc = json(sys.inputs.at("data", default: "sample-resume.json"))
#let d = doc.resumeData
#let s = doc.settings
#let pi = d.personalInfo
#let theme = rgb(s.themeColor)
#let ink = rgb(s.at("textColor", default: "#1f2937"))
#let gray400 = rgb("#9ca3af")
#let gray500 = rgb("#6b7280")
#let gray600 = rgb("#4b5563")
#let L = s.labels

// Tailwind px -> pt (1px = 0.75pt) so sizes match the web layout's intent.
#let px(n) = n * 0.75pt

#set document(title: pi.fullName + " - CV", author: pi.fullName)
#set page(
  paper: if s.documentSize == "Letter" { "us-letter" } else { "a4" },
  margin: (x: 0pt, y: px(28)),
  background: place(left + top, rect(width: 35%, height: 100%, fill: theme)),
)
// CSS-like line box: line-height 1.5 means 0.5em minus the font's own extent is the gap.
#set text(font: "Inter", size: px(12), fill: ink, lang: "da", top-edge: "ascender", bottom-edge: "descender")
#set par(leading: (s.lineSpacing - 1.21) * 1em, spacing: px(6))
#set list(marker: [•], indent: px(4), body-indent: px(6), spacing: px(3))

// TipTap HTML (p, ul/ol/li, strong, em, span style=color, br) -> Typst content via the built-in XML parser.
#let rich(node) = {
  if type(node) == str { return node }
  let kids = node.children.map(rich).join()
  let tag = node.tag
  if tag == "p" { block(spacing: px(4), kids) }
  else if tag == "strong" or tag == "b" { strong(kids) }
  else if tag == "em" or tag == "i" { emph(kids) }
  else if tag == "br" { linebreak() }
  else if tag == "ul" { list(..node.children.filter(c => type(c) == dictionary).map(li => li.children.map(rich).join())) }
  else if tag == "ol" { enum(..node.children.filter(c => type(c) == dictionary).map(li => li.children.map(rich).join())) }
  else if tag == "span" {
    let m = node.attrs.at("style", default: "").match(regex("color:\s*(#[0-9a-fA-F]{6})"))
    if m != none { text(fill: rgb(m.captures.at(0)), kids) } else { kids }
  } else { kids }
}
#let richtext(value) = {
  if value == none or value == "" { return none }
  if value.contains("<") {
    xml(bytes("<root>" + value.replace("<br>", "<br/>").replace("&nbsp;", "&#160;") + "</root>")).first().children.map(rich).join()
  } else {
    value.split("\n").map(line => block(spacing: px(4), line)).join()
  }
}

#let icon(path) = box(baseline: 15%, image(bytes(
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path fill='white' fill-opacity='0.7' d='" + path + "'/></svg>"
), height: px(12), alt: ""))
#let icons = (
  email: "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z",
  phone: "M6.6 10.8c1.4 2.8 3.8 5.1 6.6 6.6l2.2-2.2c.3-.3.7-.4 1-.2 1.1.4 2.3.6 3.6.6.6 0 1 .4 1 1V20c0 .6-.4 1-1 1C10.6 21 3 13.4 3 4c0-.6.4-1 1-1h3.5c.6 0 1 .4 1 1 0 1.3.2 2.5.6 3.6.1.3 0 .7-.2 1L6.6 10.8z",
  location: "M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7zm0 9.5c-1.4 0-2.5-1.1-2.5-2.5S10.6 6.5 12 6.5s2.5 1.1 2.5 2.5-1.1 2.5-2.5 2.5z",
)

#let side-label(t) = block(spacing: px(8), text(size: px(12), weight: "bold", tracking: 0.1em, fill: white.transparentize(40%), upper(t)))
#let main-label(t) = block(below: px(12), text(size: px(12), weight: "bold", tracking: 0.1em, fill: theme, upper(t)))
#let chips(names) = if names != none and names.len() > 0 {
  block(above: px(6), names.map(n => box(fill: rgb("#f3f4f6"), inset: (x: px(6), y: px(2)), radius: px(4), text(size: px(10), fill: gray600, n))).join(h(px(4))))
}
#let dots(level) = box(stack(dir: ltr, spacing: px(2), ..range(1, 6).map(i =>
  circle(radius: px(3), fill: if i <= level { white.transparentize(10%) } else { white.transparentize(75%) }))))
#let row(left, right) = grid(columns: (1fr, auto), column-gutter: px(8), left, right)
#let fmt-range(e) = e.startDate + " – " + if e.current { L.present } else { e.endDate }

#let sidebar = {
  set text(fill: white)
  block(spacing: px(4), text(size: px(24), weight: "bold", pi.fullName))
  block(below: px(20), text(size: px(14), fill: white.transparentize(25%), pi.title))

  side-label(L.contact)
  for k in ("email", "phone", "location") {
    if pi.at(k) != "" { block(spacing: px(6), text(size: px(12), fill: white.transparentize(10%))[#icon(icons.at(k)) #h(px(6)) #pi.at(k)]) }
  }
  v(px(20))

  side-label(L.skills)
  let cats = ()
  for sk in d.skills { let c = sk.at("category", default: ""); if c not in cats { cats.push(c) } }
  for c in cats {
    if c != "" { block(above: px(4), below: px(4), text(size: px(10), weight: "semibold", tracking: 0.05em, fill: white.transparentize(30%), upper(c))) }
    for sk in d.skills.filter(x => x.at("category", default: "") == c) {
      block(spacing: px(6), row(text(fill: white.transparentize(10%), sk.name), if s.showSkillLevel and sk.at("level", default: none) != none { dots(sk.level) }))
    }
  }
  v(px(20))

  side-label(L.languages)
  for l in d.languages { block(spacing: px(4), row(l.name, text(fill: white.transparentize(40%), l.proficiency))) }
  v(px(20))

  side-label(L.strengths)
  for st in d.strengths {
    block(spacing: px(6))[#text(weight: "semibold", st.title) \ #text(fill: white.transparentize(40%), st.description)]
  }
  v(px(20))

  side-label(L.links)
  for so in d.socials { block(spacing: px(4), text(fill: white.transparentize(10%), so.at("username", default: so.url))) }
}

#let main = {
  block(below: px(20), text(size: px(14), fill: gray600, richtext(pi.summary)))

  main-label(L.experience)
  for e in d.experience {
    block(below: px(16), breakable: true, {
      row([#text(size: px(14), weight: "semibold", e.title) \ #text(fill: gray500, e.company)], text(fill: gray400, fmt-range(e)))
      block(above: px(4), text(fill: gray600, richtext(e.description)))
      chips(e.at("skills", default: none))
    })
  }
  v(px(4))

  main-label(L.education)
  for e in d.education {
    block(below: px(12), {
      row(text(size: px(14), weight: "semibold", e.degree), text(fill: gray400, e.startDate))
      text(fill: gray500, e.school)
      chips(e.at("skills", default: none))
    })
  }
  v(px(8))

  main-label(L.projects)
  for p in d.projects {
    // An entry's heading must never sit alone at a page bottom.
    block(below: px(12), breakable: false, {
      row(text(size: px(14), weight: "semibold", p.name), text(fill: gray400, p.date))
      block(above: px(4), text(fill: gray600, richtext(p.description)))
      chips(p.at("skills", default: none))
    })
  }
  v(px(8))

  main-label(L.certifications)
  for c in d.certifications {
    block(below: px(6))[#text(size: px(14), weight: "medium", c.name) \ #text(fill: gray500)[#c.issuer · #c.date]]
  }

  for cs in d.at("customSections", default: ()) {
    v(px(14))
    main-label(cs.heading)
    if cs.at("body", default: none) != none { richtext(cs.body) }
    list(..cs.at("items", default: ()).map(i => text(fill: rgb("#374151"), i.text)))
  }
}

#grid(
  columns: (35%, 65%),
  inset: (x: px(30), y: 0pt),
  sidebar, main,
)
