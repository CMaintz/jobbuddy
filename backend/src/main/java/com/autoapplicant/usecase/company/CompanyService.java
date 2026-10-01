package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import com.autoapplicant.port.in.company.ManageCompanyResearchUseCase;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.company.CompanyResearchRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CompanyService implements GetCompaniesUseCase, ManageCompanyResearchUseCase {

    private final CompanyRepositoryPort repo;
    private final CompanyResearchRepositoryPort researchRepo;

    public CompanyService(CompanyRepositoryPort repo, CompanyResearchRepositoryPort researchRepo) {
        this.repo = repo;
        this.researchRepo = researchRepo;
    }

    @Override
    public List<Company> searchCompanies(String query, int page, int size) {
        return repo.search(query != null ? query : "", page, size);
    }

    @Override
    public Optional<Company> getCompanyById(UUID id) {
        return repo.findById(id);
    }

    @Override
    public Optional<CompanyResearch> getResearch(UUID userId, UUID companyId) {
        return researchRepo.findResearch(userId, companyId);
    }

    @Override
    public Optional<CompanyResearch> saveResearch(UUID userId, UUID companyId, String notes) {
        if (repo.findById(companyId).isEmpty()) {
            return Optional.empty();
        }
        researchRepo.saveResearch(userId, companyId, notes);
        // Read back so the caller gets the persisted timestamp; blank notes clear the field, which
        // findResearch reports as absent — a cleared note has nothing to show.
        return Optional.of(researchRepo.findResearch(userId, companyId)
                .orElse(new CompanyResearch(null, null)));
    }
}
