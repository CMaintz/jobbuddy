-- Richer writing profile: explicit do/don't rules and structure guidance.
ALTER TABLE writing_profiles ADD COLUMN style_dos TEXT[];
ALTER TABLE writing_profiles ADD COLUMN style_donts TEXT[];
ALTER TABLE writing_profiles ADD COLUMN structure_notes TEXT;
