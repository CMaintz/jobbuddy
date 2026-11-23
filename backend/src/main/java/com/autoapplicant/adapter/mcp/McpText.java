package com.autoapplicant.adapter.mcp;

/** Keeps tool answers within a token budget. */
final class McpText {

    static final String CUT_MARK = " [...]";

    private McpText() {}

    /** {@code text} if it fits in {@code max} characters, else its start and a mark saying so. */
    static String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + CUT_MARK;
    }
}
