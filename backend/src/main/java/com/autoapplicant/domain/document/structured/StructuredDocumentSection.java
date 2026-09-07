package com.autoapplicant.domain.document.structured;

import java.util.List;

public record StructuredDocumentSection(
        String id,
        String type,
        String heading,
        String body,
        List<StructuredDocumentItem> items
) {}
