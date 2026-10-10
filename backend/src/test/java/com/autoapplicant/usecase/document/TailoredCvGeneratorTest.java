package com.autoapplicant.usecase.document;

import static com.autoapplicant.usecase.document.IdentityLadenProfile.IDENTITY_VALUES;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_ID;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.PROJECT_NAME;
import static com.autoapplicant.usecase.document.IdentityLadenProfile.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.document.CvTailoringGuidance;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.CareerProfileForAi;
import com.autoapplicant.domain.document.structured.StructuredDocumentItem;
import com.autoapplicant.domain.document.structured.TailoredCvContent;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TailoredCvGeneratorTest {

    private static final PostingContext POSTING =
            new PostingContext("Backend engineer, Java and Kafka.", "DK", null, List.of());

    private final ChatProviderPort ai = mock(ChatProviderPort.class);
    private final TailoredCvGenerator generator =
            new TailoredCvGenerator(ai, new ObjectMapper(), new PromptCompositionBuilder());
    private final CareerProfileForAi source = IdentityLadenProfile.contextService().build(USER_ID);

    @Test
    void promptSentToTheModelCarriesNoIdentityValues() {
        when(ai.generateJson(any(PromptComposition.class), anyString())).thenReturn("{}");

        generate();

        ArgumentCaptor<PromptComposition> sent = ArgumentCaptor.forClass(PromptComposition.class);
        verify(ai).generateJson(sent.capture(), anyString());
        String prompt = sent.getValue().toString();
        assertThat(prompt).contains(PROJECT_NAME);
        IDENTITY_VALUES.forEach(value -> assertThat(prompt).doesNotContain(value));
    }

    @Test
    void parsesTheModelsTailoredContent() {
        when(ai.generateJson(any(PromptComposition.class), anyString())).thenReturn(
                "{\"selectedProfile\":\"Tailored.\",\"projects\":[{\"sourceId\":\"" + PROJECT_ID
                        + "\",\"title\":\"Ledger engine\"}]}");

        TailoredCvContent tailored = generate();

        assertThat(tailored.selectedProfile()).isEqualTo("Tailored.");
        assertThat(tailored.projects()).extracting(StructuredDocumentItem::title)
                .containsExactly("Ledger engine");
    }

    @Test
    void fallbackWhenTheModelFailsIsLinkFreeSoTheReviewerCannotLeakIt() {
        when(ai.generateJson(any(PromptComposition.class), anyString()))
                .thenThrow(new IllegalStateException("provider down"));

        TailoredCvContent fallback = generate();

        assertThat(fallback.projects()).extracting(StructuredDocumentItem::sourceId)
                .containsExactly(PROJECT_ID.toString());
        assertThat(fallback.projects()).allSatisfy(item -> assertThat(item.links()).isEmpty());
        assertThat(fallback.certifications())
                .allSatisfy(item -> assertThat(item.links()).isEmpty());
    }

    private TailoredCvContent generate() {
        return generator.generate(source, POSTING, null, "English", null, CvTailoringGuidance.NONE,
                List.of(), null);
    }
}
