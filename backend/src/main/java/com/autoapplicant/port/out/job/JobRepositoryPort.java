package com.autoapplicant.port.out.job;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JobRepositoryPort {
    Job save(Job job);
    List<Job> saveAll(List<Job> jobs);
    Optional<Job> findById(UUID id);
    List<Job> findAll(int page, int size);
    /** Active (not expired/taken-down) jobs only — the user-facing feed. */
    List<Job> findActive(int page, int size);
    List<Job> findAllExcluding(Set<UUID> excludedIds, int page, int size);
    Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId);
    boolean existsBySourceAndSourceJobId(JobSource source, String sourceJobId);
    Optional<Job> findByUrl(String url);
    List<Job> findByIds(List<UUID> ids);
    /** Every posting we hold from one company, live ones first. */
    List<Job> findByCompanyId(UUID companyId, int limit);
    /**
     * The enrichment queue: jobs still worth trying, never-attempted first. Jobs given
     * up on after {@code maxAttempts}, and ones tried since {@code retryBefore}, are
     * left out.
     */
    List<Job> findForEnrichment(int limit, int maxAttempts, java.time.Instant retryBefore);

    /** Enrichment produced a usable result; stop asking about this one. */
    void markEnriched(UUID jobId);

    /**
     * An attempt failed. Increments the attempt count and gives up on the job once it
     * reaches {@code maxAttempts}, so a posting that can never be enriched stops
     * consuming quota. Returns true when this was the attempt that gave up.
     */
    boolean markEnrichmentFailed(UUID jobId, String reason, int maxAttempts);

    /** How many jobs sit in each enrichment state — for the sweep's own reporting. */
    java.util.Map<com.autoapplicant.domain.job.EnrichmentStatus, Long> countByEnrichmentStatus();
    List<UUID> findStaleActiveJobIds(java.time.Instant cutoff);
    int deactivateStaleJobs(java.time.Instant cutoff);
    long count();
    long countActive();

    /**
     * How often each skill label appears across every posting we hold, most-named first.
     *
     * <p>Counted in the database rather than by loading postings: this reads the whole table, and
     * the answer is one row per distinct label. Case and surrounding space are folded here; the
     * remaining normalisation is the caller's, so there is still exactly one normalizer.
     *
     * @param maxLabels ceiling on distinct labels returned, since the tail is single mentions
     */
    List<com.autoapplicant.domain.skill.SkillMention> findSkillMentions(int maxLabels);

    /**
     * Per-company hiring aggregates over postings seen since the cutoff — the raw material for
     * ranking unsolicited-application targets. Bounded internally; companies with no postings in
     * the window are absent rather than zero-valued.
     */
    List<com.autoapplicant.domain.company.CompanyHiringSignal> findCompanyHiringSignals(
            java.time.Instant since);

    // ── URL health checks (takedown detection) ──────────────────
    /** Active non-manual jobs whose URL hasn't been probed since the cutoff, oldest check first. */
    List<Job> findUrlCheckCandidates(java.time.Instant recheckCutoff, int limit);
    /** Re-crawl of an existing posting: bump lastSeenAt (and pick up a changed deadline). */
    void refreshLastSeen(UUID jobId, java.time.Instant lastSeenAt, java.time.LocalDate applicationDeadline);
    /** Probe confirmed the posting is live: reset failures and refresh lastSeenAt. */
    void markUrlAlive(UUID jobId);
    /** Probe couldn't tell (bot-blocked, server error): just record the attempt. */
    void markUrlCheckInconclusive(UUID jobId);
    /**
     * Probe says the posting is gone. Increments the failure count and deactivates the job
     * once it reaches the threshold. Returns true when the job was deactivated.
     */
    boolean markUrlTakenDown(UUID jobId, int failureThreshold);

    /**
     * Deactivates active jobs whose application deadline lies strictly before the
     * given date (the deadline day itself still counts as applicable).
     * Returns the affected job ids so the search index can be updated.
     */
    List<UUID> deactivateDeadlineExpiredJobs(java.time.LocalDate before);

    // ── Content-fingerprint dedup (cross-listing detection) ──────────────
    /** An active job (other than {@code excludeId}) sharing this SimHash fingerprint, if any. */
    Optional<Job> findActiveDuplicateByFingerprint(long fingerprint, UUID excludeId);
    /** Store a job's content fingerprint (entity-only field). */
    void assignContentFingerprint(UUID jobId, long fingerprint);
    /** Cluster a job into a duplicate group. */
    void assignDuplicateGroup(UUID jobId, UUID duplicateGroupId);
}
