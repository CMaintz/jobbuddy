# CV & Cover-letter quality improvement plan

> Working plan for the quality audit follow-up (branch `career-ops-features`).
> Audience: Danish market, software-developer candidate, currently new-grad / early-career.
> Status legend: ☐ todo · ◐ in progress · ☑ done · ⊘ dropped/handled elsewhere

## Context corrections found while planning

- **#2 (guardrails on CV) is already DONE** — commit `c6529bc` added a shared
  `GeneratedContentGuards` that runs the fact gate + retracted-claims gate on the tailored-CV
  path as well as letters. Remaining follow-up (out of scope here): an LLM drafter→reviewer
  pass over the *structured* CV JSON (the current reviewer only revises a text body).
- **#1 was mis-framed, and re-investigated after user pushback.** Section placement is
  app-controlled in the **resume-builder**, but with caveats:
  - The `rbSection` directive applies a flex `order` from the section's index within its
    column, so drag-reordering reorders sections in the **styled preview/PDF** in *all*
    templates (not just multi-column). Good.
  - The `leftColumn`/`rightColumn` split is a *fixed* main-vs-sidebar assignment; the drag UI
    only reorders *within* a column + toggles visibility. It can't move a section across the
    boundary, and the split only visually matters on sidebar templates.
  - **Real gap:** the ATS text-PDF export `resumeDataToAts()` (`ats-pdf.service.ts`) builds
    sections in a **hard-coded order and ignores the column config entirely** — so builder
    reordering has no effect on the ATS-safe export that actually gets submitted.
- **#3 has orphaned scaffolding.** `GenDefaults.length` ("Standard") already exists client-side
  but is never sent to the backend or used in any prompt ("no backend home yet"). This is why
  past word-target attempts didn't work — the value never reached the model.

## Work items

### ☑ #1 — ATS export ordering + stage-aware default + prompt clarity (depends on #6)
1. **ATS export honors layout:** make `resumeDataToAts()` emit sections in the order (and
   respect the visibility) defined by `settings.leftColumn`+`rightColumn`, instead of its
   current hard-coded order. This is the substantive fix.
2. **Stage-aware default + make backend order authoritative (fixes the "dead ordering"):**
   `CvDocumentAssembler` emits sections in a **career-stage-aware order** (new-grad/student →
   Education + Projects above Experience; mid+/senior → Experience-first), and
   `structured-doc-mapper` derives the resume-builder's default `leftColumn`/`rightColumn`
   **from the document's section order** instead of the hard-coded `INITIAL_SETTINGS`. This
   turns the previously-inert backend ordering into the authoritative default; the user can
   still drag to override.
3. **Prompt clarity:** in `composeCvTailoringPrompt`, make explicit that the AI **selects and
   emphasises content within sections**; **section order/placement is handled by the app**.

### ⊘ #2 — Deterministic guardrails on CV — DONE in `c6529bc`. (Note only.)

### ☑ #10 — LLM drafter→reviewer pass on the structured CV
- The letter path gets a fresh-context reviewer; the CV does not. Add a structured-CV reviewer
  that critiques `TailoredCvContent` against the posting + writing profile and returns a revised
  version, **preserving `sourceId`s and the JSON schema**. Re-run the fact/retracted guards on
  the revised content. Config-gated like the letter reviewer.

### ☑ #3 — Length targets (Danish-market best practice)
- Thread the existing `GenDefaults.length` from apply/quick-apply into the generate requests.
- Backend: add a `lengthPreference` (`SHORT|STANDARD|DETAILED`) to the generate use cases and
  emit concrete targets in the prompt:
  - Cover letter / application text: 1 page max; ~3–4 short paragraphs;
    SHORT ≈ 200 words · STANDARD ≈ 300 · DETAILED ≈ 380.
  - CV: page budget by stage (new-grad → 1 page; experienced → up to 2);
    ≤ 4–5 bullets on recent roles, fewer on older; condense oldest-lowest-value first.
- Settings: keep the existing length control; ensure options map to the enum.

### ☑ #4 — Cover-letter structural scaffolding
- In the letter branch, add a soft structure: hook → evidence paragraph grounded in a **named**
  role/project → company-fit (using Verified Company Facts when present) → close/CTA. Keep it
  guidance, not a rigid template; respect the length target from #3.

### ☑ #5 — Skills grouping / cap
- Cap the emitted skills list (24) and preserve the AI's keyword-first ordering.
- **Category grouping (done end-to-end):** the real `ProfileSkill.category` is carried through
  `CareerProfileForAi.skillCategories` → `StructuredDocumentItem.category` (assembler tags each
  skill) → `ResumeSkill.category` → `skillGroups()` in resume-state → grouped render in all six
  layouts + both ATS text exports (`resumeDataToAts` / `structuredDocToAts`). Skills-form gains a
  category input (with a datalist of common groups). Categories come from the user's own data, not
  AI invention. Uncategorised skills collapse to the previous flat rendering.

### ☑ #6 — Career-stage signal
- New identity-free `CareerStage` enum (STUDENT, NEW_GRAD, EARLY_CAREER, MID_CAREER, SENIOR,
  LEAD, CAREER_CHANGER).
- Add `careerStage` to `CareerTarget` (record + entity + migration + persistence + service +
  DTO), surface in `CareerProfileForAi`, and use it in both prompts for stage-appropriate
  framing (new-grad: elevate education/projects/internships; never fabricate years).
- Expose via the career-target API so the frontend can drive #1's default layout.

### ☑ #7 — Refresh keyword coverage after review
- The reviewer pass must return updated `keywordCoverage` / `matchedKeywords` /
  `missingKeywords`; `generateDocument` uses the reviewer's metadata when the body changed so
  the ATS report describes the delivered text.

### ☑ #8 — Danish metric nouns in the fact guard
- Add common Danish metric nouns (brugere, kunder, medarbejdere, år, måneder, uger, dage,
  timer, projekter, ansøgninger, virksomheder, …) + synonyms to `DocumentFactGuard`, since the
  default output language is Danish.

### ☑ #9 — Diagnostic ATS score
- Rework `AtsReportBuilder.scoreFromCoverage` (and `basic()`) so the score actually tracks
  coverage instead of flooring at 65 — a poor match should look like a poor match.

## Sequencing
#6 → #1 → #3 → #4 → #5 → #7 → #8 → #9, then a single backend + frontend build at the end.
