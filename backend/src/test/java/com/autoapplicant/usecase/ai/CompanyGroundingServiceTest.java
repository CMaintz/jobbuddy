package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.autoapplicant.domain.document.CompanyContext;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.web.WebPageFetchPort;
import com.autoapplicant.usecase.company.InMemoryCompanyResearchRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyGroundingServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final InMemoryCompanyResearchRepository research = new InMemoryCompanyResearchRepository();
    private final CompanyGroundingService grounding = new CompanyGroundingService(
            mock(CompanyRepositoryPort.class), research,
            mock(WebPageFetchPort.class), mock(ChatProviderPort.class));

    @Test
    void groundsACoverLetterOnlyInTheRequestingUsersOwnNotes() {
        research.saveResearch(ALICE, COMPANY, "Alice's research");

        CompanyContext forAlice = grounding.contextFor(ALICE, COMPANY);
        CompanyContext forBob = grounding.contextFor(BOB, COMPANY);

        assertThat(forAlice.researchNotes()).isEqualTo("Alice's research");
        assertThat(forBob.researchNotes()).isNull();
    }

    @Test
    void noCompanyMeansNoContext() {
        assertThat(grounding.contextFor(ALICE, null)).isEqualTo(CompanyContext.EMPTY);
    }
}
