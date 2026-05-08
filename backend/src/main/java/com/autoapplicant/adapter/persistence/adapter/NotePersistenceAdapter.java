package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.NoteEntity;
import com.autoapplicant.adapter.persistence.repository.NoteJpaRepository;
import com.autoapplicant.domain.user.Note;
import com.autoapplicant.port.out.notes.NoteRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotePersistenceAdapter implements NoteRepositoryPort {

    private final NoteJpaRepository repo;

    public NotePersistenceAdapter(NoteJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Note> findByUserIdAndJobId(UUID userId, UUID jobId) {
        return repo.findByUserIdAndJobId(userId, jobId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public Note save(Note note) {
        NoteEntity entity = toEntity(note);
        return toDomain(repo.save(entity));
    }

    @Override
    public void deleteById(UUID noteId) {
        repo.deleteById(noteId);
    }

    private Note toDomain(NoteEntity e) {
        return new Note(e.getId(), e.getUserId(), e.getJobId(), e.getApplicationId(),
                e.getContent(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private NoteEntity toEntity(Note n) {
        NoteEntity e = new NoteEntity();
        e.setId(n.id());
        e.setUserId(n.userId());
        e.setJobId(n.jobId());
        e.setApplicationId(n.applicationId());
        e.setContent(n.content());
        return e;
    }
}
