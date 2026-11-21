package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What one chat turn's MCP token allows: reading JobBuddy as {@code userId}, and offering buttons
 * to that turn through {@code actions}. Every tool takes the user from here, never from its
 * arguments, which the model writes.
 */
public record McpGrant(UUID userId, Consumer<ChatAction> actions) {}
