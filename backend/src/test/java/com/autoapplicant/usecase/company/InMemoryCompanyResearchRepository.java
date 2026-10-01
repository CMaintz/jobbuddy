package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.out.company.CompanyResearchRepositoryPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Research notes keyed by (user, company), the same key the real table uses. */
public class InMemoryCompanyResearchRepository implements CompanyResearchRepositoryPort {

    private final Map<List<UUID>, CompanyResearch> rows = new HashMap<>();

    @Override
    public Optional<CompanyResearch> findResearch(UUID userId, UUID companyId) {
        return Optional.ofNullable(rows.get(List.of(userId, companyId)));
    }

    @Override
    public void saveResearch(UUID userId, UUID companyId, String notes) {
        if (notes == null || notes.isBlank()) {
            rows.remove(List.of(userId, companyId));
        } else {
            rows.put(List.of(userId, companyId), new CompanyResearch(notes, Instant.now()));
        }
    }
}
