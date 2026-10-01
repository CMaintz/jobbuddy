package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.domain.skill.EvidenceDraft;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillConfirmation;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.skills.*;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SkillController.class)
@AutoConfigureMockMvc(addFilters = false)
class SkillControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GetSkillTaxonomyUseCase       taxonomy;
    @MockitoBean ManageProfileSkillsUseCase    profileSkills;
    @MockitoBean GetSkillGapUseCase            skillGap;
    @MockitoBean SuggestSkillCandidatesUseCase skillCandidates;
    @MockitoBean GetEvidenceGapsUseCase        evidenceGaps;
    @MockitoBean ElicitEvidenceUseCase         elicitEvidence;
    @MockitoBean SecurityContextHelper         secCtx;
    @MockitoBean ProvisionFirebaseUserUseCase  provisionUser;
    @MockitoBean ResolveLinkedInUserUseCase    resolveLinkedInUser;
    @MockitoBean FirebaseAuth                  firebaseAuth;

    UUID userId  = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(secCtx.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    void search_uses_category_lookup_when_category_given() throws Exception {
        when(taxonomy.getByCategory("backend")).thenReturn(List.of(taxonomy("Java")));

        mvc.perform(get("/api/v1/skills").param("q", "ja").param("category", "backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Java"));

        verify(taxonomy).getByCategory("backend");
        verify(taxonomy, never()).search(anyString());
    }

    @Test
    void search_falls_back_to_text_search_when_category_blank() throws Exception {
        when(taxonomy.search("ja")).thenReturn(List.of(taxonomy("Java")));

        mvc.perform(get("/api/v1/skills").param("q", "ja").param("category", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(taxonomy).search("ja");
        verify(taxonomy, never()).getByCategory(any());
    }

    @Test
    void create_skill_returns_201_with_location() throws Exception {
        SkillTaxonomy saved = taxonomy("Kotlin");
        when(taxonomy.createOrGet("Kotlin", "backend")).thenReturn(saved);

        mvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kotlin","category":"backend"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/skills/" + saved.id()))
                .andExpect(jsonPath("$.name").value("Kotlin"));
    }

    @Test
    void add_profile_skill_ignores_client_id_and_user() throws Exception {
        UUID otherUser = UUID.randomUUID();
        when(profileSkills.addSkill(any(ProfileSkill.class))).thenReturn(profileSkill(skillId));

        mvc.perform(post("/api/v1/profile/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","userId":"%s","skillName":"Java",
                                 "yearsExperience":4,"category":"backend"}
                                """.formatted(UUID.randomUUID(), otherUser)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/profile/skills/" + skillId))
                .andExpect(jsonPath("$.skillName").value("Java"));

        ArgumentCaptor<ProfileSkill> captor = ArgumentCaptor.forClass(ProfileSkill.class);
        verify(profileSkills).addSkill(captor.capture());
        ProfileSkill sent = captor.getValue();
        assertThat(sent.id()).isNull();
        assertThat(sent.userId()).isEqualTo(userId);
        assertThat(sent.yearsExperience()).isEqualTo(4);
        assertThat(sent.category()).isEqualTo("backend");
    }

    @Test
    void update_profile_skill_uses_path_id_and_current_user() throws Exception {
        when(profileSkills.updateSkill(any(ProfileSkill.class))).thenReturn(profileSkill(skillId));

        mvc.perform(put("/api/v1/profile/skills/{id}", skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","skillName":"Java","proficiencyLevel":"EXPERT"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfileSkill> captor = ArgumentCaptor.forClass(ProfileSkill.class);
        verify(profileSkills).updateSkill(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(skillId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().proficiencyLevel()).isEqualTo("EXPERT");
    }

    @Test
    void delete_profile_skill_returns_204() throws Exception {
        mvc.perform(delete("/api/v1/profile/skills/{id}", skillId))
                .andExpect(status().isNoContent());

        verify(profileSkills).deleteSkill(skillId, userId);
    }

    @Test
    void skill_gap_is_analysed_for_current_user() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(skillGap.analyzeSkillGap(jobId, userId)).thenReturn(
                new GetSkillGapUseCase.SkillGapResult(List.of("Java"), List.of("Go"), 50));

        mvc.perform(get("/api/v1/jobs/{jobId}/skill-gap", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched", Matchers.contains("Java")))
                .andExpect(jsonPath("$.missing", Matchers.contains("Go")))
                .andExpect(jsonPath("$.coveragePct").value(50));
    }

    @Test
    void tailored_gaps_feed_evidence_gaps_into_tailoring() throws Exception {
        List<EvidenceGap> gaps = List.of(new EvidenceGap("Java", 12, "Template?"));
        List<EvidenceGap> tailored = List.of(new EvidenceGap("Java", 12, "Tailored?"));
        when(evidenceGaps.evidenceGaps(userId, 3)).thenReturn(gaps);
        when(elicitEvidence.tailorQuestions(userId, gaps)).thenReturn(tailored);

        mvc.perform(get("/api/v1/skills/evidence-gaps/tailored").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("Tailored?"));

        verify(elicitEvidence).tailorQuestions(userId, gaps);
    }

    @Test
    void draft_evidence_passes_answer_and_returns_draft() throws Exception {
        when(elicitEvidence.draftFromAnswer(userId, "Java", "I built X"))
                .thenReturn(new EvidenceDraft("Java", "s", "a", "r", List.of("40%")));

        mvc.perform(post("/api/v1/skills/evidence/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillName":"Java","answer":"I built X"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situation").value("s"))
                .andExpect(jsonPath("$.unsupportedFigures", Matchers.contains("40%")));
    }

    @Test
    void confirm_candidates_passes_decisions() throws Exception {
        when(skillCandidates.confirm(eq(userId), any())).thenReturn(List.of(profileSkill(skillId)));

        mvc.perform(post("/api/v1/skills/candidates/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmations":[{"name":"Java","decision":"YES",
                                  "usedInProduction":true,"yearsExperience":3}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(skillCandidates).confirm(userId, List.of(
                new SkillConfirmation("Java", SkillConfirmation.Decision.YES, true, 3)));
    }

    private SkillTaxonomy taxonomy(String name) {
        return new SkillTaxonomy(UUID.randomUUID(), name, name.toLowerCase(), null, "backend",
                List.of());
    }

    private ProfileSkill profileSkill(UUID id) {
        return new ProfileSkill(id, userId, "Java", null, "EXPERT", 4, true, 0, "backend");
    }
}
