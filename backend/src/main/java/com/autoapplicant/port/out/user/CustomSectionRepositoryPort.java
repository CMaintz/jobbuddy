package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.CustomSection;
import java.util.List;
import java.util.UUID;

public interface CustomSectionRepositoryPort {

    List<CustomSection> findByUserId(UUID userId);

    /** Upsert the user's custom sections (one row per user); returns the persisted list. */
    List<CustomSection> save(UUID userId, List<CustomSection> sections);
}
