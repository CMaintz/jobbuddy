package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.mapper.DocumentMapper;
import com.autoapplicant.adapter.persistence.repository.CvVersionJpaRepository;
import com.autoapplicant.adapter.persistence.repository.GeneratedDocumentJpaRepository;
import com.autoapplicant.adapter.persistence.repository.WritingProfileJpaRepository;
import com.autoapplicant.domain.document.CvVersion;
import com.autoapplicant.domain.document.GeneratedDocument;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.out.document.CvVersionRepositoryPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DocumentPersistenceAdapter
        implements CvVersionRepositoryPort, GeneratedDocumentRepositoryPort, WritingProfileRepositoryPort {

    private final CvVersionJpaRepository cvRepo;
    private final GeneratedDocumentJpaRepository genDocRepo;
    private final WritingProfileJpaRepository writingProfileRepo;

    public DocumentPersistenceAdapter(CvVersionJpaRepository cvRepo,
                                      GeneratedDocumentJpaRepository genDocRepo,
                                      WritingProfileJpaRepository writingProfileRepo) {
        this.cvRepo = cvRepo;
        this.genDocRepo = genDocRepo;
        this.writingProfileRepo = writingProfileRepo;
    }

    @Override
    public CvVersion save(CvVersion cvVersion) {
        return DocumentMapper.toDomain(cvRepo.save(DocumentMapper.toEntity(cvVersion)));
    }

    @Override
    public Optional<CvVersion> findById(UUID id) {
        return cvRepo.findById(id).map(DocumentMapper::toDomain);
    }

    @Override
    public List<CvVersion> findByUserId(UUID userId) {
        return cvRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public GeneratedDocument save(GeneratedDocument document) {
        return DocumentMapper.toDomain(genDocRepo.save(DocumentMapper.toEntity(document)));
    }

    @Override
    public List<GeneratedDocument> findByApplicationId(UUID applicationId) {
        return genDocRepo.findByApplicationId(applicationId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<GeneratedDocument> findByUserId(UUID userId) {
        return genDocRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public WritingProfile save(WritingProfile profile) {
        // Intentionally not implemented here — handled by WritingProfilePersistenceAdapter
        throw new UnsupportedOperationException("Use WritingProfilePersistenceAdapter");
    }

    @Override
    public Optional<WritingProfile> findByUserId(UUID userId) {
        return writingProfileRepo.findByUserId(userId).map(DocumentMapper::toDomain);
    }
}
