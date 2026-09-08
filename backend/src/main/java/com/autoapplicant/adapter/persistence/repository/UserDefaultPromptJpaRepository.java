package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.UserDefaultPromptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDefaultPromptJpaRepository
        extends JpaRepository<UserDefaultPromptEntity, UserDefaultPromptEntity.Key> {

    Optional<UserDefaultPromptEntity> findByUserIdAndCategory(UUID userId, String category);

    void deleteByUserIdAndCategory(UUID userId, String category);
}
