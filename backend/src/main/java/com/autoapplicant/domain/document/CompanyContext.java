package com.autoapplicant.domain.document;

/**
 * What generation knows about the employer, for grounding a cover letter's company references.
 *
 * <p>Two sources at two trust levels, kept apart so the prompt can label them: {@code facts} are
 * AI-extracted from the company's own website (verified), {@code researchNotes} are pasted by the
 * candidate (semi-trusted). Either may be null.
 *
 * @param facts         verified facts from the company's own site, or null
 * @param researchNotes the candidate's own research, or null
 */
public record CompanyContext(String facts, String researchNotes) {

    public static final CompanyContext EMPTY = new CompanyContext(null, null);

    public boolean hasFacts() {
        return facts != null && !facts.isBlank();
    }

    public boolean hasResearch() {
        return researchNotes != null && !researchNotes.isBlank();
    }
}
