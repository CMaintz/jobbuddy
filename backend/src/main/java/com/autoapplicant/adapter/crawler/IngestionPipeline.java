package com.autoapplicant.adapter.crawler;

import com.autoapplicant.usecase.job.JobEnrichmentService;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.JobSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final JobRepositoryPort jobRepo;
    private final JobSearchPort jobSearch;
    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final AiProviderPort aiProvider;
    private final JobEnrichmentService enrichmentService;
    private final TextCleaningService textCleaner;

    public IngestionPipeline(JobRepositoryPort jobRepo, JobSearchPort jobSearch,
                              JobEmbeddingRepositoryPort embeddingRepo, AiProviderPort aiProvider,
                              JobEnrichmentService enrichmentService, TextCleaningService textCleaner) {
        this.jobRepo = jobRepo;
        this.jobSearch = jobSearch;
        this.embeddingRepo = embeddingRepo;
        this.aiProvider = aiProvider;
        this.enrichmentService = enrichmentService;
        this.textCleaner = textCleaner;
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

            Job draft = new Job(null, raw.source(), raw.sourceJobId(), raw.url(),
                    "Untitled", null, null, raw.rawHtml(), cleanText,
                    null, null, null, null, null, null, "DK",
                    null, null, "DKK", List.of(), List.of(), List.of(),
                    null, raw.scrapedAt(), null, List.of(), null, null,
                    true, null, null);

            Job saved = jobRepo.save(draft);

            // Async: AI enrichment
            enrichmentService.enrich(saved).thenAccept(enriched -> {
                jobRepo.save(enriched);
                jobSearch.index(enriched);
                embedAsync(enriched);
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
                    "text-embedding-3-small", Instant.now());
            embeddingRepo.save(embedding);
        } catch (Exception e) {
            log.warn("Failed to create embedding for job {}: {}", job.id(), e.getMessage());
        }
    }
}
