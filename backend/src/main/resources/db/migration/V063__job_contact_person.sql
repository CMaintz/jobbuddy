-- Contact person named by the posting ("kontakt afdelingsleder Mette Hansen på 12 34 56 78").
-- Danish postings almost always name someone and invite a call before applying, which is standard
-- practice here rather than pushiness. Extracted from the posting text only — never guessed.
-- All columns nullable: most non-Danish postings name nobody.
ALTER TABLE jobs ADD COLUMN contact_name  TEXT;
ALTER TABLE jobs ADD COLUMN contact_title TEXT;
ALTER TABLE jobs ADD COLUMN contact_email TEXT;
ALTER TABLE jobs ADD COLUMN contact_phone TEXT;
