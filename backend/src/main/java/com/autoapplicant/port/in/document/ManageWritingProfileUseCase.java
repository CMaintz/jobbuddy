package com.autoapplicant.port.in.document;

import com.autoapplicant.domain.document.WritingProfile;

import java.util.Optional;
import java.util.UUID;

public interface ManageWritingProfileUseCase {
    Optional<WritingProfile> get(UUID userId);
    WritingProfile save(WritingProfile profile);
}
