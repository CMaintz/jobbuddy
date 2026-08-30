-- Skills a CV or LinkedIn export demonstrates without ever naming.
--
-- The parsers extract strictly what the document says, and that rule stays: their output is what
-- the fact guard later treats as the candidate's own account, so a skill invented at parse time
-- would become permanently "supported" everywhere downstream. But a CV that describes running
-- fortnightly retrospectives and grooming a backlog, without the word "Scrum" anywhere, has
-- evidence of a skill the profile will not carry — and the user is the one who can confirm it.
--
-- So inferences land here instead: a queue of suggestions the user answers, exactly like the
-- taxonomy-adjacency and market-demand candidates, and nothing reaches the profile unconfirmed.
CREATE TABLE parsed_skill_suggestion (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_name      VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    -- What in the document implies the skill, in the document's own words. Without this the
    -- suggestion is unarguable, and an unarguable suggestion gets clicked through rather than read.
    evidence        TEXT,
    source          VARCHAR(40)  NOT NULL,   -- CV_PARSE | LINKEDIN_PARSE
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_parsed_skill UNIQUE (user_id, normalized_name)
);

CREATE INDEX idx_parsed_skill_suggestion_user ON parsed_skill_suggestion(user_id);
