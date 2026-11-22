package com.autoapplicant.adapter.mcp;

import com.autoapplicant.port.out.chat.ChatToolsPort.Offers;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What one chat turn's MCP token allows: reading JobBuddy as {@code userId}, and offering
 * buttons and proposals to that turn of chat {@code chatId} through {@code offers}. Every tool
 * takes the user and the chat from here, never from its arguments, which the model writes.
 *
 * @param proposals how many proposals the turn has made so far
 */
public record McpGrant(UUID userId, UUID chatId, Offers offers, AtomicInteger proposals) {

    /** More than she reads carefully in one answer; a model looping on proposals stops here. */
    static final int MAX_PROPOSALS_PER_TURN = 10;

    public McpGrant(UUID userId, UUID chatId, Offers offers) {
        this(userId, chatId, offers, new AtomicInteger());
    }

    /** Counts one more proposal; false once the turn has made its share. */
    boolean takeProposal() {
        return proposals.incrementAndGet() <= MAX_PROPOSALS_PER_TURN;
    }
}
