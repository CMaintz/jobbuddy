package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanyResearch;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import com.autoapplicant.port.in.company.ManageCompanyResearchUseCase;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CompanyService implements GetCompaniesUseCase, ManageCompanyResearchUseCase {

    private final CompanyRepositoryPort repo;

    public CompanyService(CompanyRepositoryPort repo) {
        this.repo = repo;
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
    public Optional<CompanyResearch> getResearch(UUID companyId) {
        return repo.findResearch(companyId);
    }

    @Override
    public Optional<CompanyResearch> saveResearch(UUID companyId, String notes) {
        if (repo.findById(companyId).isEmpty()) {
            return Optional.empty();
        }
        repo.saveResearch(companyId, notes);
        // Read back so the caller gets the persisted timestamp; blank notes clear the field, which
        // findResearch reports as absent — a cleared note has nothing to show.
        return Optional.of(repo.findResearch(companyId).orElse(new CompanyResearch(null, null)));
    }
}
