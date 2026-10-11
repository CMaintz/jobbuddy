package com.autoapplicant.usecase.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.usecase.skills.ImpliedSkillQueue;
import com.autoapplicant.usecase.skills.ProfileSkillIngestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParsedCvWriterTest {

    @Mock ProfilePrivateInfoRepositoryPort  privateInfoRepo;
    @Mock ProfileSocialRepositoryPort       socialRepo;
    @Mock ImpliedSkillQueue                 impliedSkills;
    @Mock ProfileSkillIngestion             skillIngestion;

    ParsedCvWriter writer;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        writer = new ParsedCvWriter(privateInfoRepo, socialRepo, impliedSkills, skillIngestion);
    }

    @Test
    void writes_pii_socials_and_skills_from_a_full_cv() throws Exception {
        JsonNode node = json("""
                {"fullName":"Jane Doe","location":"Copenhagen",
                 "linkedinUrl":"https://linkedin.com/in/jane",
                 "githubUrl":"https://github.com/jane","websiteUrl":"https://jane.dev",
                 "skills":["Java","Spring"],"technologies":["Docker"]}""");
        when(privateInfoRepo.findByUserId(userId)).thenReturn(Optional.empty());
        when(socialRepo.findByUserId(userId)).thenReturn(List.of());

        writer.write(userId, node);

        ArgumentCaptor<ProfilePrivateInfo> pii = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
        verify(privateInfoRepo).save(pii.capture());
        assertThat(pii.getValue().fullName()).isEqualTo("Jane Doe");
        assertThat(pii.getValue().location()).isEqualTo("Copenhagen");

        verify(socialRepo, times(3)).save(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> skills = ArgumentCaptor.forClass(List.class);
        verify(skillIngestion).ingest(eq(userId), skills.capture());
        assertThat(skills.getValue()).containsExactly("Java", "Spring", "Docker");
        verify(impliedSkills).queue(eq(userId), any(), any(), eq(ParsedSkillSuggestion.Source.CV_PARSE));
    }

    @Test
    void merges_into_existing_private_info_preserving_untouched_fields() throws Exception {
        ProfilePrivateInfo existing = new ProfilePrivateInfo(UUID.randomUUID(), userId,
                "Old Name", "+45 12345678", "photo.jpg", "Aarhus", "Aarhus Kommune",
                "old@example.com", Instant.now(), Instant.now());
        when(privateInfoRepo.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(socialRepo.findByUserId(userId)).thenReturn(List.of());

        writer.write(userId, json("{\"fullName\":\"New Name\",\"skills\":[]}"));

        ArgumentCaptor<ProfilePrivateInfo> pii = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
        verify(privateInfoRepo).save(pii.capture());
        ProfilePrivateInfo saved = pii.getValue();
        assertThat(saved.fullName()).isEqualTo("New Name");        // updated from CV
        assertThat(saved.location()).isEqualTo("Aarhus");          // preserved (CV had none)
        assertThat(saved.phone()).isEqualTo("+45 12345678");       // preserved
        assertThat(saved.photoUrl()).isEqualTo("photo.jpg");       // preserved
        assertThat(saved.id()).isEqualTo(existing.id());           // preserved
    }

    @Test
    void skips_private_info_when_cv_has_no_name_or_location() throws Exception {
        when(socialRepo.findByUserId(userId)).thenReturn(List.of());

        writer.write(userId, json("{\"skills\":[\"Go\"]}"));

        verify(privateInfoRepo, never()).save(any());
        verify(privateInfoRepo, never()).findByUserId(any());
        verify(skillIngestion).ingest(eq(userId), any());
    }

    @Test
    void does_not_duplicate_a_social_link_that_already_exists() throws Exception {
        ProfileSocial existingLinkedin = new ProfileSocial(UUID.randomUUID(), userId,
                "LinkedIn", "https://linkedin.com/in/old", null, "linkedin", 0, null, null);
        when(socialRepo.findByUserId(userId)).thenReturn(List.of(existingLinkedin));

        writer.write(userId, json(
                "{\"linkedinUrl\":\"https://linkedin.com/in/x\",\"githubUrl\":\"https://github.com/x\"}"));

        ArgumentCaptor<ProfileSocial> cap = ArgumentCaptor.forClass(ProfileSocial.class);
        verify(socialRepo).save(cap.capture());                    // only one saved
        assertThat(cap.getValue().iconKey()).isEqualTo("github");  // linkedin skipped as dup
    }

    private static JsonNode json(String value) throws Exception {
        return new ObjectMapper().readTree(value);
    }
}
