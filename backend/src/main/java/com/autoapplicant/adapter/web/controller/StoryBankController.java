package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.user.InterviewStoryRequest;
import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.port.in.user.ManageStoryBankUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interview/stories")
@Tag(name = "Interview Story Bank")
public class StoryBankController {

    private final ManageStoryBankUseCase useCase;
    private final SecurityContextHelper secCtx;

    public StoryBankController(ManageStoryBankUseCase useCase, SecurityContextHelper secCtx) {
        this.useCase = useCase;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List the current user's STAR+R interview stories")
    @GetMapping
    public ResponseEntity<List<InterviewStory>> list() {
        return ResponseEntity.ok(useCase.list(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Create or update a STAR+R interview story")
    @PutMapping
    public ResponseEntity<InterviewStory> upsert(@Valid @RequestBody InterviewStoryRequest req) {
        return ResponseEntity.ok(useCase.upsert(secCtx.getCurrentUserId(), req.id(), req.title(),
                req.situation(), req.task(), req.action(), req.result(), req.reflection(), req.tags()));
    }

    @Operation(summary = "Delete an interview story")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        useCase.remove(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
