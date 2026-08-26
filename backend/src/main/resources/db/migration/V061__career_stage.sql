-- Candidate-side career stage (STUDENT, NEW_GRAD, EARLY_CAREER, MID_CAREER, SENIOR, LEAD,
-- CAREER_CHANGER). Identity-free; drives stage-appropriate AI framing and the default CV
-- section order (new grads lead with education/projects). NULL = unset (treated as mid-career).
ALTER TABLE career_target ADD COLUMN career_stage TEXT;
