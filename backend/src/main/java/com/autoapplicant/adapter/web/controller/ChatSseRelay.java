package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.chat.ChatEvent;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Writes a chat turn's events to her open stream. The stream is the only thing that keeps the
 * turn wanted: when it goes away mid-answer (the window closed, the connection dropped) the turn
 * is stopped, so no {@code claude} process runs on for nobody.
 *
 * <p>Events: {@code delta} ({@code {"text"}}), {@code action} ({@code {"action"}}, a checked
 * button) and {@code plan} ({@code {"plan"}}, a stored proposal), then one of {@code done},
 * {@code stopped} or
 * {@code error} ({@code {"code"}}, a {@link com.autoapplicant.domain.chat.ChatFailure} name).
 */
final class ChatSseRelay implements Consumer<ChatEvent> {

    private final SseEmitter emitter;
    private final Runnable stopTurn;
    private final AtomicBoolean finished = new AtomicBoolean();

    ChatSseRelay(SseEmitter emitter, Runnable stopTurn) {
        this.emitter = emitter;
        this.stopTurn = stopTurn;
    }

    @Override
    public void accept(ChatEvent event) {
        if (finished.get()) {
            return;
        }
        try {
            emitter.send(toSse(event));
        } catch (IOException | IllegalStateException e) {
            streamClosed();
            return;
        }
        if (event.isTerminal() && finished.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /** The stream ended from her side; stops the turn unless it had already ended. */
    void streamClosed() {
        if (finished.compareAndSet(false, true)) {
            stopTurn.run();
        }
    }

    private static SseEmitter.SseEventBuilder toSse(ChatEvent event) {
        if (event instanceof ChatEvent.Delta delta) {
            return named("delta", Map.of("text", delta.text()));
        }
        if (event instanceof ChatEvent.Action action) {
            return named("action", Map.of("action", action.action()));
        }
        if (event instanceof ChatEvent.Proposed proposed) {
            return named("plan", Map.of("plan", proposed.plan()));
        }
        if (event instanceof ChatEvent.Failed failed) {
            return named("error", Map.of("code", failed.failure().name()));
        }
        return named(event instanceof ChatEvent.Completed ? "done" : "stopped", Map.of());
    }

    private static SseEmitter.SseEventBuilder named(String name, Map<String, ?> data) {
        return SseEmitter.event().name(name).data(data);
    }
}
