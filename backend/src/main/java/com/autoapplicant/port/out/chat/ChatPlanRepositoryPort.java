package com.autoapplicant.port.out.chat;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatPlanRepositoryPort {

    /** Stores a new plan; the returned one has its ids. */
    ChatPlan create(ChatPlan plan);

    Optional<ChatPlan> findById(UUID planId);

    List<ChatPlan> findByIds(Collection<UUID> planIds);

    /**
     * Moves the item from {@code from} to {@code to} in one conditional write, so of two
     * concurrent calls at most one succeeds.
     *
     * @return whether this call moved it
     */
    boolean move(UUID itemId, PlanItem.State from, PlanItem.State to, Instant at);

    /**
     * Ends a running item: done when {@code failure} is null, failed otherwise.
     *
     * @param resultApplicationId where its documents landed, or null
     */
    void finish(UUID itemId, PlanFailure failure, UUID resultApplicationId, Instant at);

    /**
     * Ends every item still running that went running before {@code decidedBefore} as failed
     * for {@code failure}; how many there were.
     */
    int failRunning(PlanFailure failure, Instant decidedBefore, Instant at);
}
