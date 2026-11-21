package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.usecase.job.JobEmbeddingService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A scheduled crawl embeds postings without enriching them; enrichment happens later, elsewhere,
 * and may trim the very text the vector was made from. The vector has to follow the text.
 */
@Import({JobPersistenceAdapter.class, JobEmbeddingPersistenceAdapter.class})
class EmbeddingWithoutEnrichmentTest extends PostgresIntegrationTest {

    private static final int DIMENSIONS = 1536;
    private static final int MAX_ATTEMPTS = 4;
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Autowired JobPersistenceAdapter jobsAdapter;
    @Autowired JobEmbeddingPersistenceAdapter embeddings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    JobRows jobs;
    final List<String> embeddedTexts = new ArrayList<>();
    /** Runs inside the provider call, to interleave another process's write with an embed. */
    Runnable whileEmbedding = () -> { };

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
    }

    private final EmbeddingProviderPort provider = new EmbeddingProviderPort() {
        @Override
        public float[] embed(String text) {
            embeddedTexts.add(text);
            Runnable hook = whileEmbedding;
            whileEmbedding = () -> { };
            hook.run();
            float[] v = new float[DIMENSIONS];
            v[0] = 1f;
            return v;
        }

        @Override
        public String embeddingModelName() {
            return "test-embedding";
        }
    };

    private JobEmbeddingService embedder(boolean requireEnriched) {
        return new JobEmbeddingService(jobsAdapter, embeddings, provider, MAX_ATTEMPTS,
                Duration.ofHours(6), 100, requireEnriched);
    }

    /** Flushes first: a JDBC read does not trigger Hibernate's auto-flush of the last save. */
    private String embeddingStatus(UUID id) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT embedding_status FROM jobs WHERE id = ?", String.class, id);
    }

    @Test
    void withEnrichmentNotRequiredAFreshlyCrawledPostingIsEmbedded() {
        // Bug: the embedding queue only selects ENRICHED postings, so a crawl that skips
        // enrichment embeds nothing and its postings never reach search or recommendations.
        UUID crawled = jobs.insert("Backend developer", "Build our payments API", NOW);

        embedder(false).embedPending(10, Duration.ofMinutes(1));

        assertThat(embeddingStatus(crawled)).isEqualTo("EMBEDDED");
        assertThat(embeddings.findByJobId(crawled)).isPresent();
        assertThat(embeddedTexts).containsExactly("Backend developer Build our payments API");
    }

    @Test
    void byDefaultAnUnenrichedPostingWaitsForEnrichment() {
        // Bug: the flag is ignored and the server embeds raw text before enrichment has trimmed
        // it, paying for every posting twice.
        UUID crawled = jobs.insert("Backend developer", "Build our payments API", NOW);

        embedder(true).embedPending(10, Duration.ofMinutes(1));

        assertThat(embeddingStatus(crawled)).isEqualTo("PENDING");
        assertThat(embeddedTexts).isEmpty();
    }

    @Test
    void enrichmentThatChangesTheTextRequeuesTheEmbedding() {
        // Bug: a save that carries the embedding state over unconditionally leaves the posting
        // EMBEDDED with a vector of the untrimmed text, so search matches on the boilerplate
        // enrichment removed.
        UUID id = jobs.insert("Backend developer", "Build our payments API\nWe offer free fruit", NOW);
        embedder(false).embedPending(10, Duration.ofMinutes(1));
        Job crawled = jobsAdapter.findById(id).orElseThrow();

        jobsAdapter.save(crawled.toBuilder().descriptionClean("Build our payments API")
                .aiSummary("Payments backend role").build());

        assertThat(embeddingStatus(id)).isEqualTo("PENDING");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT embedding_attempts FROM jobs WHERE id = ?",
                Integer.class, id)).isZero();
        assertThat(jobsAdapter.findForEmbedding(10, MAX_ATTEMPTS, NOW, false))
                .extracting(Job::id).contains(id);
    }

    @Test
    void enrichmentThatLandsWhileTheOldTextIsBeingEmbeddedIsNotOverwritten() {
        // Bug: the crawl embeds the untrimmed text while the desktop app saves the trimmed one
        // and re-queues it; the crawl's unconditional markEmbedded then sets EMBEDDED, so the
        // stale vector stays for good.
        UUID id = jobs.insert("Backend developer", "Build our payments API\nWe offer free fruit", NOW);
        Job crawled = jobsAdapter.findById(id).orElseThrow();
        whileEmbedding = () -> jobsAdapter.save(crawled.toBuilder()
                .descriptionClean("Build our payments API").aiSummary("Payments backend role").build());

        embedder(false).embedPending(10, Duration.ofMinutes(1));

        assertThat(embeddingStatus(id)).isEqualTo("PENDING");
        embedder(false).embedPending(10, Duration.ofMinutes(1));
        assertThat(embeddingStatus(id)).isEqualTo("EMBEDDED");
        assertThat(embeddedTexts).last().isEqualTo("Backend developer Build our payments API");
    }

    @Test
    void enrichmentThatLeavesTheTextAloneKeepsTheEmbedding() {
        // Bug: every save rebuilds the row with embedding_status PENDING, so each enrichment
        // re-embeds postings whose text did not change and doubles the embedding spend.
        UUID id = jobs.insert("Backend developer", "Build our payments API", NOW);
        embedder(false).embedPending(10, Duration.ofMinutes(1));
        Job crawled = jobsAdapter.findById(id).orElseThrow();

        jobsAdapter.save(crawled.toBuilder().aiSummary("Payments backend role").build());

        assertThat(embeddingStatus(id)).isEqualTo("EMBEDDED");
    }
}
