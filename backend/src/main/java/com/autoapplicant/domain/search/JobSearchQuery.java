package com.autoapplicant.domain.search;

public record JobSearchQuery(
        String text,
        JobSearchFilters filters,
        int page,
        int size,
        String sortBy
) {}
