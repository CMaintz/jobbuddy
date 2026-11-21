package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.user.ProfileEmbedding;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Vectors from two models cannot be compared. Once an install has vectors of both (it switched
 * provider), every read must see only the stored model's, and a switch must queue, not delete.
 */
@Import({JobPersistenceAdapter.class, JobEmbeddingPersistenceAdapter.class,
        ProfileEmbeddingPersistenceAdapter.class, EmbeddingChoicePersistenceAdapter.class,
        EmbeddingModelScopeTest.Active.class})
class EmbeddingModelScopeTest extends PostgresIntegrationTest {

    static final String OPENAI = "text-embedding-3-small";
    static final String GEMINI = "gemini-embedding-001";
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @TestConfiguration
    static class Active {
        @Bean
        ActiveEmbeddingModel activeModel() {
            return new ActiveEmbeddingModel();
        }
    }

    @Autowired JobPersistenceAdapter jobsAdapter;
    @Autowired JobEmbeddingPersistenceAdapter jobEmbeddings;
    @Autowired ProfileEmbeddingPersistenceAdapter profileEmbeddings;
    @Autowired EmbeddingChoicePersistenceAdapter choices;
    @Autowired ActiveEmbeddingModel active;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
        active.use(GEMINI);
    }

    @Test
    void a_job_keeps_one_vector_per_model() {
        UUID job = jobs.insert("A", "a", NOW);
        jobEmbeddings.save(new JobEmbedding(null, job, vector(1f, 0f), OPENAI, null));
        jobEmbeddings.save(new JobEmbedding(null, job, vector(0f, 1f), GEMINI, null));
        entityManager.flush(); // a JDBC read does not trigger Hibernate's auto-flush

        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_embeddings WHERE job_id = ?",
                Integer.class, job)).isEqualTo(2);
        assertThat(jobEmbeddings.findByJobId(job)).get()
                .extracting(JobEmbedding::model).isEqualTo(GEMINI);
    }

    @Test
    void a_job_with_only_an_old_model_vector_has_none_for_search() {
        UUID job = jobs.insert("A", "a", NOW);
        jobEmbeddings.save(new JobEmbedding(null, job, vector(1f, 0f), OPENAI, null));

        assertThat(jobEmbeddings.findByJobId(job)).isEmpty();
    }

    @Test
    void neighbours_come_only_from_the_stored_model() {
        // The OpenAI vector is the closer one by raw distance; comparing it with a Gemini query
        // vector is meaningless, so it must not be in the answer at all.
        UUID openAiOnly = jobs.insert("OpenAI only", "a", NOW);
        jobEmbeddings.save(new JobEmbedding(null, openAiOnly, vector(1f, 0f), OPENAI, null));
        UUID gemini = jobs.insert("Gemini", "b", NOW);
        jobEmbeddings.save(new JobEmbedding(null, gemini, vector(0f, 1f), GEMINI, null));

        List<UUID> nearest = jobEmbeddings.findNearestNeighborJobIds(vector(1f, 0f), 10);

        assertThat(nearest).containsExactly(gemini);
    }

    @Test
    void a_profile_vector_of_the_old_model_reads_as_missing_so_it_is_recomputed() {
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)", user, user + "@example.com");
        profileEmbeddings.save(new ProfileEmbedding(null, user, vector(1f, 0f), OPENAI, null));

        assertThat(profileEmbeddings.findByUserId(user)).isEmpty();

        active.use(OPENAI);
        assertThat(profileEmbeddings.findByUserId(user)).isPresent();
    }

    @Test
    void a_switch_queues_postings_without_a_vector_for_the_new_model_and_deletes_nothing() {
        UUID oldOnly = embedded(OPENAI);
        UUID alreadyNew = embedded(GEMINI);
        UUID gaveUp = jobs.insert("C", "c", NOW);
        jdbc.update("UPDATE jobs SET embedding_status = 'FAILED', embedding_attempts = 4 WHERE id = ?", gaveUp);

        entityManager.flush();
        int queued = jobsAdapter.requeueEmbeddingsForModel(GEMINI);

        assertThat(queued).isEqualTo(2);
        assertThat(status(oldOnly)).isEqualTo("PENDING:0");
        assertThat(status(gaveUp)).isEqualTo("PENDING:0");
        assertThat(status(alreadyNew)).isEqualTo("EMBEDDED:1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_embeddings", Integer.class)).isEqualTo(2);
    }

    @Test
    void the_stored_choice_round_trips_and_there_is_only_ever_one() {
        assertThat(choices.find()).isEmpty();

        choices.save(new EmbeddingChoice("openai", OPENAI));
        choices.save(new EmbeddingChoice("gemini", GEMINI));

        assertThat(choices.find()).contains(new EmbeddingChoice("gemini", GEMINI));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_choice", Integer.class)).isEqualTo(1);
    }

    private UUID embedded(String model) {
        UUID job = jobs.insert("J", "j", NOW);
        jobEmbeddings.save(new JobEmbedding(null, job, vector(1f, 1f), model, null));
        jdbc.update("UPDATE jobs SET embedding_status = 'EMBEDDED', embedding_attempts = 1 WHERE id = ?", job);
        return job;
    }

    private String status(UUID job) {
        return jdbc.queryForObject(
                "SELECT embedding_status || ':' || embedding_attempts FROM jobs WHERE id = ?", String.class, job);
    }

    private static float[] vector(float x, float y) {
        float[] v = new float[1536];
        v[0] = x;
        v[1] = y;
        return v;
    }

    /** The stored model, switchable mid-test as a provider switch would. */
    static final class ActiveEmbeddingModel implements com.autoapplicant.port.in.ai.ResolveEmbeddingChoiceUseCase {
        private EmbeddingChoice choice;

        void use(String model) {
            choice = new EmbeddingChoice(GEMINI.equals(model) ? "gemini" : "openai", model);
        }

        @Override
        public EmbeddingChoice currentChoice() {
            return choice;
        }
    }
}
