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

`PromptEvalHarnessTest` runs it over fixtures in `backend/src/test/resources/prompt-eval/` — each a
(profile, posting, good output, weak output) set. It asserts the composed prompt still carries the
blocks that fixture needs, that a good output clears 80, and that good and weak stay at least 30
points apart. Adding a fixture is a JSON file, no code change. Current baseline: 95 vs 56 (Danish)
and 95 vs 47 (English). Everything runs offline — no API calls in CI.

The evaluator scores mechanics, not merit: a document can score 95 and still be dull. Use it to
catch regressions, not to certify quality.

## Not yet done (ranked)

1. **Score real generations, not just fixtures.** The evaluator is a Spring bean but nothing calls
   it in the generation path. Logging the score per generation would turn it into a live metric.
2. **Guard findings and ATS checks are English-only strings**, built server-side. A Danish user
   reading a Danish letter gets English diagnostics.
3. **Interests and references do not survive the resume-builder round-trip.** Both render in the
   structured document and the ATS export, but `ResumeData` has no field for them, so opening a
   tailored CV as an editable draft drops them.
4. **Outreach targets are ranked but not tracked.** A saved target has no pipeline state of its
   own until an application exists.

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
