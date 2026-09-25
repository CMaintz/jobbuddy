package com.autoapplicant.domain.document;

/**
 * The user-provided guidance for tailoring a CV: their analysed {@link WritingProfile} and their
 * standing per-section instructions ({@link CvSectionPrompts}). Bundled so the generation seam takes
 * one guidance object instead of growing its parameter list as guidance sources are added (the same
 * reason {@code PostingContext} exists). Either part may be null/empty.
 */
public record CvTailoringGuidance(WritingProfile writingProfile, CvSectionPrompts sectionPrompts) {

    public static final CvTailoringGuidance NONE = new CvTailoringGuidance(null, null);
}
