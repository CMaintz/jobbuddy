package com.autoapplicant.usecase.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyServiceTest {

    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final CompanyRepositoryPort repo = mock(CompanyRepositoryPort.class);
    private final CompanyService service = new CompanyService(repo);

    @Test
    void savingResearchForAnUnknownCompanyIsAMissAndWritesNothing() {
        when(repo.findById(COMPANY)).thenReturn(Optional.empty());

        Optional<CompanyResearch> result = service.saveResearch(COMPANY, "some notes");

        // A write to a company that does not exist must not report success (it maps to a 404).
        assertThat(result).isEmpty();
        verify(repo, never()).saveResearch(any(), any());
    }

    @Test
    void savingResearchForAKnownCompanyPersistsThenReturnsThePersistedState() {
        Instant savedAt = Instant.parse("2026-01-01T00:00:00Z");
        when(repo.findById(COMPANY)).thenReturn(Optional.of(mock(Company.class)));
        when(repo.findResearch(COMPANY)).thenReturn(Optional.of(new CompanyResearch("notes", savedAt)));

        Optional<CompanyResearch> result = service.saveResearch(COMPANY, "notes");

        verify(repo).saveResearch(eq(COMPANY), eq("notes"));
        assertThat(result).contains(new CompanyResearch("notes", savedAt));
    }
}
