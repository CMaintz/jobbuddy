package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.user.Note;
import com.autoapplicant.port.in.notes.ManageNotesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/notes")
@Tag(name = "Notes")
public class NoteController {

    public record NoteRequest(@NotBlank String content) {}

    private final ManageNotesUseCase notes;
    private final SecurityContextHelper secCtx;

    public NoteController(ManageNotesUseCase notes, SecurityContextHelper secCtx) {
        this.notes = notes;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List notes for a job")
    @GetMapping
    public ResponseEntity<List<Note>> list(@PathVariable UUID jobId) {
        return ResponseEntity.ok(notes.getNotesForJob(secCtx.getCurrentUserId(), jobId));
    }

    @Operation(summary = "Create note for a job")
    @PostMapping
    public ResponseEntity<Note> create(@PathVariable UUID jobId,
                                       @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.ok(notes.createNote(secCtx.getCurrentUserId(), jobId, req.content()));
    }

    @Operation(summary = "Update a note")
    @PutMapping("/{noteId}")
    public ResponseEntity<Note> update(@PathVariable UUID jobId,
                                       @PathVariable UUID noteId,
                                       @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.ok(notes.updateNote(noteId, secCtx.getCurrentUserId(), req.content()));
    }

    @Operation(summary = "Delete a note")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID jobId,
                                       @PathVariable UUID noteId) {
        notes.deleteNote(noteId, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
