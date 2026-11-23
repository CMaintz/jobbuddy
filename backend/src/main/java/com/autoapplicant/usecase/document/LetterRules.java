package com.autoapplicant.usecase.document;

import java.util.List;

/**
 * The rules a cover letter is written, reviewed and rewritten by, from the hard requirements of
 * the writing guide (job-search-kilder, {@code prompts/ansoegning.md}, MASTER "HÅRDE KRAV"). One
 * block for drafter, reviewer and rewriter, so no pass can undo what another shaped and spend a
 * gate round putting it back.
 */
public final class LetterRules {

    /** The guide asks for 3-5 needs; more would push the letter past one page. */
    public static final int MAX_NEEDS = 5;

    private LetterRules() {}

    public static final String BLOCK = """
            ## Letter Rules
            These rules win over any other instruction about the letter's structure or proof, \
            including the Targeting & Proof Rules: the profile supplies the proof, not the structure. \
            The letter looks forward (present and future tense); the CV looks back.
            - Write from the Employer Needs: pick the 2-3 the profile proves best and give each one \
            paragraph; leave the rest out rather than write a paragraph without proof. Each opens with a \
            present-tense claim about the employer or the field, not about the candidate. Then it \
            says what they get from the candidate and how the candidate does it for them (present \
            or future tense). It has at most ONE past-tense sentence, written as proof of the \
            paragraph's claim ("Det ved jeg, fordi ...", "That is how I know, because ..."), never \
            as a CV line. The paragraph ends looking forward, not in the proof.
            - At least half of the sentences are about the employer and what it gains.
            - No posting echo: never restate the posting's tasks or requirements as a list or \
            enumeration, and never lift an enumeration from it verbatim. Taking up ONE concrete task \
            and saying how the candidate handles it is exactly what is wanted. The posting says WHAT \
            they need done; the letter says WHY it is hard or important for them, and what they get.
            - Never volunteer what speaks against the candidate (experience they lack, something \
            new to them, gaps); that belongs in the interview. Never state where the posting was \
            found, a start date or an interview date that the profile or instructions do not give.
            - No CV recap: no walk through jobs, courses or education, chronological or otherwise. \
            A sentence that could move unchanged into the CV does not belong in the letter.
            - Motivation: name 1-2 concrete tasks the candidate looks forward to, and why. Never the \
            posting's task list.""";

    /**
     * The model's needs, each on one line, blanks dropped and capped at {@link #MAX_NEEDS}. Fewer
     * than three are kept as they are: a short list still beats writing from the task list.
     */
    public static List<String> needs(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(need -> need != null && !need.isBlank())
                .map(LetterRules::oneLine)
                .limit(MAX_NEEDS)
                .toList();
    }

    /** The needs as a prompt section, or nothing when there are none. */
    public static String needsBlock(List<String> needs) {
        if (needs == null || needs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## Employer Needs\nThe letter is written from these: one "
                + "paragraph for each of the 2-3 the profile proves best:\n");
        needs.forEach(need -> sb.append("- ").append(oneLine(need)).append('\n'));
        return sb.toString().stripTrailing();
    }

    /** One line, so a need taken from the model cannot forge a prompt section. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
