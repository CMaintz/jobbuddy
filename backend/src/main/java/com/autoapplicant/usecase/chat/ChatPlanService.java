package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.ProposalRefusedException;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import com.autoapplicant.port.in.chat.ProposeChatPlanUseCase;
import com.autoapplicant.port.in.chat.RecoverChatPlansUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.chat.ChatPlanRepositoryPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Proposals from the chat and her decisions on them. A proposal is stored before she sees it,
 * and every decision and result is stored on its item, so the chat shows the same cards after a
 * restart and the plans are the record of what the chat changed.
 */
@Service
@Profile("desktop")
public class ChatPlanService implements ProposeChatPlanUseCase, DecideChatPlanUseCase,
        RecoverChatPlansUseCase {

    private final ChatPlanRepositoryPort plans;
    private final ChatRepositoryPort chats;
    private final PlanChecks checks;
    private final Clock clock;

    public ChatPlanService(ChatPlanRepositoryPort plans, ChatRepositoryPort chats,
                           JobRepositoryPort jobs, ApplicationRepositoryPort applications,
                           Clock clock) {
        this.plans = plans;
        this.chats = chats;
        this.checks = new PlanChecks(jobs, applications, clock);
        this.clock = clock;
    }

    @Override
    public ChatPlan propose(UUID userId, UUID chatId, List<ProposedChange> changes) {
        OwnedChats.require(chats, userId, chatId);
        if (changes.isEmpty()) {
            throw new ProposalRefusedException("Propose at least one change");
        }
        List<PlanItem> items = changes.stream()
                .map(c -> PlanItem.proposed(null, c.kind(), checks.details(userId, c)))
                .toList();
        return plans.create(new ChatPlan(null, userId, chatId, clock.instant(), items));
    }

    /**
     * The move out of PENDING comes first, so a stale item still records that she approved it,
     * and two clicks racing each other cannot both get past it.
     */
    @Override
    public Claim claim(UUID userId, UUID planId, UUID itemId) {
        PlanItem item = ownedItem(userId, planId, itemId);
        if (!item.isPending()
                || !plans.move(itemId, PlanItem.State.PENDING, PlanItem.State.RUNNING, clock.instant())) {
            return new Claim.Settled(owned(userId, planId));
        }
        Optional<PlanFailure> stale = checks.staleness(userId, item);
        if (stale.isPresent()) {
            plans.finish(itemId, stale.get(), clock.instant());
            return new Claim.Settled(owned(userId, planId));
        }
        return new Claim.Approved(owned(userId, planId), item);
    }

    @Override
    public ChatPlan reject(UUID userId, UUID planId, UUID itemId) {
        ownedItem(userId, planId, itemId);
        plans.move(itemId, PlanItem.State.PENDING, PlanItem.State.REJECTED, clock.instant());
        return owned(userId, planId);
    }

    @Override
    public ChatPlan finish(UUID userId, UUID planId, UUID itemId, PlanFailure failure) {
        if (ownedItem(userId, planId, itemId).state() == PlanItem.State.RUNNING) {
            plans.finish(itemId, failure, clock.instant());
        }
        return owned(userId, planId);
    }

    @Override
    public int recoverInterrupted(Instant startedAt) {
        return plans.failRunning(PlanFailure.INTERRUPTED, startedAt, clock.instant());
    }

    private PlanItem ownedItem(UUID userId, UUID planId, UUID itemId) {
        return owned(userId, planId).item(itemId)
                .orElseThrow(() -> new NotFoundException("Plan item not found"));
    }

    private ChatPlan owned(UUID userId, UUID planId) {
        return plans.findById(planId)
                .filter(plan -> plan.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Plan not found"));
    }
}
