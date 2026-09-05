-- Full-text search over jobs, replacing Typesense.
--
-- A generated column keeps the index in step with the row automatically, so there is
-- no second store to write to and nothing that can drift out of sync. The weights say
-- what a match is worth: the title above the company and its tech stack, both above
-- the body of the posting.
--
-- 'danish' is the stemmer because the postings are Danish; it degrades to sensible
-- behaviour on the English ones rather than failing.

-- array_to_string is only STABLE, because in general an array's text form depends on
-- the element type's output function and thus on settings like DateStyle. For text[]
-- there is no such dependency, so a text[]-only wrapper can honestly promise
-- immutability — which a generated column requires.
CREATE FUNCTION text_array_to_string(text[], text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT array_to_string($1, $2) $$;

ALTER TABLE jobs ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('danish'::regconfig, coalesce(title, '')), 'A') ||
        setweight(to_tsvector('danish'::regconfig, coalesce(company_name, '')), 'B') ||
        setweight(to_tsvector('danish'::regconfig, coalesce(text_array_to_string(technologies, ' '), '')), 'B') ||
        setweight(to_tsvector('danish'::regconfig, coalesce(text_array_to_string(skills, ' '), '')), 'C') ||
        setweight(to_tsvector('danish'::regconfig, coalesce(description_clean, '')), 'D')
    ) STORED;

CREATE INDEX idx_jobs_search_vector ON jobs USING gin (search_vector);
