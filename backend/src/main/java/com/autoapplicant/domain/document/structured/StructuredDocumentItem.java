package com.autoapplicant.domain.document.structured;

import java.util.List;

public record StructuredDocumentItem(
        String sourceId,
        String title,
        String subtitle,
        String location,
        String dateRange,
        String description,
        List<String> bullets,
        List<String> technologies,
        List<String> links,
        List<String> skills,
        /** Grouping label for skill items (e.g. "Languages", "Frameworks"); null for other item types. */
        String category
) {}
