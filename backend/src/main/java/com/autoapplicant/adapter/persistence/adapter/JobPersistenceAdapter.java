package com.autoapplicant.adapter.persistence.adapter;

import com.autoapplicant.adapter.persistence.entity.JobEntity;
import com.autoapplicant.adapter.persistence.mapper.JobMapper;
import com.autoapplicant.adapter.persistence.repository.JobJpaRepository;
import com.autoapplicant.domain.job.DanishPlaces;
import com.autoapplicant.domain.job.EnrichmentStatus;
import com.autoapplicant.domain.job.GeoArea;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobSource;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JobPersistenceAdapter implements JobRepositoryPort {

    private static final String NO_CATEGORY = "-";
    /** Postings placed per sweep; a backlog of thousands is placed over a few sweeps. */
    private static final int PLACE_BACKFILL_SLICE = 2000;

    /**
     * Ceiling on postings pulled for the company-hiring aggregate. Generous for a personal-scale
     * jobs table, and bounded so the query can never turn into a full-table load.
     */
    private static final int AGGREGATION_LIMIT = 5000;

    private final JobJpaRepository repo;

    public JobPersistenceAdapter(JobJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<com.autoapplicant.domain.company.CompanyHiringSignal> findCompanyHiringSignals(
            java.time.Instant since) {
        List<JobEntity> postings = repo.findForCompanyAggregation(since,
                org.springframework.data.domain.PageRequest.of(0, AGGREGATION_LIMIT));

        Map<UUID, List<JobEntity>> byCompany = new java.util.LinkedHashMap<>();
        for (JobEntity job : postings) {
            byCompany.computeIfAbsent(job.getCompanyId(), k -> new java.util.ArrayList<>()).add(job);
        }

        java.util.List<com.autoapplicant.domain.company.CompanyHiringSignal> signals = new java.util.ArrayList<>();
        byCompany.forEach((companyId, jobs) -> {
            java.util.Set<String> technologies = new java.util.LinkedHashSet<>();
            int active = 0;
            java.time.Instant lastPosted = null;
            String companyName = null;
            for (JobEntity job : jobs) {
                if (job.getTechnologies() != null) {
                    for (String tech : job.getTechnologies()) {
                        if (tech != null && !tech.isBlank()) technologies.add(tech.strip());
                    }
                }
                if (job.isActive()) active++;
                java.time.Instant posted = job.getPostedAt() != null ? job.getPostedAt() : job.getCreatedAt();
                if (posted != null && (lastPosted == null || posted.isAfter(lastPosted))) lastPosted = posted;
                if (companyName == null && job.getCompanyName() != null) companyName = job.getCompanyName();
            }
            signals.add(new com.autoapplicant.domain.company.CompanyHiringSignal(
                    companyId, companyName, jobs.size(), active, lastPosted,
                    java.util.List.copyOf(technologies)));
        });
        return signals;
    }

    @Override
    public List<com.autoapplicant.domain.skill.SkillMention> findSkillMentions(int maxLabels) {
        if (maxLabels <= 0) return List.of();
        // [label, postings, technologyPostings] — the counts arrive as some Number subtype
        // depending on the driver, so read them as Number rather than casting to a concrete one.
        return repo.findSkillMentions(maxLabels).stream()
                .filter(row -> row.length >= 3 && row[0] instanceof String)
                .map(row -> new com.autoapplicant.domain.skill.SkillMention(
                        (String) row[0],
                        row[1] instanceof Number n ? n.intValue() : 0,
                        row[2] instanceof Number n ? n.intValue() : 0))
                .toList();
    }

    @Override
    public Job save(Job job) {
        JobEntity entity = JobMapper.toEntity(job);
        entity.setPlaces(DanishPlaces.shared().placesOfPosting(job.location(), job.title(), job.descriptionClean()));
        // URL-check state and the content fingerprint live only on the entity; carry them
        // over so crawl/enrichment re-saves don't reset them. The duplicate group is on the
        // domain but only the dedup pass sets it — preserve an existing group when the
        // incoming domain object doesn't carry one.
        if (job.id() != null) {
            repo.findById(job.id()).ifPresent(existing -> {
                entity.setLastUrlCheckAt(existing.getLastUrlCheckAt());
                entity.setUrlCheckFailures(existing.getUrlCheckFailures());
                entity.setContentFingerprint(existing.getContentFingerprint());
                if (entity.getDuplicateGroupId() == null && existing.getDuplicateGroupId() != null) {
                    entity.setDuplicateGroupId(existing.getDuplicateGroupId());
                }
                keepEmbeddingStateUnlessTextChanged(existing, entity, job);
            });
        }
        return JobMapper.toDomain(repo.save(entity));
    }

    /**
     * The vector belongs to the text it was made from. When enrichment trims the description of
     * a posting that was embedded at crawl time, the fresh entity's PENDING defaults re-queue it;
     * otherwise the embedding state is carried over, so a save that leaves the text alone does
     * not pay for the same vector again.
     */
    private static void keepEmbeddingStateUnlessTextChanged(JobEntity existing, JobEntity entity, Job job) {
        if (!JobMapper.toDomain(existing).embeddingText().equals(job.embeddingText())) {
            return;
        }
        entity.setEmbeddingStatus(existing.getEmbeddingStatus());
        entity.setEmbeddingAttempts(existing.getEmbeddingAttempts());
        entity.setEmbeddingLastAttemptAt(existing.getEmbeddingLastAttemptAt());
        entity.setEmbeddingLastError(existing.getEmbeddingLastError());
    }

    @Override
    public List<Job> saveAll(List<Job> jobs) {
        return repo.saveAll(jobs.stream().map(JobMapper::toEntity).collect(Collectors.toList()))
                .stream().map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return repo.findById(id).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findAll(int page, int size) {
        return repo.findAll(PageRequest.of(page, size)).stream()
                .map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Job> findActive(int page, int size) {
        return repo.findActiveJobs(PageRequest.of(page, size)).stream()
                .map(JobMapper::toDomain).toList();
    }

    @Override
    public List<Job> findByCompanyId(UUID companyId, int limit) {
        return repo.findByCompanyId(companyId, PageRequest.of(0, limit)).stream()
                .map(JobMapper::toDomain).toList();
    }

    @Override
    public List<Job> findActiveNotIgnoredBy(UUID userId, java.util.Collection<String> excludedCategories,
                                            int page, int size) {
        return repo.findActiveNotIgnoredBy(userId, notInList(excludedCategories), PageRequest.of(page, size))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    public Optional<Job> findBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.findBySourceAndSourceJobId(source.name(), sourceJobId).map(JobMapper::toDomain);
    }

    @Override
    public boolean existsBySourceAndSourceJobId(JobSource source, String sourceJobId) {
        return repo.existsBySourceAndSourceJobId(source.name(), sourceJobId);
    }

    @Override
    public Optional<Job> findByUrl(String url) {
        return repo.findByUrl(url).map(JobMapper::toDomain);
    }

    @Override
    public List<Job> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findAllById(ids).stream().map(JobMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UUID> findStaleActiveJobIds(Instant cutoff) {
        return repo.findStaleActiveJobIds(cutoff);
    }

    @Override
    public int deactivateStaleJobs(Instant cutoff) {
        return repo.deactivateStaleJobs(cutoff);
    }

    @Override
    public List<Job> findUrlCheckCandidates(Instant recheckCutoff, int limit) {
        return repo.findUrlCheckCandidates(recheckCutoff, PageRequest.of(0, limit))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    public List<UUID> deactivateDeadlineExpiredJobs(java.time.LocalDate before) {
        List<UUID> ids = repo.findActiveWithDeadlineBefore(before);
        if (!ids.isEmpty()) repo.deactivateDeadlineExpired(before);
        return ids;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void refreshLastSeen(UUID jobId, Instant lastSeenAt, java.time.LocalDate applicationDeadline) {
        repo.refreshLastSeen(jobId, lastSeenAt, applicationDeadline);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean markSeenBySourceJobId(com.autoapplicant.domain.job.JobSource source,
                                         String sourceJobId, Instant lastSeenAt) {
        if (source == null || sourceJobId == null || sourceJobId.isBlank()) return false;
        return repo.markSeenBySourceJobId(source.name(), sourceJobId, lastSeenAt) > 0;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void markUrlAlive(UUID jobId) {
        repo.markUrlAlive(jobId, Instant.now());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void markUrlCheckInconclusive(UUID jobId) {
        repo.markUrlCheckInconclusive(jobId, Instant.now());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean markUrlTakenDown(UUID jobId, int failureThreshold) {
        repo.countUrlCheckFailure(jobId, Instant.now());
        return repo.deactivateAtFailureThreshold(jobId, failureThreshold) > 0;
    }

    @Override
    public Optional<Job> findActiveDuplicateByFingerprint(long fingerprint, UUID excludeId) {
        return repo.findActiveByContentFingerprint(fingerprint, excludeId, PageRequest.of(0, 1))
                .stream().findFirst().map(JobMapper::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void assignContentFingerprint(UUID jobId, long fingerprint) {
        repo.updateContentFingerprint(jobId, fingerprint);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void assignDuplicateGroup(UUID jobId, UUID duplicateGroupId) {
        repo.updateDuplicateGroup(jobId, duplicateGroupId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<Job> findForEnrichment(int limit, int maxAttempts, Instant retryBefore,
                                       com.autoapplicant.domain.job.EnrichmentScope scope) {
        placeUnplacedPendingPostings();
        List<GeoArea> areas = scope.areas();
        return repo.findForEnrichment(maxAttempts, retryBefore, java.time.LocalDate.now(),
                        notInList(scope.excludedCategories()), areas.isEmpty(),
                        sqlArray(areas, a -> a.centre().lat()), sqlArray(areas, a -> a.centre().lon()),
                        sqlArray(areas, a -> (double) a.radiusKm()), limit)
                .stream().map(JobMapper::toDomain).toList();
    }

    /**
     * Postings saved before places were resolved (or written straight to the table) are placed
     * here, a slice per sweep, so the area filter sees them rather than letting them all through.
     */
    private void placeUnplacedPendingPostings() {
        DanishPlaces places = DanishPlaces.shared();
        for (JobEntity unplaced : repo.findUnplacedPending(PageRequest.of(0, PLACE_BACKFILL_SLICE))) {
            unplaced.setPlaces(places.placesOfPosting(
                    unplaced.getLocation(), unplaced.getTitle(), unplaced.getDescriptionClean()));
        }
    }

    private static String sqlArray(List<GeoArea> areas, java.util.function.ToDoubleFunction<GeoArea> value) {
        return areas.stream().map(a -> Double.toString(value.applyAsDouble(a)))
                .collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void markEnriched(UUID jobId) {
        repo.markEnriched(jobId, Instant.now());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void saveEnrichment(Job enriched) {
        repo.findById(enriched.id()).ifPresent(current -> {
            String embeddedTextBefore = JobMapper.toDomain(current).embeddingText();
            JobMapper.copyEnrichmentFields(JobMapper.toEntity(enriched), current);
            if (!JobMapper.toDomain(current).embeddingText().equals(embeddedTextBefore)) {
                current.setEmbeddingStatus(com.autoapplicant.domain.job.EmbeddingStatus.PENDING.name());
                current.setEmbeddingAttempts(0);
                current.setEmbeddingLastAttemptAt(null);
                current.setEmbeddingLastError(null);
            }
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean markEnrichmentFailed(UUID jobId, String reason, int maxAttempts) {
        if (repo.markEnrichmentFailed(jobId, truncate(reason), maxAttempts, Instant.now()) == 0) {
            return false;
        }
        return EnrichmentStatus.FAILED.name().equals(repo.findEnrichmentStatus(jobId));
    }

    @Override
    public Map<EnrichmentStatus, Long> countByEnrichmentStatus() {
        Map<EnrichmentStatus, Long> counts = new EnumMap<>(EnrichmentStatus.class);
        for (Object[] row : repo.countByEnrichmentStatus()) {
            counts.merge(EnrichmentStatus.parse((String) row[0]), ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }

    @Override
    public List<Job> findForEmbedding(int limit, int maxAttempts, Instant retryBefore,
                                      boolean requireEnriched) {
        return repo.findForEmbedding(maxAttempts, retryBefore, requireEnriched,
                        java.time.LocalDate.now(), PageRequest.of(0, limit))
                .stream().map(JobMapper::toDomain).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public EmbeddedMark markEmbedded(UUID jobId, String embeddedText, String model) {
        if (repo.markEmbeddedIfCurrent(jobId, embeddedText, model, Instant.now()) > 0) {
            return EmbeddedMark.MARKED;
        }
        boolean modelChanged = repo.findStoredEmbeddingModel().filter(stored -> !stored.equals(model)).isPresent();
        return modelChanged ? EmbeddedMark.MODEL_CHANGED : EmbeddedMark.TEXT_CHANGED;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean markEmbeddingFailed(UUID jobId, String reason, int maxAttempts) {
        if (repo.markEmbeddingFailed(jobId, truncate(reason), maxAttempts, Instant.now()) == 0) {
            return false;
        }
        return com.autoapplicant.domain.job.EmbeddingStatus.FAILED.name()
                .equals(repo.findEmbeddingStatus(jobId));
    }

    @Override
    @Transactional
    public int requeueEmbeddingsForModel(String model) {
        return repo.requeueEmbeddingsForModel(model);
    }

    @Override
    public Map<com.autoapplicant.domain.job.EmbeddingStatus, Long> countByEmbeddingStatus() {
        Map<com.autoapplicant.domain.job.EmbeddingStatus, Long> counts =
                new EnumMap<>(com.autoapplicant.domain.job.EmbeddingStatus.class);
        for (Object[] row : repo.countByEmbeddingStatus()) {
            counts.merge(com.autoapplicant.domain.job.EmbeddingStatus.parse((String) row[0]),
                    ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }

    /** The column is text, but an unbounded provider error has no business filling it. */
    private static String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    @Override
    public long count() {
        return repo.count();
    }

    @Override
    public long countActive() {
        return repo.countByIsActiveTrue();
    }

    @Override
    public long countActiveNotIgnoredBy(UUID userId, java.util.Collection<String> excludedCategories) {
        return repo.countActiveNotIgnoredBy(userId, notInList(excludedCategories));
    }

    /**
     * A NOT IN list that is never empty: an empty list renders as NOT IN (), which Postgres
     * rejects. The placeholder matches no category, so it excludes nothing.
     */
    private static java.util.Collection<String> notInList(java.util.Collection<String> categories) {
        return categories == null || categories.isEmpty() ? List.of(NO_CATEGORY) : categories;
    }
}
