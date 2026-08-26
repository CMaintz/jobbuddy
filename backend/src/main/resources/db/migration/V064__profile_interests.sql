-- Leisure interests ("fritidsinteresser") — a standard closing section on a Danish CV, and one of
-- the few places a Danish reader expects something personal. Rendered verbatim from the user's own
-- data; never AI-selected, since there is nothing to tailor about a hobby.
ALTER TABLE profiles ADD COLUMN interests TEXT[];
