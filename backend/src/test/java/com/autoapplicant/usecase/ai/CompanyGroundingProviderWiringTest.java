package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.company.Company;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.company.CompanyResearchRepositoryPort;
import com.autoapplicant.port.out.web.WebPageFetchPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * A desktop install runs chat on the user's Claude login and keeps an API key only for
 * embeddings, if at all. Company research is chat, so it has to reach the chat provider, which
 * can be the CLI agent: wired to the embeddings provider instead, it fails without a key and
 * every cover letter silently loses its company facts.
 */
@SpringJUnitConfig(CompanyGroundingService.class)
class CompanyGroundingProviderWiringTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final String FACTS = "- Builds payroll software for Danish SMEs";

    @MockitoBean CompanyRepositoryPort companyRepo;
    @MockitoBean CompanyResearchRepositoryPort researchRepo;
    @MockitoBean WebPageFetchPort webFetch;
    // Real default methods, so whichever convenience method the service calls reaches complete().
    // The install's one embedding-only key. Grounding is a chat job and must never reach for it.
    @MockitoBean(name = "embeddingProvider")
    EmbeddingProviderPort embeddingsProvider;

    @MockitoBean(name = "enrichmentChatProvider", answers = Answers.CALLS_REAL_METHODS)
    ChatProviderPort chatProvider;

    @Autowired CompanyGroundingService grounding;

    @Test
    void company_facts_come_from_the_chat_provider_when_there_is_no_api_key() {
        Company company = new Company(COMPANY_ID, "Acme", "acme", "https://acme.example", null,
                null, null, null, null, "DK", false, false, Instant.now(), Instant.now());
        when(companyRepo.findFacts(COMPANY_ID)).thenReturn(Optional.empty());
        when(companyRepo.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(webFetch.fetchText("https://acme.example")).thenReturn(Optional.of("Acme builds payroll"));
        when(chatProvider.complete(any(), anyBoolean(), anyString()))
                .thenReturn(AiCompletion.untracked(FACTS, "sonnet"));

        assertThat(grounding.contextFor(UUID.randomUUID(), COMPANY_ID).facts())
                .isEqualTo(FACTS);
        verifyNoInteractions(embeddingsProvider);
    }
}
