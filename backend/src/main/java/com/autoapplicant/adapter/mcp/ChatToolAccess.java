package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** How a chat turn's agent is let into JobBuddy's MCP server for the length of one turn. */
public interface ChatToolAccess {

    /**
     * Opens access for one turn, reading as {@code userId}; buttons the model offers go to
     * {@code actions}. The caller closes the session when the turn ends.
     */
    ToolSession open(UUID userId, Consumer<ChatAction> actions);

    /**
     * @param serverUrl the MCP endpoint, on loopback
     * @param token the bearer token for this turn alone
     * @param toolNames the server's tools, unprefixed; the agent may call these and nothing else
     * @param close revokes the token
     */
    record ToolSession(String serverUrl, String token, List<String> toolNames, Runnable close) {}
}
