package com.autoapplicant.port.in.chat;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import java.util.UUID;

/**
 * Her decisions on a plan the chat proposed. Every method takes the caller's id and treats
 * someone else's plan as missing ({@link com.autoapplicant.domain.common.NotFoundException}).
 * Running an approved item is the caller's job, through the same use cases the app's screens
 * use: {@link #claim} hands out an item at most once, and {@link #finish} records how it went.
 */
public interface DecideChatPlanUseCase {

    /**
     * Approves a pending item and checks it again against her data as it is now. Only one call
     * ever gets {@link Claim.Approved} for an item; any other gets {@link Claim.Settled}, as does
     * an item found stale, which is recorded as failed.
     */
    Claim claim(UUID userId, UUID planId, UUID itemId);

    /** Rejects a pending item; an item already decided is left as it is. */
    ChatPlan reject(UUID userId, UUID planId, UUID itemId);

    /** Records how a claimed item ran: done when {@code failure} is null. */
    ChatPlan finish(UUID userId, UUID planId, UUID itemId, PlanFailure failure);

    sealed interface Claim {

        ChatPlan plan();

        /** The caller must now run {@code item} and then {@link #finish} it. */
        record Approved(ChatPlan plan, PlanItem item) implements Claim {}

        /** Nothing to run: decided before, or stale and recorded as failed. */
        record Settled(ChatPlan plan) implements Claim {}
    }
}
