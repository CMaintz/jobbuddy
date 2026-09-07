package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CompanyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {
    Optional<CompanyEntity> findByNameIgnoreCase(String name);
    Page<CompanyEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<CompanyEntity> findBySlug(String slug);
}
