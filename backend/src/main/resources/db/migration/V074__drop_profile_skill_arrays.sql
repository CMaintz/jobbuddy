-- One representation for a user's skills.
--
-- profiles.skills and profiles.technologies held bare names; profile_skills holds a row per skill
-- with its taxonomy link, category, proficiency and years. Both were written by live code paths:
-- the CV and LinkedIn importers wrote the arrays, manual entry and confirmed suggestions wrote
-- rows. So whether a skill carried a category, a proficiency or a taxonomy link depended entirely
-- on how it had arrived — and for anyone who onboarded by uploading a CV, the answer was "none of
-- them". Everything built on those attributes silently did nothing for those users: grouped skill
-- sections on the CV, the proficiency weighting in match scoring, category-aware suggestions.
--
-- The importers now create profile_skills rows, so the arrays have no writer left. The
-- skills-versus-technologies distinction they encoded is derived from the taxonomy category
-- instead (see SkillCategories), which keeps one thing true in one place.
ALTER TABLE profiles DROP COLUMN IF EXISTS skills;
ALTER TABLE profiles DROP COLUMN IF EXISTS technologies;
