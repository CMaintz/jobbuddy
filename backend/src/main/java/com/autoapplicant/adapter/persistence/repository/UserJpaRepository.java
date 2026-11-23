package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(String email);
    Optional<UserEntity> findByLinkedinId(String linkedinId);
    Optional<UserEntity> findByFirebaseUid(String firebaseUid);
    boolean existsByEmail(String email);

    // The language column is deliberately not mapped on UserEntity: a mapped column would be
    // rewritten from whatever the entity holds on every account save, resetting the user's pick.
    @Query(value = "SELECT language FROM users WHERE id = :id", nativeQuery = true)
    Optional<String> findLanguageById(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query(value = "UPDATE users SET language = :language WHERE id = :id", nativeQuery = true)
    int updateLanguage(@Param("id") UUID id, @Param("language") String language);
}
