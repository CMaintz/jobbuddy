package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.usecase.job.JobCategoryClassifier;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final AtomicInteger enrichedCount = new AtomicInteger(0);

    private final JobRepositoryPort jobRepo;
    private final JobSearchPort jobSearch;
    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final AiProviderPort aiProvider;
    private final EnrichJobUseCase enrichJob;
    private final TextCleaningService textCleaner;
    private final JobCategoryClassifier categoryClassifier;

    public IngestionPipeline(JobRepositoryPort jobRepo, JobSearchPort jobSearch,
                              JobEmbeddingRepositoryPort embeddingRepo,
                              @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                              EnrichJobUseCase enrichJob, TextCleaningService textCleaner,
                              JobCategoryClassifier categoryClassifier) {
        this.jobRepo = jobRepo;
        this.jobSearch = jobSearch;
        this.embeddingRepo = embeddingRepo;
        this.aiProvider = aiProvider;
        this.enrichJob = enrichJob;
        this.textCleaner = textCleaner;
        this.categoryClassifier = categoryClassifier;
    }

    public void ingest(RawJobData raw) {
        try {
            // Deduplication check
            if (raw.sourceJobId() != null &&
                    jobRepo.findBySourceAndSourceJobId(raw.source(), raw.sourceJobId()).isPresent()) {
                log.debug("Skipping duplicate job: {} / {}", raw.source(), raw.sourceJobId());
                return;
            }

            String cleanText = textCleaner.clean(raw.rawHtml());
            String title = textCleaner.extractTitle(raw.rawHtml());
            JobCategory category = categoryClassifier.classify(title, cleanText);

            Job draft = new Job(null, raw.source(), raw.sourceJobId(), raw.url(),
                    title, null, null, raw.rawHtml(), cleanText,
                    null, null, null, null, null, null, "DK",
                    null, null, "DKK", List.of(), List.of(), List.of(),
                    null, raw.scrapedAt(), null, List.of(), null, null,
                    true, category, null, null);

            Job saved = jobRepo.save(draft);

            // Async: AI enrichment
            enrichJob.enrich(saved).thenAccept(enriched -> {
                jobRepo.save(enriched);
                jobSearch.index(enriched);
                embedAsync(enriched);
                int n = enrichedCount.incrementAndGet();
                if (n % 50 == 0) {
                    log.info("Enrichment progress: {} jobs enriched so far", n);
                }
            });

        } catch (Exception e) {
            log.error("Ingestion failed for raw job from {}: {}", raw.source(), e.getMessage(), e);
        }
    }

    @Async("aiTaskExecutor")
    protected void embedAsync(Job job) {
        try {
            String textToEmbed = job.title() + " " +
                    (job.descriptionClean() != null ? job.descriptionClean() : "");
            float[] vector = aiProvider.embed(textToEmbed);
            JobEmbedding embedding = new JobEmbedding(null, job.id(), vector,
                    aiProvider.embeddingModelName(), Instant.now());
            embeddingRepo.save(embedding);
        } catch (Exception e) {
            log.warn("Failed to create embedding for job {}: {}", job.id(), e.getMessage());
        }
    }
}
