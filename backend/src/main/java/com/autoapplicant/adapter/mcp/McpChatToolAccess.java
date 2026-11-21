package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Opens JobBuddy's own MCP server to one chat turn, on the port this server actually got. */
@Component
@Profile("desktop")
public class McpChatToolAccess implements ChatToolAccess {

    private final McpGrants grants;
    private final McpServer server;
    private final Environment environment;

    public McpChatToolAccess(McpGrants grants, McpServer server, Environment environment) {
        this.grants = grants;
        this.server = server;
        this.environment = environment;
    }

    @Override
    public ToolSession open(UUID userId, Consumer<ChatAction> actions) {
        String token = grants.issue(userId, actions);
        return new ToolSession("http://127.0.0.1:" + port() + McpTokenFilter.PATH, token,
                server.toolNames(), () -> grants.revoke(token));
    }

    /** The shell may pass port 0; the bound port is only known once the server has started. */
    private String port() {
        return environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
    }
}
