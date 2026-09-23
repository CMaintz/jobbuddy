package com.autoapplicant.port.in.company;

import com.autoapplicant.domain.company.CompanyResearch;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes the candidate's free-text research for a company (cover-letter grounding). */
public interface ManageCompanyResearchUseCase {

    Optional<CompanyResearch> getResearch(UUID companyId);

    /**
     * Saves the notes (blank clears them) and returns the resulting state, or empty when no such
     * company exists — so a write to a missing company is reported as a miss, not a false success.
     */
    Optional<CompanyResearch> saveResearch(UUID companyId, String notes);
}
