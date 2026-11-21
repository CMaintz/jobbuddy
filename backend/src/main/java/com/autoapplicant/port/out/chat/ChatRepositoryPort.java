package com.autoapplicant.port.out.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepositoryPort {

    /** Her chats, most recently active first. */
    List<Chat> findByUserId(UUID userId);

    Optional<Chat> findById(UUID chatId);

    Chat save(Chat chat);

    void deleteById(UUID chatId);

    /** Adds a message after the chat's others. */
    void appendMessage(UUID chatId, ChatMessage message);

    /** The chat's messages, oldest first. */
    List<ChatMessage> findMessages(UUID chatId);
}
