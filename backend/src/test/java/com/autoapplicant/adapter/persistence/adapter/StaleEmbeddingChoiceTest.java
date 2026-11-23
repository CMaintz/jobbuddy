package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.cli.QueueDrainer;
import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.port.in.job.EmbedJobsUseCase;
import com.autoapplicant.port.in.job.SweepUnenrichedJobsUseCase;
import com.autoapplicant.port.out.ai.EmbeddingProviderPort;
import com.autoapplicant.usecase.ai.EmbeddingChoiceService;
import com.autoapplicant.usecase.job.JobEmbeddingService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The scheduled crawl settles its embedding choice when it starts. If the desktop app switches
 * model while that crawl still runs, the crawl keeps embedding with the old one. Those vectors
 * must never count as the posting being embedded, or the posting has no vector for the stored
 * model and nothing ever queues it again: it is gone from search and matching for good.
 */
@Import({JobPersistenceAdapter.class, JobEmbeddingPersistenceAdapter.class,
        EmbeddingChoicePersistenceAdapter.class, EmbeddingModelScopeTest.Active.class})
class StaleEmbeddingChoiceTest extends PostgresIntegrationTest {

    private static final String OPENAI = EmbeddingModelScopeTest.OPENAI;
    private static final String GEMINI = EmbeddingModelScopeTest.GEMINI;
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired JobPersistenceAdapter jobsAdapter;
    @Autowired JobEmbeddingPersistenceAdapter jobEmbeddings;
    @Autowired EmbeddingChoicePersistenceAdapter choices;
    @Autowired EmbeddingModelScopeTest.ActiveEmbeddingModel active;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    JobRows jobs;

    @BeforeEach
    void setUp() {
        jobs = new JobRows(jdbc);
        active.use(OPENAI);
        choices.save(new EmbeddingChoice("openai", OPENAI));
    }

    /** A crawl still on OpenAI; the desktop switches to Gemini while this batch is at the API. */
    private EmbeddingProviderPort openAiSwitchedMidBatch() {
        return new EmbeddingProviderPort() {
            @Override
            public float[] embed(String text) {
                return embedAll(List.of(text)).getFirst();
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                choices.save(new EmbeddingChoice("gemini", GEMINI));
                jobsAdapter.requeueEmbeddingsForModel(GEMINI);
                return texts.stream().map(t -> vector()).toList();
            }

            @Override
            public String embeddingModelName() {
                return OPENAI;
            }
        };
    }

    private JobEmbeddingService staleCrawl() {
        return new JobEmbeddingService(jobsAdapter, jobEmbeddings, openAiSwitchedMidBatch(), 4,
                Duration.ofHours(6), 100, false);
    }

    @Test
    void a_vector_of_a_model_no_longer_stored_leaves_the_posting_queued_for_the_new_one() {
        UUID posting = jobs.insert("Kommunikationskonsulent", "Region", NOW);

        EmbedJobsUseCase.EmbedResult result = staleCrawl().embedPending(10, Duration.ofMinutes(1));
        entityManager.flush();

        assertThat(status(posting)).isEqualTo("PENDING");
        assertThat(result.succeeded()).isZero();
    }

    @Test
    void the_stale_crawl_stops_draining_instead_of_re_embedding_the_same_postings_forever() {
        jobs.insert("Kommunikationskonsulent", "Region", NOW);
        JobEmbeddingService crawl = staleCrawl();
        AtomicInteger passes = new AtomicInteger();
        // On this thread, inside the test's transaction, so the drain sees the posting; a pass
        // cap turns a spin into a failure instead of a hung build.
        EmbedJobsUseCase counted = new EmbedJobsUseCase() {
            @Override
            public boolean embed(com.autoapplicant.domain.job.Job job) {
                return crawl.embed(job);
            }

            @Override
            public EmbedResult embedPending(int limit, Duration budget) {
                if (passes.incrementAndGet() > 5) throw new IllegalStateException("drain is spinning");
                return crawl.embedPending(limit, budget);
            }
        };

        new QueueDrainer(Mockito.mock(SweepUnenrichedJobsUseCase.class), counted).drainEmbedding();

        assertThat(passes.get()).isEqualTo(1);
    }

    @Test
    void any_process_settling_the_choice_requeues_postings_a_stale_crawl_marked_embedded() {
        // A posting an older build's stale crawl marked EMBEDDED with an OpenAI vector only.
        UUID posting = jobs.insert("Kommunikationskonsulent", "Region", NOW);
        jobEmbeddings.save(new JobEmbedding(null, posting, vector(), OPENAI, null));
        jdbc.update("UPDATE jobs SET embedding_status = 'EMBEDDED' WHERE id = ?", posting);
        choices.save(new EmbeddingChoice("gemini", GEMINI));
        entityManager.flush();

        // The next crawl settles on the stored choice; it is not the desktop and sets nothing.
        new EmbeddingChoiceService(choices, jobsAdapter, "", "openai", OPENAI, GEMINI).currentChoice();

        assertThat(status(posting)).isEqualTo("PENDING");
    }

    private String status(UUID job) {
        return jdbc.queryForObject("SELECT embedding_status FROM jobs WHERE id = ?", String.class, job);
    }

    private static float[] vector() {
        float[] v = new float[1536];
        v[0] = 1f;
        return v;
    }
}
