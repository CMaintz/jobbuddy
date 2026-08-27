# Danish job-market playbook

> Why this file exists: the generation prompts encode *how to argue* (honesty, targeting, length)
> but until now encoded nothing about *who reads the result*. An LLM's priors are trained on the
> US application genre — enthusiastic, self-promoting, formulaic — which is close to the opposite
> of what a Danish recruiter rewards. This is the research behind
> `MarketConventions.DANISH_LETTER_RULES` / `DANISH_CV_RULES` and `ClicheGuard`.

## The screening reality

- Danish recruiters discard the large majority of applications within roughly half a minute of
  opening them, and the stated reason is rarely missing qualifications — it is that the text
  fails to engage in its first lines. ([Ballisager][ballisager], [JobMail][jobmail-guide])
- Roughly **half of all Danish vacancies are never advertised**; in private companies the share is
  higher still (~62%, and ~70% in SMEs). Of those unadvertised hires, ~39% come through network,
  ~27% through unsolicited applications. ([Ansøgningshjælpen][ah-skjulte], [CA][ca-usynlige])
  → The `UNSOLICITED_APPLICATION` document type is not a niche feature in this market; it
  addresses the larger half of it.
- Applications are increasingly read against a growing suspicion of AI-generated boilerplate —
  which raises the value of concrete, specific, un-generic text rather than lowering it.
  ([JobMail][jobmail-guide])

## Screening: what actually happens before a human reads anything

- Danish employers screen through applicant tracking systems that match the posting's literal
  must-have terms; Danish sources put the share of CVs that never reach a recruiter at up to **75%**,
  and note that a system matching on one term will not infer its synonym.
  ([Rise&Hire][riseandhire-ats], [AJKS][ajks-ats])
  → Mirror the posting's own wording, and include the common synonym where one exists
  (`Kubernetes` *and* `K8s`).
- Candidates who pass are typically invited to a short phone or video screening before anything
  else. ([Virksomhedsguides][virksomhedsguides])

## The interview, as Danish employers describe it

- What firms say they weigh: **63%** clarity of motivation, **52%** letting personality show, **45%**
  visible preparation. Ballisager's summary is "vi ansætter mennesker, ikke perfekte profiler" —
  authenticity beats a polished answer. ([Ballisager][ballisager-interview], [CA][ca-trends])
- The process runs in rounds: short screening → first conversation (HR + hiring manager, background
  and motivation) → second (case or the team) → final, where pay and terms are settled.
  ([Finansforbundet][finansforbundet], [Djøf][djoef-interview])
- **Pay is arriving earlier** than it used to and can come up at any point; some employers now ask
  for expectations before the first interview, which 78% of Danes consider unacceptable. Advice:
  research the range beforehand, be ready from the start, leave the real negotiation until the job
  is offered, and open high. ([Djøf][djoef-salary], [Djøfbladet][djoefbladet])
- The weakness question wants a real development area plus what you are doing about it.
  ([Djøf][djoef-salary])
- Questions back are expected — work, team, department goals, leadership style — and asking about
  the process and timeline at the end is normal. ([HK][hk-interview], [Lederne][lederne])

## Unsolicited contact: the sequence starts on the phone

This is the finding that most changes behaviour, and the app had it wrong:

1. **Ring first.** Call reception, HR, or the manager of the department you want to join. Ask
   whether you may send an application, who to send it to, and how they prefer to receive it. Open
   with "am I disturbing you?" and, if so, ask when to call back. ([HK][hk-unsolicited],
   [Krifa][krifa-unsolicited])
2. **Send**, referring to the call ("som aftalt") when there was one.
3. **Follow up after 2–3 working days** — not a week and a half. The candidate is expected to be the
   active party: *høfligt påtrængende*. Wait longer only if the company said they have no need right
   now. ([HK][hk-unsolicited], [Krifa][krifa-unsolicited])

## Cover letter (ansøgning)

| Convention | Detail |
|---|---|
| Length | One A4 page, hard. ~350–400 words / ~2.500 characters. 70% of surveyed recruiters want max one page. ([Ase][ase-indledning], [JobMail][jobmail-guide]) |
| Opener | The "krog" (hook). Formulaic openers — *"Jeg søger hermed stillingen som…"* — and floskler are the most-cited instant-reject signals. ([Ase][ase-indledning]) |
| Body | Must **not** retell the CV in prose. It adds the argument the CV cannot make. Repeating CV facts in sentences is described as misunderstanding the genre. ([HK][hk-motiveret], [Djøf][djoef]) |
| Proof | Don't assert a competence — demonstrate it with an example, coupled to the posting's own wording (*"Fordi jeg har arbejdet med X, kan jeg …"*). ([Randstad][randstad], [Job Academy][ja-cover]) |
| Angle | What the candidate contributes, not what the job gives them. Motivation is shown through specific knowledge of the company. ([HK][hk-motiveret]) |
| Register | Plain, direct, modest, informal *du*-form. Danish flat-hierarchy norms make American superlatives and self-praise read as untrustworthy. Enthusiasm must not tip into desperation. ([How to Live in Denmark][htlid], [Job Academy][ja-cover]) |
| Close | Calm and confident — one forward-looking sentence, then *Med venlig hilsen*. No pleading, no apology. ([Randstad][randstad]) |

## CV

| Convention | Detail |
|---|---|
| Length | Two A4 pages max; one page for students / new graduates. ([VisualCV][visualcv], [Ase][ase-opbygning]) |
| Tone | Straightforward, honest, easy to scan. Overdesign, inflated titles and unnecessary personal detail count *against* the candidate. ([VisualCV][visualcv]) |
| Order | Profiltekst → work experience → education/courses → IT & languages → leisure interests → references. Reverse-chronological within each section. Education moves to the top for students/new grads. ([Krifa][krifa], [Ase][ase-opbygning]) |
| Dates | Month-level ranges; unexplained gaps are noticed. ([Krifa][krifa]) |
| Photo | **No longer a fixed rule.** Common in customer-facing sectors; increasingly omitted in IT/tech and in the public sector for bias reasons. Keep it a user choice (the app already has `showProfileImage`). ([Rise&Hire][riseandhire], [Ase][ase-opbygning]) |
| Personal data | Age, address and date of birth are optional, not expected. City is usually enough. ([Ase][ase-opbygning], [VisualCV][visualcv]) |
| Languages | Spoken languages with proficiency levels matter — Danish-language ability is frequently a real requirement. ([Krifa][krifa]) |

## What this repository does with it

| Research finding | Where it lives |
|---|---|
| Market conventions for letters and CVs | `MarketConventions` — injected into the drafting **and** reviewing prompts |
| Which market applies | `MarketConventions.resolve(language, jobCountry)` — the posting's country wins over language, so a Copenhagen employer posting in English still gets Danish conventions |
| Which language to write in | `JobLanguageDetector` — deterministic detection replaces "guess from the posting" |
| Floskler and AI-tells | `ClicheGuard` — one phrase list used twice: as a prompt ban list, and as a post-generation check whose findings are fed back to the reviewer pass to be rewritten |
| Hidden job market | `UNSOLICITED_APPLICATION` guidance in `PromptCompositionBuilder` — framed as a proposal to a named person, not an application |
| Who to send one to | `OutreachTargetService` — ranks companies from crawled hiring history: skill overlap, repeat hiring, recency, agencies excluded, consultancies penalised, and a company with **nothing open right now** scored *up*, because an open role means you should just apply |
| The contact to ring first | `JobContact`, extracted during enrichment; surfaced on job details and used to address the letter |
| Availability | `CareerTarget.noticePeriod` / `earliestStartDate` → an `availability` line in the AI context, stated near the letter's close when present |
| The default voice | The seeded system templates (V068) — a template's `system_prompt` **replaces** the built-in persona, so the seeds are the app's actual voice, not a fallback |
| Short outreach | `MarketConventions.outreachRules` — a different medium from a letter, and it fails by sounding like sales rather than by sounding generic |
| Interviews | `MarketConventions.interviewRules` — flat hierarchy changes what a good answer sounds like; the mock interviewer probes like a local one |
| Screening reality | ATS synonym rule in `MarketConventions.cvRules` |
| Call-first sequence | `outreachRules` + the unsolicited letter guidance; the outreach screen says it too |
| Follow-up timing | `OutreachService.DEFAULT_FOLLOW_UP_DAYS` = 3 working days |
| Reading a posting | `MarketConventions.jobReadingRules` — `du skal` is a requirement, `det er en fordel` is not, and scoring them alike either scares off a qualified candidate or reassures an unqualified one |

## Measuring prompt changes

`DocumentQualityEvaluator` scores a generated letter model-free, so two prompt revisions can be
compared on a number instead of an impression. Six dimensions, each one a failure mode named
above:

| Dimension | Weight | What it measures |
|---|---|---|
| `filler` | 5 | Floskler and AI-tells (reuses `ClicheGuard`). Weighted highest — an instant reject, not a blemish |
| `evidence` | 3 | Profile terms and figures actually referenced. Reference *density*, not quality |
| `facts` | 3 | Figures with no support in the profile (reuses `DocumentFactGuard`) |
| `keywords` | 2 | Share of the posting's keywords covered |
| `cv_echo` | 2 | Sentences that merely restate the CV — the trap Danish advisers name most often |
| `length` | 1 | Drift from the word target the prompt asked for |

A dimension that cannot be judged (an empty document) takes weight 0 rather than full marks, so an
absent letter cannot score well for containing no mistakes.

It also runs on real generations: `AiService` scores every prose letter it delivers and logs the
total with its dimension breakdown, so a prompt change can be watched on live output. The word
target comes from `PromptCompositionBuilder.letterWordTarget`, the same value the prompt asks for,
so instruction and measurement cannot drift apart.

`PromptEvalHarnessTest` runs it over fixtures in `backend/src/test/resources/prompt-eval/` — each a
(profile, posting, good output, weak output) set. It asserts the composed prompt still carries the
blocks that fixture needs, that a good output clears 80, and that good and weak stay at least 30
points apart. Adding a fixture is a JSON file, no code change. Current baseline: 95 vs 56 (Danish)
and 95 vs 47 (English). Everything runs offline — no API calls in CI.

The evaluator scores mechanics, not merit: a document can score 95 and still be dull. Use it to
catch regressions, not to certify quality.

## How the fact guard survives rewording

The guard compares metric claims between the document and the profile. Everything below exists
because a truthful sentence should never be flagged, and every one of these was a real false
positive before it was fixed:

| The model writes | The profile says | Why it used to fail |
|---|---|---|
| `50k users` | `50,000 users` | Magnitude suffixes are now expanded before comparison |
| `40 minutes` | `40 min` | Abbreviations fold to the canonical unit |
| `16.000 brugere` | `16,000 users` | Danish and English nouns fold to one token — the systematic one, since the profile is often English and the letter Danish |
| `3 years` | `3 års erfaring` | Same folding, via `år`/`års` → `years` |
| `30 services` | `thirty services` | Number words fold to digits **only when a metric noun follows directly**, so "one of the teams" never becomes a claim |
| `550.000 kr.` | `DKK 550.000` | Danish currency is extracted at all now; it previously went unchecked in either direction |

It also derives what the profile implies but never spells out. "Three years at Netcompany" is true
of a profile containing only `Jan 2021 - Mar 2024`, where the number 3 appears nowhere — durations
are computed from date ranges (per role and totalled), with generous rounding in both directions,
because a candidate calling 30 months "two years" or "nearly three" is not lying and the guard
should not arbitrate that.

Comparison happens on the normalized form; **findings quote the document's own spelling**, so a
user who wrote `50k users` is never told that `50000 users` is unsupported.

Findings come in two tiers, because they are not the same failure:

- **Invented** — the number appears nowhere in the profile. No innocent explanation; this is what
  `fact-guard.mode=block` blocks and what the ATS report shows as FAIL.
- **Unverified** — the number *is* in the profile but attached to something else ("30 teams" where
  the profile says "30 services"). Usually a rewording, occasionally a real slip: shown as WARN,
  never blocking, and a lighter penalty in the quality score.

What it still cannot do: catch a rewording to a noun outside the list (`30 microservices` is simply
not extracted). That direction fails safe — an unrecognised noun produces no claim and therefore no
accusation — but it does mean the guard's coverage is only as wide as `METRIC_NOUNS`.

### Why it is kept, and on what terms

The case for deleting it is real: it is a regex over prose, its recall is bounded by a noun list,
and the honesty rules plus the reviewer pass are already hunting for fabrications. What it has that
they do not is that it is **deterministic and free** — it runs on every document, costs no tokens,
cannot itself hallucinate, and produces something the *user* can see and check. A fabricated number
is also the one error a candidate cannot talk their way out of in an interview, which is precisely
the failure the Danish market punishes hardest.

So it stays, on stated terms rather than vibes. `DocumentFactGuardContractTest` holds two lists:
what it **must catch** (8 fabrications) and what it **must never flag** (16 truthful sentences,
every one of which was a live false positive at some point). Both are asserted, and the test prints
recall and false positives on every run. The asymmetry is deliberate:

> Recall may be imperfect — the guard is a net, not a proof. **Precision may not.** One false
> accusation costs more trust than one missed number costs safety, because two other mechanisms are
> also looking for fabrications and neither of them is looking for false alarms.

That is the rule to apply to any future change: loosening a rule to fix a false positive usually
punches a hole in MUST_CATCH, and the suite is where that shows up before a user does.

## Where the rules reach

Every AI call in the app, and what governs it:

| Call | Governed by |
|---|---|
| Cover letter / application text | Honesty, targeting, market conventions, banned phrases, length, named contact |
| Tailored CV + its reviewer | Honesty, targeting, CV market conventions, ATS synonyms, banned phrases |
| Letter reviewer | Same rules as drafting, plus the filler findings to rewrite |
| **Refinement** | Same rules, plus an explicit "stronger means write better, never claim more" |
| CV analysis | Danish posting-reading rules (`du skal` vs `det er en fordel`) |
| Skill gaps | The same, so a missing nice-to-have is not reported as a gap |
| Interview questions, prep pack, mock interviewer | Danish interview conventions |
| Evidence questions + answer drafting | Fact guard against the user's own words |
| CV parser, LinkedIn parser | Extract-only: these write the profile every other check trusts |
| LinkedIn query plan | Danish and English titles for Danish searches |
| Company grounding, writing-style analysis, job enrichment | Already scoped to their own source text |

**Guarded output** (fact, retracted-claim and filler checks) covers generation, review and
refinement. `GuardedPathsTest` asserts nobody adds a fourth path and forgets.

## Not yet done (ranked)

1. **The quality trend has an endpoint but no chart.** `GET /api/v1/documents/quality-scores`
   returns the history; nothing plots it yet, though `sparkline` and `stat-card` exist.
3. **Contact extraction is still unverified against live postings.** The adversarial cases are
   covered (role addresses, malformed blocks, crawler precedence) but on synthetic input; the
   remaining failure mode is a name lifted from page chrome or a neighbouring vacancy, which only
   real crawled pages will show.
4. **Skill capture is import-only.** See the elicitation design below.

[ballisager]: https://ballisager.com/den-gode-ansoegning
[jobmail-guide]: https://jobmail.dk/blog/ansogning-guide
[ah-skjulte]: https://ansogningshjaelpen.dk/det-skjulte-jobmarked-din-guide-til-70-flere-jobs-i-danmark/
[ca-usynlige]: https://www.ca.dk/webinars/w/det-usynlige-jobmarked-uopfordret-jobsogning/
[ase-indledning]: https://www.ase.dk/faa-svar/jobsoegning/ansoegning/indledning
[ase-opbygning]: https://www.ase.dk/faa-svar/jobsoegning/cv/opbygning
[hk-motiveret]: https://www.hk.dk/karriere/jobsoegningen/motiveret-ansoegning
[djoef]: https://www.djoef.dk/jobsoegning/skriv-en-god-ansoegning
[randstad]: https://www.randstad.dk/job-soger/karriereradgivning/ansogning/den-gode-ansogning/
[ja-cover]: https://ansogningshjaelpen.dk/en/job-academy/modules/international/cover-letter-denmark/
[htlid]: https://www.howtoliveindenmark.com/danish-business-culture/danish-cover-letter-denmark/
[visualcv]: https://www.visualcv.com/international/denmark-cv/
[krifa]: https://www.krifa.dk/jobsoegning/cv/opbygning-af-cv
[riseandhire]: https://www.riseandhire.com/da/blog/foto-pa-cv-danmark-2026/
[riseandhire-ats]: https://www.riseandhire.com/da/blog/ats-optimeret-cv-dansk-arbejdsmarked/
[ajks-ats]: https://ajks.dk/nyhed/kaere-jobsoeger-er-du-bekendt-med-ats
[virksomhedsguides]: https://virksomhedsguides.dk/hvad-er-rekruttering/
[ballisager-interview]: https://ballisager.com/blog/virksomhedernes-bedste-raad-til-jobsamtalen
[ca-trends]: https://www.ca.dk/nyheder/8-trends-der-praeger-fremtidens-rekruttering/
[finansforbundet]: https://finansforbundet.dk/dk/arbejdsliv-og-udvikling/bliv-klar-til-jobsamtalen/
[djoef-interview]: https://www.djoef.dk/jobsoegning/gode-raad-til-jobsamtalen
[djoef-salary]: https://www.djoef.dk/aktuelt/saadan-tackler-du-loenspoergsmaalet-til-jobsamtalen
[djoefbladet]: https://www.djoefbladet.dk/artikler/2025/03/loenkrav-inden-foerste-samtale
[hk-interview]: https://www.hk.dk/karriere/jobsoegningen/gode-raad-til-jobsamtalen
[lederne]: https://www.lederne.dk/lederne-a-kasse/-/media/files/guides/gode_raad_jobsamtalen_rgb_enkeltsider.pdf
[hk-unsolicited]: https://www.hk.dk/karriere/jobsoegningen/uopfordret-ansoegning
[krifa-unsolicited]: https://www.krifa.dk/jobsoegning/ansoegning/faa-succes-med-din-uopfordrede-ansoegning
