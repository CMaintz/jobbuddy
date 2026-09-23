package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.*;
import org.springframework.stereotype.Service;

/**
 * Assembles {@link PromptComposition} objects for all AI generation paths.
 *
 * <p>Each structured-generation method (CV tailoring, application docs) accepts an optional
 * {@link PromptTemplate}. When provided, the template's {@code systemPrompt} and
 * {@code userPrompt} fields supply the AI persona and style guidance. The JSON output schema
 * is always appended by this class — it is never part of a user-editable template, ensuring
 * the response shape stays stable regardless of user customisation.
 */
@Service
public class PromptCompositionBuilder {

    public PromptComposition composeStructuredApplicationPrompt(
            String documentType,
            String careerProfileJson,
            PostingContext posting,
            String customInstructions,
            String motivationText,
            String targetLanguage,
            PromptTemplate styleTemplate,
            WritingProfile writingProfile,
            java.util.List<String> outcomeLessons,
            String companyFacts,
            String lengthPreference) {

        String jobDescription = posting != null ? posting.description() : null;
        String jobCountry = posting != null ? posting.country() : null;

        // Short recruiter/follow-up messages are outreach, not prose letters: same market, a
        // different medium, and the way they fail is sounding like sales rather than generic.
        boolean isLetter = isProseLetter(documentType);
        // Every cross-cutting guardrail resolved once for this medium: output language (the user's
        // explicit choice wins, otherwise the posting's detected language), market conventions, the
        // injection guard, and the banned-phrase block.
        GenerationGuardrails guardrails = GenerationGuardrails.forDocument(
                documentType, targetLanguage, jobDescription, jobCountry);
        String languageInstruction = guardrails.resolvedLanguage() != null
                ? "Write the document body in " + guardrails.resolvedLanguage() + "."
                : "Write in the same language as the job description when clear, otherwise Danish.";

        String docLabel = switch (documentType != null ? documentType.toUpperCase() : "") {
            case "COVER_LETTER" -> "a compelling cover letter";
            case "APPLICATION_TEXT" -> "a professional job application text";
            case "UNSOLICITED_APPLICATION" -> "an unsolicited application letter — the candidate is "
                    + "applying speculatively, there is NO posted vacancy. State early and clearly what "
                    + "kind of role the candidate is looking for, show genuine knowledge of or interest "
                    + "in the company, and make a concrete case for the value they would add. Do not "
                    + "reference 'the position' or 'the posting'. Around half of Danish vacancies are "
                    + "never advertised, so this letter is read as a proposal, not an application: lead "
                    + "with a specific problem or opportunity the candidate could take off the reader's "
                    + "hands, name the concrete evidence they have done it before, and keep it shorter "
                    + "than a posted-vacancy letter. Close by proposing a short conversation, and state "
                    + "that the candidate will follow up — never ask to be kept 'on file'. If (and only "
                    + "if) the instructions say the candidate has already phoned the company, open by "
                    + "referring to that call, which is the normal Danish sequence; otherwise write as "
                    + "a first approach and never imply a conversation that did not happen";
            case "RECRUITER_MESSAGE" -> "a brief, personalized recruiter message (under 150 words)";
            case "FOLLOW_UP_MESSAGE" -> "a polite follow-up message (under 100 words)";
            default -> "a professional document";
        };

        // System prompt: template persona if available, otherwise the built-in default
        String baseSystem = styleTemplate != null && styleTemplate.systemPrompt() != null
                ? styleTemplate.systemPrompt()
                : defaultApplicationSystemPrompt();
        String systemPrompt = baseSystem
                + "\nThe career profile context intentionally excludes the user's name, contact "
                + "information, profile image, LinkedIn URL, GitHub URL, and website URL. "
                + "Do not ask for, infer, invent, or output those private identity fields."
                + "\nReturn ONLY valid JSON — no markdown fences, no commentary.\n"
                + languageInstruction
                + "\n\n" + guardrails.untrustedInputBlock();

        // Style guidance from template, if any
        String styleGuidance = styleTemplate != null && styleTemplate.userPrompt() != null
                && !styleTemplate.userPrompt().isBlank()
                ? "\n\n## Style Guidance\n" + styleTemplate.userPrompt()
                : "";

        // Fixed task + JSON schema (never user-editable)
        String schema = """
                {
                  "body": "<full document text>",
                  "notes": ["1-3 specific observations about gaps or opportunities between the profile and this job — omit if none"]
                }""";

        String styleMemory = buildStyleMemory(writingProfile);

        // Structural scaffolding for prose letters (not the short recruiter/follow-up messages,
        // which carry their own word caps in docLabel).
        String structure = isLetter ? "\n\n" + LETTER_STRUCTURE : "";
        String lengthGuidance = isLetter ? "\n\n## Length\n" + letterLengthGuidance(lengthPreference) : "";
        String marketBlock = guardrails.marketRules().isBlank() ? "" : "\n\n" + guardrails.marketRules();
        // A posting-supplied contact is the one named recipient the letter may address. Everything
        // else about the recipient stays unnamed, per the structure block.
        String contactBlock = posting != null && posting.hasContactPerson()
                ? "\n\n## Named Contact\nThe posting names " + posting.contactPerson()
                  + " as the person to contact about this role. Address the letter to them by name, "
                  + "spelled exactly as given. Do not invent any other recipient, title, or detail "
                  + "about them."
                : "";

        String userPrompt = "Write " + docLabel + " based on the contact-free master career profile "
                + "and job description provided." + styleGuidance
                + (styleMemory.isBlank() ? "" : "\n\n" + styleMemory)
                + buildOutcomeLearnings(outcomeLessons)
                + "\n\n" + guardrails.honestyRules()
                + "\n\n" + TARGETING_RULES
                + marketBlock
                + contactBlock
                + structure
                + lengthGuidance
                + "\n\n" + guardrails.clicheBlock()
                + "\n\nReturn only valid JSON matching exactly this shape:\n" + schema
                + "\n\n## Contact-Free Master Career Profile JSON\n"
                + (careerProfileJson != null ? careerProfileJson : "")
                + "\n\n## Job Description\n"
                + (jobDescription != null ? jobDescription : "(no job description provided)")
                + requirementsBlock(posting)
                + (companyFacts != null && !companyFacts.isBlank()
                    ? "\n\n## Verified Company Facts\n(From the company's own website — trustworthy and safe "
                      + "to reference; distinct from the untrusted posting above.)\n" + companyFacts : "")
                + (customInstructions != null && !customInstructions.isBlank()
                    ? "\n\n## Additional Instructions\n" + customInstructions : "")
                + (motivationText != null && !motivationText.isBlank()
                    ? "\n\n## Applicant's Personal Motivation\n" + motivationText : "");

        return new PromptComposition(systemPrompt, userPrompt, "", "", "", "", userPrompt);
    }

    // ── Structured CV tailoring path ────────────────────────────────────────────────────────

    /**
     * Builds a prompt for structured CV tailoring.
     *
     * <p>The {@code styleTemplate} — if provided — customises the AI's persona and approach.
     * The JSON output schema (mapped to {@link com.autoapplicant.domain.document.structured.TailoredCvContent})
     * is always appended by this method.
     */
    public PromptComposition composeCvTailoringPrompt(
            String careerProfileJson,
            PostingContext posting,
            String customInstructions,
            String targetLanguage,
            PromptTemplate styleTemplate,
            CvTailoringGuidance guidance,
            java.util.List<String> outcomeLessons,
            String lengthPreference) {

        String jobDescription = posting != null ? posting.description() : null;
        String jobCountry = posting != null ? posting.country() : null;
        CvTailoringGuidance authoring = guidance != null ? guidance : CvTailoringGuidance.NONE;
        // Every cross-cutting guardrail for the CV medium, resolved once (see the application path).
        GenerationGuardrails guardrails = GenerationGuardrails.forMedium(
                GenerationGuardrails.Medium.CV, targetLanguage, jobDescription, jobCountry);
        String languageInstruction = guardrails.resolvedLanguage() != null
                ? "Write all rewritten text in " + guardrails.resolvedLanguage() + "."
                : "Write rewritten text in the same language as the job description when clear.";
        String marketRules = guardrails.marketRules();

        String baseSystem = styleTemplate != null && styleTemplate.systemPrompt() != null
                ? styleTemplate.systemPrompt()
                : defaultCvTailoringSystemPrompt();
        String systemPrompt = baseSystem + "\n" + languageInstruction + "\n\n"
                + guardrails.untrustedInputBlock();

        String styleGuidance = styleTemplate != null && styleTemplate.userPrompt() != null
                && !styleTemplate.userPrompt().isBlank()
                ? "\n\n## Style Guidance\n" + styleTemplate.userPrompt()
                : "";

        String schema = """
                {
                  "selectedProfile": "role-specific profile text",
                  "selectedSkills": ["skill"],
                  "experience": [{"sourceId":"<keep existing>","title":"...","subtitle":"...","location":"...","dateRange":"...","description":"...","bullets":["..."],"technologies":["..."],"links":[]}],
                  "projects": [],
                  "education": [],
                  "certifications": [],
                  "notes": ["1-3 specific observations about gaps or opportunities — omit if none"]
                }""";

        String styleMemory = buildStyleMemory(authoring.writingProfile());
        String sectionGuidance = sectionGuidanceBlock(authoring.sectionPrompts());

        String userPrompt = "Tailor the CV content from the contact-free master career profile below "
                + "to best match the job description. Your job is to SELECT and REWRITE the content "
                + "WITHIN each section (which profile text, which bullets, which skills, and how they "
                + "are phrased) — you do NOT control section order or placement, which the app decides "
                + "from the candidate's career stage and layout choices. Return the sections as named "
                + "in the schema; do not attempt to reorder them." + styleGuidance
                + (styleMemory.isBlank() ? "" : "\n\n" + styleMemory)
                + sectionGuidance
                + buildOutcomeLearnings(outcomeLessons)
                + "\n\nThe profile's skillCategories map files each skill under a heading (Languages, "
                + "Frameworks, Tools and so on), and the CV renders the skills section grouped by it. "
                + "Select skills knowing they will be grouped: a heading that ends up with one entry "
                + "reads as padding. Do not restate a skill's category in its name, and do not try to "
                + "order or group the list yourself — return selectedSkills as a flat list, most "
                + "relevant to this posting first, and the app groups it."
                + "\n\nRules: use only source facts; you may rewrite profile text, descriptions, "
                + "and bullets, but keep sourceId values unchanged. "
                + "Do not invent employers, titles, dates, schools, credentials, technologies, outcomes, or links."
                + "\n\n" + guardrails.honestyRules()
                + "\n\n" + TARGETING_RULES
                + "\n- When content must be condensed, drop the bullets with the lowest combination of "
                + "relevance to this posting's keywords and uniqueness within the document — not simply "
                + "the oldest ones. A dated bullet that hits posting keywords outranks a recent one that does not."
                + (marketRules.isBlank() ? "" : "\n\n" + marketRules)
                + "\n\n## Length\n" + cvLengthGuidance(lengthPreference)
                + "\n\n" + guardrails.clicheBlock()
                + "\n\nReturn only valid JSON matching exactly this shape:\n" + schema
                + "\n\n## Contact-Free Master Career Profile JSON\n"
                + (careerProfileJson != null ? careerProfileJson : "")
                + "\n\n## Job Description\n"
                + (jobDescription != null ? jobDescription : "(no job description provided)")
                + requirementsBlock(posting)
                + (customInstructions != null && !customInstructions.isBlank()
                    ? "\n\n## Additional Instructions\n" + customInstructions : "");

        return new PromptComposition(systemPrompt, userPrompt, "", "", "", "", userPrompt);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    /** The candidate's standing per-section instructions, subordinate to the honesty rules. */
    static String sectionGuidanceBlock(CvSectionPrompts sectionPrompts) {
        if (sectionPrompts == null || sectionPrompts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## Section Guidance\nThe candidate's standing "
                + "instructions for specific sections. Follow them while still obeying the honesty and "
                + "source-fact rules; they never license inventing content or overstating.");
        for (CvSection section : CvSection.values()) {
            String instruction = sectionPrompts.forSection(section);
            if (instruction != null && !instruction.isBlank()) {
                sb.append("\n- ").append(sectionLabel(section)).append(": ").append(instruction);
            }
        }
        return sb.toString();
    }

    private static String sectionLabel(CvSection section) {
        return switch (section) {
            case PROFILE -> "Profile";
            case SKILLS -> "Competencies";
            case EXPERIENCE -> "Experience";
            case PROJECTS -> "Projects";
            case EDUCATION -> "Education";
            case CERTIFICATIONS -> "Certifications";
        };
    }

    /** At most this many asks are listed; a posting demanding more is listing wishes, not requirements. */
    private static final int MAX_REQUIREMENTS = 25;

    /**
     * The posting's asks, extracted during enrichment and restated as a checklist.
     *
     * <p>The description above already contains them, but buried: a model reading 8000 characters
     * of prose reliably answers the first three demands and forgets the rest. This is the same
     * untrusted data, itemised — and it deliberately carries asks that are not skill labels
     * ("5 years of backend experience", "driving licence"), which the tiered keyword lists drop.
     */
    private static String requirementsBlock(PostingContext posting) {
        if (posting == null || posting.requirements().isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n## What The Posting Asks For\n")
                .append("Extracted from the posting above — the same untrusted data, itemised so none "
                        + "is overlooked. Answer the REQUIRED asks first, and only with what the profile "
                        + "genuinely supports: an ask the candidate cannot meet is left unanswered, never "
                        + "invented. An ask marked EXPERIENCE, EDUCATION or CERTIFICATION states a "
                        + "threshold — never claim to clear one the profile does not show.\n");
        posting.requiredFirst().stream().limit(MAX_REQUIREMENTS).forEach(r ->
                sb.append("- [").append(r.tier()).append('/').append(r.kind()).append("] ")
                  .append(oneLine(r.text())).append('\n'));
        return sb.toString().stripTrailing();
    }

    /** Flattens an extracted ask onto one line so it cannot forge prompt structure. */
    private static String oneLine(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").strip();
    }

    /**
     * Soft paragraph scaffolding for prose letters. Danish-market convention: a focused one-page
     * letter that opens specifically, proves value with a concrete example, connects to the
     * company, and closes confidently. Guidance, not a rigid template.
     */
    private static final String LETTER_STRUCTURE = """
            ## Structure
            Write it as a flowing letter (no headings, no bullet lists in the body):
            - Open with a specific hook — why THIS role at THIS company, not a generic greeting line.
            - One evidence paragraph that proves fit with a concrete, NAMED role or project from the \
            profile and its most relevant quantified outcome — depth over a list.
            - A short company-fit paragraph connecting the candidate's direction to the employer; \
            ground any company reference in the Verified Company Facts when provided.
            - Close with a brief, confident call to action.
            Do not invent a named recipient; a role-appropriate greeting the profile supports is fine.
            If — and only if — the profile carries an "availability" value, state it as one short             factual clause near the close (employers routinely ask, and a candidate who volunteers             it reads as someone who has thought the move through). Never invent a notice period or             start date the profile does not state.""";

    /**
     * The document types that get prose-letter treatment (structure, length target, market
     * conventions) — as opposed to the short recruiter and follow-up messages, which carry their
     * own word caps. Public so the quality evaluator scores exactly the documents this shapes.
     */
    public static boolean isProseLetter(String documentType) {
        String type = documentType != null ? documentType.toUpperCase() : "";
        return type.equals("COVER_LETTER") || type.equals("APPLICATION_TEXT")
                || type.equals("UNSOLICITED_APPLICATION");
    }

    /**
     * The word target for a prose letter. Single source of truth: the prompt asks for this number
     * and the evaluator scores against it, so instruction and measurement cannot drift apart.
     */
    public static int letterWordTarget(String lengthPreference) {
        return switch (normalizeLength(lengthPreference)) {
            case "SHORT" -> 200;
            case "DETAILED" -> 380;
            default -> 300;
        };
    }

    /** Word/paragraph target for prose letters, by the user's length preference. */
    private static String letterLengthGuidance(String pref) {
        int target = letterWordTarget(pref);
        return switch (normalizeLength(pref)) {
            case "SHORT" -> "Keep it tight — about " + target
                    + " words across 3 short paragraphs. One page maximum.";
            case "DETAILED" -> "You may go fuller — about " + target
                    + " words across 4 paragraphs — but never exceed one page.";
            default -> "Aim for about " + target
                    + " words across 3–4 short paragraphs. One page maximum.";
        };
    }

    /** Page/bullet budget for the tailored CV, by the user's length preference. */
    private static String cvLengthGuidance(String pref) {
        String base = "Match the length to the candidate's careerStage: a student or new grad should "
                + "fit one page; an experienced candidate may use up to two. Keep the most recent and "
                + "most relevant roles to 4–5 bullets each and older roles shorter; ";
        return base + switch (normalizeLength(pref)) {
            case "SHORT" -> "err toward a lean one-page CV, cutting the least relevant material first.";
            case "DETAILED" -> "a fuller two-page CV is acceptable when the experience genuinely supports it.";
            default -> "prefer concision — every line should earn its place against this posting.";
        };
    }

    /** SHORT | STANDARD | DETAILED, defaulting to STANDARD for null/blank/unknown input. */
    private static String normalizeLength(String pref) {
        if (pref == null || pref.isBlank()) return "STANDARD";
        return switch (pref.trim().toUpperCase()) {
            case "SHORT", "STANDARD", "DETAILED" -> pref.trim().toUpperCase();
            default -> "STANDARD";
        };
    }

    /**
     * Targeting discipline — archetype-aware framing and metrics precedence. Sharpens
     * generic output and enforces that quantified claims trace to the profile.
     * (Archetype detection + metrics precedence borrowed from an external reference implementation.)
     */
    private static final String TARGETING_RULES = """
            ## Targeting & Proof Rules
            - Detect the posting's dominant role archetype (e.g. platform/backend, AI/ML \
            implementation, data, product, design, marketing/communications) from its language, and \
            frame the profile FOR that archetype: lead with the experience, projects, and skills most \
            central to it and mirror its vocabulary. A generic, archetype-agnostic document is a failure. \
            If the profile declares targetArchetypes or a northStar, prefer that framing when it aligns \
            with the posting.
            - Quantified achievements and measurable outcomes ALREADY IN the profile are the \
            authoritative proof points — surface the ones most relevant to this posting first. Never \
            invent, round up, or embellish a metric that is not in the profile.
            - The profile's "proofPoints" are the candidate's own account of what they did and what \
            changed. Prefer them over a rephrased bullet when one fits the posting: they are the \
            most defensible material available, because the candidate wrote them to be asked about.
            - Ground every specific match claim in a concrete profile item (a named role, project, or \
            skill), never a vague assertion.
            - If the profile declares a careerStage, frame for it. For STUDENT / NEW_GRAD / \
            CAREER_CHANGER: lead with education, academic and personal projects, internships, and \
            transferable skills; treat substantial academic or self-directed projects as real, \
            defensible work; and NEVER imply years of professional experience the profile does not \
            show. For SENIOR / LEAD: lead with scope, impact, and ownership. When the stage is unset, \
            infer a reasonable stage from the profile's experience.""";

    /**
     * Lessons the user recorded on past application outcomes ("emphasise ML projects
     * next time") — the calibration loop from rejections back into generation.
     */
    private static String buildOutcomeLearnings(java.util.List<String> lessons) {
        if (lessons == null || lessons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n## Learnings From Past Applications\n")
                .append("The candidate recorded these takeaways from earlier application outcomes — apply them where relevant:\n");
        lessons.stream().limit(5).forEach(l -> sb.append("- ").append(l.strip()).append('\n'));
        return sb.toString().stripTrailing();
    }

    /** Renders the writing profile as a "## Writing Style" prompt block; empty string when there is nothing to say. */
    public String buildStyleMemory(WritingProfile profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder();
        if (profile.tone() != null) sb.append("Tone: ").append(profile.tone()).append("\n");
        if (profile.vocabularyNotes() != null)
            sb.append("Vocabulary: ").append(profile.vocabularyNotes()).append("\n");
        if (profile.phrasingPatterns() != null && !profile.phrasingPatterns().isEmpty()) {
            sb.append("Preferred phrases: ")
              .append(String.join(", ", profile.phrasingPatterns())).append("\n");
        }
        if (profile.dos() != null && !profile.dos().isEmpty()) {
            sb.append("Always:\n");
            profile.dos().stream().limit(10).forEach(d -> sb.append("- ").append(d.strip()).append('\n'));
        }
        if (profile.donts() != null && !profile.donts().isEmpty()) {
            sb.append("Never:\n");
            profile.donts().stream().limit(10).forEach(d -> sb.append("- ").append(d.strip()).append('\n'));
        }
        if (profile.structureNotes() != null && !profile.structureNotes().isBlank())
            sb.append("Structure: ").append(profile.structureNotes().strip()).append("\n");
        return sb.isEmpty() ? "" : "## Writing Style\n" + sb;
    }

    private static String appendLanguage(String systemPrompt, String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) return systemPrompt;
        return systemPrompt + "\nAlways write the output in " + targetLanguage + ".";
    }

    /**
     * The built-in application persona.
     *
     * <p>Kept even though V073 seeds this same voice as a template row: this is the fallback for a
     * database with no seeds at all, so removing it would turn an empty prompt_templates table
     * from a plain install into a broken one. The seeded row is what users read and fork; this is
     * what runs when there is nothing to read.
     */
    private static String defaultApplicationSystemPrompt() {
        return """
                You are an expert career coach and professional writer specialising in job applications.
                Your goal is to help job seekers craft compelling, authentic applications that match their unique voice.
                Always write in a professional yet personal tone. Focus on concrete achievements and relevant experience.
                When the profile includes soft skills (Communication, Leadership, etc.), weave them naturally into achievement
                narratives rather than listing them in a standalone section.
                """;
    }

    /** The built-in CV-tailoring persona. Same role as above — the no-seeds fallback. */
    private static String defaultCvTailoringSystemPrompt() {
        return """
                You are a careful CV tailoring specialist. Your task is to select and rewrite CV content
                from a structured master career profile so it best matches a specific job description.
                Return JSON only. The profile intentionally excludes the user's name and contact information
                — do not ask for it, infer it, or invent it.
                """;
    }
}
