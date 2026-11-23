package com.autoapplicant.adapter.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * JobBuddy's MCP server: JSON-RPC over plain HTTP POST, answered with a single JSON response, as
 * the Streamable HTTP transport allows (https://modelcontextprotocol.io/specification). It offers
 * tools only: no resources, prompts or sampling, so the model has nothing else to reach for.
 */
@Component
@Profile("desktop")
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    /** Newest first; a client asking for another gets the newest and may decline it. */
    static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");

    static final String SERVER_NAME = "jobbuddy";

    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;

    private static final Map<String, Object> READ_ONLY = Map.of(
            "readOnlyHint", true, "destructiveHint", false, "openWorldHint", false);

    /** Stores a proposal and nothing else; her approval in the app is what changes her data. */
    private static final Map<String, Object> PROPOSES = Map.of(
            "readOnlyHint", false, "destructiveHint", false, "openWorldHint", false);

    private final Map<String, McpTool> tools;
    private final ObjectMapper json;

    public McpServer(List<McpTool> tools, ObjectMapper json) {
        this.tools = tools.stream().collect(Collectors.toMap(
                McpTool::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.json = json;
    }

    public List<String> toolNames() {
        return List.copyOf(tools.keySet());
    }

    /** The response to {@code request}, or empty for a notification, which gets none. */
    public Optional<JsonNode> handle(JsonNode request, McpGrant grant) {
        JsonNode id = request.get("id");
        if (id == null || id.isNull()) {
            return Optional.empty();
        }
        JsonNode params = request.path("params");
        return Optional.of(switch (request.path("method").asText()) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, Map.of());
            case "tools/list" -> result(id, Map.of("tools", tools.values().stream()
                    .map(McpServer::describe).toList()));
            case "tools/call" -> call(id, params, grant);
            default -> error(id, METHOD_NOT_FOUND, "Method not found");
        });
    }

    private static Map<String, Object> initialize(JsonNode params) {
        String asked = params.path("protocolVersion").asText();
        return Map.of(
                "protocolVersion", PROTOCOL_VERSIONS.contains(asked) ? asked : PROTOCOL_VERSIONS.get(0),
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", SERVER_NAME, "version", "1"));
    }

    private static Map<String, Object> describe(McpTool tool) {
        return Map.of("name", tool.name(), "description", tool.description(),
                "inputSchema", tool.inputSchema(),
                "annotations", tool.readOnly() ? READ_ONLY : PROPOSES);
    }

    private JsonNode call(JsonNode id, JsonNode params, McpGrant grant) {
        McpTool tool = tools.get(params.path("name").asText());
        if (tool == null) {
            return error(id, INVALID_PARAMS, "Unknown tool");
        }
        try {
            return result(id, toolResult(json.writeValueAsString(
                    tool.call(grant, params.path("arguments"))), false));
        } catch (McpToolException e) {
            return result(id, toolResult(e.getMessage(), true));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("MCP tool {} failed", tool.name(), e);
            return result(id, toolResult("The tool failed; try again or ask differently.", true));
        }
    }

    private static Map<String, Object> toolResult(String text, boolean isError) {
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", isError);
    }

    private JsonNode result(JsonNode id, Object result) {
        ObjectNode response = envelope(id);
        response.set("result", json.valueToTree(result));
        return response;
    }

    private JsonNode error(JsonNode id, int code, String message) {
        ObjectNode response = envelope(id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }

    private ObjectNode envelope(JsonNode id) {
        ObjectNode response = json.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        return response;
    }
}
