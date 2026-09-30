package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.CustomSectionsEntity;
import com.autoapplicant.adapter.persistence.repository.CustomSectionsJpaRepository;
import com.autoapplicant.domain.user.CustomSection;
import com.autoapplicant.port.out.user.CustomSectionRepositoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CustomSectionsPersistenceAdapter implements CustomSectionRepositoryPort {

    private static final TypeReference<List<CustomSection>> DOMAIN_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> JSON_LIST = new TypeReference<>() {};

    private final CustomSectionsJpaRepository repo;
    private final ObjectMapper objectMapper;

    public CustomSectionsPersistenceAdapter(CustomSectionsJpaRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CustomSection> findByUserId(UUID userId) {
        return repo.findById(userId).map(this::toDomain).orElseGet(List::of);
    }

    @Override
    public List<CustomSection> save(UUID userId, List<CustomSection> sections) {
        CustomSectionsEntity entity = repo.findById(userId).orElseGet(CustomSectionsEntity::new);
        entity.setUserId(userId);
        entity.setSections(objectMapper.convertValue(
                sections != null ? sections : List.of(), JSON_LIST));
        return toDomain(repo.save(entity));
    }

    private List<CustomSection> toDomain(CustomSectionsEntity entity) {
        return objectMapper.convertValue(
                entity.getSections() != null ? entity.getSections() : List.of(), DOMAIN_LIST);
    }
}
