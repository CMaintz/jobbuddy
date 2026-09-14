package com.autoapplicant.adapter.crawler;

import com.autoapplicant.domain.company.Company;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.JobCategoryClassifier;
import com.autoapplicant.domain.job.RawJobData;
import com.autoapplicant.domain.job.SimHash;
import com.autoapplicant.port.out.company.CompanyRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);


    private final JobRepositoryPort jobRepo;
    private final TextCleaningService textCleaner;
    /** Pure domain service — stateless keyword classifier, no injection needed. */
    private final JobCategoryClassifier categoryClassifier = new JobCategoryClassifier();
    private final CompanyRepositoryPort companyRepo;

    public IngestionPipeline(JobRepositoryPort jobRepo,
                              TextCleaningService textCleaner,
                              CompanyRepositoryPort companyRepo) {
        this.jobRepo = jobRepo;
        this.textCleaner = textCleaner;
        this.companyRepo = companyRepo;
    }

    /**
     * What became of one raw posting. Returned rather than logged so the crawl summary can
     * say how many postings were new instead of how many were looked at — the two used to be
     * reported as one number, which made a re-crawl of unchanged jobs read as a fresh haul.
     */
    public enum IngestOutcome { NEW, REFRESHED, FAILED }

    /**
     * @param crossListed the posting was clustered with an existing one — the same role
     *                    re-listed under a different company, URL or ATS. Reported as a count
     *                    at the end of the crawl rather than a line each: on a large run it was
     *                    thousands of lines saying the system worked.
     */
    public record IngestResult(IngestOutcome outcome, boolean crossListed) {
        static IngestResult of(IngestOutcome outcome) { return new IngestResult(outcome, false); }
    }

    public IngestResult ingest(RawJobData raw) {
        try {
            Optional<IngestResult> refreshed = tryRefreshExisting(raw);
            return refreshed.isPresent() ? refreshed.get() : ingestNew(raw);
        } catch (Exception e) {
            log.error("Ingestion failed for raw job from {}: {}", raw.source(), e.getMessage(), e);
            return IngestResult.of(IngestOutcome.FAILED);
        }
    }

    /**
     * Deduplication check: when the posting carries a source job id we've already stored, refresh
     * its lastSeenAt (so stale detection works) and pick up a deadline the source published or
     * changed after first crawl, then report REFRESHED. Empty when this is a posting to ingest anew.
     */
    private Optional<IngestResult> tryRefreshExisting(RawJobData raw) {
        if (raw.sourceJobId() == null) return Optional.empty();
        Optional<Job> existing = jobRepo.findBySourceAndSourceJobId(raw.source(), raw.sourceJobId());
        if (existing.isEmpty()) return Optional.empty();
        Job seen = existing.get();
        java.time.LocalDate deadline = raw.applicationDeadline() != null
                ? raw.applicationDeadline() : seen.applicationDeadline();
        // Targeted update in the adapter (like markUrlAlive) — no whole-Job rebuild.
        jobRepo.refreshLastSeen(seen.id(), Instant.now(), deadline);
        log.debug("Refreshed lastSeenAt for existing job: {} / {}", raw.source(), raw.sourceJobId());
        return Optional.of(IngestResult.of(IngestOutcome.REFRESHED));
    }

    /**
     * Build, save and fingerprint a posting not seen before. Enrichment is NOT fired from here: the
     * posting is saved PENDING and the enrichment worker drains that queue at a rate the AI provider
     * can sustain. (It used to go to a two-thread pool behind a 5000-deep queue that discarded on
     * overflow, silently dropping thousands of enrichments on a large crawl; the database column is
     * the queue now — durable, countable, impossible to overflow.)
     */
    private IngestResult ingestNew(RawJobData raw) {
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
        boolean crossListed = fingerprintAndCluster(saved, cleanText);
        return new IngestResult(IngestOutcome.NEW, crossListed);
    }

    /**
     * Fingerprints the cleaned description (SimHash) and, when an existing active job shares
     * the fingerprint, clusters both under a shared duplicate_group_id — catching agency
     * re-posts of the same role under a different company/URL that source+id dedup misses.
     * Flag-only (never drops a job); best-effort so a failure never breaks ingestion.
     *
     * <p>The group is the storage: both postings carry the same duplicate_group_id, so every
     * alternative URL for one role is a query away — which is what a reader needs when one ATS
     * is broken and another is not. Detection is logged at DEBUG and counted, because a busy
     * crawl produced thousands of INFO lines whose only news was that the feature worked.
     *
     * @return true when this posting was clustered with an existing one
     */
    private boolean fingerprintAndCluster(Job saved, String cleanText) {
        try {
            long fp = SimHash.fingerprint(cleanText);
            if (fp == 0L) return false; // too little content to fingerprint reliably
            boolean clustered = jobRepo.findActiveDuplicateByFingerprint(fp, saved.id())
                    .map(other -> clusterWith(saved, other))
                    .orElse(false);
            jobRepo.assignContentFingerprint(saved.id(), fp);
            return clustered;
        } catch (Exception e) {
            log.warn("Fingerprint/dedup failed for job {}: {}", saved.id(), e.getMessage());
            return false;
        }
    }

    /**
     * Put {@code saved} and its verbatim re-post {@code other} under one duplicate_group_id,
     * creating the group when the other posting has none yet. Returns true — the two are clustered.
     */
    private boolean clusterWith(Job saved, Job other) {
        UUID group = other.duplicateGroupId() != null ? other.duplicateGroupId() : UUID.randomUUID();
        if (other.duplicateGroupId() == null) jobRepo.assignDuplicateGroup(other.id(), group);
        jobRepo.assignDuplicateGroup(saved.id(), group);
        log.debug("Cross-listing: job {} ({}) duplicates {} — group {}",
                saved.id(), saved.source(), other.id(), group);
        return true;
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
            backfillWebsiteIfMissing(company, raw.companyWebsiteUrl());
            return company.id();
        } catch (Exception e) {
            log.warn("Company resolution failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    /** Fill in the company homepage from the crawl when the record still has none. */
    private void backfillWebsiteIfMissing(Company company, String website) {
        if (website != null && !website.isBlank()
                && (company.website() == null || company.website().isBlank())) {
            // Targeted single-field backfill in the adapter — no whole-Company rebuild.
            companyRepo.backfillWebsite(company.id(), website.trim());
        }
    }

}
