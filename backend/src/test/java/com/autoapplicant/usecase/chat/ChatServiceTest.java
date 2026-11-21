package com.autoapplicant.usecase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatTranscript;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final InMemoryChatRepository chats = new InMemoryChatRepository();
    private final FakeChatAgent agent = new FakeChatAgent();
    private final ChatTurns running = new ChatTurns(2);
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);
    private final ChatService service =
            new ChatService(chats, agent, Clock.fixed(NOW, ZoneOffset.UTC), running, jobs);
    private final ChatTurnService turns =
            new ChatTurnService(chats, agent, Clock.fixed(NOW, ZoneOffset.UTC), running);
    private final UUID her = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();

    @Test
    void each_new_chat_gets_a_session_of_its_own() {
        Chat first = service.create(her);
        Chat second = service.create(her);

        assertThat(first.sessionId()).isNotNull().isNotEqualTo(second.sessionId());
        assertThat(service.list(her)).extracting(Chat::id).contains(first.id(), second.id());
    }

    @Test
    void she_sees_only_her_own_chats() {
        service.create(someoneElse);
        Chat mine = service.create(her);

        assertThat(service.list(her)).extracting(Chat::id).containsExactly(mine.id());
    }

    @Test
    void messages_are_jobbuddys_own_copy_and_survive_the_session() {
        Chat chat = service.create(her);
        ChatMessage hello = new ChatMessage(ChatMessage.Role.USER, "Hej", NOW);
        chats.appendMessage(chat.id(), hello);
        agent.sessions.add(chat.sessionId());

        assertThat(service.messages(her, chat.id())).isEqualTo(new ChatTranscript(List.of(hello), false));

        agent.sessions.remove(chat.sessionId());
        assertThat(service.messages(her, chat.id())).isEqualTo(new ChatTranscript(List.of(hello), true));
    }

    @Test
    void offered_buttons_come_back_checked_and_a_vanished_posting_loses_its_button() {
        Chat chat = service.create(her);
        UUID kept = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        when(jobs.findByIds(List.of(kept))).thenReturn(List.of(
                Job.builder().id(kept).title("Kommunikationskonsulent").companyName("Arla").build()));
        when(jobs.findByIds(List.of(deleted))).thenReturn(List.of());
        chats.appendMessage(chat.id(), new ChatMessage(ChatMessage.Role.ASSISTANT, "Her er to.", NOW,
                List.of(unresolved(ChatAction.Kind.OPEN_POSTING, kept),
                        unresolved(ChatAction.Kind.OPEN_POSTING, deleted))));

        assertThat(service.messages(her, chat.id()).messages().get(0).actions()).containsExactly(
                new ChatAction(ChatAction.Kind.OPEN_POSTING, List.of(
                        new ChatAction.Posting(kept, "Kommunikationskonsulent", "Arla"))));
    }

    private static ChatAction unresolved(ChatAction.Kind kind, UUID postingId) {
        return new ChatAction(kind, List.of(ChatAction.Posting.unresolved(postingId)));
    }

    @Test
    void a_new_chat_has_lost_nothing() {
        Chat chat = service.create(her);

        assertThat(service.messages(her, chat.id()).contextLost()).isFalse();
    }

    @Test
    void deleting_a_chat_also_forgets_its_transcript() {
        Chat chat = service.create(her);

        service.delete(her, chat.id());

        assertThat(service.list(her)).isEmpty();
        assertThat(agent.forgotten).containsExactly(chat.sessionId());
    }

    @Test
    void deleting_a_chat_mid_answer_stops_its_turn_before_the_transcript_goes() {
        Chat chat = service.create(her);
        List<ChatEvent> received = new ArrayList<>();
        turns.send(her, chat.id(), "Hej", received::add);

        service.delete(her, chat.id());

        assertThat(received).containsExactly(new ChatEvent.Stopped());
        assertThat(agent.operations).containsExactly(
                "stop:" + chat.sessionId(), "forget:" + chat.sessionId());
    }

    @Test
    void rename_trims_and_a_blank_title_is_refused() {
        Chat chat = service.create(her);

        assertThat(service.rename(her, chat.id(), "  Opslag i Aarhus ").title())
                .isEqualTo("Opslag i Aarhus");
        assertThatThrownBy(() -> service.rename(her, chat.id(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void someone_elses_chat_cannot_be_read_renamed_or_deleted() {
        Chat theirs = service.create(someoneElse);

        assertThatThrownBy(() -> service.messages(her, theirs.id()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.rename(her, theirs.id(), "x"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(her, theirs.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.list(someoneElse)).hasSize(1);
        assertThat(agent.forgotten).isEmpty();
    }
}
