package com.autoapplicant.usecase.document;

import static com.autoapplicant.usecase.document.IdentityLadenProfile.CREDENTIAL_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.FULL_NAME;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.IDENTITY_VALUES;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_GITHUB_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_ID;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_LIVE_URL;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_NAME;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.DocumentTheme;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.StructuredDocumentSection;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.CvSectionPromptsRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * End to end through tailored-CV generation: a user with every identity field filled in gets a
 * CV whose prompts (drafting and review) carry none of it, while the finished document still
 * shows their name and their own project and credential links.
 */
class TailoredCvAiPrivacyTest {

    private static final String FABRICATED_LINK = "https://invented.example/fake";
    private static final String AI_RESPONSE = "{\"selectedProfile\":\"Tailored.\",\"projects\":"
            + "[{\"sourceId\":\"" + PROJECT_ID + "\",\"title\":\"Ledger engine\",\"links\":[\""
            + FABRICATED_LINK + "\"]}]}";

    private final ChatProviderPort ai = mock(ChatProviderPort.class);

    @Test
    void promptsCarryNoIdentityAndTheDocumentGetsTheLinksBack() {
        when(ai.generateJson(any(PromptComposition.class), anyString())).thenReturn(AI_RESPONSE);

        StructuredDocument cv = service().generateTailoredCv(USER_ID, null, "Java and Kafka.",
                null, "English", null, null, false, DocumentTheme.defaults(), null);

        ArgumentCaptor<PromptComposition> sent = ArgumentCaptor.forClass(PromptComposition.class);
        verify(ai, atLeast(2)).generateJson(sent.capture(), anyString());
        String prompts = sent.getAllValues().toString();
        assertThat(prompts).contains(PROJECT_NAME);
        IDENTITY_VALUES.forEach(value -> assertThat(prompts).doesNotContain(value));

        assertThat(cv.identity().name()).isEqualTo(FULL_NAME);
        assertThat(onlyItem(cv, "projects").links())
                .containsExactly(PROJECT_GITHUB_URL, PROJECT_LIVE_URL);
        assertThat(onlyItem(cv, "certifications").links()).containsExactly(CREDENTIAL_URL);
    }

    private static StructuredDocumentItem onlyItem(StructuredDocument cv, String sectionId) {
        StructuredDocumentSection section = cv.sections().stream()
                .filter(s -> sectionId.equals(s.id())).findFirst().orElseThrow();
        assertThat(section.items()).hasSize(1);
        return section.items().get(0);
    }

    private StructuredDocumentService service() {
        ObjectMapper mapper = new ObjectMapper();
        PromptCompositionBuilder promptBuilder = new PromptCompositionBuilder();
        TailoredCvReviewer reviewer = new TailoredCvReviewer(ai, mapper, promptBuilder);
        ReflectionTestUtils.setField(reviewer, "autoReviewEnabled", true);
        return new StructuredDocumentService(users(), profiles(), privateInfo(), socials(),
                mock(JobRepositoryPort.class), mock(PromptTemplateRepositoryPort.class),
                mock(WritingProfileRepositoryPort.class), IdentityLadenProfile.contextService(),
                new CvDocumentAssembler(mock(AtsReportBuilder.class),
                        mock(KeywordCoverageCalculator.class)),
                new TailoredCvGenerator(ai, mapper, promptBuilder), reviewer,
                mock(AtsReportBuilder.class), mock(KeywordCoverageCalculator.class),
                mock(ApplicationRepositoryPort.class), mock(GeneratedContentGuards.class),
                mock(CvSectionPromptsRepositoryPort.class));
    }

    private static UserRepositoryPort users() {
        UserRepositoryPort repo = mock(UserRepositoryPort.class);
        when(repo.findById(USER_ID)).thenReturn(Optional.of(IdentityLadenProfile.user()));
        return repo;
    }

    private static ProfileRepositoryPort profiles() {
        ProfileRepositoryPort repo = mock(ProfileRepositoryPort.class);
        when(repo.findByUserId(USER_ID)).thenReturn(Optional.of(IdentityLadenProfile.profile()));
        return repo;
    }

    private static ProfilePrivateInfoRepositoryPort privateInfo() {
        ProfilePrivateInfoRepositoryPort repo = mock(ProfilePrivateInfoRepositoryPort.class);
        when(repo.findByUserId(USER_ID))
                .thenReturn(Optional.of(IdentityLadenProfile.privateInfo()));
        return repo;
    }

    private static ProfileSocialRepositoryPort socials() {
        ProfileSocialRepositoryPort repo = mock(ProfileSocialRepositoryPort.class);
        when(repo.findByUserId(USER_ID)).thenReturn(IdentityLadenProfile.socials());
        return repo;
    }
}
