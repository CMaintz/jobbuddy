package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.CustomSection;
import com.autoapplicant.domain.user.CustomSectionItem;
import com.autoapplicant.port.in.user.ManageCustomSectionsUseCase;
import com.autoapplicant.port.out.user.CustomSectionRepositoryPort;
import com.autoapplicant.usecase.common.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CustomSectionsService implements ManageCustomSectionsUseCase {

    private final CustomSectionRepositoryPort repo;

    public CustomSectionsService(CustomSectionRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<CustomSection> getCustomSections(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Override
    public List<CustomSection> saveCustomSections(UUID userId, List<CustomSection> sections) {
        return repo.save(userId, normalize(sections));
    }

    /** Assign stable ids to new sections and drop empty headings before persisting. */
    private static List<CustomSection> normalize(List<CustomSection> sections) {
        List<CustomSection> out = new ArrayList<>();
        for (CustomSection section : Values.listOrEmpty(sections)) {
            if (section != null && section.heading() != null && !section.heading().isBlank()) {
                out.add(new CustomSection(idOrNew(section.id()), section.heading().strip(),
                        normalizeItems(section.items())));
            }
        }
        return out;
    }

    /** Assign stable ids to new items and drop empty lines. */
    private static List<CustomSectionItem> normalizeItems(List<CustomSectionItem> items) {
        List<CustomSectionItem> out = new ArrayList<>();
        for (CustomSectionItem item : Values.listOrEmpty(items)) {
            if (item != null && item.text() != null && !item.text().isBlank()) {
                out.add(new CustomSectionItem(idOrNew(item.id()), item.text().strip()));
            }
        }
        return out;
    }

    private static String idOrNew(String id) {
        return id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
    }
}
