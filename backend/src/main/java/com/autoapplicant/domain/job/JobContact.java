package com.autoapplicant.domain.job;

/**
 * The person a posting names as its contact — "Har du spørgsmål til stillingen, så kontakt
 * afdelingsleder Mette Hansen på 12 34 56 78".
 *
 * <p>Danish postings almost always name someone and invite a call before applying, and calling is
 * standard advice here rather than pushiness. Extracting the contact turns that into something the
 * app can act on: a letter addressed to a real person instead of "Kære rekrutteringsteam", and a
 * prompt to ring before writing.
 *
 * <p>Only ever populated from what the posting itself states — never guessed, never looked up.
 * A field is null when the posting did not give it.
 */
public record JobContact(String name, String title, String email, String phone) {

    /** True when nothing was extracted, so callers can skip the whole block. */
    public boolean isEmpty() {
        return blank(name) && blank(title) && blank(email) && blank(phone);
    }

    /** A contact worth addressing a letter to — a name is the minimum. */
    public boolean hasName() {
        return !blank(name);
    }

    /** "Mette Hansen, afdelingsleder" / "Mette Hansen" — for prompts and UI. */
    public String display() {
        if (blank(name)) return "";
        return blank(title) ? name.strip() : name.strip() + ", " + title.strip();
    }

    /** Null when every field is blank, so an empty extraction is stored as no contact at all. */
    public static JobContact ofNullable(String name, String title, String email, String phone) {
        JobContact contact = new JobContact(trimToNull(name), trimToNull(title),
                trimToNull(email), trimToNull(phone));
        return contact.isEmpty() ? null : contact;
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static String trimToNull(String v) {
        return blank(v) ? null : v.strip();
    }
}
