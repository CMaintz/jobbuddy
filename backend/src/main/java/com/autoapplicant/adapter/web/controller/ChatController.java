package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.chat.ChatRequests;
import com.autoapplicant.adapter.web.dto.chat.ChatView;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatTranscript;
import com.autoapplicant.port.in.chat.ChatTurnUseCase;
import com.autoapplicant.port.in.chat.ManageChatsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The app-wide chat on the user's own Claude Code. Desktop only, like the agent behind it.
 * A turn's answer streams back as server-sent events on the POST that sends the message (see
 * {@link ChatSseRelay} for the events).
 */
@RestController
@RequestMapping("/api/v1/chats")
@Tag(name = "Chat")
@Profile("desktop")
public class ChatController {

    /** The turn enforces its own time limit, so the stream itself never times out. */
    private static final long NO_STREAM_TIMEOUT = 0L;

    private final ManageChatsUseCase chats;
    private final ChatTurnUseCase turns;
    private final SecurityContextHelper secCtx;

    public ChatController(ManageChatsUseCase chats, ChatTurnUseCase turns,
                          SecurityContextHelper secCtx) {
        this.chats = chats;
        this.turns = turns;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List her chats, most recently active first")
    @GetMapping
    public List<ChatView> list() {
        return chats.list(secCtx.getCurrentUserId()).stream().map(ChatView::of).toList();
    }

    @Operation(summary = "Start a new, empty chat")
    @ApiResponse(responseCode = "201", description = "Chat created")
    @PostMapping
    public ResponseEntity<ChatView> create() {
        Chat chat = chats.create(secCtx.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatView.of(chat));
    }

    @Operation(summary = "Rename a chat")
    @PatchMapping("/{chatId}")
    public ChatView rename(@PathVariable UUID chatId, @RequestBody ChatRequests.Rename req) {
        return ChatView.of(chats.rename(secCtx.getCurrentUserId(), chatId, req.title()));
    }

    @Operation(summary = "Delete a chat and its transcript")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> delete(@PathVariable UUID chatId) {
        chats.delete(secCtx.getCurrentUserId(), chatId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "The chat's messages so far, and whether the agent still has them")
    @GetMapping("/{chatId}/messages")
    public ChatTranscript messages(@PathVariable UUID chatId) {
        return chats.messages(secCtx.getCurrentUserId(), chatId);
    }

    @Operation(summary = "Send a message; the answer streams back as server-sent events")
    @ApiResponse(responseCode = "409", description = "The chat is already answering (CHAT_BUSY)")
    @ApiResponse(responseCode = "429", description = "Too many chats are answering (TOO_MANY_CHATS)")
    @PostMapping(value = "/{chatId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@PathVariable UUID chatId, @RequestBody ChatRequests.Message req) {
        UUID userId = secCtx.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(NO_STREAM_TIMEOUT);
        ChatSseRelay relay = new ChatSseRelay(emitter, () -> turns.stop(userId, chatId));
        emitter.onCompletion(relay::streamClosed);
        emitter.onError(e -> relay.streamClosed());
        turns.send(userId, chatId, req.text(), relay);
        return emitter;
    }

    @Operation(summary = "Stop the chat's running answer")
    @ApiResponse(responseCode = "204", description = "Stopped, or nothing was running")
    @PostMapping("/{chatId}/stop")
    public ResponseEntity<Void> stop(@PathVariable UUID chatId) {
        turns.stop(secCtx.getCurrentUserId(), chatId);
        return ResponseEntity.noContent().build();
    }
}
