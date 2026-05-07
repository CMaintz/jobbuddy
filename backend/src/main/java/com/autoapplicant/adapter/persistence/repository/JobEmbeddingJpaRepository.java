package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.JobEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobEmbeddingJpaRepository extends JpaRepository<JobEmbeddingEntity, UUID> {
    Optional<JobEmbeddingEntity> findByJobId(UUID jobId);

    @Query(value = """
            SELECT je.job_id
            FROM job_embeddings je
            JOIN jobs j ON j.id = je.job_id
            WHERE j.is_active = true
            ORDER BY je.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findNearestNeighborJobIds(@Param("embedding") String embedding, @Param("limit") int limit);
}
