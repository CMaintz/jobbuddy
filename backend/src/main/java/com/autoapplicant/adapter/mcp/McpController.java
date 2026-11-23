package com.autoapplicant.adapter.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the chat's own {@code claude} reaches JobBuddy's data. Only a running turn's token gets
 * here ({@link McpTokenFilter}); the desktop shell never calls it.
 */
@RestController
@Tag(name = "Chat tools (MCP)")
@Profile("desktop")
public class McpController {

    private final McpServer server;

    public McpController(McpServer server) {
        this.server = server;
    }

    @Operation(summary = "MCP JSON-RPC endpoint for the chat's local Claude Code")
    @ApiResponse(responseCode = "202", description = "A notification; it has no response")
    @PostMapping(value = McpTokenFilter.PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode request, HttpServletRequest http) {
        McpGrant grant = (McpGrant) http.getAttribute(McpTokenFilter.GRANT_ATTRIBUTE);
        return server.handle(request, grant)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.accepted().build());
    }
}
