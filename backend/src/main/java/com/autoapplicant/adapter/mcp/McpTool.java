package com.autoapplicant.adapter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One tool of JobBuddy's MCP server. Tools read, as the grant's user, offer a checked button, or
 * propose a change for her to approve. None changes her data: a proposal is stored and waits for
 * her click in the app, which no tool can give.
 */
public interface McpTool {

    String name();

    /** What the model is told the tool does and when to use it. */
    String description();

    /** JSON Schema of the arguments. */
    Map<String, Object> inputSchema();

    /** False for a tool that stores something, even if only a proposal. */
    default boolean readOnly() {
        return true;
    }

    /**
     * The tool's answer, serialised to JSON for the model.
     *
     * @throws McpToolException the arguments are wrong or name something that is not there; the
     *     message is shown to the model so it can correct itself
     */
    Object call(McpGrant grant, JsonNode arguments);
}
