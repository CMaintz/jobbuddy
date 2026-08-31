package com.autoapplicant.usecase.document;

/**
 * Display headings for CV sections, in the language the CV is written in.
 *
 * <p>The section {@code type} ids stay English and stable — the resume-builder mapper, the ATS
 * export, and the layout config all key off them. Only the human-readable heading changes, so a
 * Danish CV reads "Erhvervserfaring" rather than "Experience" while every consumer keeps working.
 *
 * <p>A market's conventions live in {@link MarketConventions}; this is only the vocabulary.
 *
 * <p>The resume builder exports CVs too, from its own model, and takes its headings from the
 * frontend's {@code resumeBuilder.section.*} translations. The two must agree, or the same CV
 * reads differently depending on which path exported it — keep them in step.
 */
final class CvSectionLabels {

    private final boolean danish;

    private CvSectionLabels(boolean danish) {
        this.danish = danish;
    }

    static CvSectionLabels forLanguage(String language) {
        return new CvSectionLabels(JobLanguageDetector.isDanish(language));
    }

    String profile()        { return danish ? "Profil" : "Profile"; }
    String skills()         { return danish ? "Kompetencer" : "Skills"; }
    String experience()     { return danish ? "Erhvervserfaring" : "Experience"; }
    String projects()       { return danish ? "Projekter" : "Projects"; }
    String education()      { return danish ? "Uddannelse" : "Education"; }
    String certifications() { return danish ? "Certificeringer" : "Certifications"; }
    String languages()      { return danish ? "Sprog" : "Languages"; }
    String interests()      { return danish ? "Fritidsinteresser" : "Interests"; }
    String references()     { return danish ? "Referencer" : "References"; }

    /**
     * The reference line Danish CVs close on. Danish readers expect the line and read its absence
     * as an omission; English-language CV advice now treats "References available on request" as
     * dead weight, so it is Danish-only.
     */
    String referencesNote() {
        return danish ? "Referencer oplyses gerne efter aftale." : null;
    }

    boolean isDanish() {
        return danish;
    }
}
