package com.autoapplicant.adapter.persistence.repository;

import com.autoapplicant.adapter.persistence.entity.JobEmbeddingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobEmbeddingJpaRepository extends JpaRepository<JobEmbeddingEntity, UUID> {
    Optional<JobEmbeddingEntity> findByJobIdAndModel(UUID jobId, String model);

    /** Only the given model's vectors: distances between two models' vectors mean nothing. */
    @Query(value = """
            SELECT je.job_id
            FROM job_embeddings je
            JOIN jobs j ON j.id = je.job_id
            WHERE j.is_active = true
              AND je.model = :model
            ORDER BY je.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findNearestNeighborJobIds(@Param("embedding") String embedding,
                                         @Param("model") String model,
                                         @Param("limit") int limit);
}
