-- Outcome calibration: what happened and what to change next time.
ALTER TABLE applications ADD COLUMN outcome_feedback TEXT;
ALTER TABLE applications ADD COLUMN outcome_lessons TEXT;
