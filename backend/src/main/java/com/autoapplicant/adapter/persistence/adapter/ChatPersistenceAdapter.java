package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.ChatEntity;
import com.autoapplicant.adapter.persistence.repository.ChatJpaRepository;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatPersistenceAdapter implements ChatRepositoryPort {

    private final ChatJpaRepository repo;

    public ChatPersistenceAdapter(ChatJpaRepository repo) {
        this.repo = repo;
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

    private static Chat toDomain(ChatEntity e) {
        return new Chat(e.getId(), e.getUserId(), e.getSessionId(), e.getTitle(), e.getCreatedAt(),
                e.getLastActivityAt());
    }
}
