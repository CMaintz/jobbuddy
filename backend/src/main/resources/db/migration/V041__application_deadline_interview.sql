-- The user's own application deadline (ansøgningsfrist) and interview time, kept apart from the
-- crawled posting's deadline: many postings have none, and the user's date is the one they act on.
-- interview_at has no time zone on purpose. It is the wall-clock time the user typed, and the
-- reminder email must repeat it as typed; a zoned value would come back shifted by the server's
-- UTC offset.
ALTER TABLE applications
    ADD COLUMN deadline     DATE,
    ADD COLUMN interview_at TIMESTAMP WITHOUT TIME ZONE;

-- The language the user reads the app in, so emails sent while they are away match it. Danish
-- is the app's default, so a user who never chose reads Danish here too.
ALTER TABLE users
    ADD COLUMN language VARCHAR(2) NOT NULL DEFAULT 'da';
