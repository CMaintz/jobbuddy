package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.port.in.user.ManageCertificationsUseCase;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CertificationService implements ManageCertificationsUseCase {

    private final CertificationRepositoryPort repo;

    public CertificationService(CertificationRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Certification addCertification(UUID userId, Certification certification) {
        Certification withUser = new Certification(null, userId, certification.name(),
                certification.issuer(), certification.issuedAt(), certification.expiresAt(),
                certification.credentialUrl(), null);
        return repo.save(withUser);
    }

    @Override
    public List<Certification> getCertifications(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public void deleteCertification(UUID userId, UUID id) {
        repo.deleteByIdAndUserId(id, userId);
    }
}
