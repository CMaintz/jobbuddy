package com.autoapplicant.adapter.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON Schema builders for tool arguments. */
final class McpSchema {

    private static final String TYPE = "type";
    private static final String DESCRIPTION = "description";

    private McpSchema() {}

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    static Map<String, Object> none() {
        return object(Map.of(), List.of());
    }

    static Map<String, Object> string(String description) {
        return Map.of(TYPE, "string", DESCRIPTION, description);
    }

    static Map<String, Object> oneOf(String description, List<String> values) {
        return Map.of(TYPE, "string", DESCRIPTION, description, "enum", values);
    }

    static Map<String, Object> integer(String description, int min, int max) {
        return Map.of(TYPE, "integer", DESCRIPTION, description, "minimum", min, "maximum", max);
    }

    static Map<String, Object> ids(String description, int max) {
        return Map.of(TYPE, "array", DESCRIPTION, description, "maxItems", max,
                "items", Map.of(TYPE, "string", "format", "uuid"));
    }
}
