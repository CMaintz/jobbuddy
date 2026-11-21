package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.GlobalExceptionHandler;
import com.autoapplicant.config.SecurityConfig;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatEvent;
import com.autoapplicant.domain.chat.ChatFailure;
import com.autoapplicant.domain.chat.ChatRejectedException;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.in.chat.ChatTurnUseCase;
import com.autoapplicant.port.in.chat.ManageChatsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The chat endpoints behind the desktop profile's real filter chain. */
@WebMvcTest(
        controllers = ChatController.class,
        properties = {
            "app.desktop.token=" + ChatControllerTest.TOKEN,
            "app.desktop.shell-origin=app://jobbuddy",
            "app.data-dir=build/desktop-test-data"
        })
@ActiveProfiles("desktop")
@Import({SecurityConfig.class, SecurityContextHelper.class, GlobalExceptionHandler.class})
class ChatControllerTest {

    /** Plain words, so the secret scanner does not read it as a leaked key. */
    static final String TOKEN = "chat-test-token-for-the-desktop-filter";
    private static final String TOKEN_HEADER = "X-JobBuddy-Token";

    @Autowired MockMvc mvc;

    @MockitoBean ProvisionLocalUserUseCase localUser;
    @MockitoBean ManageChatsUseCase chats;
    @MockitoBean ChatTurnUseCase turns;

    private final User local = new User(UUID.randomUUID(), "local@jobbuddy.invalid", null, null,
            null, UserRole.USER, true, true, Instant.now(), Instant.now());
    private final UUID chatId = UUID.randomUUID();

    @BeforeEach
    void givenTheLocalUser() {
        when(localUser.provisionLocalUser()).thenReturn(local);
    }

    @Test
    void every_chat_endpoint_needs_the_shell_token() throws Exception {
        List<MockHttpServletRequestBuilder> calls = List.of(
                get("/api/v1/chats"),
                post("/api/v1/chats"),
                patch("/api/v1/chats/" + chatId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"),
                delete("/api/v1/chats/" + chatId),
                get("/api/v1/chats/" + chatId + "/messages"),
                message("Hej"),
                post("/api/v1/chats/" + chatId + "/stop"));
        for (MockHttpServletRequestBuilder call : calls) {
            mvc.perform(call).andExpect(status().isForbidden());
        }

        verifyNoInteractions(chats, turns);
    }

    @Test
    void the_answer_streams_as_server_sent_events_in_order() throws Exception {
        doAnswer(inv -> {
            Consumer<ChatEvent> events = inv.getArgument(3);
            events.accept(new ChatEvent.Delta("Hej "));
            events.accept(new ChatEvent.Delta("med dig"));
            events.accept(new ChatEvent.Completed());
            return null;
        }).when(turns).send(eq(local.id()), eq(chatId), eq("Hej"), any());

        String body = stream(message("Hej").header(TOKEN_HEADER, TOKEN));

        assertThat(body).containsSubsequence(
                "event:delta", "\"text\":\"Hej \"",
                "event:delta", "\"text\":\"med dig\"",
                "event:done");
    }

    @Test
    void a_failed_turn_ends_the_stream_with_its_reason() throws Exception {
        doAnswer(inv -> {
            Consumer<ChatEvent> events = inv.getArgument(3);
            events.accept(new ChatEvent.Failed(ChatFailure.NOT_LOGGED_IN));
            return null;
        }).when(turns).send(eq(local.id()), eq(chatId), eq("Hej"), any());

        assertThat(stream(message("Hej").header(TOKEN_HEADER, TOKEN)))
                .contains("event:error", "\"code\":\"NOT_LOGGED_IN\"");
    }

    @Test
    void a_busy_chat_is_refused_with_a_code_the_ui_can_show() throws Exception {
        doThrow(new ChatRejectedException(ChatFailure.CHAT_BUSY))
                .when(turns).send(eq(local.id()), eq(chatId), eq("Hej"), any());

        mvc.perform(message("Hej").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_BUSY"));
    }

    @Test
    void too_many_running_chats_is_a_429() throws Exception {
        doThrow(new ChatRejectedException(ChatFailure.TOO_MANY_CHATS))
                .when(turns).send(eq(local.id()), eq(chatId), eq("Hej"), any());

        mvc.perform(message("Hej").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_CHATS"));
    }

    @Test
    void stop_is_for_the_signed_in_user() throws Exception {
        mvc.perform(post("/api/v1/chats/" + chatId + "/stop").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isNoContent());

        verify(turns).stop(local.id(), chatId);
    }

    @Test
    void the_list_shows_title_and_last_activity_but_not_the_session() throws Exception {
        Instant at = Instant.parse("2026-11-21T10:00:00Z");
        when(chats.list(local.id())).thenReturn(List.of(
                new Chat(chatId, local.id(), UUID.randomUUID(), "Opslag i Aarhus", at, at)));

        mvc.perform(get("/api/v1/chats").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(chatId.toString()))
                .andExpect(jsonPath("$[0].title").value("Opslag i Aarhus"))
                .andExpect(jsonPath("$[0].lastActivityAt").exists())
                .andExpect(jsonPath("$[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    private MockHttpServletRequestBuilder message(String text) {
        return post("/api/v1/chats/" + chatId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}");
    }

    private String stream(MockHttpServletRequestBuilder call) throws Exception {
        MvcResult started = mvc.perform(call).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
