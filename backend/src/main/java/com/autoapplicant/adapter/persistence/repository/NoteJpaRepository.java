package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteJpaRepository extends JpaRepository<NoteEntity, UUID> {
    List<NoteEntity> findByUserIdAndJobId(UUID userId, UUID jobId);
    List<NoteEntity> findByUserId(UUID userId);
}
