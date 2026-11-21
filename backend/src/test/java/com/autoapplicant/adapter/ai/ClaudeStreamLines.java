package com.autoapplicant.adapter.ai;

/** Stream-json lines as Claude Code 2.1 prints them, trimmed to the fields that matter. */
final class ClaudeStreamLines {

    static final String INIT = "{\"type\":\"system\",\"subtype\":\"init\",\"tools\":[],"
            + "\"mcp_servers\":[],\"model\":\"claude-sonnet-5-5\"}";

    static final String RESULT_OK = "{\"type\":\"result\",\"subtype\":\"success\","
            + "\"is_error\":false,\"result\":\"2+2 er 4.\",\"stop_reason\":\"end_turn\"}";

    static final String RATE_LIMIT_ALLOWED = "{\"type\":\"rate_limit_event\","
            + "\"rate_limit_info\":{\"status\":\"allowed\",\"rateLimitType\":\"seven_day\"}}";

    static final String RATE_LIMIT_REJECTED = "{\"type\":\"rate_limit_event\","
            + "\"rate_limit_info\":{\"status\":\"rejected\",\"rateLimitType\":\"five_hour\"}}";

    private ClaudeStreamLines() {}

    static String textDelta(String text) {
        return "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"index\":1,"
                + "\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text + "\"}},"
                + "\"parent_tool_use_id\":null}";
    }

    /** A new content block starting: a text block, or a tool call between two text blocks. */
    static String blockStart(String blockType) {
        return "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_start\",\"index\":1,"
                + "\"content_block\":{\"type\":\"" + blockType + "\"}},\"parent_tool_use_id\":null}";
    }

    static String thinkingDelta(String thinking) {
        return "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"index\":0,"
                + "\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"" + thinking + "\"}},"
                + "\"parent_tool_use_id\":null}";
    }

    static String assistantError(String error, String text) {
        return "{\"type\":\"assistant\",\"message\":{\"model\":\"<synthetic>\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]},"
                + "\"error\":\"" + error + "\"}";
    }

    static String resultError(String text) {
        return "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":true,"
                + "\"terminal_reason\":\"api_error\",\"result\":\"" + text + "\"}";
    }
}
