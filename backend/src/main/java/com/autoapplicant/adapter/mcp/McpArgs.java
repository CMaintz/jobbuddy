package com.autoapplicant.adapter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads tool arguments, which the model writes and posting text can steer. Anything that is not
 * what the schema promises is refused with a message for the model, never coerced.
 */
final class McpArgs {

    private McpArgs() {}

    static Optional<String> text(JsonNode args, String name, int maxLength) {
        JsonNode value = args.path(name);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return Optional.empty();
        }
        if (!value.isTextual() || value.asText().length() > maxLength) {
            throw new McpToolException(name + " must be text of at most " + maxLength + " characters");
        }
        return Optional.of(value.asText().strip());
    }

    static int number(JsonNode args, String name, int fallback, int min, int max) {
        JsonNode value = args.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (!value.canConvertToInt()) {
            throw new McpToolException(name + " must be a whole number");
        }
        return Math.clamp(value.asInt(), min, max);
    }

    static UUID id(JsonNode args, String name) {
        return parseId(text(args, name, Integer.MAX_VALUE)
                .orElseThrow(() -> new McpToolException(name + " is required")), name);
    }

    static List<UUID> ids(JsonNode args, String name, int max) {
        JsonNode values = args.path(name);
        if (!values.isArray() || values.isEmpty() || values.size() > max) {
            throw new McpToolException(name + " must list 1 to " + max + " posting ids");
        }
        List<UUID> ids = new ArrayList<>();
        values.forEach(v -> ids.add(parseId(v.asText(), name)));
        return ids;
    }

    /** One of {@code allowed}, matched without regard to case. */
    static Optional<String> choice(JsonNode args, String name, List<String> allowed) {
        return text(args, name, Integer.MAX_VALUE).map(value -> allowed.stream()
                .filter(value::equalsIgnoreCase)
                .findFirst()
                .orElseThrow(() -> new McpToolException(name + " must be one of " + allowed)));
    }

    private static UUID parseId(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new McpToolException(name + " must be posting ids as returned by the tools");
        }
    }
}
