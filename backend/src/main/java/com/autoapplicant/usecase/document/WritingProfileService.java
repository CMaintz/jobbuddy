package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.port.in.document.ManageWritingProfileUseCase;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class WritingProfileService implements ManageWritingProfileUseCase {

    private final WritingProfileRepositoryPort repo;

    public WritingProfileService(WritingProfileRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public Optional<WritingProfile> get(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public WritingProfile save(WritingProfile profile) {
        return repo.save(profile);
    }
}
