package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase.Claim;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Carries out an item of a chat plan when she approves it. It lives here, apart from the chat's
 * agent and its MCP tools, because it holds the use cases that change her data; only her own
 * request to the app reaches it. Each change runs through the same use case the app's screens use.
 */
@Component
@Profile("desktop")
public class ChatPlanApproval {

    private static final Logger log = LoggerFactory.getLogger(ChatPlanApproval.class);

    private final DecideChatPlanUseCase decisions;
    private final PlanItemRunner runner;

    public ChatPlanApproval(DecideChatPlanUseCase decisions, PlanItemRunner runner) {
        this.decisions = decisions;
        this.runner = runner;
    }

    /**
     * Runs the item if this call is the one that approves it, and returns the plan as it now
     * stands. A repeated or late click gets the plan back unchanged.
     *
     * @throws com.autoapplicant.domain.common.NotFoundException the plan is not hers
     */
    public ChatPlan approve(UUID userId, UUID planId, UUID itemId) {
        Claim claim = decisions.claim(userId, planId, itemId);
        if (!(claim instanceof Claim.Approved approved)) {
            return claim.plan();
        }
        return decisions.finish(userId, planId, itemId, run(userId, approved));
    }

    /**
     * The app's own use cases refuse with these two: not found, for what the item is about, and
     * not allowed. Anything else is unexpected. Null when it ran.
     */
    private PlanFailure run(UUID userId, Claim.Approved approved) {
        try {
            runner.run(userId, approved.item());
            return null;
        } catch (IllegalArgumentException e) {
            return approved.item().details().applicationId() == null
                    ? PlanFailure.POSTING_GONE : PlanFailure.APPLICATION_GONE;
        } catch (IllegalStateException e) {
            return PlanFailure.NOT_ALLOWED;
        } catch (RuntimeException e) {
            log.warn("Approved chat plan item {} failed", approved.item().id(), e);
            return PlanFailure.FAILED;
        }
    }
}
