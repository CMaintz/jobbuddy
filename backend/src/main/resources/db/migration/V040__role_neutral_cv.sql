-- CV output that fits any profession, not just software.
--
-- 1. The two plain-text CV prompts from V022 told the model to build a "Technical Skills" section
--    of programming languages, frameworks, databases and cloud. On a communications or compliance
--    CV that heading is wrong and its subcategories are empty. They now group skills as tools,
--    methods and domain knowledge. V022 stays as it was (Flyway checksums); rows are matched on
--    the explainer_key V037 gave them.
--
-- 2. Compliance frameworks and regulations move from Security to Domain. Security is a technical
--    category, so NIS2 and ISO 27001 were sent to the model as technologies and printed under a
--    "Security" heading next to Keycloak. They are knowledge of a field, which is what Domain is.
--    Security tools (Keycloak, Burp Suite, OWASP and the rest) stay where they are.
--
--    profile_skills keeps its own copy of the category from when the skill was added, and that
--    copy wins over the taxonomy, so the copies still holding the old value move too. A category
--    the user changed by hand no longer reads Security and is left alone.

UPDATE prompt_templates SET
    description = 'Tailor master CV to a specific job posting. Groups skills by tools, methods and domain knowledge, includes spoken languages as their own section, uses soft skills as narrative context.',
    system_prompt = $sys$You are a CV specialist with expertise in the Danish and Northern European job market.

Key rules:
- Skills are grouped in a "Skills" section under headings that fit the candidate's field, such as tools and systems, methods and frameworks, and domain knowledge (regulation, standards, subject areas). Use only headings the candidate has content for.
- Spoken languages (from the spokenLanguages field) go in a dedicated "Languages" section with proficiency levels (Native, Fluent, etc.)
- Soft skills (communication, leadership, teamwork, etc.) are NOT listed explicitly. Weave them naturally into experience descriptions and the profile summary as concrete examples
- Ways of working (Scrum, PRINCE2, risk assessment methods, etc.) are used as context in experience descriptions, not as standalone chips
- Preserve 100% of factual information. Never invent new experience$sys$,
    user_prompt = $usr$Rewrite the CV below to be optimally targeted to the attached job posting.

Guidelines:
- Reorder experiences and skills to highlight the most relevant at the top
- Incorporate keywords from the job posting naturally into the text
- Quantify results with numbers where the source has them; otherwise describe the scope and what changed
- Format as structured plaintext with clear section headers:
  1. Profile / Summary
  2. Skills (grouped as above)
  3. Work Experience
  4. Projects (if relevant)
  5. Education
  6. Certifications (if relevant)
  7. Languages (spoken)
- Remove or deprioritise irrelevant sections$usr$
WHERE is_system AND explainer_key = 'prompts.explainers.cvPlaintextTailoredEn';

UPDATE prompt_templates SET
    description = 'Tilpas master-CV til et specifikt jobopslag. Gruppér kompetencer efter værktøjer, metoder og fagviden, inkludér talte sprog som egen sektion, og brug bløde kompetencer som kontekst i beskrivelserne.',
    system_prompt = $sys$Du er CV-specialist med ekspertise i dansk og nordeuropæisk jobmarked.

Vigtige regler:
- Kompetencer samles i en "Kompetencer"-sektion under overskrifter, der passer til kandidatens fag, fx værktøjer og systemer, metoder og rammeværk samt fagviden (lovgivning, standarder, fagområder). Brug kun overskrifter, kandidaten har indhold til.
- Talte sprog (fra spokenLanguages-feltet) placeres i en egen "Sprog"-sektion med profilniveauer (Modersmål, Flydende osv.)
- Bløde kompetencer (kommunikation, ledelse, samarbejde osv.) NÆVNES IKKE i en eksplicit liste. Væv dem naturligt ind i erfaringsbeskrivelserne og profilteksten som konkrete eksempler
- Arbejdsformer (Scrum, PRINCE2, risikovurderingsmetoder osv.) bruges som kontekst i erfaringsbeskrivelser, ikke som standalone-chips
- Bevar 100% af faktuelle informationer. Opfind aldrig ny erfaring
- Skriv på dansk medmindre andet er angivet$sys$,
    user_prompt = $usr$Omskriv CV'et nedenfor så det er optimalt målrettet det vedlagte jobopslag.

Retningslinjer:
- Omstrukturer rækkefølgen af erfaringer og kompetencer så de mest relevante fremhæves øverst
- Inkorporer nøgleord fra jobopslaget naturligt i teksten
- Kvantificér resultater med tal, hvor kilden har dem. Ellers beskriv omfanget og hvad der blev anderledes
- Formatér som struktureret plaintext med klare sektionsoverskrifter:
  1. Profil/Resumé
  2. Kompetencer (grupperet som ovenfor)
  3. Erhvervserfaring
  4. Projekter (hvis relevant)
  5. Uddannelse
  6. Certificeringer (hvis relevant)
  7. Sprog (talte)
- Fjern eller nedprioritér irrelevante afsnit$usr$
WHERE is_system AND explainer_key = 'prompts.explainers.cvPlaintextTailoredDa';

UPDATE profile_skills ps SET category = 'Domain'
FROM skill_taxonomy t
WHERE ps.taxonomy_id = t.id
  AND ps.category = 'Security'
  AND t.normalized_name IN ('gdpr', 'iso 27001', 'nis2', 'nist', 'soc 2', 'pci dss', 'hipaa');

UPDATE skill_taxonomy SET category = 'Domain'
WHERE category = 'Security'
  AND normalized_name IN ('gdpr', 'iso 27001', 'nis2', 'nist', 'soc 2', 'pci dss', 'hipaa');
