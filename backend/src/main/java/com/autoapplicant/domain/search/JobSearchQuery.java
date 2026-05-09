package com.autoapplicant.domain.search;

import java.util.UUID;

public record JobSearchQuery(
        String text,
        JobSearchFilters filters,
        int page,
        int size,
        String sortBy,
        UUID userId
) {
    public JobSearchQuery(String text, JobSearchFilters filters, int page, int size, String sortBy) {
        this(text, filters, page, size, sortBy, null);
    }
}
