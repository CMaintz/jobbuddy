package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.document.CreatePromptTemplateRequest;
import com.autoapplicant.adapter.web.dto.document.PromptTemplateResponse;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.port.in.document.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
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
    private final UpdatePromptTemplateUseCase update;
    private final DeletePromptTemplateUseCase delete;
    private final FavouritePromptTemplateUseCase favourites;
    private final SelectDefaultPromptUseCase defaults;
    private final SecurityContextHelper secCtx;

    public PromptController(CreatePromptTemplateUseCase create, GetPromptTemplatesUseCase getAll,
                             DuplicatePromptTemplateUseCase duplicate,
                             UpdatePromptTemplateUseCase update, DeletePromptTemplateUseCase delete,
                             FavouritePromptTemplateUseCase favourites,
                             SelectDefaultPromptUseCase defaults,
                             SecurityContextHelper secCtx) {
        this.create = create;
        this.getAll = getAll;
        this.duplicate = duplicate;
        this.update = update;
        this.delete = delete;
        this.favourites = favourites;
        this.defaults = defaults;
        this.secCtx = secCtx;
    }

    @Operation(summary = "List prompt templates (with the caller's favourite and default flags)")
    @GetMapping
    public ResponseEntity<List<PromptTemplateResponse>> list() {
        UUID userId = secCtx.getCurrentUserId();
        var favIds = favourites.getFavouriteIds(userId);
        List<PromptTemplate> templates = getAll.getTemplates(userId);

        // Which row is actually in use per category — one lookup per category present, so the UI
        // can mark it without asking again per row.
        java.util.Set<UUID> selected = templates.stream()
                .map(PromptTemplate::category)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(c -> defaults.getDefault(userId, c).map(PromptTemplate::id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return ResponseEntity.ok(templates.stream()
                .map(t -> PromptTemplateResponse.from(t, favIds.contains(t.id()), selected.contains(t.id())))
                .toList());
    }

    @Operation(summary = "Use this template as the default for its category",
            description = "Records the caller's own choice. The app's seeded prompt is never "
                    + "modified, so resetting always has something to fall back to.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Default set"),
        @ApiResponse(responseCode = "403", description = "Not a template the caller may default to"),
        @ApiResponse(responseCode = "400", description = "Template not found, or not in that category")
    })
    @PutMapping("/{id}/default")
    public ResponseEntity<Void> selectDefault(@PathVariable UUID id) {
        PromptTemplate template = getAll.getTemplates(secCtx.getCurrentUserId()).stream()
                .filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        defaults.selectDefault(secCtx.getCurrentUserId(), template.category(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore the app's default prompt for a category")
    @ApiResponse(responseCode = "204", description = "Default reset")
    @DeleteMapping("/defaults/{category}")
    public ResponseEntity<Void> resetDefault(
            @PathVariable com.autoapplicant.domain.document.PromptCategory category) {
        defaults.resetDefault(secCtx.getCurrentUserId(), category);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Favourite a prompt template")
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> favourite(@PathVariable UUID id) {
        favourites.favourite(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unfavourite a prompt template")
    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> unfavourite(@PathVariable UUID id) {
        favourites.unfavourite(secCtx.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create prompt template")
    @ApiResponse(responseCode = "201", description = "Template created")
    @PostMapping
    public ResponseEntity<PromptTemplate> create(@Valid @RequestBody CreatePromptTemplateRequest req) {
        UUID userId = secCtx.getCurrentUserId();
        PromptTemplate template = new PromptTemplate(null, userId, req.name(), req.category(),
                req.description(), req.systemPrompt(), req.userPrompt(), req.outputConstraints(),
                req.isPublic(), null, 1, null, null, false,
                req.tags() != null ? req.tags() : List.of(), 0, false, false);
        PromptTemplate saved = create.createTemplate(userId, template);
        return ResponseEntity.created(URI.create("/api/v1/prompts/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update a prompt template (own templates; system templates require admin)")
    @ApiResponses({
        @ApiResponse(responseCode = "403", description = "Not the owner / not an admin for system templates"),
        @ApiResponse(responseCode = "400", description = "Template not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PromptTemplate> update(@PathVariable UUID id,
                                                  @Valid @RequestBody CreatePromptTemplateRequest req) {
        PromptTemplate changes = new PromptTemplate(null, null, req.name(), req.category(),
                req.description(), req.systemPrompt(), req.userPrompt(), req.outputConstraints(),
                req.isPublic(), null, 0, null, null, false,
                req.tags(), 0, false, false);
        return ResponseEntity.ok(update.updateTemplate(
                id, secCtx.getCurrentUserId(), secCtx.isAdmin(), changes));
    }

    @Operation(summary = "Delete a prompt template (own templates; system templates require admin)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "403", description = "Not the owner / not an admin for system templates")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        delete.deleteTemplate(id, secCtx.getCurrentUserId(), secCtx.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Duplicate a prompt template")
    @ApiResponse(responseCode = "201", description = "Duplicate created")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PromptTemplate> duplicate(@PathVariable UUID id,
                                                      @RequestParam(required = false) String name) {
        PromptTemplate saved = duplicate.duplicate(id, secCtx.getCurrentUserId(), name);
        return ResponseEntity.created(URI.create("/api/v1/prompts/" + saved.id())).body(saved);
    }
}
