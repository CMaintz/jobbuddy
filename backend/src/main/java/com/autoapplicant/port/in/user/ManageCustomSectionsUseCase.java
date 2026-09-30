package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.CustomSection;
import java.util.List;
import java.util.UUID;

public interface ManageCustomSectionsUseCase {

    List<CustomSection> getCustomSections(UUID userId);

    List<CustomSection> saveCustomSections(UUID userId, List<CustomSection> sections);
}
