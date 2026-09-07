package com.autoapplicant.port.out.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyFacts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepositoryPort {
    Company save(Company company);
    Optional<Company> findById(UUID id);
    Company findOrCreate(String name);
    /** Set the company homepage only when it is currently empty (crawl-provided backfill). */
    void backfillWebsite(UUID companyId, String website);
    List<Company> search(String query, int page, int size);

    // ── Cached company grounding facts ──────────────────────
    Optional<CompanyFacts> findFacts(UUID companyId);
    void saveFacts(UUID companyId, String facts);
}
