package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared helper that resolves skill taxonomy entries for a batch of skill junction records.
 * Eliminates the identical loadWithSkills() pattern in WorkExperience, Project, and Education adapters.
 */
@Component
public class SectionSkillLoader {

    private final SkillTaxonomyRepositoryPort taxonomyRepo;

    public SectionSkillLoader(SkillTaxonomyRepositoryPort taxonomyRepo) {
        this.taxonomyRepo = taxonomyRepo;
    }

    /**
     * Given a list of skill junction entities (links), resolves the full SkillTaxonomy for each
     * and returns a map keyed by the owning entity's ID.
     *
     * @param links             junction records (e.g. WorkExperienceSkillEntity)
     * @param entityIdExtractor function to extract the owning entity ID from a link
     * @param taxonomyIdExtractor function to extract the taxonomy ID from a link
     * @param <L>               the junction entity type
     * @return map from entity UUID to list of resolved SkillTaxonomy entries
     */
    public <L> Map<UUID, List<SkillTaxonomy>> resolve(
            List<L> links,
            Function<L, UUID> entityIdExtractor,
            Function<L, UUID> taxonomyIdExtractor) {
        if (links.isEmpty()) return Map.of();
        Set<UUID> taxIds = links.stream().map(taxonomyIdExtractor).collect(Collectors.toSet());
        Map<UUID, SkillTaxonomy> taxById = taxonomyRepo.findByIds(taxIds).stream()
                .collect(Collectors.toMap(SkillTaxonomy::id, s -> s));
        return links.stream()
                .collect(Collectors.groupingBy(
                        entityIdExtractor,
                        Collectors.mapping(
                                l -> taxById.get(taxonomyIdExtractor.apply(l)),
                                Collectors.filtering(Objects::nonNull, Collectors.toList()))));
    }
}
