-- Communications and governance, risk and compliance (GRC) skills, so a profile outside software
-- gets categories, CV headings and Danish/English synonym matching like an engineer's does.
--
-- Same conventions as V021/V031/V032: name is the display form, normalized_name is lowercased,
-- category uses the fixed vocabulary. Disciplines and regulation are Domain (knowledge of a
-- field, not a technology), ways of working are Methodology, products are Tool. Aliases follow
-- V030's rules: each one names exactly one skill and is matched case-insensitively as a whole
-- token.
--
-- Deliberately not aliased:
--   * "SoMe": lowercased it is the English word "some", so every English CV would count as
--     covering a social media requirement. SkillCanonicalizer maps the exact label "SoMe" instead.
--   * "PR": a developer's CV says "PR" for pull requests. "presse" and "pressearbejde" carry it.
--   * bare "beredskab": Beredskab Fyn and the other fire services use it for firefighting.
--   * bare "DORA": engineers write "DORA metrics" for delivery performance, so the regulation is
--     only known by its full name and "DORA-forordningen".

INSERT INTO skill_taxonomy (name, normalized_name, category, aliases) VALUES
  -- Communications
  ('Internal Communication', 'internal communication', 'Domain', '{intern kommunikation}'),
  ('Strategic Communication', 'strategic communication', 'Domain', '{strategisk kommunikation}'),
  ('Crisis Communication', 'crisis communication', 'Domain', '{krisekommunikation}'),
  ('Public Relations', 'public relations', 'Domain', '{presse,pressearbejde,media relations}'),
  ('Social Media', 'social media', 'Domain', '{sociale medier,some-marketing,social media marketing}'),
  ('Copywriting', 'copywriting', 'Domain', '{tekstforfatning}'),
  ('Content Marketing', 'content marketing', 'Domain', '{indholdsmarkedsføring}'),
  ('SEO', 'seo', 'Domain', '{search engine optimization,søgemaskineoptimering}'),
  ('Canva', 'canva', 'Tool', '{}'),
  ('Adobe InDesign', 'adobe indesign', 'Tool', '{indesign}'),
  ('Mailchimp', 'mailchimp', 'Tool', '{}'),
  ('Hootsuite', 'hootsuite', 'Tool', '{}'),
  ('Meta Business Suite', 'meta business suite', 'Tool', '{}'),

  -- Governance, risk and compliance
  ('GRC', 'grc', 'Domain', '{"governance, risk and compliance","governance risk and compliance"}'),
  ('Compliance', 'compliance', 'Domain', '{}'),
  ('Information Security', 'information security', 'Domain', '{informationssikkerhed}'),
  ('Security Awareness', 'security awareness', 'Domain', '{sikkerhedsawareness,cyber awareness}'),
  ('Data Protection', 'data protection', 'Domain', '{databeskyttelse,persondatabeskyttelse}'),
  ('Business Continuity', 'business continuity', 'Domain', '{beredskabsplanlægning,kriseberedskab,it-beredskab}'),
  ('ISO 27002', 'iso 27002', 'Domain', '{}'),
  ('Digital Operational Resilience Act', 'digital operational resilience act', 'Domain', '{dora-forordningen}'),
  ('CIS Controls', 'cis controls', 'Domain', '{}'),
  ('Risk Management', 'risk management', 'Methodology', '{risikostyring}'),
  ('Risk Assessment', 'risk assessment', 'Methodology', '{risikovurdering,risikoanalyse}')
ON CONFLICT (name) DO NOTHING;

-- Which classifier rules version has been applied to stored jobs. JobCategoryBackfill claims a
-- version by inserting it, reclassifies the active jobs in the same transaction, and skips any
-- version already present. The source category is never stored, so this cannot be done in SQL.
CREATE TABLE job_category_backfill (
    rules_version integer PRIMARY KEY,
    applied_at timestamp with time zone DEFAULT now() NOT NULL
);
