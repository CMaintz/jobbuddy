# career-ops `modes/` — Borrowable Ideas for AutoApplicant

Analysis of the `modes/` prompt "brain" in santifer/career-ops (English/base files only; localization exists via per-language subfolders `ar/ da/ de/ …` but is out of scope). These are the reusable **prompt-engineering and scoring techniques**, filtered to NET-NEW items that AutoApplicant does not already have. Effort and confidence are my estimate for porting into a Java/Spring + Angular app.

AutoApplicant already has, and I have **excluded**: dimensional weighted ATS scoring, JSON-return/server-assembled document pipeline, drafter→reviewer critique loop, writing-style memory, anti-fabrication/tool-of-trade/prompt-injection/privacy hardening, upskill gap analysis, outcome→generation feedback, multi-source + LinkedIn crawling, Typesense semantic search.

Legend — Effort: S/M/L. Confidence: that the item is genuinely additive (not already covered).

---

## Tier 1 — High-value, clearly additive prompt/scoring techniques

### 1. Posting-Legitimacy / Ghost-Job Assessment ("Block G")
**Source:** `oferta.md` (Block G), `_shared.md` (§Posting Legitimacy)
**What it does:** A separate, non-scoring qualitative assessment of whether a posting is a real, active opening. Aggregates signals — posting freshness, apply-button state, JD tech-specificity, requirements realism, layoff/hiring-freeze news, reposting churn, role/company fit — into a 3-tier verdict (High Confidence / Proceed with Caution / Suspicious) with a per-signal table (Positive/Neutral/Concerning) and legitimate-explanation caveats.
**Why borrow:** AutoApplicant scores *fit* but has nothing that tells a user "this posting itself may be a ghost job / not worth your effort." Orthogonal to the dimensional score, so it slots in cleanly as a new output section without disturbing existing scoring.
**Port effort:** M — new AI use case + a `PostingLegitimacy` DTO/enum (tier + signals list) rendered as its own report block; some signals (reposting churn) need crawler history you already store.
**Confidence:** High

### 2. Legitimacy is DECOUPLED from the fit score (architectural rule)
**Source:** `_shared.md` (§Posting Legitimacy, §Scoring System), `oferta.md`
**What it does:** Hard rule that Block G never touches the 1–5 fit score. All the red-flag/legal signals below are "reported separately, orthogonal to ghost-job detection." Prevents one salient warning from silently tanking (or inflating) an otherwise-good match.
**Why borrow:** A design principle for AutoApplicant's scoring service: keep "is this a good fit for me" and "is this posting/employer safe/real" on separate axes so neither contaminates the other. Cheap to adopt, prevents a whole class of scoring bugs.
**Port effort:** S — a scoring-service convention + separate response fields.
**Confidence:** High

### 3. Culture-Screen capping with mandatory surfacing
**Source:** `_shared.md` (§Scoring System, "How to score Cultural signals"), `oferta.md` (Block A Culture screen)
**What it does:** Reads a user-declared `culture_screen.require` list, hunts the JD/research for evidence, and **caps** the culture dimension at 2/5 when evidence contradicts requirements (or when `deprioritize_if_absent` and no evidence). Any role scoring 4.5+ overall but ≤2 on culture must carry an explicit "High technical fit, unconfirmed/poor culture fit — verify before applying" warning. Names *what* was missing, not just a number.
**Why borrow:** AutoApplicant has a `cultureFit` dimension but (per the brief) it's a weighted contributor. The capping + mandatory-warning pattern is net-new: it stops a strong technical match from silently burying a culture problem. A high-value refinement to the existing `cultureFit` axis.
**Port effort:** M — scoring rule + user-config for `require` criteria + a warning flag on the result.
**Confidence:** High

### 4. Compensation-Reliability Taxonomy (company-type → trust tier + component split)
**Source:** `oferta.md` (Block D), `_shared.md` (§Company Type and Compensation Reliability)
**What it does:** Before trusting any salary number, classify the *hiring entity* into ~10 company types (public big-tech, VC startup, agency/outsourcing, local SMB, commission-heavy, staffing listing, gov/academic, …), each mapped to a comp-reliability tier. Then split advertised comp into advertised range / likely guaranteed base / variable-conditional cash / expected stable cash / non-cash benefits, and flag low-reliability phrases ("OTE", "up to", "comprehensive salary", "attendance bonus", "13th salary"). Generates 3–6 tailored HR verification questions. Classifies the *legal employer*, not the brand.
**Why borrow:** Completely net-new. AutoApplicant does no compensation-reliability reasoning. This turns a raw JD salary string into a trust-scored, decomposed estimate + a "what to ask HR" checklist — high user value and purely prompt-driven.
**Port effort:** M — new use case; a `CompAnalysis` schema (company type, reliability tier, component breakdown, verification questions). Taxonomy tables live in the prompt.
**Confidence:** High

### 5. Advertised-figure verbatim discipline ("never blend advertised with researched")
**Source:** `oferta.md` (Block D), `_shared.md`
**What it does:** The JD's own advertised salary is stored **verbatim** as its own field (`advertised_comp`), always the first row of the comp table, never merged with or replaced by researched market estimates. `null` when the JD says nothing — never estimated.
**Why borrow:** A clean anti-fabrication rule specific to compensation that complements AutoApplicant's existing anti-fabrication stance. Guarantees the user can always see exactly what the posting claimed vs. what the model inferred. Trivial to adopt.
**Port effort:** S — one field + a prompt rule.
**Confidence:** High

### 6. STAR **+R** (Reflection) story structure as a seniority signal
**Source:** `oferta.md` (Block F), `interview/plan.md`, `interview/practice.md`, `interview/debrief.md`
**What it does:** Extends STAR with a mandatory **Reflection** column ("what I learned / what I'd do differently"). Framed explicitly as the junior-vs-senior differentiator: juniors describe what happened, seniors extract lessons. Every generated/mapped behavioral story carries it, and the practice/debrief loops prompt for it when missing.
**Why borrow:** If AutoApplicant generates interview prep or behavioral answers at all, adding the Reflection beat is a cheap, high-signal quality lift. Even if not, it's a strong pattern for the CV/cover "achievement" framing.
**Port effort:** S — prompt template change (add Reflection field to story schema).
**Confidence:** High

### 7. Reusable Story Bank (accumulating master-story memory)
**Source:** `oferta.md` (Block F "Story Bank"), `interview-prep.md` (Step 5), `interview/*.md`
**What it does:** A persistent `story-bank.md` of 5–10 master STAR+R stories. Each new evaluation checks whether a needed story exists; if not, appends it. Interview modes map bank stories to likely questions (strong/partial/none fit) and flag gaps. Over time the candidate builds a reusable, de-duplicated answer set adaptable to any interview.
**Why borrow:** This is the interview-side analogue of AutoApplicant's writing-style memory — a *content* memory rather than a *style* memory. Net-new: a per-user accumulating store of proven stories that generation and prep both draw from.
**Port effort:** M — a `StoryBank` entity/table + retrieval into generation + gap-detection use case.
**Confidence:** High

### 8. Retracted-Claims hard gate (candidate-owned "never say this again" list)
**Source:** `interview/practice.md`, `interview/debrief.md`
**What it does:** A `retracted-claims.md` the candidate builds when they concede mid-practice that a claim isn't defensible under pressure. It's a **hard gate**: the model must never resurface a retracted claim in any "stronger version" or suggested answer, even if the candidate just said it. The mode proactively offers to record retractions.
**Why borrow:** A novel negative-memory primitive that pairs with anti-fabrication: not just "don't invent," but "don't repeat this specific thing the user disowned." Directly protects the user from over-claiming in interviews and in generated documents.
**Port effort:** M — a per-user retracted-claims store + a filter applied in every generation/prep prompt.
**Confidence:** High

### 9. Interviewer-side Red-Flag Detector ("is this company safe to join?")
**Source:** `interview-redflag.md`, cross-ref in `oferta.md` (Risk Summary row)
**What it does:** Analyzes the **interviewer's** turns across saved interview transcripts for four structural signals (scope ambiguity, defensive closure after strong answers, evaluator competency gap, contradictory process signals), scores them (present-in-1-session +1, pattern-in-2+ +2), and assigns a warning tier (None / ⚠️ Enter with eyes open / 🚩 Reconsider). At the top tier it suggests (never auto-writes) a blacklist row.
**Why borrow:** Entirely net-new axis: post-interview company-safety analysis distinct from win/loss tracking. Even a lightweight version ("paste your interview notes, get structural red flags") is differentiated. Depends on having interview transcripts stored.
**Port effort:** L — requires an interview-transcript store + new analysis use case + tiering schema.
**Confidence:** Med (high value, but gated on transcript capture which AutoApplicant may not have)

### 10. Archetype detection driving downstream framing
**Source:** `_shared.md` (§Archetype Detection), `oferta.md` (Step 0, Blocks B/E/F), `_profile.template.md`
**What it does:** Classify each JD into one of N role archetypes (or a hybrid of 2) from keyword signals, then let the archetype steer *everything downstream*: which proof points to prioritize (Block B), how to rewrite the summary (Block E), which stories to prep (Block F). A user profile maps their own projects to each archetype.
**Why borrow:** AutoApplicant tailors CV/cover per-JD, but archetype detection is a reusable intermediate abstraction that makes tailoring consistent and explainable ("this is an LLMOps role → lead with evals/observability"). Note: this overlaps with existing task #9 (Master-CV archetype/North-Star layer) — worth aligning.
**Port effort:** M — a classification step feeding the generation prompts + a user archetype-map in profile. (Domain archetype taxonomy is app-specific.)
**Confidence:** Med (partially overlaps planned work; the *downstream-steering* discipline is the additive part)

---

## Tier 2 — Solid, additive, narrower scope

### 11. Geo-mismatch check (structured location field vs JD body)
**Source:** `oferta.md` (Block A, Geo-mismatch check)
**What it does:** Cross-checks the posting's structured location/remote field against the JD body; flags a contradiction ("field says remote but body says 'hybrid, 3 days/week'") with the **verbatim** JD line. Careful negation handling (ignores "no onsite requirement", optional offsites). Silence = no flag.
**Why borrow:** Cheap, high-precision, catches a common real frustration (remote-tagged roles that are secretly hybrid). Purely prompt-level given you already have structured location metadata from crawling.
**Port effort:** S — prompt rule + one flag field.
**Confidence:** High

### 12. Work-authorization / sponsorship 4-tier classifier
**Source:** `oferta.md` (Block A, Work-authorization check)
**What it does:** Compares candidate work rights against JD sponsorship language into exactly four tiers (✅ Sponsors / ➖ Not needed / ⚠️ Unstated-and-neutral / ⛔ No-sponsorship-hard-stop), with verbatim JD quoting and an explicit rule that only ⛔ is a real blocker (Unstated is neutral, not a penalty).
**Why borrow:** Turns a fuzzy, high-stakes concern into a deterministic, non-alarmist tier. The "silence ≠ refusal" discipline avoids over-penalizing. Net-new structured output.
**Port effort:** S — prompt rule + enum field; needs candidate work-auth in profile.
**Confidence:** High

### 13. Employment-classification-risk signal (contractor-vs-employee)
**Source:** `oferta.md` (Block G signal 6)
**What it does:** Detects language associated with contractor/services status (1099, T4A, "invoice for services", "consulting agreement", IR35 phrasing) **plus** a corroborating omission (no benefits/PTO/end-date), and emits a non-alarmist "confirm classification before accepting" note. Descriptive, never prescriptive.
**Why borrow:** Net-new protective signal; catches misclassification that a fit-score never would. The two-condition trigger (explicit wording + corroborating omission) is a good false-positive guard worth copying wholesale.
**Port effort:** S — prompt rule + advisory note field. Jurisdiction term-list can be a small config table.
**Confidence:** Med

### 14. AI-buzzword vs. infrastructure-maturity mismatch
**Source:** `oferta.md` (Block G signal 7)
**What it does:** Flags when heavy "AI/transformation/enablement" language sits on top of signals that the org isn't ready (mid-level IC expected to "drive AI transformation", ≤5-person team owning org-wide transformation, legacy-heavy industry). Requires 2+ of 3 signal classes. Suggests interview probes about actual system maturity.
**Why borrow:** Genuinely novel and topical — protects candidates from "the AI role is really digitization/backlog cleanup." Purely prompt-driven pattern recognition over JD text.
**Port effort:** S — prompt rule + advisory note.
**Confidence:** Med

### 15. Pay-transparency range-width heuristic (pure arithmetic red flag)
**Source:** `oferta.md` (Block G signal 13)
**What it does:** If a posting states a comp range with matching currency+period, computes width; flags when `top − bottom > 0.5 × bottom` as "unusually wide → band likely undecided or posting templated/aggregated." Strong guardrails: skip on missing/ambiguous currency or period, skip on mismatched currencies or non-positive floor. Explicitly framed as a generic heuristic, not a legal check.
**Why borrow:** Zero-cost, deterministic, and computed server-side (fits AutoApplicant's "weighting/logic server-side" style perfectly — it doesn't even need the LLM). Good "ask the recruiter for the real band" nudge.
**Port effort:** S — a pure server-side function on parsed comp bounds.
**Confidence:** Med

### 16. Blacklist gate (user-owned do-not-apply list) as a GATE, never a signal
**Source:** `oferta.md` (Blacklist gate), `apply.md` (Step 5), `interview-redflag.md` (suggestion)
**What it does:** A user-maintained `blacklist.md`; on a match the flow **stops and asks** ("X is on your blacklist since {date}: {reason}. Still want to evaluate/apply?") rather than silently refusing or proceeding. Never auto-populated; a blacklist entry never changes any score — it is a gate, not a scoring input. The red-flag detector can *suggest* additions but never writes them.
**Why borrow:** Clean human-in-the-loop pattern + the discipline that a gate must not leak into scoring. Net-new user-control primitive.
**Port effort:** S–M — a blacklist entity + a pre-evaluation/pre-apply gate + confirm UX in Angular.
**Confidence:** Med

### 17. Risk Summary block (one-screen aggregation, "not evaluated" is first-class)
**Source:** `oferta.md` (§Risk Summary)
**What it does:** A fixed-order summary table aggregating every risk signal's verdict onto one screen (legitimacy tier, employment classification, culture screen, interview red flags, AI/infra mismatch). Three states per row: ✅ clear / ⚠️ finding / **— not evaluated**. "Aggregation only, zero new judgment" — never re-scores. Making "not evaluated" explicit means an all-✅ summary is trustworthy.
**Why borrow:** Excellent UX/architecture pattern: collect scattered warnings into one auditable panel, and never hide a check that didn't run. Directly applicable to AutoApplicant's evaluation report frontend.
**Port effort:** M — a summary DTO assembled from the individual signals + an Angular panel.
**Confidence:** High

### 18. "Sell senior without lying" / downlevel strategy block
**Source:** `oferta.md` (Block C)
**What it does:** Detects JD level vs candidate's natural level for the archetype, then produces (a) a "sell senior truthfully" plan — specific archetype-adapted phrases and achievements to highlight — and (b) an "if they downlevel me" plan — accept-if-fair, negotiate a 6-month review, define promotion criteria.
**Why borrow:** Net-new strategic advice layer beyond match scoring; turns a level-mismatch from a silent penalty into an actionable plan. Prompt-only.
**Port effort:** S — prompt section + a strategy field in the report.
**Confidence:** Med

### 19. Triage Brief — a compact scoring context separate from full eval
**Source:** `_brief.template.md`, referenced by `triage.md`, `patterns.md`
**What it does:** A tiny (~1.5–2K token) user "brief" — archetypes, comp hard-floor, location-scoring rubric, hard-DQ criteria (instant FAIL), soft red flags (−0.5 each), priority-override companies — used for fast first-pass go/no-go triage instead of loading the full CV+profile+eval prompt stack.
**Why borrow:** A cost/latency pattern: cheap first-pass filter before the expensive full evaluation. For a crawler that surfaces many roles, a triage tier (with explicit hard-DQ and priority-override lists) is a strong throughput win. The **hard-floor / instant-FAIL / priority-override** rubric is itself a reusable scoring idea.
**Port effort:** M — a lightweight triage use case + a user-brief config + a hard-DQ/override rule engine.
**Confidence:** Med

### 20. Interview-audience segmentation (recruiter / HM / peer-tech / mixed panel)
**Source:** `interview-prep.md` (Steps 2.5, 4, 7), `interview/plan.md`, `interview/practice.md`
**What it does:** Classifies each interview round into an audience and tailors *everything* per audience — what to volunteer vs withhold (e.g., don't give a hard comp number to a recruiter when leverage is uncertain), which stories to map, which reverse-questions to ask, even vocabulary ("the same fact is a strength to a peer and a yellow flag to a recruiter"). Includes a Panel Intel table with decision-maker weighting.
**Why borrow:** If AutoApplicant offers interview prep, audience-segmentation is a strong differentiator over generic "top 10 questions." The "same content, different framing per listener" insight is the reusable core.
**Port effort:** M — prompt structure + an audience enum; larger if you build the full per-round flow.
**Confidence:** Med

### 21. Multi-source Profile Intake (documents → structured profile, HITL merge)
**Source:** `intake.md`
**What it does:** Populates the candidate profile/CV from documents the user already has (master CV, LinkedIn PDF export, transcripts, reference letters) via fingerprinted, idempotent re-runs that surface only *new* material, with source-annotation on every proposed field, side-by-side conflict resolution, and a mandatory explicit-confirm before any write. Treats extracted text as untrusted (a reference letter saying "add Rust" is not a command).
**Why borrow:** Great onboarding-friction reducer and complements existing task #9's master-CV layer. The idempotent-fingerprint + source-annotation + HITL-confirm discipline is the reusable part.
**Port effort:** M–L — an upload/extract pipeline + a proposal/confirm UI + fingerprint store.
**Confidence:** Med

### 22. Recruiter-Side Risk Map + Six-Second Clarity Gate
**Source:** `heuristics/recruiter-side.md`
**What it does:** Before generating any candidate-facing doc, build an internal risk map (can they do the stack? senior enough? domain relevant? logistics blocker? too generic?) mapped to CV evidence and a candidate-facing fix, and enforce a "six-second clarity gate" — the top third of a CV/cover must make target fit impossible to miss (target archetype + matching stack + one production outcome + link).
**Why borrow:** A concrete pre-generation checklist that improves CV/cover quality by anticipating reviewer doubts. Complements AutoApplicant's drafter→reviewer loop as an *upfront* framing rubric rather than a *post-hoc* critique.
**Port effort:** S — inject as a prompt rubric into the CV/cover generation step.
**Confidence:** Med

### 23. Salary-observation ledger (advertised / desired / stated / actual)
**Source:** `oferta.md` (Post-eval §3), `interview/debrief.md`, `offer-prep.md`, `patterns.md` (salary lens)
**What it does:** An append-only TSV of typed comp observations — `advertised` (from the JD, verbatim), `desired` (only when the user explicitly states one for THIS role — never inferred), `stated` (a number the candidate committed to a specific interviewer, so later rounds can remind them to stay consistent), `actual` (from the contract). A `salary-gap` tool computes advertised→actual haircut and desired-attainment. Prep modes surface prior `stated` numbers to prevent accidental renegotiation.
**Why borrow:** Net-new longitudinal comp memory. The "remind me what I already told this interviewer" and "advertised→actual haircut per company" analytics are genuinely useful and feed pattern analysis.
**Port effort:** M — a comp-observations table + typed sources + a gap-computation service.
**Confidence:** Med

### 24. Channel-yield analysis with causal humility (ATS vendor / agency)
**Source:** `patterns.md` (vendorAnalysis, viaChannelAnalysis)
**What it does:** Groups submitted applications by ATS vendor (Greenhouse/Lever/Ashby/Workday) and by recruiter/agency channel, computes advance-rate per channel, and reports **channel yield, never bias** ("X% of your apps go through {vendor} and it advances far less — route those companies via referral instead"). Respects a `sufficientSample` flag: below threshold it's an observation, never a recommendation. Cites the "correlated rejections through a shared screener" research motivation.
**Why borrow:** AutoApplicant tracks applications; this is a net-new analytics lens with an important guardrail (never claim discrimination, gate recommendations on sample size). The causal-humility framing is a reusable rule for *all* AutoApplicant analytics.
**Port effort:** M — analytics query over the tracker + sample-size gating + careful copy.
**Confidence:** Med

### 25. Draft-generation trigger threshold (score-gated auto-draft)
**Source:** `oferta.md` (Cover Letter Draft auto-generated after Block G; "Draft Application Answers only if score ≥ 4.5")
**What it does:** Auto-generates a *starter* cover-letter draft at evaluation time (real CV bullets + JD-mirrored keywords + flagged gaps + explicit placeholders for the parts needing user input), and only drafts application-form answers when the fit score clears a high bar (≥4.5). Cheap artifacts are eager; expensive/committal ones are score-gated.
**Why borrow:** A good cost-control + UX pattern: produce a low-effort scaffold immediately, gate the higher-effort committal content behind a quality threshold. The "explicit placeholder for what needs the human" convention is reusable in AutoApplicant's generation.
**Port effort:** S — a score-gate condition + a draft-with-placeholders prompt variant.
**Confidence:** Med

---

## Tier 3 — Interesting but lower priority / conditional

### 26. Jurisdiction-aware legal-signal family (config-table-driven)
**Source:** `oferta.md` (Block G signals 10–12, 14), `apply.md` (Steps 5c/5d), `offer-prep.md`, `interview-redflag.md` (Step 2c)
**What it does:** A family of signals driven by versioned YAML data tables (agency-licensing, immigration-status-overreach, jurisdiction-prohibited content like "Canadian experience"/salary-history, protected-grounds interview questions, minimum-wage lawyer-routing). Each carries legal basis + effective date + `as_of` + sources, is agent-judged (never keyword-matched), states only verifiable facts about the posting/statute, **never asserts the employer broke the law**, and routes conclusions to "ask your lawyer."
**Why borrow:** The *architecture* is highly reusable even if you skip the legal content: **externalize volatile/jurisdictional knowledge into cited, dated data tables the prompt reads, rather than baking it into the prompt or trusting model memory** — with a hard "never state law from memory" rule. Adopt the pattern; the specific legal tables are heavy and maintenance-intensive.
**Port effort:** L (full legal coverage) / S (just the "cited dated config-table + never-from-memory" pattern for any volatile knowledge).
**Confidence:** Med (pattern: High; the legal content itself: Low priority for AutoApplicant)

### 27. Offer-Prep "reading companion, describe-never-judge" posture
**Source:** `offer-prep.md`
**What it does:** A contract-reading companion that walks an offer clause-by-clause (verbatim quote + plain-English meaning + neutral tags like `[commonly negotiated]`/`[ask your lawyer]`/`[differs from what you were told]`), lists notable **absences**, cross-checks against what was promised (a `notes.md` of verbal/email promises with source+date), and outputs two lists: questions-for-your-lawyer and items-to-raise-with-employer. Hard guards: never "safe to sign", no severity ratings, no online research, never headless, always ends with a fixed disclaimer.
**Why borrow:** Net-new offer-stage capability. The reusable ideas: **verbatim-quote-then-explain**, **notable-absences detection** (what a contract *doesn't* say), **promises-vs-document consistency check**, and the "describe, route judgment to a human, fixed disclaimer" posture for high-stakes advice.
**Port effort:** L — a new document-ingestion + clause-tagging use case + offer artifact storage.
**Confidence:** Med (high value if AutoApplicant covers offer stage; otherwise out of current scope)

### 28. Bounded research budget (anti-runaway guardrail)
**Source:** `oferta.md` (§Bounded Research Budget), `_shared.md` (§Subagent delegation)
**What it does:** Hard caps on company/comp research: e.g. ≤5 total web searches per evaluation, prefer multi-answer queries, stop early, never delegate to a recursive/deep-research skill, never spawn nested agents (which "can burn tens of millions of tokens"). Research is always inline and bounded.
**Why borrow:** If AutoApplicant ever adds web-augmented research or agentic tool-use, this is the cost-safety rule to copy verbatim. Even now it's a good principle for any bounded external-lookup step.
**Port effort:** S — a query-count cap in whatever research/tool step exists.
**Confidence:** Med (conditional on having web-research at all)

### 29. Liveness gate before expensive work (URL inputs)
**Source:** `oferta.md` (§Liveness gate), `apply.md` (Step 5 preflight)
**What it does:** For URL inputs, confirm the posting is still live (real title/JD/apply path vs 404/expired/redirect-to-generic-careers) **before** running a full evaluation — "a dead link must never reach Block A; it wastes a full evaluation, report, and PDF on phantom content." Reuses the fetched snapshot downstream.
**Why borrow:** AutoApplicant crawls, so postings can go stale between crawl and generation. A cheap liveness check before spending tokens/compute on generation is a clean efficiency guard.
**Port effort:** S — a HEAD/GET liveness check in the pre-generation path (you likely already fetch pages).
**Confidence:** Med

---

## Deliberately EXCLUDED (duplicates AutoApplicant, or not prompt-brain)

- **Cite-exact-CV-lines when matching / keyword-reformulate-never-fabricate / tool-of-trade conflation** (`_shared.md`, `oferta.md` Block B, `_writing.md`) — AutoApplicant already has anti-fabrication incl. tool-of-trade + "silence beats invention." (The *"map each JD requirement to an exact CV line in a table"* presentation format is a minor borrowable if AutoApplicant doesn't already show the mapping — S effort, Low-priority.)
- **Writing-style calibration / Voice-DNA anti-slop / ATS unicode-normalization** (`_writing.md`, `heuristics/recruiter-side.md`) — AutoApplicant already has writing-style memory; these largely duplicate it.
- **Untrusted-input / prompt-injection framing everywhere** — AutoApplicant already has the untrusted-input guard.
- **Outcome recording + tracker feedback** (`outcome.md`, `patterns.md` funnel) — AutoApplicant already tracks applications with outcome→generation feedback. (The *misfit* signal in `patterns.md` Step 1b — "your fluent answers cluster on archetype X but you keep applying to Y" — IS additive, but depends on interview transcripts; folded conceptually into items #9/#20.)
- **`discover.md` (ATS board resolution), `scan`/`pipeline`/`batch`/`auto-pipeline`** — crawler/plumbing, and AutoApplicant already has its own multi-source + LinkedIn crawler + Typesense.
- **Spend-tier model routing** (`_shared.md`) — infra/config, not a prompt technique.
- **Localization subfolders** — noted as present; not a technique to port (AutoApplicant would do i18n its own way).

---

## Overall take
The single biggest gap this "brain" fills for AutoApplicant is **posting-and-employer risk analysis that is deliberately kept OFF the fit score** — ghost-job legitimacy tiering, culture-screen capping, compensation-reliability decomposition, and a family of orthogonal red-flag signals, all aggregated into a one-screen Risk Summary where "not evaluated" is a first-class state. The second cluster is **interview-side memory and structure** — STAR+R, an accumulating Story Bank, a Retracted-Claims hard gate, audience-segmented prep, and an interviewer-behavior red-flag detector. The recurring architectural discipline worth internalizing project-wide: *keep gates/signals separate from scores, quote sources verbatim, externalize volatile knowledge into cited dated tables instead of prompt/model memory, and route high-stakes judgment to the human with a fixed disclaimer.*
