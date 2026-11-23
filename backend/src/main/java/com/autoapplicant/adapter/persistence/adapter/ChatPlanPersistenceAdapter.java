package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ChatPlanEntity;
import com.autoapplicant.adapter.persistence.entity.ChatPlanItemEntity;
import com.autoapplicant.adapter.persistence.entity.ChatPlanItemEntity.DetailsJson;
import com.autoapplicant.adapter.persistence.repository.ChatPlanItemJpaRepository;
import com.autoapplicant.adapter.persistence.repository.ChatPlanJpaRepository;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.PlanItem.Details;
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
        row.setDetails(toJson(item.details()));
        row.setState(item.state().name());
        return row;
    }

    private static DetailsJson toJson(Details details) {
        return switch (details) {
            case Details.Posting p -> new DetailsJson(p.postingId(), null, p.title(), p.company(),
                    null, null, null, null);
            case Details.Documents d -> new DetailsJson(d.postingId(), null, d.title(), d.company(),
                    null, null, null, null);
            case Details.StatusMove s -> new DetailsJson(s.postingId(), s.applicationId(), s.title(),
                    s.company(), s.fromStatus(), s.toStatus(), null, null);
            case Details.Interview i -> new DetailsJson(i.postingId(), i.applicationId(), i.title(),
                    i.company(), null, null, i.interviewBefore(), i.interviewAt());
        };
    }

    private static Details toDomain(PlanItem.Kind kind, DetailsJson j) {
        return switch (kind) {
            case SAVE_POSTING, IGNORE_POSTING -> new Details.Posting(j.postingId(), j.title(), j.company());
            case GENERATE_DOCUMENTS -> new Details.Documents(j.postingId(), j.title(), j.company());
            case UPDATE_STATUS -> new Details.StatusMove(j.postingId(), j.applicationId(), j.title(),
                    j.company(), j.fromStatus(), j.toStatus());
            case LOG_INTERVIEW -> new Details.Interview(j.postingId(), j.applicationId(), j.title(),
                    j.company(), j.interviewBefore(), j.interviewAt());
        };
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
    public int failRunning(PlanFailure failure, Instant decidedBefore, Instant at) {
        return items.failRunning(failure.name(), decidedBefore, at);
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
        PlanItem.Kind kind = PlanItem.Kind.valueOf(e.getKind());
        return new PlanItem(e.getId(), kind, toDomain(kind, e.getDetails()),
                PlanItem.State.valueOf(e.getState()),
                e.getFailure() == null ? null : PlanFailure.valueOf(e.getFailure()),
                e.getResultApplicationId(), e.getDecidedAt(), e.getFinishedAt());
    }
}
