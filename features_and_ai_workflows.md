# Features & AI Workflows

# Job Sources

Implemented crawler connectors (feed the shared ingestion pipeline):

- Jobindex, Jobnet, IT-Jobbank, Jobdanmark (Danish boards, RSS/JSON)
- ATS boards: Greenhouse, Lever, Teamtailor, Cornerstone OnDemand
- Careerjet (aggregator)
- **LinkedIn Jobs** — personal-use, low-volume connector over the public `jobs-guest` endpoints (`LinkedInConnector`). Kept off the shared 4-hour schedule; runs on its own jittered `LinkedInCrawlScheduler`. Search keywords come from per-user **LLM-generated query plans** (`linkedin_query_plan`), crossed with configured locations, rotated per run, recency-filtered, with randomized delays and a per-run cap.

Planned / not yet implemented: The Hub, Ofir, WorkinDenmark, generic company career pages.

---

# Job Ingestion Pipeline

```text
Crawler
→ Raw Data
→ Extraction
→ Normalization
→ Deduplication
→ AI Enrichment
→ Search Index
→ Matching Engine
```

## Deduplication & Liveness

- **Exact dedup** — by `source` + `sourceJobId` (re-crawls refresh `lastSeenAt` instead of re-inserting).
- **Cross-listing dedup** — a 64-bit **SimHash** content fingerprint (`SimHash`, from an external reference implementation)
  of the cleaned description clusters the same role re-listed under a different company/URL (the
  agency-repost case) via a shared `duplicate_group_id`. Flag-only — jobs are grouped, never dropped.
- **Liveness (3-state)** — `JobUrlProbePort` classifies postings as ALIVE / INCONCLUSIVE / GONE /
  GONE_SOFT. Only 404/410 (and, after two sightings, "no longer available" markers incl. Danish)
  deactivate a posting; 403/429/5xx are treated as inconclusive (bot protection), **never** as
  proof the job is gone. Complements crawl-presence staleness expiry.

---

# Search & Filtering

## Standard Filters

- title
- company
- location
- salary
- technologies
- languages
- remote/hybrid
- seniority

---

## Advanced Preferences

### Positive

- startups
- backend-heavy
- remote-first
- product companies
- modern tech stacks

### Negative

- consulting firms
- recruiter agencies
- specific companies
- outdated technologies

---

# Matching Engine

# Recommendation Explainability

The system should explain WHY jobs are recommended.

Example:

- strong Java overlap
- backend-heavy role
- Kubernetes alignment
- remote preference matched

---

# Recommendation Feedback Loops

Users should be able to:

- like/dislike recommendations
- request more jobs like a posting
- request fewer jobs like a posting
- hide recommendation categories

---

# Skill Taxonomy Layer

Maintain normalized technical skill relationships.

Example:

```text
Java
→ Spring
→ Spring Boot
→ Hibernate
```

Purpose:

- semantic matching
- inferred skill relationships
- missing-skill analysis
- transferability scoring

---

# Matching Engine

## Layer 1 — Hard Constraints

- location
- salary
- work authorization
- remote preferences

---

## Layer 2 — Semantic Matching

Compare:

- CVs
- user profiles
- job descriptions
- saved jobs
- previous applications

using embeddings.

---

## Layer 3 — Behavioral Learning

Learn from:

- saved jobs
- ignored jobs
- applied jobs
- interview conversions

---

# Structured Career Profile Workflow

The platform should revolve around a structured “Master / Brutto CV” profile.

Users maintain:

- experience
- technical skills
- projects
- certifications
- education
- achievements
- portfolio links

The AI should generate tailored outputs from this structured profile.

## Career Targeting (archetypes / North-Star)

A `career_target` record (`GET`/`PUT /api/v1/profile/career-target`) stores the identity-free
targeting layer behind archetype-aware generation: **target archetypes** (declared role-types),
a **North-Star** statement, a positioning **narrative** (distinct from the CV summary), and
**culture requirements**. These are folded into the contact-free AI context (`CareerProfileForAi`),
so generation leads with the candidate's declared archetypes/North-Star when they align with the
posting. (Compensation expectations and document style already exist as `Profile.desiredSalary*`
and `DocumentTheme`.)

---

# Project-Centric AI Grounding

Projects should be treated as first-class profile entities.

Projects should support:

- descriptions
- technologies
- architecture details
- GitHub links
- measurable outcomes
- business impact

The AI should emphasize the most relevant projects per role.

---

# AI CV Features

- ATS optimization
- keyword analysis
- quantified achievement suggestions
- readability improvements
- role-specific tailoring

## Posting Risk Assessment

The CV-vs-job analysis (`/api/v1/ai/analyze`) returns a **risk block kept strictly separate
from the fit score** (`RiskAssessment`, framing from an external reference implementation):
- **Legitimacy** — HIGH_CONFIDENCE / CAUTION / SUSPICIOUS / NOT_ASSESSED (ghost-posting cues:
  stale/vague postings, contradictory or unrealistic requirements, no concrete team/role detail).
- **Risk signals** — a short list of `{label, severity, note}` items.
- **Compensation reliability** — HIGH / MEDIUM / LOW / UNKNOWN (how far advertised pay is real
  base vs variable / "up to" / commission).

Risk **never** adjusts the 0-100 score or its dimensions. The prompt is instructed to surface
signals, never accuse, and note legitimate explanations — the human decides. Surfaced in the
analysis UI as its own card (legitimacy + pay-reliability pills, signal list).

## ATS Render Hardening

At PDF render time (`PdfRenderingService`, techniques from an external reference implementation):
- **Unicode→ASCII text-layer normalization** — smart quotes, en/em dashes, ellipses,
  non-breaking/thin spaces, and zero-width characters are folded to plain ASCII so ATS
  parsers (which read the embedded text, not the glyphs) extract clean keywords. Models
  routinely emit these characters; decorative separators are left intact.
- **Post-render page-budget check** — measures the true page count of the rendered PDF
  (CV ≤ 2 pages, other documents ≤ 1) and warns, or fails when
  `app.pdf.page-budget.strict=true`. Layout is never silently mutated to fit.

---

# AI Application Generation

## Inputs

- selected job
- selected CV
- prompt template
- writing style memory
- custom instructions

---

## Outputs

- editable CV
- editable cover letter
- editable application text
- recruiter outreach message
- follow-up messages

---

# Automatic Reviewer Loop

After generation, a fresh-context **reviewer pass** critiques the draft (a "demanding hiring
manager") against the posting and the user's writing profile — missed keywords, weak/generic
framing, overreaching claims, style mismatches — then returns a revised version plus a critique
list. Wired into document generation (`AiService.generateDocument`), config-gated:

- `app.ai.auto-review.enabled` (default `true`) — each pass is one extra LLM call.
- `app.ai.auto-review.max-iterations` (default `1`) — loop stops early once a pass reports no further critique.

Also available as a manual on-demand endpoint (`ReviewDocumentUseCase`). This realizes part of
the "optimization loops" idea from Future Features.

---

# Prompt Safety & Anti-Fabrication

Fixed, server-side guardrails on every generation/analysis prompt (not user-editable):

- **No fabrication** of skills, experience, credentials, or outcomes — frame adjacent experience or leave the gap visible.
- **No tool-of-trade conflation** — using a technology is not building it; never claim the candidate authored a project/tool unless the profile attributes it.
- **Silence beats invention** — omit details not in the profile rather than manufacture them.
- **Untrusted-input guard** — scraped/posted job text (incl. crawled LinkedIn/board HTML) is treated as data to evaluate, never as instructions (prompt-injection defense).
- **Privacy** — identity fields (name, contact, photo, links) are never sent to the AI.

## Deterministic Fact Gate

A model-free backstop (`DocumentFactGuard`, ported from an external reference implementation's `verify-cv-facts.mjs`):
after generation it extracts metric-like claims — percentages, currency figures, multipliers,
and `<number> <metric-noun>` counts — from the document and flags any not supported by the
source career profile, catching invented or inflated numbers at **zero token cost**. Digit-folding
(multilingual) and thousands-separator normalization are applied symmetrically to document and
source, so a truthful number never false-fails. Config: `app.ai.fact-guard.enabled` (default `true`),
`app.ai.fact-guard.mode` = `warn` (log, default) | `block` (fail generation). Complements the LLM
reviewer loop — the reviewer's honesty rules are the first line of defense, the fact gate is the
deterministic net.

---

# AI Provider Options

Generation is provider-pluggable via `app.ai.generation-provider`:

- `openai` (default) / `gemini` — API providers.
- `claude-cli` / `codex` / `cli` — a **local CLI agent** (Claude Code / Codex): prompts are piped to
  the agent's stdin (`app.ai.cli.command`, default `claude -p`), so generation runs on a flat-fee
  subscription instead of per-token API cost.

Embeddings (`app.ai.enrichment-provider`) must remain a real API — CLI agents produce text, not
vectors. The split (generation → CLI, embeddings → cheap API) captures the cost savings while
keeping semantic search working. Best for on-demand generation, not high-throughput bulk enrichment.

**Spend tiers** — each operation class routes to a model tier: `app.ai.enrichment-tier` (default
`economy`) and `app.ai.generation-tier` (default `standard`), each resolving to the provider's
`economy-model` / `standard-model` / `premium-model` (falling back to `model`). So bulk enrichment
runs on the cheap/fast model while on-demand generation can be dialled up to premium — a single knob
for cost vs quality. Defaults preserve the current models (no behaviour change).

---

# Document Types

The system should model:

```text
CV
CoverLetter
ApplicationText
RecruiterMessage
FollowUpMessage
```

Cover letters and application texts are separate concepts and should be generated differently.

---

# Language Handling

The platform should support:

- Danish generation
- English generation
- bilingual workflows

Default language should be inferred from:

- job listing language
- company locale
- posting metadata

Users should always be able to override language.

---

# Prompt Template System

Users should be able to:

- create prompt templates
- save templates
- version templates
- categorize templates
- duplicate templates
- use long-form instructions

---

# Prompt Composition

```text
System Prompt
+ User Strategy Prompt
+ CV Context
+ Job Description
+ Writing Style Memory
+ Output Constraints
= Final Prompt
```

---

# Writing Style Memory

Store:

- previous applications
- successful applications
- tone preferences
- vocabulary patterns
- sentence structure

Goal:

Generate applications that sound authentic.

---

# Application Tracking

```text
Saved
→ Preparing
→ Applied
→ Recruiter Contact
→ Interview
→ Technical Test
→ Final Round
→ Offer
→ Rejected
→ Archived
```

---

# Funnel Velocity

Every application status change writes an immutable row to an append-only ledger
(`application_status_event`). `GET /api/v1/analytics/funnel-velocity` derives the **average
time-in-stage** between consecutive transitions (e.g. how long roles sit in "applied" before
"interview"), enabling velocity / rejection-latency analytics beyond the current status alone.

---

# Dashboard Widgets

- recommended jobs
- pending applications
- upcoming interviews
- AI recommendations
- analytics
- weekly insights

---

# Future Features

## Chrome Extension

- save jobs
- autofill applications
- inline AI analysis

---

## Email Integration

- parse recruiter emails
- detect interview invites
- auto-update application states

---

## Autonomous Agents

Potential future capabilities:

- automatic application generation
- semi-autonomous applying
- optimization loops
- autonomous prioritization

