package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.Education;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EducationRepositoryPort {
    Education save(Education education);
    List<Education> findByUserId(UUID userId);
    Optional<Education> findById(UUID id);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
