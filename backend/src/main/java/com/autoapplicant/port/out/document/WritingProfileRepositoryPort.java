package com.autoapplicant.port.out.document;

import com.autoapplicant.domain.document.WritingProfile;

import java.util.Optional;
import java.util.UUID;

public interface WritingProfileRepositoryPort {
    WritingProfile save(WritingProfile profile);
    Optional<WritingProfile> findByUserId(UUID userId);
}
