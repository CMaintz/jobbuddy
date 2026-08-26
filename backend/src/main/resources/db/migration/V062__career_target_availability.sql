-- Availability: notice period ("opsigelsesvarsel") and earliest start date. Danish employers
-- routinely ask for both in the application itself, and a candidate who states them reads as
-- someone who has thought the move through. Identity-free, so both are safe to send to the AI.
--
-- notice_period is free text on purpose: real answers are "3 måneder", "1 month", "immediately",
-- and "negotiable" — an enum would force users to lie by rounding.
ALTER TABLE career_target ADD COLUMN notice_period TEXT;
ALTER TABLE career_target ADD COLUMN earliest_start_date DATE;
