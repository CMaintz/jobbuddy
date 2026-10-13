# PROTOTYPE - Typst render spike (throwaway, never merge)

Question: can Typst be the single engine for both the template-studio / resume-builder preview
and the final CV/letter PDF, with an identical layout, a real text PDF, and a small footprint?

## Run

```bash
# one-time: put typst 0.15.1 at bin/typst.exe (github.com/typst/typst/releases)
T="bin/typst.exe compile --root . --ignore-system-fonts --font-path fonts --input data=sample-resume.json modern-2col.typ"
$T out/cv.pdf && $T --format svg 'out/cv-{p}.svg' && $T --format png --ppi 96 'out/cv-{p}.png'
python compare_glyphs.py out/cv.pdf              # preview vs PDF glyph positions (needs PyMuPDF)
java TypstRenderSpike.java modern-2col.typ sample-resume.json pdf   # backend call path
java TypstRenderSpike.java probes/escape.typ sample-resume.json pdf # sandbox probes
```

## Findings (Typst 0.15.1, Windows, Inter bundled)

- **Layout identity:** all 2,430 text glyphs sit at the same position (within 0.2pt) on the same
  page in the SVG preview and the PDF. Same page count, same line breaks. Layout is computed once and
  then exported, so this holds by construction, not by luck.
- **Port effort:** `modern-2col` (sidebar running across pages, skill dots, chips, SVG icons) was
  ported in ~190 lines and compiled first try. Page breaks are controllable (`breakable: false`).
- **Rich text:** resume fields are TipTap HTML. Typst's built-in `xml()` parses it
  (p, ul/ol/li, strong, em, span color, br); needs `<br>` -> `<br/>` and named entities mapped.
  Production should do this conversion once, server-side, to a small JSON AST.
- **ATS text:** the PDF is tagged. PyMuPDF extracts clean text in reading order (sidebar, then main),
  æøå intact. Old xpdf splits letter-spaced headings ("K O N TA K T"): avoid `tracking` in ATS mode.
- **Speed/memory:** ~50-60 ms per render as a cold CLI process (PDF or SVG), ~70 ms via Java
  `ProcessBuilder` incl. temp dir; ~23 MB peak RSS. Binary 50 MB. No browser, no daemon.
- **Sandbox:** blocks reads outside `--root`, URLs inside SVG images, and infinite loops.
  It **does download `@preview` packages from the network**, even through a computed import string,
  so static checks are not enough: run the renderer without network access (verified that a dead
  proxy makes the import fail). Wall-clock timeout + memory limit still needed for pathological input.
- **Diagnostics:** compiler errors are precise (line, column, hint) - good input for an AI fix-up turn.

## Verdict

Typst works for this. Next: replace openhtmltopdf behind a renderer port in `port/out`, port the
built-in templates, and serve the preview as per-page SVG from the same compile.
