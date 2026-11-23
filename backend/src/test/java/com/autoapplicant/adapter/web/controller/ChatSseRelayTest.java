package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.domain.chat.ChatEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatSseRelayTest {

    private final AtomicInteger stops = new AtomicInteger();

    @Test
    void when_she_is_gone_the_turn_is_stopped_once_and_nothing_more_is_written() {
        CountingEmitter gone = new CountingEmitter(true);
        ChatSseRelay relay = new ChatSseRelay(gone, stops::incrementAndGet);

        relay.accept(new ChatEvent.Delta("Hej"));
        relay.accept(new ChatEvent.Delta("igen"));
        relay.accept(new ChatEvent.Stopped());

        assertThat(stops).hasValue(1);
        assertThat(gone.sends).isEqualTo(1);
    }

    @Test
    void a_turn_that_ended_is_not_stopped_when_the_stream_closes() {
        CountingEmitter open = new CountingEmitter(false);
        ChatSseRelay relay = new ChatSseRelay(open, stops::incrementAndGet);

        relay.accept(new ChatEvent.Delta("Hej"));
        relay.accept(new ChatEvent.Completed());
        relay.streamClosed();

        assertThat(stops).hasValue(0);
        assertThat(open.sends).isEqualTo(2);
    }

    @Test
    void a_stream_that_closes_mid_answer_stops_the_turn() {
        ChatSseRelay relay = new ChatSseRelay(new CountingEmitter(false), stops::incrementAndGet);

        relay.accept(new ChatEvent.Delta("Hej"));
        relay.streamClosed();
        relay.streamClosed();

        assertThat(stops).hasValue(1);
    }

    /** An emitter whose client may already have disconnected. */
    private static final class CountingEmitter extends SseEmitter {

        private final boolean clientGone;
        int sends;

        CountingEmitter(boolean clientGone) {
            this.clientGone = clientGone;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sends++;
            if (clientGone) {
                throw new IOException("Broken pipe");
            }
        }

        @Override
        public void complete() {
            // No servlet response behind this emitter.
        }
    }
}
