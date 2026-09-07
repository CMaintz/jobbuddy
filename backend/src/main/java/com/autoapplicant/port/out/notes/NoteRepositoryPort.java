package com.autoapplicant.port.out.notes;

import com.autoapplicant.domain.user.Note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepositoryPort {
    List<Note> findByUserIdAndJobId(UUID userId, UUID jobId);
    Optional<Note> findById(UUID id);
    Note save(Note note);
    void deleteById(UUID noteId);
}
