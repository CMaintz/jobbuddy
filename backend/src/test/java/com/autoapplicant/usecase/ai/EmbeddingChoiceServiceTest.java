package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.EmbeddingChoice;
import com.autoapplicant.port.out.ai.EmbeddingChoiceRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbeddingChoiceServiceTest {

    private static final String OPENAI_MODEL = "text-embedding-3-small";
    private static final String GEMINI_MODEL = "gemini-embedding-001";
    private static final EmbeddingChoice OPENAI = new EmbeddingChoice("openai", OPENAI_MODEL);
    private static final EmbeddingChoice GEMINI = new EmbeddingChoice("gemini", GEMINI_MODEL);

    private final EmbeddingChoiceRepositoryPort stored = mock(EmbeddingChoiceRepositoryPort.class);
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);

    private EmbeddingChoiceService service(String setupProvider, String fallbackProvider) {
        return new EmbeddingChoiceService(stored, jobs, setupProvider, fallbackProvider,
                OPENAI_MODEL, GEMINI_MODEL);
    }

    @Test
    void desktop_setup_stores_her_choice_for_the_crawl_to_follow() {
        when(stored.find()).thenReturn(Optional.empty());

        assertThat(service("gemini", "openai").currentChoice()).isEqualTo(GEMINI);

        verify(stored).save(GEMINI);
    }

    @Test
    void switching_provider_queues_every_posting_without_a_vector_for_the_new_model() {
        when(stored.find()).thenReturn(Optional.of(OPENAI));

        service("gemini", "openai").currentChoice();

        verify(stored).save(GEMINI);
        verify(jobs).requeueEmbeddingsForModel(GEMINI_MODEL);
    }

    @Test
    void the_same_choice_again_writes_nothing() {
        when(stored.find()).thenReturn(Optional.of(GEMINI));

        assertThat(service("gemini", "openai").currentChoice()).isEqualTo(GEMINI);

        verify(stored, never()).save(any());
    }

    @Test
    void the_crawl_follows_the_stored_choice_over_its_own_env() {
        // The crawl's env says openai; the database says gemini. Embedding the crawl's postings
        // with another model than her profile makes every match meaningless.
        when(stored.find()).thenReturn(Optional.of(GEMINI));

        assertThat(service("", "openai").currentChoice()).isEqualTo(GEMINI);

        verify(stored, never()).save(any());
    }

    @Test
    void every_process_that_settles_requeues_for_the_stored_model() {
        // A crawl that settled on the old model before a switch may have marked postings embedded
        // with old vectors only. Whoever starts next puts them back in the queue.
        when(stored.find()).thenReturn(Optional.of(GEMINI));

        service("", "openai").currentChoice();

        verify(jobs).requeueEmbeddingsForModel(GEMINI_MODEL);
    }

    @Test
    void with_nothing_stored_env_config_is_the_fallback_and_is_not_written() {
        when(stored.find()).thenReturn(Optional.empty());

        assertThat(service("", "openai").currentChoice()).isEqualTo(OPENAI);

        verify(stored, never()).save(any());
    }

    @Test
    void an_unknown_provider_is_refused_by_name() {
        when(stored.find()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("mistral", "openai").currentChoice())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mistral");
    }
}
