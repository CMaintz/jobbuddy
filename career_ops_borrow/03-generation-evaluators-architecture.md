# career-ops → AutoApplicant: Generation, Output Quality, Evaluators & Architecture

Analysis slice: document generation / ATS rendering, standalone/cheap-model evaluators,
eval-regression harness, and architecture/ecosystem cross-cutting rules.

Source repo: `C:\Users\akash\Projects\CLI-based\career-ops` (santifer/career-ops).
Target: **AutoApplicant** (Java/Spring + Angular). Only **net-new, additive** ideas are listed;
duplicates of what AutoApplicant already has are called out and skipped.

Legend: **Effort** = Small / Med / Large + *where*. **Confidence** = H/M/L.

---

## A. ATS PDF / text-layer verification techniques (highest priority)

### A1. Unicode → ASCII "ATS normalization" pass before render
- **Source:** `generate-pdf.mjs` → `normalizeTextForATS()` / `sanitizeText()` (lines 43–113).
- **What it does:** Right before HTML→PDF, it rewrites body-text-only (masks `<style>`/`<script>`,
  skips tag attributes/URLs) the Unicode characters that break PDF text extractors / legacy ATS:
  em/en-dash → `-`, smart quotes → straight, ellipsis → `...`, zero-width chars → removed, nbsp → space,
  arrows → ` to `/` from `, middot/bullet → ` | `, €/£ spelled to `EUR`/`GBP` (deliberately leaves ¥
  ambiguous), and `**bold**` → `<strong>`. Returns a `{replacements}` breakdown it logs.
- **Why borrow:** AutoApplicant already does server-side ATS-friendly rendering, but this is a
  *specific, battle-tested character blocklist* for the text layer that ATS parsers actually choke on.
  The reasoning (why each glyph is dangerous, why ¥ is left alone) is the valuable part — encode it as
  a normalization step in the PDF pipeline and log a per-document replacement count for QA.
- **Port effort:** Small (rendering — a pre-render text filter in the Java PDF assembly).
- **Confidence:** H.

### A2. Kill ligatures + choose an extraction-clean font stack
- **Source:** `templates/cv-template.html` `<head>` CSS; `modes/pdf.md` "PDF Design".
- **What it does:** Sets `font-variant-ligatures: none; font-feature-settings: "liga" 0,"clig" 0,"dlig" 0`
  on `*` and `body` because headless Chromium substitutes fi/fl/ffi with U+FB01/FB02/FB03 glyphs, which
  PDF extractors decode back to those codepoints — so "verification" becomes "veriﬁcation" and a literal
  ATS keyword search *misses it*. It also explains that variable woff2 fonts (Space Grotesk/DM Sans)
  inject spurious intra-word spaces ("SUM M ARY", "Germ any") in extraction, so the ATS template falls back
  to a static system sans stack (Liberation Sans/Arial/DejaVu) that is "verified clean via pypdf".
- **Why borrow:** These two failure modes (ligature codepoints and variable-font advance spacing) silently
  corrupt keyword coverage on an otherwise-correct PDF. AutoApplicant's ATS template should hard-disable
  ligatures and pin an extraction-verified static font stack; keep fancy fonts only for the "designed"
  (human-facing) template variant.
- **Port effort:** Small (rendering / template CSS).
- **Confidence:** H.

### A3. Post-render page-tree page count + page-budget gate (`--strict-pages`)
- **Source:** `generate-pdf.mjs` → `countRenderedPdfPages()` (reads the PDF catalog `/Pages`→`/Count`,
  not text) and `enforcePageBudget()` (lines 285–339).
- **What it does:** After Chromium writes the PDF, it parses the *actual* rendered page count from the PDF
  page tree (following the catalog reference so page-like text in content streams isn't miscounted), then
  enforces a page budget (default 2). Overflow warns-with-trimming-guidance by default; `--strict-pages`
  makes it a hard reject that still leaves the draft on disk for inspection but does **not** publish it.
  Layout is never mutated to force a fit — the check is deliberately separate from rendering.
- **Why borrow:** AutoApplicant renders server-side and can measure the true page count (PDFBox exposes it
  directly — even easier than career-ops's regex). A "max pages" policy per template/market (1-page vs
  2-page) with a soft-warn/hard-fail toggle is a clean quality gate that currently doesn't exist.
- **Port effort:** Small (rendering — PDFBox `getNumberOfPages()` + a policy flag on the render request).
- **Confidence:** H.

### A4. Font inlining as base64 data: URLs (rendering robustness)
- **Source:** `generate-pdf.mjs` → `inlineLocalFonts()` (lines 544–569).
- **What it does:** Rewrites `url('./fonts/x.woff2')` refs to base64 `data:` URLs before render, because a
  headless browser loading via `setContent()` silently drops `file://` subresources and falls back to
  system fonts — producing a PDF that looks right locally but wrong on the server. Includes a path-traversal
  containment check on font names.
- **Why borrow:** If AutoApplicant ever renders via a headless browser (or a templating engine that
  resolves relative asset paths), self-hosted fonts can silently vanish server-side. Inlining fonts (or
  ensuring absolute/classpath resolution) guarantees the server PDF matches the design. Lower priority if
  AutoApplicant uses a pure-Java PDF library that embeds fonts directly.
- **Port effort:** Small (rendering / infra).
- **Confidence:** M (depends on AutoApplicant's PDF engine).

### A5. Untrusted-content render hardening (JS off, non-local requests aborted)
- **Source:** `generate-pdf.mjs` → `renderHtmlToPdf()` (lines 626–644).
- **What it does:** Renders with `javaScriptEnabled: false` and a route handler that aborts every request
  that isn't `file:`/`data:`. Rationale in-code: the CV HTML is assembled from `cv.md` + the *job posting*
  + the eval report, and **job postings are untrusted input** — JS off stops an injected `<script>`,
  aborting non-local requests stops an injected `<img src="https://…">` from beaconing data out.
- **Why borrow:** AutoApplicant assembles documents from AI output + untrusted JD text. Any HTML/browser
  render path (or even email preview) should disable scripting and block network egress. This is a concrete
  SSRF/exfil-hardening checklist for the render step.
- **Port effort:** Small (rendering / infra) if a browser is in the loop; N/A for pure-Java PDF.
- **Confidence:** M.

### A6. LaTeX ATS trick: `\pdfgentounicode=1` + `\input{glyphtounicode}`
- **Source:** `templates/cv-template.tex` (lines 24, 45); `modes/latex.md`.
- **What it does:** Makes pdfLaTeX emit a ToUnicode map so the resulting PDF has *selectable, copy-pasteable,
  ATS-extractable* text instead of glyph-index soup. Plus a `sanitizeUrl()` + `escapeLatex()` split (URLs
  validated for scheme, everything else escaped) and a **replacer-function** substitution to stop a bullet
  containing `$'`/`$&` from splicing the template (build-cv-latex.mjs lines 162–169).
- **Why borrow:** Only relevant if AutoApplicant offers/plans a LaTeX export path. The escape-injection
  lesson (use a *replacer function*, never a replacement string, when the injected value may contain `$`)
  applies to *any* templating substitution — including Java `String.replaceAll`/`Matcher.replaceAll`, which
  has the identical `$`/`\` interpretation footgun.
- **Port effort:** Small (rendering) if LaTeX exists; the replacer-function lesson is a Small audit of
  existing template substitution code regardless.
- **Confidence:** M.

### A7. CV section-order guard (rendered vs source order)
- **Source:** `generate-pdf.mjs` → `validateCvSectionOrder()` + `SECTION_ALIASES` (lines 142–272).
- **What it does:** Extracts section headings from the rendered HTML and from the source `cv.md`, maps
  spelling variants (incl. diacritic-folded multilingual aliases) to canonical keys, and throws if the
  rendered order diverges from source order — catching an agent that silently scrambled sections.
  `--allow-reorder` downgrades to a warning for deliberately tailored orders.
- **Why borrow:** AutoApplicant builds a `StructuredDocument` from AI→JSON, so it already controls order —
  but a *guard* that the assembled document's section order matches an approved canonical order (or the
  master CV) is cheap insurance against a model reordering sections in a way that hurts the six-second scan.
- **Port effort:** Small (schema/validation — a check over the assembled document model).
- **Confidence:** M.

---

## B. Fact / anti-fabrication gate (very high value)

### B1. Deterministic post-generation fact gate (`verify-cv-facts.mjs`)
- **Source:** `verify-cv-facts.mjs` (whole file, ~725 lines, extensive self-test).
- **What it does:** A **non-LLM, regex-based** validator run *after* generation and *before* PDF render as a
  hard gate. It extracts from the generated document (a) metric-like claims — percentages, currency, `Nx`
  multipliers, and "N <noun>" counts (users, downloads, repos…) — and (b) explicit non-metric claims
  (employer via "worked at/joined", title via "served as/role:", tools via "using/tech stack:"), then
  fails if any claim is **absent from the source files** (`cv.md`, `article-digest.md`). Supports a
  `config/cv-facts.json` allow-list (`allow_metrics`, `allow_facts`, `forbidden_phrases`, `warn_phrases`),
  emits `pass`/`warn`/`block`. The cover-letter generator reuses the same gate (`assertFacts`) before
  importing the renderer, so a failed gate never leaves a misleading artifact.
- **Why borrow:** This is the single biggest net-new item. AutoApplicant hardens prompts against
  fabrication and runs a drafter→reviewer loop, but has no **deterministic, cheap, model-independent**
  check that every number/employer/title/tool in the final document actually traces to the candidate's
  source data. It's the perfect complement to the LLM reviewer: catches inflated metrics ("50 users" →
  "50k users") and invented authorship that an LLM reviewer can miss, at zero token cost, and it *blocks*
  the render. Especially valuable now that a cheap local CLI-agent provider can generate content.
- **Port effort:** Med (new use case + schema). Port the claim-extraction rules to Java regex, feed it the
  assembled document text and the candidate's structured profile/master-CV as the "source", add a
  per-user allow-list table. The self-test cases are a ready-made spec.
- **Confidence:** H.

### B2. Hard-won extraction robustness rules (port with the gate)
- **Source:** same file — the comment blocks are a spec of real bugs.
- **What it does (the rules worth copying):**
  - **Digit folding** (`foldDigits`): NFKC + Arabic-Indic/Devanagari/Thai/… digit blocks → ASCII, so a
    localized CV isn't silently unchecked (a gate that extracts *zero* claims falsely "passes"). Directly
    relevant to AutoApplicant's DA/EN (and any future) multilingual output.
  - **Thousands-separator normalization** (space/period/comma grouping all compare equal) so a truthful
    "16 181" / "16.181" / "16,181" doesn't false-fail.
  - **Magnitude-suffix binding** (`50k`/`1.5M` bound to the number) so a 1000× inflation isn't normalized
    away to "50 users".
  - **Symmetric application:** every fold/normalize is applied to *both* the document and the source, so it
    can only ever reveal *more* claims on both sides — never hide one. Good invariant to preserve when porting.
- **Why borrow:** These are the failure modes that make a naive fact-checker either useless (misses
  inflation) or annoying (false-fails truthful CVs). Copying the *rules*, not just the idea, saves
  AutoApplicant from re-discovering each one.
- **Port effort:** Med (bundled with B1).
- **Confidence:** H.

---

## C. Eval / regression harness for prompt-output quality (high priority)

### C1. Golden-set eval harness with `$0` deterministic replay (`eval-golden.mjs` + `evals/`)
- **Source:** `eval-golden.mjs`, `evals/golden/*.json`, `evals/fixtures/*__<model>.txt`, `evals/README.md`.
- **What it does:** A regression harness for evaluation *quality*. Each golden case is a synthetic JD +
  a frozen reference label `{archetype, score, provenance}`. The harness gets each candidate model's
  machine-readable `---SCORE_SUMMARY---` block (SCORE + ARCHETYPE), compares to the label, and exits 0/1 on
  an **archetype-agreement gate** (default ≥0.8), with `score` as a secondary ±0.5 tolerance-banded signal.
  Two modes: `--replay` reads recorded fixtures (offline, deterministic, no API key, CI-friendly) and
  `--live` shells out to the real evaluator. Tunables are named constants (`SCORE_TOLERANCE`,
  `MIN_ARCHETYPE_AGREEMENT`, `COST_PER_RUN_USD`). Reports mean |Δscore|, unscored count, median latency.
- **Why borrow:** AutoApplicant has ATS dimensional scoring and multiple providers but (per the brief) no
  **regression test that a prompt/model/provider change didn't degrade output quality**. This design is
  directly portable: freeze a small labeled set of JDs, snapshot the current evaluator's structured output
  as fixtures, and assert future changes stay within tolerance — in CI, deterministically, for free. The
  "agreement-with-reference, not absolute correctness" framing is exactly right for a judge you keep changing.
- **Port effort:** Med (new use case + infra). Build as a JUnit/integration test: golden JDs as resources,
  a stored-fixture replay path, an assertion on the parsed structured score/archetype. The machine-readable
  summary contract (C2) is the enabler.
- **Confidence:** H.

### C2. A stable machine-readable summary contract (`---SCORE_SUMMARY---`)
- **Source:** every `*-eval.mjs` emits it; parsed by `eval-golden.mjs` `parseSummary()`.
- **What it does:** Forces the model to end its output with a fixed, greppable block
  (`COMPANY / ROLE / SCORE / ARCHETYPE / LEGITIMACY`) so downstream tooling parses one contract regardless
  of which model/provider produced the prose. This is what makes cross-model eval, replay fixtures, and
  tracker rows trivial.
- **Why borrow:** AutoApplicant already produces structured JSON, so it has *a* contract — but the lesson is
  to keep a **provider-independent, versioned output contract** that the eval harness and any cheap/local
  model must satisfy. Add archetype + legitimacy fields to the eval output if not present (see F items).
- **Port effort:** Small (schema/prompt).
- **Confidence:** H.

### C3. Priority-based context-budget compression (`lib/context-budget.mjs`)
- **Source:** `lib/context-budget.mjs`; used by `openai-eval.mjs` `buildBudgetedPrompt()`.
- **What it does:** Tags `_shared.md` sections P0/P1/P2 (P0 = scoring/archetype/legitimacy/global rules,
  never dropped; P2 = voice/writing-style/ATS prose, dropped first) and, when a prompt approaches the model
  context window, trims lowest-priority sections and reports what it removed. Zero-dep ~4-chars/token
  estimate. Ensures the *scoring-critical* instructions always survive on small-context/cheap models.
- **Why borrow:** With a new local CLI-agent provider (often a smaller context window than a frontier API),
  AutoApplicant benefits from *prioritized* prompt assembly: never let writing-style/voice guidance crowd
  out the anti-fabrication + scoring rubric. Encode a priority order on prompt-template fragments and trim
  from the bottom when over budget.
- **Port effort:** Med (prompt / prompt-template system).
- **Confidence:** M.

---

## D. Cheap / local-model evaluation patterns (strengthens the CLI-agent path)

### D1. One evaluation, many backends via a single OpenAI-compatible runner
- **Source:** `openai-eval.mjs` (whole file); mirrored by `ollama-eval.mjs`, `gemini-eval.mjs`.
- **What it does:** The *same* evaluation logic (`modes/oferta.md` + `_shared.md` + `cv.md`) runs against
  any OpenAI-compatible endpoint by just varying `--url/--model/--key` — OpenRouter, Together, Groq,
  DeepSeek, Zhipu, and **local servers (LM Studio / llama.cpp / vLLM / Ollama `/v1`)**. Output parity is a
  stated invariant: the report structure is identical across all tiers/models.
- **Why borrow:** AutoApplicant already has OpenAI/Gemini/local-CLI providers; the net-new idea is the
  **strict output-parity contract across providers** plus treating "any OpenAI-compatible base URL" as one
  provider so adding Groq/DeepSeek/a local vLLM is config, not code. Reinforces the value of C2's contract.
- **Port effort:** Small–Med (infra — a generic OpenAI-compatible provider adapter parameterized by base URL,
  if not already present). Partly duplicate — flag only the parity-contract + local-endpoint angle.
- **Confidence:** M.

### D2. Endpoint privacy/security guard for eval calls
- **Source:** `openai-eval.mjs` lines 150–189.
- **What it does:** Before sending `cv.md` + full JD + API key to an endpoint: requires HTTPS for any
  non-loopback host (plain `http` allowed *only* for `localhost`/`127.0.0.1`/`::1`), requires an API key for
  hosted endpoints, and prints an explicit "your CV + JD will be sent to <host>" privacy line. PDF write path
  has a matching path-traversal guard (refuses to write outside the project root).
- **Why borrow:** AutoApplicant sends candidate PII + JD to external models. A codified rule — *reject
  cleartext transport to any non-local endpoint, surface which host receives PII, allow plain-http only for
  loopback* — is a clean, auditable safety control for the provider layer (esp. for user-configurable
  endpoints / BYO-key).
- **Port effort:** Small (infra — validation in the provider/HTTP client config).
- **Confidence:** H.

### D3. Cross-model prompt-caching, host-gated
- **Source:** `openai-eval.mjs` → `buildSystemMessage()` (lines 286–292).
- **What it does:** The large static prefix (shared rubric + cv, ~12K tokens) is byte-identical across every
  offer, so on gateways that honor an ephemeral `cache_control` breakpoint (OpenRouter/DeepSeek/…) it marks
  the system prefix cacheable; `api.openai.com` (which caches long prefixes automatically and rejects the
  field) gets a plain string. Prompt *text* is unchanged either way.
- **Why borrow:** AutoApplicant re-sends a large stable system prompt per JD. Structuring the prompt as a
  stable cacheable prefix + a small per-JD suffix, and setting provider-appropriate cache hints, cuts token
  spend on high-volume batches. (For the Anthropic path this maps to prompt-caching `cache_control` blocks.)
- **Port effort:** Small–Med (prompt / provider adapter).
- **Confidence:** M.

### D4. Spend-tier → model routing, kept model-agnostic in ONE table
- **Source:** `config/profile.yml` `spend_tier`; mapping table in `modes/_shared.md` (lines 43–59).
- **What it does:** A single `spend_tier: economy|standard|premium` profile knob selects the model tier
  (economy = cheapest/fastest, no extended thinking; premium = most capable + adaptive thinking). The
  *only* place concrete model names appear is one table; everything else references "the tier's model" so
  the routing stays model-agnostic and one table edit re-routes globally. Output format is invariant across
  tiers. A per-offer `auto_pdf_score_threshold` and a two-pass `triage_threshold` gate further cut cost
  (cheap triage pass filters before the expensive full eval; PDFs only auto-render above a score).
- **Why borrow:** AutoApplicant has multiple providers but (per brief) a spend-tier abstraction — "route
  high-volume scanning to the cheap/local CLI agent, high-stakes tailoring to a premium model, in one
  config knob" — is net-new and directly strengthens the new local-agent path. The triage-before-full-eval
  and score-gated-render patterns are concrete token-savers.
- **Port effort:** Med (schema + infra — a tier enum on the user profile + a routing layer over providers).
- **Confidence:** H.

---

## E. `profile.yml` fields worth adding to AutoApplicant's profile model

`config/profile.example.yml` is a rich, well-commented profile schema. Net-new fields worth adding
(AutoApplicant already has candidate basics, target roles, and archetypes — those are skipped):

- **`narrative` block** — `headline`, `exit_story`, `superpowers[]`, `proof_points[] {name, url, hero_metric}`.
  Gives the generator a stable, user-authored "voice/positioning" source to bridge into summaries
  (the pdf mode literally injects an "exit narrative bridge"). *Effort: Small (schema).* *Conf: H.*
- **`compensation` block** — `target_range`, `currency`, `minimum` (walk-away), `location_flexibility`.
  Feeds comp-fit scoring and comp-reliability tiering. *Small (schema).* *Conf: H.*
- **`location` authorization fields** — `authorized_in[]`, `needs_sponsorship`, structured `visa_status`.
  Drives a hard work-authorization blocker in scoring rather than free text. *Small (schema).* *Conf: H.*
- **`culture_screen`** — `require[]` + `deprioritize_if_absent` to structurally cap a "cultural fit"
  scoring dimension at 2/5 when the JD shows no evidence. A clean way to make a soft dimension enforceable.
  *Small–Med (schema + scoring).* *Conf: M.*
- **`re_apply_windows`** — per-company cooldowns (`same_role_days`, `cross_role_bucket`, `last_apply_date`)
  so the scanner skips recently-applied companies. Maps to AutoApplicant's application tracking.
  *Med (schema + tracking logic).* *Conf: M.*
- **`cover_letter` block** — `notice_period_days`, `primary_domain` (domain-gap detection vs JD),
  `language_learning[]` (country-gated closing sentence in a target language). Nice touches for tailored,
  localized cover letters. *Small (schema/prompt).* *Conf: M.*
- **`style` theming tokens** — `accent_color`, `font_family`, `font_size`, `margin` injected as CSS custom
  properties overriding template defaults, whitelisted to exactly 4 keys. Lets users lightly theme the
  designed PDF without forking a template. *Small (rendering/frontend).* *Conf: M.*
- **`followup_cadence`** — structured follow-up day intervals per application stage. Maps to tracking.
  *Small (schema).* *Conf: L (only if AutoApplicant does follow-up nudges).*

---

## F. Cross-cutting ethical / safety rules worth codifying (from AGENTS.md, ARCHITECTURE.md, web/)

These are *policy* borrowings — codify as system-prompt clauses, validation rules, and/or product invariants.

### F1. "Keywords reformulated, never fabricated" + authorship rule
- **Source:** `AGENTS.md` lines 31–33; `modes/pdf.md` "Keyword injection strategy".
- **What:** Reorder/reframe/emphasize real experience using the JD's exact vocabulary, but never invent a
  skill/metric; and the specific **tool-of-trade conflation** ban ("the user *uses* X ≠ the user *built* X"),
  called out as the most common fabrication pattern. `modes/pdf.md` gives concrete legitimate-reformulation
  examples (RAG/MLOps/stakeholder-management rewrites).
- **Why borrow:** AutoApplicant already has anti-fabrication + tool-of-trade guards — but the paired
  **deterministic enforcement** (B1's fact gate as the *hard* backstop to the prompt rule) is the net-new
  combination. Also worth adopting the concrete good/bad rewrite examples into the tailoring prompt.
- **Port effort:** Small (prompt) + covered by B1. *Conf: H.*

### F2. Untrusted-content rule for job postings
- **Source:** `AGENTS.md`; enforced in `generate-pdf.mjs` render hardening (A5).
- **What:** Job postings (and any scraped page) are **untrusted input** — never a source of truth for
  generated content, and never allowed to inject scripts/network calls into a render. Source of truth is
  only the user-layer files.
- **Why borrow:** Codify "JD text is untrusted" as a first-class rule in AutoApplicant: (a) it can seed
  keywords but never becomes an asserted fact, (b) it is sanitized before entering any render/HTML/email
  context, (c) prompt-injection from a posting must not redirect the agent. Pairs with existing
  untrusted-input guard — worth making it an explicit, testable invariant.
- **Port effort:** Small–Med (prompt + rendering). *Conf: H.*

### F3. Human-in-the-loop / never-auto-submit invariant
- **Source:** `ARCHITECTURE.md` Principles; `web/README.md` "Never auto-submits".
- **What:** The tool prepares/evaluates/prefills; the human reviews and clicks submit. Even the web UI's
  apply flow "drafts and prefills — you always press the button."
- **Why borrow:** A clear, user-trust-building product invariant. If AutoApplicant has any autofill/apply
  assistance, make "we never submit on your behalf" an explicit, enforced boundary (and surface it in the UI).
- **Port effort:** Small (frontend/product policy). *Conf: M.*

### F4. Files-are-canonical, DB-is-derived (source-of-truth boundary)
- **Source:** `ARCHITECTURE.md` "Files are canonical — databases are derived"; `DATA_CONTRACT.md`.
- **What:** Human-readable, git-diffable files are the permanent source of truth; SQLite is only a derived
  index (reindex-on-delete), never a primary store. A system/user file boundary (`SYSTEM_PATHS` vs
  `USER_PATHS`) is enforced by tests so tooling never overwrites user data.
- **Why borrow (adapted):** This is CLI/local-first doctrine and does **not** port literally to a DB-backed
  web app. The *transferable* principle: keep a **clear boundary between system-owned config/templates and
  user-owned data**, and make the candidate's structured profile/master-CV the *single source of truth* that
  generation and the fact gate both read — don't let generated artifacts or scraped JD text become a second
  source of truth. Encode as an invariant/tests, not as a filesystem layout.
- **Port effort:** Small (schema/architecture discipline) — mostly a design rule + a test. *Conf: M.*

### F5. Subagent/adversarial-audit cost guardrail (opt-in, not default)
- **Source:** `modes/pdf.md` Step 20 "Hiring-manager audit — off by default, opt-in only"; `AGENTS.md`.
- **What:** A *separate* adversarial reviewer (role-plays the hiring manager, returns keep/cut/rewrite
  per bullet) exists but is **opt-in** because it costs an extra subagent dispatch + web research; it is not
  re-run after the user acts on it (avoids doubling cost for an already-decided verdict).
- **Why borrow:** AutoApplicant already has a drafter→reviewer loop; the net-new nuances are (a) gate the
  *expensive extra* adversarial pass behind an explicit flag / score threshold, and (b) don't re-run an audit
  the user has already acted on. Straightforward cost discipline for multi-pass generation.
- **Port effort:** Small (prompt / orchestration). *Conf: M.*

---

## G. What does NOT transfer (be honest)

- **Self-updater (`update-system.mjs`, ~60KB).** Purpose is safely `git`-pulling new *system files* from
  upstream into a user's local checkout without touching `USER_PATHS`, re-exec'ing the updater, and checking
  out only allowlisted paths. This is entirely a **local-install, file-based distribution** mechanism.
  AutoApplicant is a deployed web app — it ships via normal CI/CD and DB migrations. **No port.** (The only
  reusable seed of an idea is the system-vs-user boundary discipline, already captured in F4.)
- **File-based plugin system (`plugins/`, `plugins-registry/`, `_engine.mjs`, `_lock.mjs`).** Directory-drop
  plugins discovered via `manifest.json` + `config/plugins.yml`, gated by presence of `.env` keys, loaded
  by a Node engine. This is a **local CLI extensibility** model (integrations that "need a key or talk to an
  external service"). A Spring web app expresses the same intent with Spring's DI/plugin beans, feature
  flags, and per-tenant credential storage — the *concept* (opt-in, key-gated integrations, default-off)
  transfers, the *file-drop mechanism* does not. **No literal port; adopt the "default-off, key-gated
  integration" principle only.**
- **`scaffolder/` + `seeds/`.** Dev-time generators for new providers/portal configs and seed data — CLI
  developer ergonomics, not a product feature. **No port.**
- **Go TUI dashboard (`dashboard/`).** A terminal UI over the local files, explicitly "isolated from the
  core, never required." AutoApplicant already has an Angular dashboard. **No port.** (Only worth a glance
  for *what* it surfaces — pipeline table, funnel/analytics, action queue — which the Next.js `web/` app
  also lists and AutoApplicant largely already has.)
- **Multi-CLI entry files** (`CLAUDE.md`, `CODEX.md`, `GEMINI.md`, `.agents/skills/`, the open agent-skill
  standard wrappers). Purely about interop with different AI *coding* CLIs reading Markdown prompt files —
  irrelevant to a web app whose "agent" is a backend provider. **No port.**
- **The Next.js `web/` app itself.** It's an alpha *local-first file viewer* (no server, no DB) — the
  opposite of AutoApplicant's architecture. Nothing to port structurally; only the *feature list* and the
  "never auto-submit" invariant (F3) are of interest.

---

## Priority shortlist (do these first)

1. **B1/B2 — deterministic fact gate** as a hard pre-render block (highest ROI; complements the LLM reviewer).
2. **A1 + A2 + A3 — ATS text normalization, ligature-kill/font-stack, and post-render page-budget gate.**
3. **C1/C2 — golden-set regression harness** with replay fixtures + a stable structured-output contract.
4. **D4 + D2 — spend-tier routing (cheap/local for volume) + endpoint privacy guard** for the provider layer.
5. **E — add `narrative`, `compensation`, `location`-authorization, and `style` fields** to the profile model.
6. **F1/F2 — codify "reformulate-never-fabricate", tool-of-trade, and JD-is-untrusted** as enforced rules.
