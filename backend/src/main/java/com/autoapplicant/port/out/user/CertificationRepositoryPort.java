package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.Certification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificationRepositoryPort {
    Certification save(Certification certification);
    List<Certification> findByUserId(UUID userId);
    Optional<Certification> findById(UUID id);
    void deleteByIdAndUserId(UUID id, UUID userId);
}
