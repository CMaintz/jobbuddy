package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.CompanyEntity;
import com.autoapplicant.adapter.persistence.repository.CompanyJpaRepository;
import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.company.CompanySize;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CompanyPersistenceAdapter implements CompanyRepositoryPort {

    private final CompanyJpaRepository repo;

    public CompanyPersistenceAdapter(CompanyJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Company save(Company company) {
        return toDomain(repo.save(toEntity(company)));
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Company> findByName(String name) {
        return repo.findByNameIgnoreCase(name).map(this::toDomain);
    }

    @Override
    public Company findOrCreate(String name) {
        return repo.findByNameIgnoreCase(name)
                .map(this::toDomain)
                .orElseGet(() -> {
                    CompanyEntity e = new CompanyEntity();
                    e.setName(name);
                    return toDomain(repo.save(e));
                });
    }

    @Override
    public List<Company> search(String query, int page, int size) {
        return repo.findByNameContainingIgnoreCase(query, PageRequest.of(page, size))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Company> findBySlug(String slug) {
        return repo.findBySlug(slug).map(this::toDomain);
    }

    private Company toDomain(CompanyEntity e) {
        CompanySize size = e.getSizeRange() != null
                ? CompanySize.valueOf(e.getSizeRange()) : null;
        return new Company(e.getId(), e.getName(), e.getSlug(), e.getWebsite(),
                e.getLinkedinUrl(), e.getDescription(), e.getLogoUrl(), size,
                e.getIndustry(), e.getCountry(), e.isConsulting(), e.isRecruitingAgency(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private CompanyEntity toEntity(Company d) {
        CompanyEntity e = new CompanyEntity();
        e.setId(d.id());
        e.setName(d.name());
        e.setSlug(d.slug());
        e.setWebsite(d.website());
        e.setLinkedinUrl(d.linkedinUrl());
        e.setDescription(d.description());
        e.setLogoUrl(d.logoUrl());
        e.setSizeRange(d.sizeRange() != null ? d.sizeRange().name() : null);
        e.setIndustry(d.industry());
        e.setCountry(d.country());
        e.setConsulting(d.isConsulting());
        e.setRecruitingAgency(d.isRecruitingAgency());
        return e;
    }
}
