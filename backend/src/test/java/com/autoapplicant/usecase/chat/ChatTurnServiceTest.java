package com.autoapplicant.usecase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.domain.chat.ChatRejectedException;
import com.autoapplicant.domain.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatTurnServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final int MAX_CONCURRENT = 2;

    private final InMemoryChatRepository chats = new InMemoryChatRepository();
    private final FakeChatAgent agent = new FakeChatAgent();
    private final ChatTurnService turns = new ChatTurnService(
            chats, agent, Clock.fixed(NOW, ZoneOffset.UTC), new ChatTurns(MAX_CONCURRENT));
    private final UUID her = UUID.randomUUID();
    private final List<ChatEvent> received = new ArrayList<>();

    @Test
    void a_turn_runs_in_the_chats_own_session() {
        Chat chat = newChat(her);

        turns.send(her, chat.id(), "Hej", received::add);

        assertThat(agent.last().sessionId()).isEqualTo(chat.sessionId());
        assertThat(agent.last().message()).isEqualTo("Hej");
    }

    @Test
    void a_second_message_while_the_chat_is_answering_is_refused() {
        Chat chat = newChat(her);
        turns.send(her, chat.id(), "Første", received::add);

        assertThatThrownBy(() -> turns.send(her, chat.id(), "Anden", received::add))
                .isInstanceOfSatisfying(ChatRejectedException.class,
                        e -> assertThat(e.failure()).isEqualTo(ChatFailure.CHAT_BUSY));
        assertThat(agent.turns).hasSize(1);
    }

    @Test
    void the_chat_takes_messages_again_once_its_turn_ends() {
        Chat chat = newChat(her);
        turns.send(her, chat.id(), "Første", received::add);
        agent.last().events().accept(new ChatEvent.Completed());

        turns.send(her, chat.id(), "Anden", received::add);

        assertThat(agent.turns).hasSize(2);
    }

    @Test
    void only_so_many_chats_answer_at_once_and_a_finished_one_frees_its_slot() {
        for (int i = 0; i < MAX_CONCURRENT; i++) {
            turns.send(her, newChat(her).id(), "Hej", received::add);
        }
        UUID oneTooMany = newChat(her).id();

        assertThatThrownBy(() -> turns.send(her, oneTooMany, "Hej", received::add))
                .isInstanceOfSatisfying(ChatRejectedException.class,
                        e -> assertThat(e.failure()).isEqualTo(ChatFailure.TOO_MANY_CHATS));

        agent.turns.get(0).events().accept(new ChatEvent.Failed(ChatFailure.TIMEOUT));
        turns.send(her, oneTooMany, "Hej", received::add);
        assertThat(agent.turns).hasSize(MAX_CONCURRENT + 1);
    }

    @Test
    void a_turn_that_fails_as_it_starts_frees_its_chat_and_slot() {
        agent.failImmediately = new ChatEvent.Failed(ChatFailure.CLAUDE_MISSING);
        Chat chat = newChat(her);
        turns.send(her, chat.id(), "Hej", received::add);
        agent.failImmediately = null;

        turns.send(her, chat.id(), "Igen", received::add);

        assertThat(received).containsExactly(new ChatEvent.Failed(ChatFailure.CLAUDE_MISSING));
        assertThat(agent.turns).hasSize(2);
    }

    @Test
    void stop_ends_the_running_turn() {
        Chat chat = newChat(her);
        turns.send(her, chat.id(), "Hej", received::add);

        turns.stop(her, chat.id());

        assertThat(received).containsExactly(new ChatEvent.Stopped());
    }

    @Test
    void someone_elses_chat_can_be_neither_used_nor_stopped() {
        Chat theirs = newChat(UUID.randomUUID());
        turns.send(theirs.userId(), theirs.id(), "Hej", received::add);

        assertThatThrownBy(() -> turns.send(her, theirs.id(), "Hej", received::add))
                .isInstanceOf(NotFoundException.class);
        turns.stop(her, theirs.id());
        assertThat(received).isEmpty();
    }

    @Test
    void stopping_a_chat_that_is_gone_or_idle_is_harmless() {
        Chat idle = newChat(her);

        turns.stop(her, idle.id());
        turns.stop(her, UUID.randomUUID());

        assertThat(received).isEmpty();
    }

    @Test
    void a_failure_while_starting_frees_the_chat_and_its_slot() {
        Chat chat = newChat(her);
        chats.failNextSave = new IllegalStateException("database down");

        assertThatThrownBy(() -> turns.send(her, chat.id(), "Hej", received::add))
                .isInstanceOf(IllegalStateException.class);

        for (int i = 0; i < MAX_CONCURRENT - 1; i++) {
            turns.send(her, newChat(her).id(), "Hej", received::add);
        }
        turns.send(her, chat.id(), "Igen", received::add);
        assertThat(agent.turns).hasSize(MAX_CONCURRENT);
    }

    @Test
    void a_blank_or_overlong_message_is_refused_before_any_turn_starts() {
        Chat chat = newChat(her);

        assertThatThrownBy(() -> turns.send(her, chat.id(), " \t ", received::add))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> turns.send(her, chat.id(), "x".repeat(20_001), received::add))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(agent.turns).isEmpty();
        turns.send(her, chat.id(), "  Hej  ", received::add);
        assertThat(agent.last().message()).isEqualTo("Hej");
    }

    @Test
    void the_first_message_names_an_untitled_chat_and_marks_it_active() {
        Chat chat = newChat(her);

        turns.send(her, chat.id(), "  Find   nye opslag inden for kommunikation i Aarhus og"
                + " omegn, gerne med fokus på intern kommunikation  ", received::add);

        Chat saved = chats.findById(chat.id()).orElseThrow();
        assertThat(saved.title())
                .isEqualTo("Find nye opslag inden for kommunikation i Aarhus og omegn,…");
        assertThat(saved.lastActivityAt()).isEqualTo(NOW);
    }

    @Test
    void a_later_message_keeps_the_title_she_chose() {
        Chat chat = chats.save(newChat(her).withTitle("Mine opslag"));

        turns.send(her, chat.id(), "Hej", received::add);

        assertThat(chats.findById(chat.id()).orElseThrow().title()).isEqualTo("Mine opslag");
    }

    private Chat newChat(UUID owner) {
        Instant earlier = NOW.minusSeconds(3600);
        return chats.save(new Chat(null, owner, UUID.randomUUID(), null, earlier, earlier));
    }
}
