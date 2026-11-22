package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ChatPlanEntity;
import com.autoapplicant.adapter.persistence.entity.ChatPlanItemEntity;
import com.autoapplicant.adapter.persistence.repository.ChatPlanItemJpaRepository;
import com.autoapplicant.adapter.persistence.repository.ChatPlanJpaRepository;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.port.out.chat.ChatPlanRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChatPlanPersistenceAdapter implements ChatPlanRepositoryPort {

    private final ChatPlanJpaRepository plans;
    private final ChatPlanItemJpaRepository items;

    public ChatPlanPersistenceAdapter(ChatPlanJpaRepository plans, ChatPlanItemJpaRepository items) {
        this.plans = plans;
        this.items = items;
    }

    /** Plan and items together, so she never sees a card for a plan that lost its items. */
    @Override
    @Transactional
    public ChatPlan create(ChatPlan plan) {
        ChatPlanEntity e = new ChatPlanEntity();
        e.setUserId(plan.userId());
        e.setChatId(plan.chatId());
        e.setCreatedAt(plan.createdAt());
        UUID planId = plans.save(e).getId();
        List<ChatPlanItemEntity> rows = new ArrayList<>();
        for (int position = 0; position < plan.items().size(); position++) {
            rows.add(toEntity(planId, position, plan.items().get(position)));
        }
        items.saveAll(rows);
        return findById(planId).orElseThrow();
    }

    private static ChatPlanItemEntity toEntity(UUID planId, int position, PlanItem item) {
        ChatPlanItemEntity row = new ChatPlanItemEntity();
        row.setPlanId(planId);
        row.setPosition(position);
        row.setKind(item.kind().name());
        row.setDetails(item.details());
        row.setState(item.state().name());
        return row;
    }

    @Override
    public Optional<ChatPlan> findById(UUID planId) {
        return findByIds(List.of(planId)).stream().findFirst();
    }

    @Override
    public List<ChatPlan> findByIds(Collection<UUID> planIds) {
        if (planIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanItem>> itemsByPlan = items.findByPlanIdInOrderByPositionAsc(planIds)
                .stream()
                .collect(Collectors.groupingBy(ChatPlanItemEntity::getPlanId,
                        Collectors.mapping(ChatPlanPersistenceAdapter::toDomain, Collectors.toList())));
        return plans.findAllById(planIds).stream()
                .map(p -> new ChatPlan(p.getId(), p.getUserId(), p.getChatId(), p.getCreatedAt(),
                        itemsByPlan.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Override
    public int failAllRunning(PlanFailure failure, Instant at) {
        return items.failAllRunning(failure.name(), at);
    }

    @Override
    public boolean move(UUID itemId, PlanItem.State from, PlanItem.State to, Instant at) {
        return items.move(itemId, from.name(), to.name(), at) == 1;
    }

    @Override
    public void finish(UUID itemId, PlanFailure failure, UUID resultApplicationId, Instant at) {
        PlanItem.State state = failure == null ? PlanItem.State.DONE : PlanItem.State.FAILED;
        items.finish(itemId, state.name(), failure == null ? null : failure.name(),
                resultApplicationId, at);
    }

    private static PlanItem toDomain(ChatPlanItemEntity e) {
        return new PlanItem(e.getId(), PlanItem.Kind.valueOf(e.getKind()), e.getDetails(),
                PlanItem.State.valueOf(e.getState()),
                e.getFailure() == null ? null : PlanFailure.valueOf(e.getFailure()),
                e.getResultApplicationId(), e.getDecidedAt(), e.getFinishedAt());
    }
}
