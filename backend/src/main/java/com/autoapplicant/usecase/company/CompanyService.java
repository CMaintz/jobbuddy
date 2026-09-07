package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.port.in.company.GetCompaniesUseCase;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CompanyService implements GetCompaniesUseCase {

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
}
