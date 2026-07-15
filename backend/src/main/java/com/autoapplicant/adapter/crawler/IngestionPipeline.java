package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.usecase.job.JobCategoryClassifier;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
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
    private final CompanyRepositoryPort companyRepo;

    public IngestionPipeline(JobRepositoryPort jobRepo, JobSearchPort jobSearch,
                              JobEmbeddingRepositoryPort embeddingRepo,
                              @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                              EnrichJobUseCase enrichJob, TextCleaningService textCleaner,
                              JobCategoryClassifier categoryClassifier,
                              CompanyRepositoryPort companyRepo) {
        this.jobRepo = jobRepo;
        this.jobSearch = jobSearch;
        this.embeddingRepo = embeddingRepo;
        this.aiProvider = aiProvider;
        this.enrichJob = enrichJob;
        this.textCleaner = textCleaner;
        this.categoryClassifier = categoryClassifier;
        this.companyRepo = companyRepo;
    }

    public void ingest(RawJobData raw) {
        try {
            // Deduplication check — refresh lastSeenAt for existing jobs so stale detection works
            if (raw.sourceJobId() != null) {
                var existing = jobRepo.findBySourceAndSourceJobId(raw.source(), raw.sourceJobId());
                if (existing.isPresent()) {
                    Job seen = existing.get();
                    jobRepo.save(new Job(seen.id(), seen.source(), seen.sourceJobId(), seen.url(),
                            seen.title(), seen.companyId(), seen.companyName(), seen.descriptionRaw(),
                            seen.descriptionClean(), seen.employmentType(), seen.seniority(), seen.remoteType(),
                            seen.location(), seen.municipality(), seen.region(), seen.country(),
                            seen.salaryMin(), seen.salaryMax(), seen.currency(), seen.technologies(),
                            seen.skills(), seen.languages(), seen.postedAt(), seen.scrapedAt(),
                            seen.aiSummary(), seen.aiTags(), seen.aiSeniorityEstimate(),
                            seen.duplicateGroupId(), seen.isActive(), seen.jobCategory(),
                            seen.createdAt(), seen.updatedAt(), seen.shortDescription(), Instant.now(),
                            seen.applicationDeadline()));
                    log.debug("Refreshed lastSeenAt for existing job: {} / {}", raw.source(), raw.sourceJobId());
                    return;
                }
            }

            String cleanText = textCleaner.clean(raw.rawHtml());
            String title = textCleaner.extractTitle(raw.rawHtml());
            JobCategory category = categoryClassifier.classify(raw.rawCategories(), title, cleanText);
            UUID companyId = resolveCompany(raw);

            Job draft = new Job(null, raw.source(), raw.sourceJobId(), raw.url(),
                    title, companyId, raw.companyName(), raw.rawHtml(), cleanText,
                    null, null, null, null, null, null, "DK",
                    null, null, "DKK", List.of(), List.of(), List.of(),
                    null, raw.scrapedAt(), null, List.of(), null, null,
                    true, category, null, null, raw.shortDescription(), Instant.now(),
                    raw.applicationDeadline());

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
            }).exceptionally(ex -> {
                log.error("Enrichment failed for job {} ({}): {}", saved.id(), saved.sourceJobId(), ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Ingestion failed for raw job from {}: {}", raw.source(), e.getMessage(), e);
        }
    }

    /**
     * Resolves (or creates) the company when the source provided a structured name,
     * and backfills the company homepage if the crawl carried one and it's still empty.
     */
    private UUID resolveCompany(RawJobData raw) {
        String name = raw.companyName();
        if (name == null || name.isBlank()) return null;
        try {
            Company company = companyRepo.findOrCreate(name.trim());
            String website = raw.companyWebsiteUrl();
            if (website != null && !website.isBlank()
                    && (company.website() == null || company.website().isBlank())) {
                companyRepo.save(new Company(company.id(), company.name(), company.slug(),
                        website.trim(), company.linkedinUrl(), company.description(), company.logoUrl(),
                        company.sizeRange(), company.industry(), company.country(),
                        company.isConsulting(), company.isRecruitingAgency(),
                        company.createdAt(), company.updatedAt()));
            }
            return company.id();
        } catch (Exception e) {
            log.warn("Company resolution failed for '{}': {}", name, e.getMessage());
            return null;
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
