package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyFacts;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.web.WebPageFetchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Supplies verified company facts to ground cover-letter references. Cache-first: reuses facts
 * stored on the company until they go stale; on a miss it fetches the company's OWN website
 * (never a URL from the untrusted posting), extracts a few facts with a cheap model, and caches
 * them. Returns null when grounding is disabled, no website is known, or extraction fails — so
 * generation simply proceeds without a company-facts block.
 */
@Service
public class CompanyGroundingService {

    private static final Logger log = LoggerFactory.getLogger(CompanyGroundingService.class);

    private final CompanyRepositoryPort companyRepo;
    private final WebPageFetchPort webFetch;
    private final ChatProviderPort ai;

    @Value("${app.ai.company-grounding.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.company-grounding.staleness-days:60}")
    private int stalenessDays;

    public CompanyGroundingService(CompanyRepositoryPort companyRepo,
                                   WebPageFetchPort webFetch,
                                   @Qualifier("enrichmentAiProvider") ChatProviderPort ai) {
        this.companyRepo = companyRepo;
        this.webFetch = webFetch;
        this.ai = ai;
    }

    /** Verified facts for the company, or null when unavailable. Never throws. */
    public String factsFor(UUID companyId) {
        if (!enabled || companyId == null) return null;
        try {
            var cached = companyRepo.findFacts(companyId);
            if (cached.isPresent() && isFresh(cached.get().researchedAt())) {
                return cached.get().facts();
            }
            Company company = companyRepo.findById(companyId).orElse(null);
            if (company == null || company.website() == null || company.website().isBlank()) {
                return cached.map(CompanyFacts::facts).orElse(null);
            }
            String page = webFetch.fetchText(company.website()).orElse(null);
            if (page == null || page.isBlank()) {
                return cached.map(CompanyFacts::facts).orElse(null);
            }
            String facts = extract(company.name(), page);
            if (facts != null && !facts.isBlank()) {
                companyRepo.saveFacts(companyId, facts);
                log.info("Company grounding: cached {} facts chars for {}", facts.length(), company.name());
                return facts;
            }
            return cached.map(CompanyFacts::facts).orElse(null);
        } catch (Exception e) {
            log.warn("Company grounding failed for {}: {}", companyId, e.getMessage());
            return null;
        }
    }

    private boolean isFresh(Instant researchedAt) {
        return researchedAt != null
                && Duration.between(researchedAt, Instant.now()).compareTo(Duration.ofDays(stalenessDays)) <= 0;
    }

    private String extract(String companyName, String pageText) {
        String system = "You extract verified, specific facts about a company from its own website. "
                + "Output 4-6 short factual bullet lines (what they build/offer, mission, notable products "
                + "or facts). Use ONLY information present in the provided text — never speculate or invent. "
                + "No preamble, no headings, just the bullets.";
        String user = "Company: " + companyName + "\n\nWebsite text:\n" + pageText;
        PromptComposition composition = new PromptComposition(system, user, "", "", "", "", user);
        String out = ai.generate(composition, AiOperations.COMPANY_GROUNDING);
        return out != null ? out.trim() : null;
    }
}
