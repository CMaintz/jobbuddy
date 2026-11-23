package com.autoapplicant.domain.document;

/**
 * A rendered document's page count against what Danish hiring expects of it: a CV runs to 2 pages
 * at most, a letter or message to 1. Going over is reported, never fixed by squeezing the layout.
 */
public record PageBudget(int pageCount, int budget) {

    private static final int CV_PAGES = 2;
    private static final int OTHER_PAGES = 1;

    public static PageBudget of(DocumentType type, int pageCount) {
        return new PageBudget(pageCount, type == DocumentType.CV ? CV_PAGES : OTHER_PAGES);
    }

    public boolean overBudget() {
        return pageCount > budget;
    }
}
