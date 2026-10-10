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
) {
    /**
     * This item with its links removed. Links are structured URLs (a project's
     * github.com/handle/repo, a credential page) that identify the user, so they never travel
     * to the AI provider; the assembler re-attaches them from source after the call.
     */
    public StructuredDocumentItem withoutLinks() {
        return new StructuredDocumentItem(sourceId, title, subtitle, location, dateRange, description,
                bullets, technologies, List.of(), skills, category);
    }

    /** Every item of {@code items} without links; a null list stays null. */
    public static List<StructuredDocumentItem> withoutLinks(List<StructuredDocumentItem> items) {
        return items == null ? null : items.stream().map(StructuredDocumentItem::withoutLinks).toList();
    }
}
