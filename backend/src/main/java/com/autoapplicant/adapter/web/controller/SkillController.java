package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.web.dto.user.RecordEvidenceRequest;
import com.autoapplicant.adapter.web.dto.user.SkillConfirmationRequest;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.GetSkillGapUseCase;
import com.autoapplicant.port.in.skills.GetSkillTaxonomyUseCase;
import com.autoapplicant.port.in.skills.ManageProfileSkillsUseCase;
import com.autoapplicant.port.in.skills.GetEvidenceGapsUseCase;
import com.autoapplicant.port.in.skills.SuggestSkillCandidatesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Skills")
public class SkillController {

    private final GetSkillTaxonomyUseCase taxonomy;
    private final ManageProfileSkillsUseCase profileSkills;
    private final GetSkillGapUseCase skillGap;
    private final SuggestSkillCandidatesUseCase skillCandidates;
    private final GetEvidenceGapsUseCase evidenceGaps;
    private final SecurityContextHelper secCtx;

    public SkillController(GetSkillTaxonomyUseCase taxonomy, ManageProfileSkillsUseCase profileSkills,
                           GetSkillGapUseCase skillGap, SuggestSkillCandidatesUseCase skillCandidates,
                           GetEvidenceGapsUseCase evidenceGaps,
                           SecurityContextHelper secCtx) {
        this.taxonomy = taxonomy;
        this.profileSkills = profileSkills;
        this.skillGap = skillGap;
        this.skillCandidates = skillCandidates;
        this.evidenceGaps = evidenceGaps;
        this.secCtx = secCtx;
    }

    @Operation(summary = "Claimed, in-demand skills with nothing to prove them",
            description = "The skills the candidate lists, the postings they match ask for, and no "
                    + "story in the bank supports. Derived on demand, so a gap closes the moment "
                    + "evidence is written. Deterministic — no AI call.")
    @GetMapping("/api/v1/skills/evidence-gaps")
    public ResponseEntity<List<EvidenceGap>> evidenceGaps(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(evidenceGaps.evidenceGaps(secCtx.getCurrentUserId(), limit));
    }

    @Operation(summary = "Record evidence for one claimed skill",
            description = "Stored as a STAR story tagged with the skill, so generation and "
                    + "interview prep both draw on it.")
    @PostMapping("/api/v1/skills/evidence")
    public ResponseEntity<InterviewStory> recordEvidence(@RequestBody RecordEvidenceRequest req) {
        return ResponseEntity.ok(evidenceGaps.recordEvidence(secCtx.getCurrentUserId(),
                req.skillName(), req.situation(), req.action(), req.result()));
    }

    @Operation(summary = "Skills worth asking the user about, best first",
            description = "Deterministic — no AI call. Candidates come from the seeded taxonomy "
                    + "(neighbours of skills the user already has) and from the postings the user "
                    + "actually matches, ranked by how many of those postings name them. A skill "
                    + "nobody is hiring for is not worth a question.")
    @GetMapping("/api/v1/skills/candidates")
    public ResponseEntity<List<SkillCandidate>> candidates(@RequestParam(defaultValue = "12") int limit) {
        return ResponseEntity.ok(skillCandidates.suggest(secCtx.getCurrentUserId(), limit));
    }

    @Operation(summary = "Answer a round of skill suggestions",
            description = "Accepted skills are added to the profile, declined ones are never "
                    + "offered again, skipped ones return next time. Returns the skills added.")
    @PostMapping("/api/v1/skills/candidates/confirm")
    public ResponseEntity<List<ProfileSkill>> confirmCandidates(@RequestBody SkillConfirmationRequest req) {
        return ResponseEntity.ok(
                skillCandidates.confirm(secCtx.getCurrentUserId(), req.confirmations()));
    }

    public record CreateSkillRequest(String name, String category) {}

    @Operation(summary = "Search skill taxonomy")
    @GetMapping("/api/v1/skills")
    public ResponseEntity<List<SkillTaxonomy>> search(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(taxonomy.getByCategory(category));
        }
        return ResponseEntity.ok(taxonomy.search(q));
    }

    @Operation(summary = "Create or get skill in taxonomy")
    @ApiResponse(responseCode = "201", description = "Skill created or already existed")
    @PostMapping("/api/v1/skills")
    public ResponseEntity<SkillTaxonomy> createSkill(@RequestBody CreateSkillRequest req) {
        SkillTaxonomy saved = taxonomy.createOrGet(req.name(), req.category());
        return ResponseEntity.created(URI.create("/api/v1/skills/" + saved.id())).body(saved);
    }

    @Operation(summary = "List skill categories")
    @GetMapping("/api/v1/skills/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(taxonomy.getCategories());
    }

    @Operation(summary = "List profile skills")
    @GetMapping("/api/v1/profile/skills")
    public ResponseEntity<List<ProfileSkill>> getProfileSkills() {
        return ResponseEntity.ok(profileSkills.getSkills(secCtx.getCurrentUserId()));
    }

    @Operation(summary = "Add skill to profile")
    @ApiResponse(responseCode = "201", description = "Skill added")
    @PostMapping("/api/v1/profile/skills")
    public ResponseEntity<ProfileSkill> addProfileSkill(@RequestBody ProfileSkill skill) {
        UUID userId = secCtx.getCurrentUserId();
        ProfileSkill toSave = new ProfileSkill(null, userId, skill.skillName(), skill.taxonomyId(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder(), null);
        ProfileSkill saved = profileSkills.addSkill(toSave);
        return ResponseEntity.created(URI.create("/api/v1/profile/skills/" + saved.id())).body(saved);
    }

    @Operation(summary = "Update profile skill")
    @PutMapping("/api/v1/profile/skills/{id}")
    public ResponseEntity<ProfileSkill> updateProfileSkill(@PathVariable UUID id,
                                                            @RequestBody ProfileSkill skill) {
        UUID userId = secCtx.getCurrentUserId();
        ProfileSkill toSave = new ProfileSkill(id, userId, skill.skillName(), skill.taxonomyId(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder(), null);
        return ResponseEntity.ok(profileSkills.updateSkill(toSave));
    }

    @Operation(summary = "Delete profile skill")
    @DeleteMapping("/api/v1/profile/skills/{id}")
    public ResponseEntity<Void> deleteProfileSkill(@PathVariable UUID id) {
        profileSkills.deleteSkill(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Analyse skill gap between a job and the authenticated user's profile")
    @GetMapping("/api/v1/jobs/{jobId}/skill-gap")
    public ResponseEntity<GetSkillGapUseCase.SkillGapResult> getSkillGap(@PathVariable UUID jobId) {
        return ResponseEntity.ok(skillGap.analyzeSkillGap(jobId, secCtx.getCurrentUserId()));
    }
}
