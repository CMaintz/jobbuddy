package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.skills.GetSkillTaxonomyUseCase;
import com.autoapplicant.port.in.skills.ManageProfileSkillsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Skills")
public class SkillController {

    private final GetSkillTaxonomyUseCase taxonomy;
    private final ManageProfileSkillsUseCase profileSkills;
    private final SecurityContextHelper secCtx;

    public SkillController(GetSkillTaxonomyUseCase taxonomy, ManageProfileSkillsUseCase profileSkills,
                           SecurityContextHelper secCtx) {
        this.taxonomy = taxonomy;
        this.profileSkills = profileSkills;
        this.secCtx = secCtx;
    }

    @GetMapping("/api/v1/skills")
    public ResponseEntity<List<SkillTaxonomy>> search(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(taxonomy.getByCategory(category));
        }
        return ResponseEntity.ok(taxonomy.search(q));
    }

    @GetMapping("/api/v1/skills/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(taxonomy.getCategories());
    }

    @GetMapping("/api/v1/profile/skills")
    public ResponseEntity<List<ProfileSkill>> getProfileSkills() {
        return ResponseEntity.ok(profileSkills.getSkills(secCtx.getCurrentUserId()));
    }

    @PostMapping("/api/v1/profile/skills")
    public ResponseEntity<ProfileSkill> addProfileSkill(@RequestBody ProfileSkill skill) {
        UUID userId = secCtx.getCurrentUserId();
        ProfileSkill toSave = new ProfileSkill(null, userId, skill.skillName(), skill.taxonomyId(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder());
        return ResponseEntity.ok(profileSkills.addSkill(toSave));
    }

    @PutMapping("/api/v1/profile/skills/{id}")
    public ResponseEntity<ProfileSkill> updateProfileSkill(@PathVariable UUID id,
                                                            @RequestBody ProfileSkill skill) {
        UUID userId = secCtx.getCurrentUserId();
        ProfileSkill toSave = new ProfileSkill(id, userId, skill.skillName(), skill.taxonomyId(),
                skill.proficiencyLevel(), skill.yearsExperience(), skill.usedInProduction(),
                skill.displayOrder());
        return ResponseEntity.ok(profileSkills.updateSkill(toSave));
    }

    @DeleteMapping("/api/v1/profile/skills/{id}")
    public ResponseEntity<Void> deleteProfileSkill(@PathVariable UUID id) {
        profileSkills.deleteSkill(id, secCtx.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
