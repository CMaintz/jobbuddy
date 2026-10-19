-- A user-facing explanation per app-shipped prompt: what it does and when to pick it over its
-- neighbours. The text itself lives in the frontend i18n files (da/en), so the row stores only the
-- key. User-made templates leave it NULL; their own description is what explains them.
--
-- Rows are matched by name because the seed in V022 gave them generated ids.
ALTER TABLE prompt_templates ADD COLUMN explainer_key varchar(100);

UPDATE prompt_templates t SET explainer_key = 'prompts.explainers.' || v.slug
FROM (VALUES
    ('Ansøgningstekst — appens standard', 'applicationTextStandard'),
    ('Ansøgningstekst — felt i formular', 'applicationTextFormField'),
    ('Ansøgningstekst — Kompakt', 'applicationTextCompact'),
    ('CV — Tailored to Job Posting (v2)', 'cvPlaintextTailoredEn'),
    ('CV — Tilpasset til jobopslag (v2)', 'cvPlaintextTailoredDa'),
    ('CV — Vinklet til jobopslag', 'cvPlaintextAngled'),
    ('Ansøgning — dansk marked', 'coverLetterDanishMarket'),
    ('Ansøgning — Dansk', 'coverLetterDanishClassic'),
    ('Application — Danish market, in English', 'coverLetterDanishMarketEnglish'),
    ('Application — English', 'coverLetterEnglishClassic'),
    ('Analyse — CV vs. Jobopslag', 'cvAnalysis'),
    ('CV-tilpasning — dansk marked', 'cvTailoringDanishMarket'),
    ('CV Tailoring — Default', 'cvTailoringAts'),
    ('CV-tilpasning — appens standard', 'cvTailoringMinimal'),
    ('Opfølgning — dansk marked', 'followUpDanishMarket'),
    ('Follow-up — Danish market', 'followUpDanishMarketEnglish'),
    ('Cold contact — Questions before applying', 'coldContactQuestions'),
    ('Follow-up — After Application', 'followUpAfterApplication'),
    ('Follow-up — Polite re-surface', 'followUpResurface'),
    ('Interview prep — STAR answers', 'interviewPrepStar'),
    ('Kontakt til rekrutterer — kort og konkret', 'recruiterDanishMarket'),
    ('Recruiter DM — Concrete & confident', 'recruiterConcrete'),
    ('Recruiter Message — LinkedIn', 'recruiterLinkedIn'),
    ('Uopfordret ansøgning — dansk marked', 'unsolicitedDanishMarket'),
    ('Unsolicited application — Danish market', 'unsolicitedDanishMarketEnglish')
) AS v(name, slug)
WHERE t.name = v.name AND t.is_system;
