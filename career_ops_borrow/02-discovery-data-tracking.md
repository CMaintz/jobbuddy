# career-ops → AutoApplicant: Discovery, Data Model, Tracking & Pipeline

Analysis of the **discovery / data-layer / tracking / pipeline** slice of `santifer/career-ops`
(local clone at `C:\Users\akash\Projects\CLI-based\career-ops`), scoped to what is **net-new**
for AutoApplicant (Java/Spring + Angular, Postgres-backed).

AutoApplicant already has: a Postgres multi-source crawler (Jobindex/Jobnet/IT-Jobbank/Jobdanmark +
Greenhouse/Lever/Teamtailor/Cornerstone/Careerjet) with dedup-by-`source+id`, cleaning, AI enrichment,
embeddings, Typesense index, crawler-state, URL health checks, job-expiry/last-seen, ignored-jobs; a
personal LinkedIn connector with LLM keyword plans; semantic search + matching; application tracking
with statuses and outcome-lesson capture; and a structured "Master CV". Items below are filtered to
skip those.

---

## TL;DR — what actually transfers

The valuable stuff is **specific mechanics** (SimHash JD fingerprinting, liveness classification
vocabulary, repost clustering, trust scoring, the provider plugin contract, atomic number reservation,
reconcile/normalize/dedup passes, spend-tier model routing), NOT the "markdown-files-are-canonical"
doctrine. AutoApplicant is DB-backed and multi-user; the file-as-source-of-truth model is the one
thing that explicitly does **not** port (see the "Does NOT transfer" section). Nearly every borrowable
item is a small-to-medium pure-function port that slots into the existing ingestion pipeline as an
enrichment or gate.

---

## BORROWABLE ITEMS

### 1. JD-content SimHash fingerprint + cross-listing detection
**Source:** `fingerprint-core.mjs`, integration in `scan.mjs` (`formatScanHistoryRow`, `collectFingerprintHistory`, `findCrossListings`)

**What it does:** Computes a 64-bit SimHash over 3-token shingles of the normalized JD body, stored as
16 hex chars. Near-duplicate texts stay within a few Hamming bits, so any two postings can be compared
later without keeping the body. `findCrossListings` flags a new posting whose fingerprint is ≥0.92
similar to a recent posting **from a different company** — i.e. the same job re-listed by an agency
with the employer name stripped, which URL-dedup and company+role-dedup both miss.

**Why borrow:** AutoApplicant dedups on `source+id` and (per the modes doc) can do embedding-based
semantic search, but a *cheap, deterministic, zero-LLM* content hash for the specific
"agency re-post of a direct listing under a different company name" case is a real gap. Embeddings are
overkill and non-deterministic for exact-body dedup; SimHash Hamming distance is exact and reproducible.
It complements (doesn't replace) the Typesense/embedding layer.

**Port effort:** Small–Med. Pure algorithm → a Java `JdFingerprintService` (SHA-1 shingling, popcount
Hamming). Add a `jd_fingerprint CHAR(16)` column on the job/posting table, populate during the
enrichment stage, and add a cross-company near-dup check in the ingestion pipeline. Lives in:
pipeline change + schema (one column).

**Confidence:** H (self-contained, well-tested reference, obvious fit).

---

### 2. Liveness classification vocabulary (`classifyLiveness`)
**Source:** `liveness-core.mjs` (+ `liveness-api.mjs`, `liveness-browser.mjs`, `check-liveness.mjs`)

**What it does:** A pure function that classifies a fetched page as `active` / `expired` / `uncertain`
from `{status, requestedUrl, finalUrl, bodyText, applyControls}`. It carries a large, battle-hardened
pattern library: multilingual "position filled / no longer accepting / offre pourvue / stelle besetzt"
banners (accent-normalized first), anti-bot/Cloudflare interstitial patterns (→ `uncertain`, never
`expired`), 403/429/5xx → `uncertain` (throttling ≠ gone), `404/410` → `expired`, redirect-off-posting
detection (job-id token lost from final URL), apply-control detection in multiple languages, and a
min-content-length heuristic.

**Why borrow:** AutoApplicant has URL health checks and expiry/last-seen, but the *decision logic* here
encodes years of edge-case learning that a naive "HTTP 200 = alive" check gets wrong. The most valuable
insight for AutoApplicant: **a false "expired" is worse than uncertain** — because a wrongly-expired
posting gets permanently dedup-filtered out of future crawls. Their explicit `uncertain` bucket for
403/429/5xx/bot-walls prevents the compounding failure where "scanning harder earns a 429 → marked dead
→ never rescanned." Whatever AutoApplicant's health checker does today, it should adopt this
three-state (active/expired/uncertain) model and never mark a rate-limited/blocked page expired.

**Port effort:** Med. Port `classifyLiveness` as a Java `LivenessClassifier` (regex library + status
rules) and wire the three-state result into the existing URL-health / last-seen logic so `uncertain`
never triggers expiry. Also worth borrowing the **two-rung ladder** (`liveness-api.mjs`): try a
free ATS JSON endpoint first (a 200 on a per-job endpoint proves liveness with no browser), fall back to
headless render only when inconclusive. Lives in: pipeline change (health-check stage) + a small util.

**Confidence:** H (the pattern library alone is worth the port; directly improves an existing feature).

---

### 3. Repost clustering / ghost-posting detection (`detect-reposts.mjs`)
**Source:** `detect-reposts.mjs` (+ `role-matcher.mjs` for fuzzy title match)

**What it does:** Groups scan-history rows by normalized company, fuzzy-matches role titles, and flags a
company+role that appears 2+ times with **different URLs** inside a 90-day sliding window — the same
opening being repeatedly re-listed (a "ghost posting" / perpetually-open req signal). Only `added` rows
count (skipped/expired rows describe dead postings, not reposts). Includes an inverted-index optimization
so it stays near-linear on large employers instead of going O(n²) on fuzzy matches.

**Why borrow:** AutoApplicant tracks last-seen and dedups exact URLs, but doesn't (per the brief) surface
the *pattern* "this company keeps relisting this role under new URLs" — a strong signal that a posting is
a ghost/evergreen req worth deprioritizing. This is a genuinely useful, novel triage signal that the
existing dedup misses because the URLs differ.

**Port effort:** Med. Port as a scheduled analytics job (`RepostDetectionService`) over the postings
table grouped by normalized company + fuzzy title. Surface as a `repost_count` / `ghost_posting` flag on
the posting, feeding into ranking/matching. Lives in: use case / scheduled job + a derived flag column.
Note the sliding-window + union-find logic is the load-bearing part; the fuzzy title matcher
(`role-matcher.mjs`, token Jaccard ≥0.6 with baseline-token discounting) ports alongside it.

**Confidence:** M–H (clear value; needs a decent company-name normalizer, which AutoApplicant likely
already has for its dedup).

---

### 4. Lightweight trust/legitimacy scoring (`_trust-validator.mjs`)
**Source:** `providers/_trust-validator.mjs`

**What it does:** Enriches each scanned posting with a `trustScore` (0–100), `trustFlags[]`, and
`trustLevel` (high/medium/low) using cheap heuristics: invalid/missing apply URL, URL-shortener /
suspicious domain (bit.ly, forms.gle…), and company↔domain mismatch (skipped when the URL is on a known
ATS allowlist so a legit Greenhouse link isn't penalized). **Never drops jobs — flag only.** Fully
config-driven with a no-op fallback that scores everything 100.

**Why borrow:** A zero-cost, deterministic scam/ghost-posting filter is net-new for AutoApplicant. It's
a fast pre-filter that runs before any AI enrichment spend, and the flags become a UI badge ("low trust:
suspicious domain") and a ranking input. The "flag, never drop; ATS-allowlist exemption" design is the
right call for a web app where the user should see and override.

**Port effort:** Small. Pure heuristics → `TrustValidator` in the ingestion pipeline; persist
`trust_score` + `trust_flags` on the posting; expose in the Angular list as a badge. Lives in:
pipeline change + schema (2 columns) + small frontend badge.

**Confidence:** H.

---

### 5. Provider plugin contract (filesystem-convention registry)
**Source:** `providers/README.md`, `providers/_registry.mjs`, `providers/_types.js`, and per-board modules
(`greenhouse.mjs`, `ashby.mjs`, `lever.mjs`, `teamtailor.mjs`, `workday.mjs`, `bamboohr.mjs`, `breezy.mjs`, …)

**What it does:** Every job source is a self-contained module exporting `{ id, detect(entry), fetch(entry, ctx) }`.
A registry auto-loads all non-underscore files alphabetically (deterministic `detect()` precedence),
routes each portal entry by explicit `provider:` → local-parser → first `detect()` hit, and a malformed
provider is logged and skipped, never fatal. `ctx` is an injected HTTP transport with timeout + shared
user agent. **~80 providers** exist under this one contract, and adding one is: drop a file + a test.

**Why borrow:** AutoApplicant already has multiple crawlers, but if they're bespoke per-source classes,
this **uniform `detect`/`fetch` + auto-registry + injected HTTP context** pattern is a cleaner
architecture that makes adding an ATS trivial and isolates failures. Even more valuable is the sheer
**catalog**: ~80 public ATS/board integrations (Ashby, Workable, SmartRecruiters, Recruitee, Personio,
Workday, iCIMS, SuccessFactors, Teamtailor, Breezy, RemoteOK, WeWorkRemotely, TheHub, arbeitnow, etc.)
you can port board-by-board as new `JobSourceConnector` beans. Each provider is a self-documenting spec
of that board's public JSON API endpoint and shape.

**Port effort:** Med (the pattern: a `JobSourceConnector` interface + Spring registry with
`supports(entry)` / `fetch(entry, ctx)` and a shared `HttpJobFetcher`). Small per additional board once
the pattern exists. Lives in: new connector interface + pipeline wiring.

**Confidence:** H for the pattern; H for the connector catalog as a shopping list.

---

### 6. Provider SSRF hardening + list-endpoint enrichment patterns
**Source:** `greenhouse.mjs` (`assertGreenhouseUrl`, `redirect:'error'`, `/offices` enrichment),
`ashby.mjs` (host allowlist, backoff+jitter retry, `secondaryLocations` folding), `liveness-api.mjs`
(fixed-host API URL built from strict-charset path segments)

**What it does:** Two reusable disciplines. (a) **SSRF safety:** every provider validates the target
host against a hard allowlist AND passes `redirect: 'error'` so a server-side redirect can't smuggle the
request to an internal host; API URLs are built from a *fixed* host + strictly-validated path segments
(no `..`, no slashes). (b) **Zero-token location enrichment:** Greenhouse hides real cities in a separate
`/offices` endpoint (only fetched when a board actually shows work-model-only locations like "Hybrid");
Ashby's `secondaryLocations[]` are folded in so an EU-eligible role isn't misread as Canada-only.

**Why borrow:** Any server-side crawler that fetches user/config-derived URLs is an SSRF target;
AutoApplicant crawls many external hosts. The **allowlist + `redirect:error` + fixed-host-template**
pattern is a concrete hardening checklist. The location-enrichment bugs are exactly the kind that
silently drop good jobs from a location filter — worth knowing per-provider.

**Port effort:** Small (SSRF: a shared `assertAllowedHost` + no-follow-redirect fetch policy applied in
the HTTP client). Small per-provider for the enrichment quirks. Lives in: pipeline/HTTP-client change.

**Confidence:** H for SSRF discipline; M for the per-board enrichment (only matters for boards you crawl).

---

### 7. Atomic sequential number reservation (`reserve-report-num.mjs`)
**Source:** `reserve-report-num.mjs`

**What it does:** Allocates the next sequential report number atomically across concurrent processes
using `O_CREAT|O_EXCL` sentinel files (`NNN-RESERVED.md`) under a tracker lock, with retry-on-collision,
PID-liveness-checked GC of stale sentinels (4h TTL), and ownership tokens for safe release. It scans
existing report files AND tracker rows to compute the high-water mark.

**Why borrow (honestly, mostly a design lesson):** This is a clever solution to a **filesystem** problem
AutoApplicant *doesn't have* — Postgres gives you `BIGSERIAL`/sequences/`SELECT ... FOR UPDATE` for free.
So don't port the sentinel machinery. The transferable idea: if AutoApplicant ever generates
**user-facing sequential IDs** (per-user application numbers, report numbers) that must be gap-tolerant
and race-safe under parallel batch workers, use a DB sequence or an advisory-locked counter — and note
their subtle bug fix (a bare `YYYY-MM-DD.md` file being misread as report #2026, poisoning all future
numbering) as a reminder to make ID parsing strict.

**Port effort:** Small (a DB sequence) — but it's a "you already have this" item. Lives in: schema (a
sequence) if needed at all.

**Confidence:** H that the mechanism does NOT port; M that the sequential-ID concept is even wanted.

---

### 8. Reconcile / normalize / dedup maintenance passes
**Source:** `reconcile-pipeline.mjs`, `normalize-statuses.mjs`, `dedup-tracker.mjs`

**What they do:**
- **reconcile-pipeline:** idempotently moves postings that batch-mode already evaluated out of the
  "pending" inbox into "processed" (fixing the class of bug where a processed job re-surfaces and gets
  re-evaluated → duplicate work). Idempotent + dry-run + backup.
- **normalize-statuses:** maps all non-canonical status strings (multilingual aliases, bolded/dated
  variants, "DUPLICADO", "repost #N") to a canonical set derived from a single `states.yml`, moving
  provenance to notes. Key lesson: the canonical alias list is **derived from one source file**, not
  hand-maintained, so it can't drift.
- **dedup-tracker:** merges duplicate application rows (normalized company + exact role), keeping the
  highest score and the **most-advanced status** (via a `STATUS_RANK` ordering where `hired` outranks all,
  active > terminal), merging notes.

**Why borrow (mostly as principles):** In a DB world you don't need file-rewriting passes — constraints
and `status` enums prevent most of this at write time. But three ideas transfer directly: (a) a
**single-source-of-truth status enum with a derived alias map** (AutoApplicant likely has statuses; make
sure aliases/legacy values normalize from ONE definition); (b) a **status-rank / advancement ordering**
so that when two records for the same application collide, the more-advanced one wins (relevant to
outcome-capture and any re-import/merge); (c) an idempotent **"already-processed → don't re-evaluate"**
reconciliation as a guard in the ingestion pipeline (AutoApplicant's `source+id` dedup covers most of
this, but the *evaluated-state* reconciliation is a distinct concern).

**Port effort:** Small–Med as principles (status enum + rank comparator + an idempotency guard). Lives in:
schema (status enum/alias table) + use case (merge/reconcile logic).

**Confidence:** M (valuable as design guidance; the scripts themselves are file-format-specific).

---

### 9. Append-only status-transition ledger + observation logs
**Source:** `DATA_CONTRACT.md` (`status-log.tsv`, `salary-observations.tsv`, `assessments.tsv`),
consumed by `funnel-velocity.mjs`, `salary-gap.mjs`

**What it does:** Alongside the tracker (which holds *current state*), an **append-only ledger** records
every status transition (`tracker# / date / from / to / source / note`) and separate append-only logs for
salary observations and skills-assessments. The tracker stays the source of truth for *state*; the ledger
records *when transitions happened*, enabling funnel-velocity / rejection-latency analytics. Corrections
are new rows, never edits.

**Why borrow:** AutoApplicant tracks statuses and outcome lessons, but an **immutable transition history**
(vs. just current status) is what unlocks funnel analytics: time-in-stage, rejection latency, conversion
rates per source. This is a classic event-sourcing-lite pattern that fits Postgres perfectly (an
`application_status_events` table). The salary-observation and assessment logs are similar append-only
side-tables feeding compensation-gap and assessment-staleness analytics.

**Port effort:** Med. Add an `application_status_events` table written on every status change, plus
analytics queries (funnel velocity, rejection latency). Optionally salary/assessment event tables. Lives
in: schema (event tables) + use case (analytics) + frontend (funnel dashboard).

**Confidence:** H (natural DB fit, unlocks analytics AutoApplicant probably wants).

---

### 10. Spend-tier model routing (`_shared.md` Spend Tier)
**Source:** `modes/_shared.md` (Spend Tier section), applied in `modes/pipeline.md` / `modes/batch.md`
pre-screen gate

**What it does:** A single `spend_tier` config (economy/standard/premium) maps to a model lineup
(cheap/balanced/capable) in **one table**, and every other mode refers only to "the tier's model" — never
a hardcoded model name — so changing the lineup touches one row. On standard/premium, a **cheap
economy-model pre-screen** runs before the full evaluation: obvious mismatches are discarded (with an
auditable `discard.log` line) so expensive-model spend is spent only on plausible fits.

**Why borrow:** AutoApplicant does AI enrichment + generation across many jobs; a **two-stage
cheap-model-gate-then-expensive-model** flow plus a centralized spend tier is a direct cost lever. The
"cheap pre-screen filters before the expensive pass, with an auditable discard log" pattern maps cleanly
onto AutoApplicant's enrichment/matching pipeline (use a small model to reject obvious non-fits before
running full enrichment/embedding or cover-letter generation).

**Port effort:** Med. A `spendTier` setting + a model-routing table in the AI provider abstraction, and a
cheap pre-screen gate in the enrichment/matching stage with a persisted discard-reason. Lives in: use
case (pipeline gate) + config + AI provider adapter (ties into the existing `AiProviderPort`).

**Confidence:** H (clear cost/quality win; aligns with the existing provider-port work — task #8).

---

### 11. Scan-history recheck-after-days (avoid permanent dedup lock-out)
**Source:** `scan.mjs` (`scanHistoryPolicy`, `shouldDedupScanHistoryRow`, `collectSeenUrls`),
`normalizeUrlForDedup`

**What it does:** The dedup key is a **normalized** URL (strips locale/UTM/tracking params via an
*allowlist* — NOT a blanket strip, because some ATSes key the posting off a query param like Greenhouse's
`gh_jid`; lowercases host+path; drops trailing slash). Crucially, a configurable
`scan_history.recheck_after_days` lets an old "seen" URL become eligible for re-checking again, so a
posting isn't dedup-suppressed forever.

**Why borrow:** Two concrete lessons for AutoApplicant's `source+id` dedup and URL health: (a) URL
normalization for dedup should be an **allowlist strip**, since blanket-stripping query params collapses
distinct roles on some ATSes; (b) a **recheck-after-N-days** escape valve prevents a transiently-failed or
falsely-expired posting from being suppressed permanently (pairs with the liveness `uncertain` bucket in
item 2). AutoApplicant's last-seen/expiry logic should have an analogous re-crawl window.

**Port effort:** Small. A URL-normalization util (allowlist param strip) + a `recheckAfterDays` policy in
the crawler-state/dedup check. Lives in: pipeline change.

**Confidence:** M–H.

---

### 12. Pipeline liveness sweep + blacklist gate ordering
**Source:** `modes/pipeline.md`, `modes/auto-pipeline.md` (Steps 0.5 / 0.6), `data/blacklist.md`

**What it does:** Orchestration ordering worth copying: before spending any AI budget on a batch,
(a) run a **bulk zero-token liveness sweep** and drop dead URLs up front, then (b) a **blacklist gate**
(user's own do-not-apply company list — flag/confirm, never a scoring input), then (c) a cheap
pre-screen, and only then the full evaluation. Dead/blacklisted/mismatched postings never reach the
expensive stage.

**Why borrow:** This is the **gate ordering** for a cost-efficient pipeline: cheapest, most-decisive
filters first. AutoApplicant has ignored-jobs and health checks; formalizing the ordering (liveness →
blacklist/ignored → cheap pre-screen → full enrichment/generation) as an explicit pipeline stage sequence
prevents wasted enrichment spend. The blacklist-as-flag-not-filter (surface + confirm, user always wins)
is a good HITL default.

**Port effort:** Small (mostly a re-ordering of existing pipeline stages + ensuring ignored/blacklist is
checked pre-enrichment). Lives in: pipeline/use-case orchestration.

**Confidence:** M–H.

---

## Does NOT transfer to AutoApplicant (be explicit)

- **"Markdown files are canonical, SQLite is derived" doctrine** (`ARCHITECTURE.md`, `DATA_CONTRACT.md`).
  This is the *core* career-ops design and it is the **wrong fit** for AutoApplicant. career-ops is
  local-first, single-user, git-diffable, and multi-CLI: it keeps `data/applications.md` /
  `data/pipeline.md` / `reports/*.md` as the permanent source of truth precisely so the web UI, Go
  dashboard, and thousands of fork scripts all read plain files, and treats SQLite as a throwaway index.
  AutoApplicant is **Postgres-backed and multi-user with a web app** — its DB *is* correctly the source of
  truth. Adopting the file doctrine would mean giving up transactions, constraints, concurrent multi-user
  writes, and query performance for no benefit. Take the *mechanics* (fingerprint, liveness, trust,
  reposts, status-events) into DB tables/services; leave the file doctrine behind.

- **The system/user file boundary + `update-system.mjs` self-updater** (`DATA_CONTRACT.md` SYSTEM_PATHS /
  USER_PATHS). This exists because career-ops ships as files into a user's repo and must self-update
  without clobbering their data. AutoApplicant deploys as a server; this whole concern is handled by
  normal deploy/migration tooling. Not applicable.

- **Atomic filesystem report-number reservation** (item 7's mechanism). Solved for free by Postgres
  sequences. Port the *concept* only if user-facing sequential IDs are ever needed.

- **File-rewriting reconcile/normalize/dedup passes as scripts** (item 8's implementations). The
  *principles* transfer (derived status enum, status-rank merge, idempotent reconciliation); the
  markdown-table-rewriting code does not.

- **Tracker lock files / `pipeline-lock.mjs` / `portal-health-lock.mjs`.** These emulate transactions on
  a filesystem. Postgres row/advisory locks replace them entirely.

---

## Genuinely clever mechanics worth calling out

- **SimHash-over-shingles for zero-cost near-dup detection** (item 1) with an int32-halves Hamming
  popcount hot path and a `maxDistanceFor(threshold)` trick that turns a float similarity compare into an
  integer bit-distance compare in the O(offers×history) inner loop. Deterministic, dependency-free.
- **"A false expired is worse than uncertain"** (item 2) — the three-state liveness model and the
  403/429/5xx/bot-wall → `uncertain` rule that prevents a compounding "scan harder → get throttled →
  marked dead → never rescanned" failure.
- **Two-rung liveness ladder** (item 2): free ATS JSON endpoint first, headless browser only when
  inconclusive — with the subtlety that org-level endpoints (Ashby) require parsing the board to confirm
  *this* posting is still listed.
- **Repost clustering with an inverted-index gate** (item 3) that keeps fuzzy title matching near-linear on
  large employers by pre-filtering candidate pairs on shared *non-baseline* tokens before ever calling the
  expensive matcher.
- **Allowlist query-param stripping for dedup** (item 11) — the non-obvious insight that blanket-stripping
  query params breaks ATSes that key the posting off a param.
- **Provider auto-registry with deterministic alphabetical `detect()` precedence** and fail-soft loading
  (item 5) — a malformed board never breaks a scan.
- **SSRF-by-construction**: fixed API host template + strict path-segment charset + `redirect:error`
  (item 6).
- **Centralized spend-tier table + cheap pre-screen gate with auditable discard log** (item 10).
- **Append-only transition ledger separate from current-state tracker** (item 9) — event-sourcing-lite for
  funnel analytics.
