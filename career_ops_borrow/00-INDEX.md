# career-ops → AutoApplicant: borrowable-features analysis

A sequential, agent-driven audit of **career-ops** (`santifer/career-ops`, a mature local-first,
AI-agnostic job-search tool) for features/techniques worth borrowing into **AutoApplicant**.
Analyzed 2026-08 across three slices:

- [`01-modes.md`](01-modes.md) — the `modes/` prompt "brain" (evaluation, apply, cover, interview, offer)
- [`02-discovery-data-tracking.md`](02-discovery-data-tracking.md) — discovery scripts, data model, tracking, pipeline
- [`03-generation-evaluators-architecture.md`](03-generation-evaluators-architecture.md) — PDF/ATS rendering, evaluators, cost, architecture

## What career-ops is (and what does NOT transfer)

career-ops is a **local-first, files-are-canonical, AI-agnostic** CLI tool: Markdown prompt files
under `modes/` executed by whatever coding-agent CLI you run, with human-readable `.md`/SQLite as
storage. That defining architecture — **markdown-as-source-of-truth, the self-updater, the
file-drop plugin system, scaffolder/seeds, the Go TUI, multi-CLI entry files** — solves local
single-user file problems that AutoApplicant's Postgres + Spring + Angram stack already solves.
**Borrow the algorithms and prompt techniques; leave the file doctrine behind.**

## Prioritized roadmap (cross-slice synthesis)

### Tier 1 — high value, clear gap, low/medium effort
1. **Deterministic (non-LLM) fact gate** — regex/normalization check that every metric, employer,
   title, and tool in a generated document traces back to the source profile; **blocks the render**
   on an unsourced claim. Zero token cost, complements the LLM reviewer loop. *This is the single
   biggest gap AutoApplicant has today* (nothing currently guarantees claim→source). [slice 3]
2. **ATS rendering hardening** — Unicode→ASCII normalization (em-dash/smart-quotes/zero-width),
   ligature-kill (fi/fl break keyword search), and a post-render **page-budget gate** (measure true
   PDF page count, warn/hard-fail, never mutate layout to fit). Small, cleanly ports to PDFBox. [slice 3]
3. **Employer/posting-risk analysis, kept SEPARATE from the fit score** — ghost-job legitimacy
   tiering (Block G), trust/scam heuristics, compensation-reliability taxonomy, culture-screen
   capping + mandatory "verify before applying" warning, aggregated into one auditable **Risk
   Summary** where "— not evaluated" is first-class. Risk signals never contaminate the 1–5 score. [slices 1 & 2]
4. **Dedup/liveness hardening in ingestion** — JD **SimHash fingerprint** (catches agency re-posts
   under a different company that source+id/embedding dedup miss), a three-state **liveness** model
   (403/429/5xx → *uncertain*, never *dead* — false-expired is the costly error), and repost
   clustering. Ports as ingestion-pipeline stages + a couple of columns. [slice 2]

### Tier 2 — medium effort, strong value
5. **Golden-set eval regression harness** — offline `$0` replay fixtures asserting prompt/model
   changes don't degrade score/archetype agreement; run in CI. Needs a stable machine-readable
   output contract (a parseable score block). [slice 3]
6. **Spend-tier model routing + cheap pre-screen gate** — one config maps cheap/balanced/premium
   models; a cheap (or local CLI-agent) model triages/rejects non-fits *before* expensive enrichment
   or generation. Directly leverages the CLI-agent provider just added. [slices 2 & 3]
7. **Archetype detection + per-archetype framing** — classify each posting into role archetypes,
   then pull archetype-specific framing/proof points from the profile. (This is workstream #9.) [slice 1]
8. **Profile-model additions** — `narrative`, `compensation`, location/work-authorization,
   `culture_screen`, and `style` theming fields worth adding to the Master-CV model. [slice 3]
9. **Append-only status-transition ledger** — an immutable status-event table unlocks
   funnel-velocity / rejection-latency analytics beyond just "current status". [slice 2]
10. **Interview-side memory** — reusable **Story Bank** (interview analogue of writing-style memory),
    **STAR+R** (adds a reflection/seniority beat), and a **retracted-claims hard gate** (user-disowned
    claims can never resurface). [slice 1]

### Tier 3 — principles & opportunistic
- Provider-plugin contract + ~80-board public-ATS catalog (a shopping list for more connectors). [slice 2]
- SSRF/endpoint-privacy hardening for crawlers and any hosted-model calls (host allowlist,
  `redirect:error`, reject cleartext PII to non-loopback). [slices 2 & 3]
- Cross-cutting rules already partly adopted (reformulate-never-fabricate, tool-of-trade ban,
  JD-is-untrusted, never auto-submit) — codify the rest.

### Explicitly skip (local-first mechanics, no fit here)
markdown-as-source-of-truth doctrine · self-updater · file-drop plugins · scaffolder/seeds ·
Go TUI · atomic file-based number reservation · filesystem locks.

## Relationship to in-flight work
- Workstream **#9 (archetype/North-Star + metrics precedence)** = Tier-2 item 7 + the "metrics
  precedence" idea, and pairs naturally with Tier-1 item 3 (risk scoring) and item 1 (fact gate).
- The auto-reviewer loop, prompt hardening, and CLI-agent provider (already shipped) are the
  foundation the fact gate (1), eval harness (5), and spend-tier routing (6) build on.
