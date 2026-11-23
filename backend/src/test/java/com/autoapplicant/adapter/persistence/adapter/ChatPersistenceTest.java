package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatMessage.Role;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Chats and their kept messages through V045 on a real Postgres. */
@Import({ChatPersistenceAdapter.class, UserPersistenceAdapter.class})
class ChatPersistenceTest extends PostgresIntegrationTest {

    private static final Instant AT = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired ChatPersistenceAdapter chats;
    @Autowired UserPersistenceAdapter users;

    private Chat chat;

    @BeforeEach
    void givenAChat() {
        UUID userId = users.save(new User(null, UUID.randomUUID() + "@example.dk", null, null, null,
                UserRole.USER, true, true, null, null)).id();
        chat = chats.save(new Chat(null, userId, UUID.randomUUID(), null, AT, AT));
    }

    @Test
    void messages_come_back_in_the_order_they_were_kept_even_with_one_timestamp() {
        chats.appendMessage(chat.id(), new ChatMessage(Role.USER, "Hej", AT));
        chats.appendMessage(chat.id(), new ChatMessage(Role.ASSISTANT, "Hej med dig", AT));
        chats.appendMessage(chat.id(), new ChatMessage(Role.USER, "Tak", AT));

        assertThat(chats.findMessages(chat.id()))
                .containsExactly(new ChatMessage(Role.USER, "Hej", AT),
                        new ChatMessage(Role.ASSISTANT, "Hej med dig", AT),
                        new ChatMessage(Role.USER, "Tak", AT));
    }

    @Test
    void an_answers_buttons_come_back_with_it() {
        ChatAction open = new ChatAction(ChatAction.Kind.STEP_THROUGH, List.of(
                ChatAction.Posting.unresolved(UUID.randomUUID()),
                ChatAction.Posting.unresolved(UUID.randomUUID())));
        chats.appendMessage(chat.id(), new ChatMessage(Role.ASSISTANT, "Gennemgå dem", AT, List.of(open)));

        assertThat(chats.findMessages(chat.id()).get(0).actions()).containsExactly(open);
    }

    @Test
    void deleting_a_chat_deletes_its_messages() {
        chats.appendMessage(chat.id(), new ChatMessage(Role.USER, "privat", AT.plus(1, ChronoUnit.SECONDS)));

        chats.deleteById(chat.id());

        assertThat(chats.findMessages(chat.id())).isEmpty();
    }
}
