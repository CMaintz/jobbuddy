package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.skill.TaxonomyCandidate;
import com.autoapplicant.port.in.skills.CurateSkillTaxonomyUseCase;
import com.autoapplicant.port.in.skills.GetSkillTaxonomyUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Curating the skill vocabulary every user picks from.
 *
 * <p>Admin-only because these rows are shared reference data: approving one changes what the
 * autocomplete offers, how CVs group the skill, and whether it counts as a technology — for
 * everybody, not for the approver.
 */
@RestController
@RequestMapping("/api/v1/admin/skill-taxonomy")
@Tag(name = "Admin - Skill Taxonomy")
public class SkillTaxonomyAdminController {

    /** Enough to review in one sitting; the tail is single-posting noise anyway. */
    private static final int DEFAULT_LIMIT = 50;

    private final CurateSkillTaxonomyUseCase curate;
    private final GetSkillTaxonomyUseCase taxonomy;

    public SkillTaxonomyAdminController(CurateSkillTaxonomyUseCase curate,
                                        GetSkillTaxonomyUseCase taxonomy) {
        this.curate = curate;
        this.taxonomy = taxonomy;
    }

    @Operation(summary = "Skills the job market names that the taxonomy does not know, most-demanded first")
    @GetMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TaxonomyCandidate>> candidates(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(curate.candidates(limit));
    }

    @Operation(summary = "The categories an approval may file a skill under")
    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(taxonomy.getCategories());
    }

    @Operation(summary = "Add a candidate to the taxonomy under a chosen category")
    @ApiResponse(responseCode = "400", description = "Name or category missing — a category is never guessed")
    @PostMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SkillTaxonomy> approve(@RequestBody ApproveSkillRequest request) {
        return ResponseEntity.ok(curate.approve(request.name(), request.category()));
    }

    @Operation(summary = "Rule a label out of the taxonomy so the queue stops offering it")
    @ApiResponse(responseCode = "204", description = "Rejected")
    @PostMapping("/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reject(@RequestBody RejectSkillRequest request) {
        curate.reject(request.name(), request.reason());
        return ResponseEntity.noContent().build();
    }

    /** @param category one of the taxonomy's own categories — required, never inferred */
    public record ApproveSkillRequest(String name, String category) {}

    /** @param reason optional note to the next admin on why this is not a skill */
    public record RejectSkillRequest(String name, String reason) {}
}
