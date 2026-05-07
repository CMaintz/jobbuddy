package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.CvVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvVersionRepositoryPort {
    CvVersion save(CvVersion cvVersion);
    Optional<CvVersion> findById(UUID id);
    List<CvVersion> findByUserId(UUID userId);
}
