package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyHiringSignal;
import com.autoapplicant.domain.company.OutreachReason;
import com.autoapplicant.domain.company.OutreachTarget;
import com.autoapplicant.domain.user.Profile;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OutreachTargetServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final com.autoapplicant.port.out.job.JobRepositoryPort jobRepo =
            Mockito.mock(com.autoapplicant.port.out.job.JobRepositoryPort.class);
    private final com.autoapplicant.port.out.company.CompanyRepositoryPort companyRepo =
            Mockito.mock(com.autoapplicant.port.out.company.CompanyRepositoryPort.class);
    private final com.autoapplicant.port.out.company.OutreachContactRepositoryPort outreachRepo =
            Mockito.mock(com.autoapplicant.port.out.company.OutreachContactRepositoryPort.class);
    private final com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort profileSkillRepo =
            Mockito.mock(com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort.class);
    private final com.autoapplicant.port.out.user.CareerTargetRepositoryPort careerTargetRepo =
            Mockito.mock(com.autoapplicant.port.out.user.CareerTargetRepositoryPort.class);

    private final OutreachTargetService service =
            new OutreachTargetService(jobRepo, companyRepo, outreachRepo, profileSkillRepo, careerTargetRepo);

    private void profileWith(String... technologies) {
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of());
        when(profileSkillRepo.findByUserId(USER)).thenReturn(
                java.util.Arrays.stream(technologies)
                        .map(name -> new com.autoapplicant.domain.skill.ProfileSkill(
                                UUID.randomUUID(), USER, name, null, null, null, false, 0, null))
                        .toList());
        when(careerTargetRepo.findByUserId(USER)).thenReturn(Optional.empty());
    }

    private static CompanyHiringSignal signal(String name, int postings, int active,
                                              Instant lastPosted, String... technologies) {
        return new CompanyHiringSignal(UUID.randomUUID(), name, postings, active, lastPosted,
                List.of(technologies));
    }

    private void signals(CompanyHiringSignal... signals) {
        when(jobRepo.findCompanyHiringSignals(any())).thenReturn(List.of(signals));
        when(companyRepo.findById(any())).thenReturn(Optional.empty());
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    @Test
    void companiesOutsideTheCandidatesFieldAreNotTargets() {
        profileWith("Java", "Spring Boot");
        signals(signal("Bakery ApS", 4, 0, daysAgo(10), "Baking", "Customer service"));
        assertThat(service.findOutreachTargets(USER, 10, false)).isEmpty();
    }

    @Test
    void anAlreadyTrackedCompanyIsNotSuggestedAgain() {
        profileWith("Java");
        UUID companyId = UUID.randomUUID();
        when(jobRepo.findCompanyHiringSignals(any())).thenReturn(List.of(
                new CompanyHiringSignal(companyId, "Tracked A/S", 3, 0, daysAgo(5), List.of("Java"))));
        when(companyRepo.findById(any())).thenReturn(Optional.empty());
        // Once it is on the outreach list, re-suggesting it asks the user to decide again.
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of(new com.autoapplicant.domain.company
                .OutreachContact(UUID.randomUUID(), USER, companyId, "Tracked A/S",
                com.autoapplicant.domain.company.OutreachStatus.SAVED, null, null, null, null, null,
                null, null)));

        assertThat(service.findOutreachTargets(USER, 10, false)).isEmpty();
    }

    @Test
    void aProfileWithNoSkillsYieldsNothingRatherThanEverything() {
        when(outreachRepo.findByUserId(USER)).thenReturn(List.of());
        when(profileSkillRepo.findByUserId(USER)).thenReturn(List.of());
        when(careerTargetRepo.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.findOutreachTargets(USER, 10, false)).isEmpty();
    }

    @Test
    void companiesHiringRightNowAreExcludedByDefault() {
        profileWith("Java");
        signals(signal("Has Openings A/S", 3, 2, daysAgo(5), "Java"));
        assertThat(service.findOutreachTargets(USER, 10, false)).isEmpty();

        // …and included, but ranked as the weaker target, when explicitly asked for.
        List<OutreachTarget> included = service.findOutreachTargets(USER, 10, true);
        assertThat(included).hasSize(1);
        assertThat(included.getFirst().hasOpenRole()).isTrue();
        assertThat(included.getFirst().reasons()).extracting(OutreachReason::code).contains("hasOpenRoles");
    }

    @Test
    void aRepeatHirerWithNothingOpenOutranksAnOccasionalOne() {
        profileWith("Java", "Kubernetes");
        signals(
                signal("Repeat Hirer A/S", 4, 0, daysAgo(20), "Java", "Kubernetes"),
                signal("Occasional ApS", 1, 0, daysAgo(300), "Java"));

        List<OutreachTarget> targets = service.findOutreachTargets(USER, 10, false);
        assertThat(targets).extracting(OutreachTarget::companyName)
                .containsExactly("Repeat Hirer A/S", "Occasional ApS");
        assertThat(targets.getFirst().score()).isGreaterThan(targets.getLast().score());
        assertThat(targets.getFirst().matchedTechnologies()).containsExactly("Java", "Kubernetes");
    }

    @Test
    void recruitingAgenciesAreNeverTargets() {
        profileWith("Java");
        UUID agencyId = UUID.randomUUID();
        when(jobRepo.findCompanyHiringSignals(any())).thenReturn(List.of(
                new CompanyHiringSignal(agencyId, "Recruiters ApS", 8, 0, daysAgo(3), List.of("Java"))));
        when(companyRepo.findById(agencyId)).thenReturn(Optional.of(new Company(
                agencyId, "Recruiters ApS", "recruiters", null, null, null, null, null, null,
                "Denmark", false, true, null, null)));

        // An unsolicited letter to an agency reaches nobody who is hiring.
        assertThat(service.findOutreachTargets(USER, 10, false)).isEmpty();
    }

    @Test
    void consultanciesRankLowerThanEmployersHiringForThemselves() {
        profileWith("Java");
        UUID consultancyId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        when(jobRepo.findCompanyHiringSignals(any())).thenReturn(List.of(
                new CompanyHiringSignal(consultancyId, "Consult A/S", 4, 0, daysAgo(10), List.of("Java")),
                new CompanyHiringSignal(employerId, "Product A/S", 4, 0, daysAgo(10), List.of("Java"))));
        when(companyRepo.findById(consultancyId)).thenReturn(Optional.of(new Company(
                consultancyId, "Consult A/S", "consult", null, null, null, null, null, null,
                "Denmark", true, false, null, null)));
        when(companyRepo.findById(employerId)).thenReturn(Optional.of(new Company(
                employerId, "Product A/S", "product", null, null, null, null, null, null,
                "Denmark", false, false, null, null)));

        assertThat(service.findOutreachTargets(USER, 10, false))
                .extracting(OutreachTarget::companyName)
                .containsExactly("Product A/S", "Consult A/S");
    }

    @Test
    void everyTargetExplainsItself() {
        profileWith("Java");
        signals(signal("Explainable A/S", 3, 0, daysAgo(10), "Java"));
        OutreachTarget target = service.findOutreachTargets(USER, 10, false).getFirst();
        // Reasons are translatable codes plus arguments, never prose baked in one language.
        assertThat(target.reasons()).extracting(OutreachReason::code)
                .contains("skillOverlap", "nothingOpen");
        assertThat(target.reasons()).filteredOn(r -> r.code().equals("skillOverlap"))
                .singleElement().extracting(r -> r.args().get("skills")).isEqualTo("Java");
        assertThat(target.score()).isBetween(1, 100);
    }

    @Test
    void theLimitIsRespected() {
        profileWith("Java");
        signals(signal("A", 3, 0, daysAgo(5), "Java"),
                signal("B", 3, 0, daysAgo(5), "Java"),
                signal("C", 3, 0, daysAgo(5), "Java"));
        assertThat(service.findOutreachTargets(USER, 2, false)).hasSize(2);
    }
}
