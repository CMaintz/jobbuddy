package com.autoapplicant.domain.document;

/**
 * The posting as generation sees it: its text, the market it hires into, and the person it names.
 *
 * <p>These three travel together through every generation prompt, and passing them separately was
 * pushing the compose methods past a dozen parameters. Any of them may be null — a pasted job
 * description has no country and names nobody.
 *
 * @param description   the cleaned posting text, or null when generating without one
 * @param country       the posting's country, which selects market conventions
 * @param contactPerson the person the posting names, pre-rendered ("Mette Hansen, afdelingsleder"),
 *                      or null. Only ever a name the posting itself stated — never inferred.
 */
public record PostingContext(String description, String country, String contactPerson) {

    public static final PostingContext EMPTY = new PostingContext(null, null, null);

    /** A posting with text but no known country or contact — the pasted-description case. */
    public static PostingContext ofDescription(String description) {
        return new PostingContext(description, null, null);
    }

    public boolean hasContactPerson() {
        return contactPerson != null && !contactPerson.isBlank();
    }
}
