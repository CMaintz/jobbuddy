package com.autoapplicant.domain.document.structured;

import java.util.List;

public record StructuredDocumentSection(
        String id,
        String type,
        String heading,
        String body,
        List<StructuredDocumentItem> items
) {
    /** The {@code type} for user-authored custom sections (shared across build, assemble, render). */
    public static final String TYPE_CUSTOM = "custom";

    /** This section with every item's links removed (see {@link StructuredDocumentItem#withoutLinks()}). */
    public StructuredDocumentSection withoutLinks() {
        return new StructuredDocumentSection(
                id, type, heading, body, StructuredDocumentItem.withoutLinks(items));
    }
}
