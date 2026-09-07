package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.Certification;

import java.util.List;
import java.util.UUID;

public interface ManageCertificationsUseCase {
    Certification addCertification(UUID userId, Certification certification);
    List<Certification> getCertifications(UUID userId);
    void deleteCertification(UUID userId, UUID id);
}
