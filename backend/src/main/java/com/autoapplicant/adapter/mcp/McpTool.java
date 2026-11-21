package com.autoapplicant.adapter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One tool of JobBuddy's MCP server. Tools only read, as the grant's user, or offer a checked
 * button; none changes anything (writing tools are #316).
 */
public interface McpTool {

    String name();

    /** What the model is told the tool does and when to use it. */
    String description();

    /** JSON Schema of the arguments. */
    Map<String, Object> inputSchema();

    /**
     * The tool's answer, serialised to JSON for the model.
     *
     * @throws McpToolException the arguments are wrong or name something that is not there; the
     *     message is shown to the model so it can correct itself
     */
    Object call(McpGrant grant, JsonNode arguments);
}
