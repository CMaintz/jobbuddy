package com.autoapplicant.usecase.notes;

import com.autoapplicant.domain.user.Note;
import com.autoapplicant.port.in.notes.ManageNotesUseCase;
import com.autoapplicant.port.out.notes.NoteRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NoteService implements ManageNotesUseCase {

    private final NoteRepositoryPort noteRepo;

    public NoteService(NoteRepositoryPort noteRepo) {
        this.noteRepo = noteRepo;
    }

    @Override
    public List<Note> getNotesForJob(UUID userId, UUID jobId) {
        return noteRepo.findByUserIdAndJobId(userId, jobId);
    }

    @Override
    public Note createNote(UUID userId, UUID jobId, String content) {
        return noteRepo.save(new Note(null, userId, jobId, null, content, null, null));
    }

    @Override
    public Note updateNote(UUID noteId, UUID userId, String content) {
        Note existing = noteRepo.findById(noteId)
                .filter(n -> n.userId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        return noteRepo.save(new Note(existing.id(), existing.userId(), existing.jobId(),
                existing.applicationId(), content, existing.createdAt(), null));
    }

    @Override
    public void deleteNote(UUID noteId, UUID userId) {
        noteRepo.findById(noteId)
                .filter(n -> n.userId().equals(userId))
                .ifPresent(n -> noteRepo.deleteById(noteId));
    }
}
