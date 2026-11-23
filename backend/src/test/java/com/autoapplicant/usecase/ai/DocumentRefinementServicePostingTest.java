package com.autoapplicant.usecase.ai;

import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.INVENTING_LETTER;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.POSTING;
import static com.autoapplicant.usecase.ai.PostingAwareGuardsFixture.QUOTING_LETTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.RefineDocumentRequest;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.CvSectionTemplates;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** A refined letter is checked against the posting it answers, in strict mode. */
class DocumentRefinementServicePostingTest {

    private final ChatProviderPort provider = mock(ChatProviderPort.class);
    private final CareerProfileContextService profile = mock(CareerProfileContextService.class);
    private final DocumentRefinementService service = new DocumentRefinementService(
            provider, profile, PostingAwareGuardsFixture.guards("block"), mock(CvSectionTemplates.class));

    @Test
    void aPostingFigureQuotedAboutTheEmployerDoesNotBlockTheRefinement() throws Exception {
        modelReturns(QUOTING_LETTER);
        assertThat(service.refine(request()).get().refinedContent()).isEqualTo(QUOTING_LETTER);
    }

    @Test
    void anInventedFigureStillBlocksTheRefinement() {
        modelReturns(INVENTING_LETTER);
        assertThatThrownBy(() -> service.refine(request()).get())
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("40 medarbejdere");
    }

    private void modelReturns(String refined) {
        when(profile.buildJson(any())).thenReturn("{}");
        when(provider.generate(any(), anyString())).thenReturn(refined);
    }

    private static RefineDocumentRequest request() {
        return new RefineDocumentRequest(UUID.randomUUID(), "Udkast.", "Stram det op.", POSTING, "Danish",
                null, null);
    }
}
