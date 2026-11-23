package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryChatRepository implements ChatRepositoryPort {

    private final Map<UUID, Chat> chats = new LinkedHashMap<>();
    RuntimeException failNextSave;
    private final Map<UUID, List<ChatMessage>> messages = new LinkedHashMap<>();

    @Override
    public List<Chat> findByUserId(UUID userId) {
        return chats.values().stream()
                .filter(c -> c.userId().equals(userId))
                .sorted(Comparator.comparing(Chat::lastActivityAt).reversed())
                .toList();
    }

    @Override
    public Optional<Chat> findById(UUID chatId) {
        return Optional.ofNullable(chats.get(chatId));
    }

    @Override
    public Chat save(Chat chat) {
        if (failNextSave != null) {
            RuntimeException failure = failNextSave;
            failNextSave = null;
            throw failure;
        }
        Chat stored = chat.id() == null
                ? new Chat(UUID.randomUUID(), chat.userId(), chat.sessionId(), chat.title(),
                        chat.createdAt(), chat.lastActivityAt())
                : chat;
        chats.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void deleteById(UUID chatId) {
        chats.remove(chatId);
        messages.remove(chatId);
    }

    @Override
    public void appendMessage(UUID chatId, ChatMessage message) {
        messages.computeIfAbsent(chatId, id -> new ArrayList<>()).add(message);
    }

    @Override
    public List<ChatMessage> findMessages(UUID chatId) {
        return List.copyOf(messages.getOrDefault(chatId, List.of()));
    }
}
