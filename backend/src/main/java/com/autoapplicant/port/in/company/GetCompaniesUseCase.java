package com.autoapplicant.port.in.company;

import com.autoapplicant.domain.company.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetCompaniesUseCase {
    List<Company> searchCompanies(String query, int page, int size);
    Optional<Company> getCompanyById(UUID id);
    Optional<Company> getCompanyBySlug(String slug);
}
