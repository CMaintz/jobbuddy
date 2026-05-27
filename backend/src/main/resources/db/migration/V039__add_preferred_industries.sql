-- V039: Add preferred_industries to user preferences for industry-based filtering.
ALTER TABLE preferences ADD COLUMN preferred_industries TEXT[];
