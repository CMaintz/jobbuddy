package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.InterviewStoryEntity;
import com.autoapplicant.adapter.persistence.repository.InterviewStoryJpaRepository;
import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class InterviewStoryPersistenceAdapter implements InterviewStoryRepositoryPort {

    private final InterviewStoryJpaRepository repo;

    public InterviewStoryPersistenceAdapter(InterviewStoryJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<InterviewStory> findByUserId(UUID userId) {
        return repo.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public InterviewStory save(InterviewStory story) {
        InterviewStoryEntity e = story.id() != null
                ? repo.findById(story.id()).orElseGet(InterviewStoryEntity::new)
                : new InterviewStoryEntity();
        e.setUserId(story.userId());
        e.setTitle(story.title());
        e.setSituation(story.situation());
        e.setTask(story.task());
        e.setAction(story.action());
        e.setResult(story.result());
        e.setReflection(story.reflection());
        e.setTags(toArray(story.tags()));
        return toDomain(repo.save(e));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        repo.deleteByIdAndUserId(id, userId);
    }

    private InterviewStory toDomain(InterviewStoryEntity e) {
        return new InterviewStory(e.getId(), e.getUserId(), e.getTitle(), e.getSituation(),
                e.getTask(), e.getAction(), e.getResult(), e.getReflection(),
                toList(e.getTags()), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static List<String> toList(String[] arr) { return arr != null ? Arrays.asList(arr) : List.of(); }
    private static String[] toArray(List<String> l) { return l != null ? l.toArray(String[]::new) : new String[0]; }
}
