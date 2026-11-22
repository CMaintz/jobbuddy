package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.chatplan.ChatPlanApproval;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Her decisions on what the chat proposed. These endpoints sit behind the desktop shell's token,
 * which the chat's agent never has, so approving is something only she can do.
 */
@RestController
@RequestMapping("/api/v1/chats/plans/{planId}/items/{itemId}")
@Tag(name = "Chat")
@Profile("desktop")
public class ChatPlanController {

    private final ChatPlanApproval approval;
    private final DecideChatPlanUseCase decisions;
    private final SecurityContextHelper secCtx;

    public ChatPlanController(ChatPlanApproval approval, DecideChatPlanUseCase decisions,
                              SecurityContextHelper secCtx) {
        this.approval = approval;
        this.decisions = decisions;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Approve a proposed change and carry it out; the plan as it now stands")
    @ApiResponse(responseCode = "404", description = "No such plan item of hers")
    @PostMapping("/approve")
    public ChatPlan approve(@PathVariable UUID planId, @PathVariable UUID itemId) {
        return approval.approve(secCtx.getCurrentUserId(), planId, itemId);
    }

    @Operation(summary = "Reject a proposed change; the plan as it now stands")
    @ApiResponse(responseCode = "404", description = "No such plan item of hers")
    @PostMapping("/reject")
    public ChatPlan reject(@PathVariable UUID planId, @PathVariable UUID itemId) {
        return decisions.reject(secCtx.getCurrentUserId(), planId, itemId);
    }
}
