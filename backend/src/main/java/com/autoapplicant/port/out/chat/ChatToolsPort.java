package com.autoapplicant.port.out.chat;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatPlan;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** How a chat turn's agent is let into JobBuddy's MCP server for the length of one turn. */
public interface ChatToolsPort {

    /**
     * Opens access for one turn of chat {@code chatId}, reading as {@code userId}; what the model
     * offers her goes to {@code offers}. The caller closes the session when the turn ends.
     */
    ToolSession open(UUID userId, UUID chatId, Offers offers);

    /**
     * Where a turn's tools hand what they offer her, already checked by the server.
     *
     * @param actions buttons that open something in the app
     * @param plans proposed changes, stored and waiting for her to approve or reject them
     */
    record Offers(Consumer<ChatAction> actions, Consumer<ChatPlan> plans) {}

    /**
     * @param serverUrl the MCP endpoint, on loopback
     * @param token the bearer token for this turn alone
     * @param toolNames the server's tools, unprefixed; the agent may call these and nothing else
     * @param close revokes the token
     */
    record ToolSession(String serverUrl, String token, List<String> toolNames, Runnable close) {}
}
