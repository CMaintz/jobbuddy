package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.JobEmbedding;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(JobEmbeddingPersistenceAdapter.class)
class JobEmbeddingPersistenceAdapterTest extends PostgresIntegrationTest {

    private static final int DIMENSIONS = 1536;
    private static final String MODEL = "text-embedding-3-small";
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired JobEmbeddingPersistenceAdapter embeddings;
    @Autowired JdbcTemplate jdbc;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    @Test
    void nearest_neighbours_are_ordered_by_cosine_distance() {
        UUID exact = embed(jobs.insert("A", "a", NOW), vector(1f, 0f));
        UUID close = embed(jobs.insert("B", "b", NOW), vector(0.9f, 0.1f));
        UUID far = embed(jobs.insert("C", "c", NOW), vector(0f, 1f));

        List<UUID> nearest = embeddings.findNearestNeighborJobIds(vector(1f, 0f), 10);

        assertThat(nearest).containsExactly(exact, close, far);
    }

    @Test
    void cosine_distance_ignores_magnitude() {
        UUID sameDirection = embed(jobs.insert("A", "a", NOW), vector(5f, 5f));
        UUID otherDirection = embed(jobs.insert("B", "b", NOW), vector(1f, 0f));

        List<UUID> nearest = embeddings.findNearestNeighborJobIds(vector(0.1f, 0.1f), 10);

        assertThat(nearest).containsExactly(sameDirection, otherDirection);
    }

    @Test
    void the_limit_is_respected_and_inactive_postings_are_skipped() {
        embed(jobs.insert("Inactive", "x", NOW, false, null), vector(1f, 0f));
        UUID best = embed(jobs.insert("A", "a", NOW), vector(0.9f, 0.1f));
        embed(jobs.insert("B", "b", NOW), vector(0f, 1f));

        List<UUID> nearest = embeddings.findNearestNeighborJobIds(vector(1f, 0f), 1);

        assertThat(nearest).containsExactly(best);
    }

    @Test
    void saving_again_for_the_same_job_replaces_the_vector_round_tripping_it_exactly() {
        UUID jobId = jobs.insert("A", "a", NOW);
        embeddings.save(new JobEmbedding(null, jobId, vector(1f, 0f), MODEL, null));

        float[] replacement = vector(0.25f, -0.5f);
        embeddings.save(new JobEmbedding(null, jobId, replacement, MODEL, null));

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM job_embeddings WHERE job_id = ?", Integer.class, jobId);
        assertThat(rows).isEqualTo(1);
        assertThat(embeddings.findByJobId(jobId)).get()
                .extracting(JobEmbedding::embedding)
                .isEqualTo(replacement);
    }

    private UUID embed(UUID jobId, float[] vector) {
        embeddings.save(new JobEmbedding(null, jobId, vector, MODEL, null));
        return jobId;
    }

    /** A full-width vector whose first two components carry the direction under test. */
    private static float[] vector(float x, float y) {
        float[] v = new float[DIMENSIONS];
        v[0] = x;
        v[1] = y;
        return v;
    }
}
