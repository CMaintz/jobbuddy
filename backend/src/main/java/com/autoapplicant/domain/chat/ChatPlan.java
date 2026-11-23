package com.autoapplicant.domain.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Changes the chat proposed to her, waiting for her to approve or reject them. The model can only
 * propose: what it asked for is checked and stored here, and nothing changes until she approves an
 * item in the app. The plan is also the record of what was proposed, decided and done.
 *
 * @param chatId the chat it was proposed in; null once that chat is deleted, since the record of
 *     what changed her data outlives the conversation
 * @param items in the order they were proposed; one for a simple change
 */
public record ChatPlan(UUID id, UUID userId, UUID chatId, Instant createdAt, List<PlanItem> items) {

    public ChatPlan {
        items = List.copyOf(items);
    }

    public Optional<PlanItem> item(UUID itemId) {
        return items.stream().filter(i -> i.id().equals(itemId)).findFirst();
    }
}
