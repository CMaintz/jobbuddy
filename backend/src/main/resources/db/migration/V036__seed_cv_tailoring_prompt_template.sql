-- V036: Seed system prompt template for structured CV tailoring.
--
-- The APPLICATION_* templates seeded in V017 (COVER_LETTER, APPLICATION, RECRUITER_MESSAGE,
-- GENERAL, CV_ANALYSIS) serve the prose-based generation path.
-- This template serves the structured CV tailoring path (TailoredCvGenerator).
--
-- The system_prompt and user_prompt here provide the AI persona and style guidance only.
-- The JSON output schema for TailoredCvContent is always injected by PromptCompositionBuilder
-- and is deliberately NOT stored here — keeping user-editable content separate from
-- the code-managed output contract.

INSERT INTO prompt_templates (user_id, name, category, description, system_prompt, user_prompt,
                              is_public, is_system, version_number)
VALUES
(NULL,
 'CV Tailoring — Default',
 'CV_TAILORING',
 'Default system prompt for structured CV tailoring. Focuses on factual accuracy, relevant selection, and keyword optimisation. The JSON output schema is enforced by the application.',
 'You are a careful CV tailoring specialist with deep knowledge of applicant tracking systems (ATS) and hiring practices.
Your task is to select and rewrite CV content from a structured master career profile so it best matches a specific job description.
The profile intentionally excludes the candidate''s name and contact information — do not ask for it, infer it, or invent it.
Prioritise relevance and specificity: select only the experience, projects, and skills that genuinely align with the role.
Rewrite bullet points to lead with strong action verbs and quantified outcomes where the source data supports it.
Never fabricate employers, job titles, dates, educational credentials, technologies, or measurable results.',
 'Select the most relevant profile text, skills, experience entries, projects, education, and certifications
for the target role. Rewrite descriptions and bullet points to emphasise relevance and impact.
Keep all sourceId values unchanged — they link back to the user''s master profile records.
Include keyword coverage metrics and note 1–3 specific gaps between the profile and the job requirements.',
 TRUE, TRUE, 1);
