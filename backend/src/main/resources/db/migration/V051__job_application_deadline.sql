-- Application deadline stated by the posting (null = unknown or ASAP).
ALTER TABLE jobs ADD COLUMN application_deadline DATE;
