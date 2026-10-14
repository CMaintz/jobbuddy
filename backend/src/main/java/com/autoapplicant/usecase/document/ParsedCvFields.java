package com.autoapplicant.usecase.document;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;

/** Null-tolerant reads of the CV parser's JSON response, whose fields are all optional. */
final class ParsedCvFields {

    private ParsedCvFields() {}

    static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    static List<String> arrayOrEmpty(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || !child.isArray()) return List.of();
        String[] values = new String[child.size()];
        for (int i = 0; i < child.size(); i++) values[i] = child.get(i).asText();
        return Arrays.asList(values);
    }
}
