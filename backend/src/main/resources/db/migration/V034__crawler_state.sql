-- Tracks the RSS page offset per source so each crawl run advances through
-- the catalog rather than re-fetching the same first page every time.
CREATE TABLE crawler_state (
    source       VARCHAR(50)              NOT NULL,
    page_offset  INT                      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_crawler_state PRIMARY KEY (source)
);
