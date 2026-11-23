package com.autoapplicant.usecase.document;

/**
 * The posting a document was written from, as the fact gate needs it: its text, whose figures a
 * sentence about the employer may quote, and the company's name, which marks such a sentence.
 * Either part may be null; {@link #NONE} exempts nothing.
 */
public record EmployerPosting(String text, String companyName) {

    public static final EmployerPosting NONE = new EmployerPosting(null, null);

    public static EmployerPosting of(String text) {
        return new EmployerPosting(text, null);
    }

    boolean isBlank() {
        return text == null || text.isBlank();
    }
}
