package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.CompanyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {
    Optional<CompanyEntity> findByNameIgnoreCase(String name);
    Page<CompanyEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Companies whose name, lower-cased and without dots and commas, starts with {@code prefix},
     * oldest first. {@code prefix} is a LIKE pattern escaped with a backslash.
     */
    @Query("select c from CompanyEntity c"
            + " where lower(replace(replace(trim(c.name), '.', ''), ',', '')) like :prefix escape '\\'"
            + " order by c.createdAt asc, c.id asc")
    List<CompanyEntity> findByPlainNamePrefix(@Param("prefix") String prefix);
}
