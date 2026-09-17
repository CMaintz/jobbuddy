package com.autoapplicant.usecase.job;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.job.SavedJobRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The postings that represent "this user's market": their pipeline (applied + saved — explicit
 * interest) plus the nearest embedding-matched jobs (roles the matching engine considers
 * reachable).
 *
 * <p>Extracted from {@code MarketSkillGapService}, which needed exactly this corpus and had it private.
 * Skill-candidate ranking needs the same definition, and two definitions of "my market" that drift
 * apart would give the user contradictory advice on two screens.
 */
@Service
public class MarketCorpusService {

    private static final int MAX_PIPELINE_JOBS = 15;
    private static final int MAX_MATCHED_JOBS = 10;

    private final SavedJobRepositoryPort savedJobRepo;
    private final ApplicationRepositoryPort applicationRepo;
    private final JobRepositoryPort jobRepo;
    private final JobEmbeddingRepositoryPort jobEmbeddingRepo;
    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;

    public MarketCorpusService(SavedJobRepositoryPort savedJobRepo,
                               ApplicationRepositoryPort applicationRepo,
                               JobRepositoryPort jobRepo,
                               JobEmbeddingRepositoryPort jobEmbeddingRepo,
                               ProfileEmbeddingRepositoryPort profileEmbeddingRepo) {
        this.savedJobRepo = savedJobRepo;
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.jobEmbeddingRepo = jobEmbeddingRepo;
        this.profileEmbeddingRepo = profileEmbeddingRepo;
    }

    /**
     * Pipeline jobs first (explicit interest), then embedding-matched market jobs.
     *
     * @param requireDescription when true, only postings with body text — what a prompt needs.
     *                           Structured-field consumers (technologies, skills) can take the rest.
     */
    public List<Job> collect(UUID userId, boolean requireDescription) {
        Set<UUID> ids = new LinkedHashSet<>();
        applicationRepo.findByUserId(userId).stream()
                .map(Application::jobId)
                .filter(Objects::nonNull)
                .limit(MAX_PIPELINE_JOBS)
                .forEach(ids::add);
        savedJobRepo.findJobIdsByUserId(userId).stream()
                .limit(MAX_PIPELINE_JOBS)
                .forEach(ids::add);

        profileEmbeddingRepo.findByUserId(userId)
                .map(ProfileEmbedding::embedding)
                .ifPresent(vector -> jobEmbeddingRepo
                        .findNearestNeighborJobIds(vector, MAX_MATCHED_JOBS)
                        .forEach(ids::add));

        return jobRepo.findByIds(new ArrayList<>(ids)).stream()
                .filter(job -> !requireDescription
                        || (job.descriptionClean() != null && !job.descriptionClean().isBlank()))
                .limit(MAX_PIPELINE_JOBS + MAX_MATCHED_JOBS)
                .toList();
    }
}
