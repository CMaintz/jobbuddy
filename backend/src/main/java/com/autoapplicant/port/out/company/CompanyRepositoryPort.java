package com.autoapplicant.port.out.company;

import com.autoapplicant.domain.company.Company;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepositoryPort {
    Company save(Company company);
    Optional<Company> findById(UUID id);
    Optional<Company> findByName(String name);
    Company findOrCreate(String name);
}
