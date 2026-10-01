package com.autoapplicant.port.in.company;

import com.autoapplicant.domain.company.CompanyResearch;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the candidate's free-text research for a company (cover-letter grounding).
 * Notes belong to the user who wrote them; nobody else can read or change them.
 */
public interface ManageCompanyResearchUseCase {

    Optional<CompanyResearch> getResearch(UUID userId, UUID companyId);

    /**
     * Saves the notes (blank clears them) and returns the resulting state, or empty when no such
     * company exists — so a write to a missing company is reported as a miss, not a false success.
     */
    Optional<CompanyResearch> saveResearch(UUID userId, UUID companyId, String notes);
}
