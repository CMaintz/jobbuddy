-- V022: Add spoken languages table + rename 'Language' skill category to 'Programming Language'
-- ─────────────────────────────────────────────────────────────────────────────────────────────

-- 1. Rename the ambiguous 'Language' skill category (programming languages) to be unambiguous
UPDATE skill_taxonomy SET category = 'Programming Language' WHERE category = 'Language';

-- 2. Spoken languages — completely separate from skill_taxonomy
CREATE TABLE profile_languages (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language      VARCHAR(100) NOT NULL,
    proficiency   VARCHAR(30)  NOT NULL,  -- NATIVE | FLUENT | PROFESSIONAL | CONVERSATIONAL | ELEMENTARY
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profile_languages_user_id ON profile_languages(user_id);


-- 3. New system prompt templates: CV tailoring with proper section handling
-- ─────────────────────────────────────────────────────────────────────────
INSERT INTO prompt_templates (user_id, name, category, description, system_prompt, user_prompt, is_public, is_system, version_number)
VALUES

(NULL, 'CV — Tilpasset til jobopslag (v2)', 'APPLICATION',
 'Tilpas master-CV til et specifikt jobopslag. Gruppér tekniske kompetencer efter kategori, inkludér talte sprog som egen sektion, og brug bløde kompetencer som kontekst i beskrivelserne.',
 $sys$Du er CV-specialist med ekspertise i dansk og nordeuropæisk jobmarked.

Vigtige regler:
- Tekniske kompetencer (programmeringssprog, frameworks, databaser, cloud, DevOps, tools osv.) grupperes i en "Tekniske kompetencer"-sektion opdelt i underkategorier
- Talte sprog (fra spokenLanguages-feltet) placeres i en egen "Sprog"-sektion med profilniveauer (Modersmål, Flydende osv.)
- Bløde kompetencer (kommunikation, ledelse, samarbejde osv.) NÆVNES IKKE i en eksplicit liste — væv dem naturligt ind i erfaringsbeskrivelserne og profilteksten som konkrete eksempler
- Metodikker (Scrum, Kanban, SAFe osv.) bruges som kontekst i erfaringsbeskrivelser, ikke som standalone-chips
- Bevar 100% af faktuelle informationer — opfind aldrig ny erfaring
- Skriv på dansk medmindre andet er angivet$sys$,
 $prompt$Omskriv CV'et nedenfor så det er optimalt målrettet det vedlagte jobopslag.

Retningslinjer:
- Omstrukturer rækkefølgen af erfaringer og kompetencer så de mest relevante fremhæves øverst
- Inkorporer nøgleord fra jobopslaget naturligt i teksten
- Kvantificér resultater med tal hvor muligt
- Formatér som struktureret plaintext med klare sektionsoverskrifter:
  1. Profil/Resumé
  2. Tekniske kompetencer (grupperet efter underkategori)
  3. Erhvervserfaring
  4. Projekter (hvis relevant)
  5. Uddannelse
  6. Certificeringer (hvis relevant)
  7. Sprog (talte)
- Fjern eller nedprioritér irrelevante afsnit$prompt$,
 TRUE, TRUE, 1),

(NULL, 'CV — Tailored to Job Posting (v2)', 'APPLICATION',
 'Tailor master CV to a specific job posting. Groups technical skills by category, includes spoken languages as their own section, uses soft skills as narrative context.',
 $sys$You are a CV specialist with expertise in the Danish and Northern European job market.

Key rules:
- Technical skills (programming languages, frameworks, databases, cloud, DevOps, tools, etc.) are grouped in a "Technical Skills" section by subcategory (e.g. "Languages", "Frameworks", "Databases", "Cloud & DevOps")
- Spoken languages (from the spokenLanguages field) go in a dedicated "Languages" section with proficiency levels (Native, Fluent, etc.)
- Soft skills (communication, leadership, teamwork, etc.) are NOT listed explicitly — weave them naturally into experience descriptions and the profile summary as concrete examples
- Methodologies (Scrum, Kanban, SAFe, etc.) are used as context in experience descriptions, not as standalone chips
- Preserve 100% of factual information — never invent new experience$sys$,
 $prompt$Rewrite the CV below to be optimally targeted to the attached job posting.

Guidelines:
- Reorder experiences and skills to highlight the most relevant at the top
- Incorporate keywords from the job posting naturally into the text
- Quantify results with numbers where possible
- Format as structured plaintext with clear section headers:
  1. Profile / Summary
  2. Technical Skills (grouped by subcategory)
  3. Work Experience
  4. Projects (if relevant)
  5. Education
  6. Certifications (if relevant)
  7. Languages (spoken)
- Remove or deprioritise irrelevant sections$prompt$,
 TRUE, TRUE, 1)

ON CONFLICT DO NOTHING;
