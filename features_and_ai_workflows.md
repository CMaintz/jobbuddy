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

