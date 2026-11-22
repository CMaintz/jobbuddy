package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.chatplan.ChatPlanApproval;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.chat.ChatRequests;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.document.GenerationSettings;
import com.autoapplicant.port.in.chat.DecideChatPlanUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Her decisions on what the chat proposed. These endpoints sit behind the desktop shell's token,
 * which the chat's agent never has, so approving is something only she can do.
 */
@RestController
@RequestMapping("/api/v1/chats/plans/{planId}")
@Tag(name = "Chat")
@Profile("desktop")
@ApiResponse(responseCode = "404", description = "No such plan or item of hers")
public class ChatPlanController {

    private static final String ITEM = "/items/{itemId}";

    private final ChatPlanApproval approval;
    private final DecideChatPlanUseCase decisions;
    private final SecurityContextHelper secCtx;

    public ChatPlanController(ChatPlanApproval approval, DecideChatPlanUseCase decisions,
                              SecurityContextHelper secCtx) {
        this.approval = approval;
        this.decisions = decisions;
        this.secCtx = secCtx;
    }

    @Operation(summary = "The plan as it now stands, for a card waiting on documents")
    @GetMapping
    public ChatPlan plan(@PathVariable UUID planId) {
        return decisions.plan(secCtx.getCurrentUserId(), planId);
    }

    @Operation(summary = "Approve a proposed change and carry it out; documents are queued")
    @PostMapping(ITEM + "/approve")
    public ChatPlan approve(@PathVariable UUID planId, @PathVariable UUID itemId,
                            @RequestBody(required = false) ChatRequests.Approve req) {
        return approval.approve(secCtx.getCurrentUserId(), planId, itemId, settings(req));
    }

    @Operation(summary = "Reject a proposed change")
    @PostMapping(ITEM + "/reject")
    public ChatPlan reject(@PathVariable UUID planId, @PathVariable UUID itemId) {
        return decisions.reject(secCtx.getCurrentUserId(), planId, itemId);
    }

    @Operation(summary = "Approve every change still waiting in the plan")
    @PostMapping("/approve")
    public ChatPlan approvePending(@PathVariable UUID planId,
                                   @RequestBody(required = false) ChatRequests.Approve req) {
        return approval.approvePending(secCtx.getCurrentUserId(), planId, settings(req));
    }

    @Operation(summary = "Reject every change still waiting in the plan")
    @PostMapping("/reject")
    public ChatPlan rejectPending(@PathVariable UUID planId) {
        return decisions.rejectPending(secCtx.getCurrentUserId(), planId);
    }

    private static GenerationSettings settings(ChatRequests.Approve req) {
        return req == null
                ? GenerationSettings.DEFAULTS
                : new GenerationSettings(req.lengthPreference(), req.cvTemplate(), req.voice(),
                        req.language());
    }
}
