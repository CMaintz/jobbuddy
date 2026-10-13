-- Section templates: how each CV section is written, chosen per section like any other prompt.
--
-- They replace cv_section_prompts (one free-text instruction per section per user). A user now
-- picks a default per section from the app's templates, their own (up to the per-kind cap) and the
-- shared ones they favourited, and the refine popover can apply any of them to a single field.
--
-- Section templates have no system prompt: they are writing instructions applied under the CV
-- persona, never a persona themselves. Every section category gets exactly one default (see V022),
-- because the resolver falls back to the oldest app row when none is flagged.
--
-- The experience frameworks follow their canonical sources: Google's X-Y-Z is Laszlo Bock, "My
-- Personal Formula for a Winning Resume" (LinkedIn, 2014); STAR is the behavioural-interview method
-- compressed to bullet length; CAR is the career-centre Challenge-Action-Result convention.
DROP TABLE cv_section_prompts;

INSERT INTO prompt_templates (user_id, name, category, description, system_prompt, user_prompt,
                              output_constraints, is_public, is_system, is_default, is_protected,
                              version_number, tags, explainer_key) VALUES

(NULL, 'Profile text', 'CV_PROFILE', 'A short profile text written for the posting.', NULL,
 $usr$Write the profile text for this posting in 3-5 lines.
- Line one says what the candidate is and what they are strongest at, in the posting's vocabulary
  where the source supports it.
- Then one or two concrete strengths the source backs (a domain, a kind of problem, a result) and
  where the candidate is heading.
- No adjectives the source cannot back ("passionate", "dynamic", "results-oriented"), no
  throat-clearing, no list of soft skills.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionProfile'),

(NULL, 'Competencies', 'CV_SKILLS', 'The skills the posting asks for that the profile backs, most relevant first.', NULL,
 $usr$Select the skills this posting asks for that the source genuinely supports, most relevant first.
- Use the posting's own spelling of a skill when the source supports it.
- Prefer skills the source shows in use, in a role or a project, over ones it merely lists.
- Leave soft skills out; the experience should show them.
- Fewer relevant skills beat a long list.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionSkills'),

(NULL, 'Experience: accomplishment bullets', 'CV_EXPERIENCE', 'Short bullets on what you did and what changed.', NULL,
 $usr$Write each role as accomplishment bullets: 3-5 for recent or relevant roles, 1-2 for older ones.
- Open each bullet with a strong verb in the past tense (present tense for a current role) and put
  what changed early.
- One achievement per bullet, one to two lines, about 25 words at most.
- Say what the candidate did and what came of it, not what they were responsible for.
  "Responsible for..." and "Worked on..." are never the bullet.
- Use a number only when the source states it. Without one, show scope (team size, budget, users,
  volume), a before-and-after change or recognition, in the source's own terms.
- Keep ownership as the source states it: "contributed to" stays "contributed to", and a team
  result stays a team result.
- Vary the sentence shape. The same pattern on every bullet reads as machine-written.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionExperience'),

(NULL, 'Experience: STAR', 'CV_EXPERIENCE', 'Situation, Task, Action, Result, compressed into one bullet.', NULL,
 $usr$Write the experience bullets with the STAR method (Situation, Task, Action, Result), compressed
to fit a CV bullet.
- STAR is a four-step story; a bullet has room for one line of it. Keep Situation and Task to a
  short clause, or drop them when the role already gives the context, and spend the words on
  Action and Result.
- Shape: action verb and what the candidate did, then the short context, then the result.
  Example: "Relaunched the club's Instagram with a weekly event series, gaining about 200
  followers in one semester."
- One to two lines per bullet. Never write the four parts out as labelled steps or as a narrative
  ("In a situation where... I was tasked with...").
- The result must come from the source. When the source has no outcome, end on the concrete
  action and its scope; never invent a result to complete the pattern.$usr$,
 NULL, FALSE, TRUE, FALSE, TRUE, 1, '{}', 'prompts.explainers.sectionExperienceStar'),

(NULL, 'Experience: Google X-Y-Z', 'CV_EXPERIENCE', 'Accomplished X, as measured by Y, by doing Z.', NULL,
 $usr$Write the experience bullets with Google's X-Y-Z formula: "Accomplished [X] as measured by [Y],
by doing [Z]".
- Result first: a verb and what was accomplished (X), then the measure (Y), then how (Z), usually
  introduced by "by".
  Example: "Cut average case handling time from 3 to 1 month by introducing a shared triage board."
- Y is a figure from the source. Put the absolute amount beside a percentage when the source has
  both ("12% ($1.2M)"), and add a baseline or peer comparison when the source gives one.
- When the source has no figure for a bullet, do not force the formula. Use concrete scope from
  the source as the measure (how many, how often, how large), or write a plain action-and-outcome
  bullet. Never invent or estimate a figure.
- One sentence of one to two lines. Do not repeat the literal words "accomplished", "as measured
  by" and "by doing"; vary the verbs so the structure carries the bullet, not the template.$usr$,
 NULL, FALSE, TRUE, FALSE, TRUE, 1, '{}', 'prompts.explainers.sectionExperienceXyz'),

(NULL, 'Experience: CAR', 'CV_EXPERIENCE', 'Challenge, Action, Result: the obstacle makes the result count.', NULL,
 $usr$Write the experience bullets with the CAR method (Challenge, Action, Result).
- The Challenge is what made the work hard or necessary: a deadline, a shrinking market, a broken
  process, missing resources. It is what makes the result impressive.
- Shape: action verb and what the candidate did, the challenge (often an "in..." or "despite..."
  clause), then the result.
  Example: "Grew sales 30% in a declining market by introducing lean process improvement."
- Name the challenge in a few words, and only when the source supports it. When the source states
  none, write action and result without inventing one.
- The result must come from the source: a figure when it has one, otherwise a concrete qualitative
  outcome.
- One to two lines per bullet.$usr$,
 NULL, FALSE, TRUE, FALSE, TRUE, 1, '{}', 'prompts.explainers.sectionExperienceCar'),

(NULL, 'Projects', 'CV_PROJECTS', 'What each project is, your part in it and what came of it.', NULL,
 $usr$For each project, one line on what it is and the candidate's part in it, then 1-3 bullets on
what they built or did and what came of it.
- Name the technologies or methods the source attributes to the project, in the posting's terms
  where they match.
- Claim authorship only where the source says the candidate built it; using a tool is not
  building it.
- Prefer the projects most relevant to this posting. For early-career candidates, academic and
  personal projects are real work.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionProjects'),

(NULL, 'Education', 'CV_EDUCATION', 'Degree, institution and dates, plus what helps this posting.', NULL,
 $usr$Keep education entries factual: degree, institution and dates as the source states them.
- Add a line only when it helps this posting: a thesis or major project topic, a relevant
  specialisation, or a grade the source gives.
- Never translate a degree into a different-sounding title.
- For experienced candidates keep education brief; for students and new graduates it may carry
  relevant coursework and projects.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionEducation'),

(NULL, 'Certifications', 'CV_CERTIFICATIONS', 'Name, issuer and year, most relevant first.', NULL,
 $usr$List certifications as name, issuer and year, as the source states them.
- Lead with the ones the posting mentions or that match its requirements; drop unrelated ones
  that have expired.
- No description unless the name does not explain itself.$usr$,
 NULL, FALSE, TRUE, TRUE, TRUE, 1, '{}', 'prompts.explainers.sectionCertifications');
