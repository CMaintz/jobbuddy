-- Categories a user never wants to see: left out of her feed, her matches and enrichment.
-- Empty by default, because her field is wide and nothing should be hidden unasked.
ALTER TABLE preferences ADD COLUMN excluded_categories text[] NOT NULL DEFAULT '{}';
