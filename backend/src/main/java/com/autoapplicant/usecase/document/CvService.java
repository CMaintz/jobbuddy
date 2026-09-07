package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.port.in.document.GetCvVersionsUseCase;
import com.autoapplicant.port.in.document.UploadCvUseCase;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CvService implements UploadCvUseCase, GetCvVersionsUseCase {

    private final CvVersionRepositoryPort repo;

    public CvService(CvVersionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public CvVersion uploadCv(UUID userId, String name, String content, String format) {
        CvVersion cv = new CvVersion(null, userId, name, content,
                format != null ? format : "MARKDOWN", null, false, 1, null, null);
        return repo.save(cv);
    }

    @Override
    public List<CvVersion> getCvVersions(UUID userId) {
        return repo.findByUserId(userId);
    }
}
