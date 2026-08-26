package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyHiringSignal;
import com.autoapplicant.domain.company.OutreachReason;
import com.autoapplicant.domain.company.OutreachTarget;
import com.autoapplicant.domain.user.CareerTarget;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.company.FindOutreachTargetsUseCase;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Ranks companies worth an unsolicited application.
 *
 * <p>The app could already write an "uopfordret ansøgning"; what it could not do was tell you who
 * to send one to. Around half of Danish vacancies are never advertised (62% in private companies,
 * ~70% in SMEs), and roughly a quarter of those hires come through unsolicited contact — so
 * discovery here is not a niche feature, it addresses the larger half of the market.
 *
 * <p>The ranking is deterministic and explainable: every target carries the reasons it was picked,
 * because a recommendation the user cannot argue with is one they cannot trust. Reasons are
 * emitted as translatable codes, not English prose. No AI call.
 *
 * <p>The central inversion: a company with a matching role open right now is a WORSE unsolicited
 * target, not a better one — the right move there is to apply to the posting. What you want is a
 * company that has hired people like you repeatedly and has nothing open today.
 */
@Service
public class OutreachTargetService implements FindOutreachTargetsUseCase {

    /** How far back the hiring history is read. A year captures seasonal hiring without noise. */
    private static final Duration WINDOW = Duration.ofDays(365);

    private static final Duration RECENT = Duration.ofDays(90);
    private static final Duration SEMI_RECENT = Duration.ofDays(180);

    /** Below this overlap the company is not in the candidate's field at all. */
    private static final int MIN_TECHNOLOGY_OVERLAP = 1;

    private final JobRepositoryPort jobRepo;
    private final CompanyRepositoryPort companyRepo;
    private final OutreachContactRepositoryPort outreachRepo;
    private final ProfileRepositoryPort profileRepo;
    private final CareerTargetRepositoryPort careerTargetRepo;

    public OutreachTargetService(JobRepositoryPort jobRepo, CompanyRepositoryPort companyRepo,
                                 OutreachContactRepositoryPort outreachRepo,
                                 ProfileRepositoryPort profileRepo,
                                 CareerTargetRepositoryPort careerTargetRepo) {
        this.jobRepo = jobRepo;
        this.companyRepo = companyRepo;
        this.outreachRepo = outreachRepo;
        this.profileRepo = profileRepo;
        this.careerTargetRepo = careerTargetRepo;
    }

    @Override
    public List<OutreachTarget> findOutreachTargets(UUID userId, int limit, boolean includeCompaniesHiringNow) {
        Set<String> candidateTerms = candidateTerms(userId);
        if (candidateTerms.isEmpty()) return List.of();

        // Already-tracked companies live on the outreach list now; re-suggesting them would ask
        // the user to decide something they have already decided.
        Set<UUID> alreadyTracked = outreachRepo.findByUserId(userId).stream()
                .map(com.autoapplicant.domain.company.OutreachContact::companyId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        Instant now = Instant.now();
        List<OutreachTarget> targets = new ArrayList<>();
        for (CompanyHiringSignal signal : jobRepo.findCompanyHiringSignals(now.minus(WINDOW))) {
            if (signal.companyId() != null && alreadyTracked.contains(signal.companyId())) continue;
            List<String> matched = overlap(signal.technologies(), candidateTerms);
            if (matched.size() < MIN_TECHNOLOGY_OVERLAP) continue;

            Company company = signal.companyId() != null
                    ? companyRepo.findById(signal.companyId()).orElse(null) : null;
            // Agencies advertise on behalf of employers; an unsolicited letter to one goes nowhere.
            if (company != null && company.isRecruitingAgency()) continue;

            boolean hasOpenRole = signal.activeCount() > 0;
            if (hasOpenRole && !includeCompaniesHiringNow) continue;

            targets.add(score(signal, company, matched, hasOpenRole, now));
        }

        return targets.stream()
                .sorted(Comparator.comparingInt(OutreachTarget::score).reversed()
                        .thenComparing(OutreachTarget::companyName, Comparator.nullsLast(String::compareTo)))
                .limit(Math.max(1, limit))
                .toList();
    }

    private OutreachTarget score(CompanyHiringSignal signal, Company company, List<String> matched,
                                 boolean hasOpenRole, Instant now) {
        List<OutreachReason> reasons = new ArrayList<>();
        int score = Math.min(60, matched.size() * 15);
        String named = String.join(", ", matched.stream().limit(4).toList());
        reasons.add(matched.size() > 4
                ? new OutreachReason("skillOverlapMore",
                        java.util.Map.of("skills", named, "count", String.valueOf(matched.size() - 4)))
                : OutreachReason.of("skillOverlap", "skills", named));

        Instant lastPosted = signal.lastPostedAt();
        if (lastPosted != null && lastPosted.isAfter(now.minus(RECENT))) {
            score += 15;
            reasons.add(OutreachReason.of("hiringRecent"));
        } else if (lastPosted != null && lastPosted.isAfter(now.minus(SEMI_RECENT))) {
            score += 8;
            reasons.add(OutreachReason.of("hiringSemiRecent"));
        }

        if (signal.postingCount() >= 3) {
            score += 15;
            reasons.add(OutreachReason.of("repeatHirer", "count", signal.postingCount()));
        } else if (signal.postingCount() == 2) {
            score += 8;
            reasons.add(OutreachReason.of("postedTwice"));
        }

        if (!hasOpenRole) {
            score += 10;
            reasons.add(OutreachReason.of("nothingOpen"));
        } else {
            reasons.add(OutreachReason.of("hasOpenRoles", "count", signal.activeCount()));
        }

        // Consultancies hire constantly and place people on client projects; a real signal, but a
        // weaker one than an employer hiring for itself.
        if (company != null && company.isConsulting()) {
            score -= 20;
            reasons.add(OutreachReason.of("consultancy"));
        }

        return new OutreachTarget(
                signal.companyId(),
                company != null && company.name() != null ? company.name() : signal.companyName(),
                company != null ? company.website() : null,
                Math.max(0, Math.min(100, score)),
                List.copyOf(reasons),
                lastPosted,
                matched,
                hasOpenRole);
    }

    /** The candidate's technologies and skills, lowercased — what "in my field" means for them. */
    private Set<String> candidateTerms(UUID userId) {
        Set<String> terms = new LinkedHashSet<>();
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile != null) {
            addAll(terms, profile.technologies());
            addAll(terms, profile.skills());
        }
        CareerTarget target = careerTargetRepo.findByUserId(userId).orElse(null);
        if (target != null) addAll(terms, target.targetArchetypes());
        return terms;
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            if (value != null && !value.isBlank()) target.add(value.strip().toLowerCase(Locale.ROOT));
        }
    }

    /** Company technologies the candidate genuinely shares, in the company's own spelling. */
    private static List<String> overlap(List<String> companyTechnologies, Set<String> candidateTerms) {
        if (companyTechnologies == null) return List.of();
        List<String> matched = new ArrayList<>();
        for (String tech : companyTechnologies) {
            if (tech != null && candidateTerms.contains(tech.strip().toLowerCase(Locale.ROOT))) {
                matched.add(tech.strip());
            }
        }
        return matched;
    }
}
