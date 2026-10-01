package com.autoapplicant.usecase.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyServiceTest {

    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final CompanyRepositoryPort repo = mock(CompanyRepositoryPort.class);
    private final InMemoryCompanyResearchRepository research = new InMemoryCompanyResearchRepository();
    private final CompanyService service = new CompanyService(repo, research);

    @BeforeEach
    void companyExists() {
        when(repo.findById(COMPANY)).thenReturn(Optional.of(mock(Company.class)));
    }

    @Test
    void savingResearchForAnUnknownCompanyIsAMissAndWritesNothing() {
        UUID unknown = UUID.randomUUID();
        when(repo.findById(unknown)).thenReturn(Optional.empty());

        Optional<CompanyResearch> result = service.saveResearch(ALICE, unknown, "some notes");

        // A write to a company that does not exist must not report success (it maps to a 404).
        assertThat(result).isEmpty();
        assertThat(research.findResearch(ALICE, unknown)).isEmpty();
    }

    @Test
    void savingResearchReturnsThePersistedState() {
        Optional<CompanyResearch> result = service.saveResearch(ALICE, COMPANY, "notes");

        assertThat(result).hasValueSatisfying(r -> {
            assertThat(r.notes()).isEqualTo("notes");
            assertThat(r.updatedAt()).isNotNull();
        });
    }

    @Test
    void twoUsersKeepSeparateNotesOnTheSameCompany() {
        service.saveResearch(ALICE, COMPANY, "Alice's take");
        service.saveResearch(BOB, COMPANY, "Bob's take");

        assertThat(service.getResearch(ALICE, COMPANY)).map(CompanyResearch::notes).contains("Alice's take");
        assertThat(service.getResearch(BOB, COMPANY)).map(CompanyResearch::notes).contains("Bob's take");
    }

    @Test
    void oneUserClearingTheirNotesLeavesTheOtherUsersAlone() {
        service.saveResearch(ALICE, COMPANY, "Alice's take");
        service.saveResearch(BOB, COMPANY, "Bob's take");

        Optional<CompanyResearch> cleared = service.saveResearch(BOB, COMPANY, "  ");

        assertThat(cleared).contains(new CompanyResearch(null, null));
        assertThat(service.getResearch(BOB, COMPANY)).isEmpty();
        assertThat(service.getResearch(ALICE, COMPANY)).map(CompanyResearch::notes).contains("Alice's take");
    }

    @Test
    void aUserWithoutNotesSeesNothingEvenWhenSomeoneElseHasSome() {
        service.saveResearch(ALICE, COMPANY, "Alice's take");

        assertThat(service.getResearch(BOB, COMPANY)).isEmpty();
    }
}
