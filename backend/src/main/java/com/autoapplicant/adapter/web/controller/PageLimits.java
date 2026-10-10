package com.autoapplicant.adapter.web.controller;

/**
 * Bounds for client-supplied page sizes and result limits. An out-of-range value is clamped
 * rather than rejected, so an existing client asking for too much still gets a page back.
 */
final class PageLimits {

    /** The most rows one request may ask for. */
    static final int MAX_PAGE_SIZE = 100;

    private static final int MIN_PAGE_SIZE = 1;

    private PageLimits() {}

    /** {@code requested} pulled into {@code [1, MAX_PAGE_SIZE]}. */
    static int clampSize(int requested) {
        return Math.min(Math.max(requested, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }
}
