package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.port.out.chat.ChatPlanRepositoryPort;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Plans in memory, with the same conditional moves as the database. */
public final class InMemoryChatPlans implements ChatPlanRepositoryPort {

    private final Map<UUID, ChatPlan> plans = new LinkedHashMap<>();

    @Override
    public synchronized ChatPlan create(ChatPlan plan) {
        UUID id = UUID.randomUUID();
        List<PlanItem> items = plan.items().stream()
                .map(i -> new PlanItem(UUID.randomUUID(), i.kind(), i.details(), i.state(),
                        i.failure(), i.resultApplicationId(), i.decidedAt(), i.finishedAt()))
                .toList();
        ChatPlan stored = new ChatPlan(id, plan.userId(), plan.chatId(), plan.createdAt(), items);
        plans.put(id, stored);
        return stored;
    }

    @Override
    public synchronized Optional<ChatPlan> findById(UUID planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    @Override
    public synchronized List<ChatPlan> findByIds(Collection<UUID> planIds) {
        return planIds.stream().map(plans::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public synchronized int failRunning(PlanFailure failure, Instant decidedBefore, Instant at) {
        List<UUID> running = plans.values().stream()
                .flatMap(p -> p.items().stream())
                .filter(i -> i.state() == PlanItem.State.RUNNING && i.decidedAt().isBefore(decidedBefore))
                .map(PlanItem::id)
                .toList();
        running.forEach(id -> finish(id, failure, null, at));
        return running.size();
    }

    @Override
    public synchronized boolean move(UUID itemId, PlanItem.State from, PlanItem.State to, Instant at) {
        return update(itemId, i -> i.state() == from
                ? new PlanItem(i.id(), i.kind(), i.details(), to, null, null, at, null)
                : null);
    }

    @Override
    public synchronized void finish(UUID itemId, PlanFailure failure, UUID resultApplicationId,
                                    Instant at) {
        update(itemId, i -> i.state() == PlanItem.State.RUNNING
                ? new PlanItem(i.id(), i.kind(), i.details(),
                        failure == null ? PlanItem.State.DONE : PlanItem.State.FAILED, failure,
                        resultApplicationId, i.decidedAt(), at)
                : null);
    }

    /** Replaces the item with what {@code change} returns; null leaves it and returns false. */
    private boolean update(UUID itemId, UnaryOperator<PlanItem> change) {
        for (ChatPlan plan : plans.values()) {
            for (PlanItem item : plan.items()) {
                if (item.id().equals(itemId)) {
                    PlanItem changed = change.apply(item);
                    if (changed == null) {
                        return false;
                    }
                    plans.put(plan.id(), new ChatPlan(plan.id(), plan.userId(), plan.chatId(),
                            plan.createdAt(), plan.items().stream()
                                    .map(i -> i.id().equals(itemId) ? changed : i).toList()));
                    return true;
                }
            }
        }
        return false;
    }
}
