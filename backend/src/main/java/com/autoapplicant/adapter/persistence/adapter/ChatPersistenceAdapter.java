package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ChatEntity;
import com.autoapplicant.adapter.persistence.entity.ChatMessageEntity;
import com.autoapplicant.adapter.persistence.repository.ChatJpaRepository;
import com.autoapplicant.adapter.persistence.repository.ChatMessageJpaRepository;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatPersistenceAdapter implements ChatRepositoryPort {

    private final ChatJpaRepository repo;
    private final ChatMessageJpaRepository messages;

    public ChatPersistenceAdapter(ChatJpaRepository repo, ChatMessageJpaRepository messages) {
        this.repo = repo;
        this.messages = messages;
    }

    @Override
    public List<Chat> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByLastActivityAtDesc(userId).stream()
                .map(ChatPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<Chat> findById(UUID chatId) {
        return repo.findById(chatId).map(ChatPersistenceAdapter::toDomain);
    }

    @Override
    public Chat save(Chat chat) {
        ChatEntity e = new ChatEntity();
        e.setId(chat.id());
        e.setUserId(chat.userId());
        e.setSessionId(chat.sessionId());
        e.setTitle(chat.title());
        e.setCreatedAt(chat.createdAt());
        e.setLastActivityAt(chat.lastActivityAt());
        return toDomain(repo.save(e));
    }

    @Override
    public void deleteById(UUID chatId) {
        repo.deleteById(chatId);
    }

    /** One turn per chat at a time, so counting gives the next position without a race. */
    @Override
    public void appendMessage(UUID chatId, ChatMessage message) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setChatId(chatId);
        e.setPosition(messages.countByChatId(chatId));
        e.setRole(message.role().name());
        e.setText(message.text());
        e.setCreatedAt(message.at());
        e.setActions(message.actions());
        e.setPlanIds(message.planIds());
        messages.save(e);
    }

    @Override
    public List<ChatMessage> findMessages(UUID chatId) {
        return messages.findByChatIdOrderByPositionAsc(chatId).stream()
                .map(e -> new ChatMessage(ChatMessage.Role.valueOf(e.getRole()), e.getText(),
                        e.getCreatedAt(), e.getActions(), e.getPlanIds()))
                .toList();
    }

    private static Chat toDomain(ChatEntity e) {
        return new Chat(e.getId(), e.getUserId(), e.getSessionId(), e.getTitle(), e.getCreatedAt(),
                e.getLastActivityAt());
    }
}
