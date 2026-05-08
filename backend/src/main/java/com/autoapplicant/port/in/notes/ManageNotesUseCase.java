package com.autoapplicant.port.in.notes;

import com.autoapplicant.domain.user.Note;

import java.util.List;
import java.util.UUID;

public interface ManageNotesUseCase {
    List<Note> getNotesForJob(UUID userId, UUID jobId);
    Note createNote(UUID userId, UUID jobId, String content);
    Note updateNote(UUID noteId, UUID userId, String content);
    void deleteNote(UUID noteId, UUID userId);
}
