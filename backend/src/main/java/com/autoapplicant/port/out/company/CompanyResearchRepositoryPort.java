package com.autoapplicant.port.out.company;

import com.autoapplicant.domain.company.CompanyResearch;
import java.util.Optional;
import java.util.UUID;

/** A user's own research notes on a company. Each user sees and edits only their own. */
public interface CompanyResearchRepositoryPort {

    Optional<CompanyResearch> findResearch(UUID userId, UUID companyId);

    /** Stores the notes; blank notes delete them. */
    void saveResearch(UUID userId, UUID companyId, String notes);
}
