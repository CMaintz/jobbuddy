package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.document.CreatePromptTemplateRequest;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.in.document.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prompts")
@Tag(name = "Prompts")
public class PromptController {

    private final CreatePromptTemplateUseCase create;
    private final GetPromptTemplatesUseCase getAll;
    private final DuplicatePromptTemplateUseCase duplicate;
    private final SecurityContextHelper secCtx;

    public PromptController(CreatePromptTemplateUseCase create, GetPromptTemplatesUseCase getAll,
                             DuplicatePromptTemplateUseCase duplicate, SecurityContextHelper secCtx) {
        this.create = create;
        this.getAll = getAll;
        this.duplicate = duplicate;
        this.secCtx = secCtx;
    }

    @GetMapping
    public ResponseEntity<List<PromptTemplate>> list() {
        return ResponseEntity.ok(getAll.getTemplates(secCtx.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<PromptTemplate> create(@Valid @RequestBody CreatePromptTemplateRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        PromptTemplate template = new PromptTemplate(null, userId, req.name(), req.category(),
                req.description(), req.systemPrompt(), req.userPrompt(), req.outputConstraints(),
                req.isPublic(), null, 1, null, null, false);
        return ResponseEntity.ok(create.createTemplate(userId, template));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PromptTemplate> duplicate(@PathVariable UUID id,
                                                      @RequestParam(required = false) String name) {
        return ResponseEntity.ok(duplicate.duplicate(id, secCtx.getCurrentUserId(), name));
    }
}
