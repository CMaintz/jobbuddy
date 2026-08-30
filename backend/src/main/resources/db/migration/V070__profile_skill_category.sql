-- Skill categories never reached a profile skill. ProfileSkill.category was derived at read
-- time by joining skill_taxonomy, and only when taxonomy_id happened to be set — nothing
-- resolved a typed skill name to a taxonomy row, so a free-text skill stayed uncategorised
-- forever, and even a resolved one came back uncategorised from the save path.
--
-- Give the row its own category so a skill outside the taxonomy can still be filed, and
-- backfill what the taxonomy already knows.
ALTER TABLE profile_skills ADD COLUMN IF NOT EXISTS category VARCHAR(100);

COMMENT ON COLUMN profile_skills.category IS
  'Category for this skill. Resolved from skill_taxonomy when the name matches one, otherwise set by the user.';

-- 1. Adopt the taxonomy row for skills that name a known skill but were never linked to it.
UPDATE profile_skills ps
   SET taxonomy_id = st.id
  FROM skill_taxonomy st
 WHERE ps.taxonomy_id IS NULL
   AND LOWER(TRIM(ps.skill_name)) = st.normalized_name;

-- 2. Backfill the category from whatever taxonomy row the skill is now linked to.
UPDATE profile_skills ps
   SET category = st.category
  FROM skill_taxonomy st
 WHERE ps.category IS NULL
   AND ps.taxonomy_id = st.id
   AND st.category IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_profile_skills_category ON profile_skills(category);
