package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobEmbedding;
import com.autoapplicant.domain.job.JobCategoryClassifier;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.domain.job.SimHash;
import com.autoapplicant.port.in.job.EnrichJobUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final AiProviderPort aiProvider;
    private final EnrichJobUseCase enrichJob;
    private final TextCleaningService textCleaner;
    /** Pure domain service — stateless keyword classifier, no injection needed. */
    private final JobCategoryClassifier categoryClassifier = new JobCategoryClassifier();
    private final CompanyRepositoryPort companyRepo;

    public IngestionPipeline(JobRepositoryPort jobRepo,
                              JobEmbeddingRepositoryPort embeddingRepo,
                              @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                              EnrichJobUseCase enrichJob, TextCleaningService textCleaner,
                              CompanyRepositoryPort companyRepo) {
        this.jobRepo = jobRepo;
        this.embeddingRepo = embeddingRepo;
        this.aiProvider = aiProvider;
        this.enrichJob = enrichJob;
        this.textCleaner = textCleaner;
        this.companyRepo = companyRepo;
    }

    public void ingest(RawJobData raw) {
        try {
            // Deduplication check — refresh lastSeenAt for existing jobs so stale detection
            // works, and pick up a deadline the source published (or changed) after first crawl.
            if (raw.sourceJobId() != null) {
                var existing = jobRepo.findBySourceAndSourceJobId(raw.source(), raw.sourceJobId());
                if (existing.isPresent()) {
                    Job seen = existing.get();
                    var deadline = raw.applicationDeadline() != null
                            ? raw.applicationDeadline() : seen.applicationDeadline();
                    // Targeted update in the adapter (like markUrlAlive) — no whole-Job rebuild.
                    jobRepo.refreshLastSeen(seen.id(), Instant.now(), deadline);
                    log.debug("Refreshed lastSeenAt for existing job: {} / {}", raw.source(), raw.sourceJobId());
                    return;
                }
            }

            String cleanText = textCleaner.clean(raw.rawHtml());
            String title = textCleaner.extractTitle(raw.rawHtml());
            JobCategory category = categoryClassifier.classify(raw.rawCategories(), title, cleanText);
            UUID companyId = resolveCompany(raw);

            Job draft = Job.builder()
                    .source(raw.source()).sourceJobId(raw.sourceJobId()).url(raw.url())
                    .title(title).companyId(companyId).companyName(raw.companyName())
                    .descriptionRaw(raw.rawHtml()).descriptionClean(cleanText)
                    .location(raw.location()).country("DK").currency("DKK")
                    .postedAt(raw.postedAt()).scrapedAt(raw.scrapedAt())
                    .isActive(true).jobCategory(category)
                    .shortDescription(raw.shortDescription()).lastSeenAt(Instant.now())
                    .applicationDeadline(raw.applicationDeadline())
                    .build();

            Job saved = jobRepo.save(draft);

            // Cross-listing dedup: fingerprint the description and cluster verbatim re-posts
            // (same role re-listed under a different company/URL) via duplicate_group_id.
            fingerprintAndCluster(saved, cleanText);

            // Async: AI enrichment
            enrichJob.enrich(saved).thenAccept(enriched -> {
                jobRepo.save(enriched);
                embed(enriched);
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
     * Fingerprints the cleaned description (SimHash) and, when an existing active job shares
     * the fingerprint, clusters both under a shared duplicate_group_id — catching agency
     * re-posts of the same role under a different company/URL that source+id dedup misses.
     * Flag-only (never drops a job); best-effort so a failure never breaks ingestion.
     */
    private void fingerprintAndCluster(Job saved, String cleanText) {
        try {
            long fp = SimHash.fingerprint(cleanText);
            if (fp == 0L) return; // too little content to fingerprint reliably
            jobRepo.findActiveDuplicateByFingerprint(fp, saved.id()).ifPresent(other -> {
                UUID group = other.duplicateGroupId() != null ? other.duplicateGroupId() : UUID.randomUUID();
                if (other.duplicateGroupId() == null) jobRepo.assignDuplicateGroup(other.id(), group);
                jobRepo.assignDuplicateGroup(saved.id(), group);
                log.info("Cross-listing detected: job {} ({}) duplicates {} — group {}",
                        saved.id(), saved.source(), other.id(), group);
            });
            jobRepo.assignContentFingerprint(saved.id(), fp);
        } catch (Exception e) {
            log.warn("Fingerprint/dedup failed for job {}: {}", saved.id(), e.getMessage());
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
                // Targeted single-field backfill in the adapter — no whole-Company rebuild.
                companyRepo.backfillWebsite(company.id(), website.trim());
            }
            return company.id();
        } catch (Exception e) {
            log.warn("Company resolution failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Runs on the enrichment callback's thread (already the AI task executor).
     * Deliberately not {@code @Async}: a self-invocation would bypass the Spring
     * proxy anyway, so the annotation would only mislead.
     */
    private void embed(Job job) {
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
