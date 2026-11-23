package com.autoapplicant.port.in.chat;

import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.ProposedChange;
import java.util.List;
import java.util.UUID;

/**
 * Stores what the chat model proposes as a plan waiting for her decision. This is the only way
 * the model's tools reach anything that writes, and it writes only the proposal: carrying it out
 * is {@link DecideChatPlanUseCase}, which only her own click reaches.
 */
public interface ProposeChatPlanUseCase {

    /**
     * @throws com.autoapplicant.domain.common.NotFoundException the chat is not hers
     * @throws com.autoapplicant.domain.chat.ProposalRefusedException a change names something
     *     that is not there or not hers, is not allowed, or she already has too many waiting
     */
    ChatPlan propose(UUID userId, UUID chatId, List<ProposedChange> changes);
}
