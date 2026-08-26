# Skill elicitation — design proposal

> Question asked: should the app capture skills interactively (a conversational Q&A, or a large
> industry→skill taxonomy) rather than only importing them from a CV or LinkedIn PDF?
>
> **Verdict: yes — but as evidence capture, not skill enumeration.** The distinction decides
> whether this feature helps or actively hurts.

## Why "a gargantuan list of industries and their skills" would not work here

It is the obvious design, and this codebase is unusually hostile to it:

- **Generation refuses unproven skills by design.** `HONESTY_RULES` forbids claiming anything the
  profile cannot back up, `ClicheGuard` strips the adjectives people reach for instead of evidence,
  and `DocumentFactGuard` blocks unsupported figures. A skill checked in a list, with nothing
  behind it, is exactly what those guards are built to suppress. Adding 300 checkboxes either does
  nothing, or tempts keyword stuffing the guards then have to fight.
- **The market rules say the same thing louder.** The single most-cited Danish rejection reason in
  `danish_market_playbook.md` is the unproven adjective: *prove, don't assert*. A longer list of
  nouns is more to assert and no more to prove.
- **The evaluator already measures the right thing.** `DocumentQualityEvaluator`'s `evidence`
  dimension counts *named, concrete* profile items that reach the letter — employers, projects,
  figures. It does not reward list length. Whatever we build should move that number.

So the scarce resource is not skill *names*. It is skill *evidence*: the one concrete thing the
candidate did with each skill, which a letter can cite and an interviewer can probe.

## What already exists (and should not be rebuilt)

| Piece | What it already does |
|---|---|
| `skill_taxonomy` (V012) | Hierarchical skills with parents, categories and aliases — seeded |
| `profile_skill` | `proficiencyLevel`, `yearsExperience`, `usedInProduction`, `category` — **rich, and mostly empty** |
| `interview_story` (V059) | STAR+R records with tags — an evidence store, already built |
| `SkillGapService` | "What does my market demand that I lack", from the user's real pipeline plus embedding-matched jobs |
| `DocumentQualityEvaluator` | Measures whether evidence actually reaches the output |

The gap is not analysis and not storage. It is that nothing ever *asks*, so `profile_skill`'s
interesting columns stay null and `interview_story` stays empty unless the user volunteers prose.

## Proposed shape: three passes, only one of them AI

### Pass 1 — Inventory (deterministic, instant, free)
Start from what import already found, then expand along `skill_taxonomy` adjacency: someone with
Spring Boot is asked about JPA, Hibernate, Maven — the taxonomy's own neighbours, not an industry
catalogue. Presented as fast chips: *yes / no / used but wouldn't claim it*.

This is where the "big list" idea belongs — scoped by adjacency to what the user demonstrably has,
which keeps it short and relevant instead of exhaustive and generic.

### Pass 2 — Rank by what the user's market actually asks for (deterministic)
Order the unclaimed candidates by frequency across the postings **this user matches**, which the
crawler already stores per job as `technologies` and `skills`. `SkillGapService` does the
model-driven version of this; the cheap frequency count is enough to decide question order.

The question stops being "which skills exist in your industry" and becomes "which of your unlisted
skills would change your next ten applications". Only the second is worth a user's attention.

### Pass 3 — Elicit evidence (AI, where it earns its cost)
For the highest-value confirmed skills that have no evidence behind them, a short exchange:

> You marked Kubernetes as used in production. Which system, and what went wrong that you had to fix?

The answer is parsed into an **existing `interview_story`** row (STAR+R), tagged with the skill, and
`profile_skill` gains its `yearsExperience` / `usedInProduction` / proficiency values as a
by-product. One or two model calls per skill — a parsing step, not a chatbot.

### Why this split between taxonomy and AI
- The **taxonomy** decides what can be asked — no hallucinated skills.
- The **market data** decides what is worth asking — no generic questionnaire.
- The **AI** only phrases the follow-up and parses free text — the two things it is actually better
  at than code, and neither of which lets it invent a claim the user did not make.

The privacy invariant is untouched: questions and answers are skills and work, never identity.

## What it feeds

- `interview_story` rows tagged by skill → the letter prompt's **Targeting & Proof Rules** finally
  have concrete proof points to surface, and `InterviewPrepService` gets a real story bank.
- `profile_skill`'s empty columns → CV tailoring can prefer skills marked `usedInProduction` over
  ones merely listed.
- The evaluator's `evidence` dimension should measurably rise. **This is testable**: add a fixture
  pair to `prompt-eval/` with and without elicited evidence and watch the score move. If it does
  not move, the feature is not working, and we would know rather than assume.

## The failure mode to design against

A 40-question onboarding wall. Nobody finishes those, and a half-finished one leaves the profile
worse than import did.

Mitigations, in order of importance:

1. **Never a gate.** A permanent, resumable surface, three questions at a time.
2. **Trigger it where it is obviously worth it** — this is the strongest version of the idea:
   - a generated letter scores low on `evidence` → offer two questions about the skills that letter
     needed and could not prove;
   - a matched posting names a skill the profile lists without evidence → ask about that one.
3. **Always show the payoff**: "answering this lets your letters cite the Kubernetes work" beats a
   progress bar.

## Cost

Passes 1 and 2 are free (SQL and a taxonomy walk). Pass 3 batches 3–5 skills per call, is capped
per session, and is resumable — and runs against the local-CLI provider like every other generation
path, so a flat-fee subscription covers it.

## Recommended first slice

1. Passes 1 and 2 with **no AI at all**, writing to `profile_skill`. Immediately useful, and it
   makes the empty columns real.
2. The evaluator-triggered prompt from mitigation 2 — a single question after a low-evidence
   generation, writing one `interview_story`.
3. Only then the batched elicitation flow, once there is evidence in the fixtures that the score
   actually moves.
