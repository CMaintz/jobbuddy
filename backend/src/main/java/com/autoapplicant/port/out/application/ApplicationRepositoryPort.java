package com.autoapplicant.port.out.application;

import com.autoapplicant.domain.application.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepositoryPort {
    Application save(Application application);
    Optional<Application> findById(UUID id);
    List<Application> findByUserId(UUID userId);
    Page<Application> findByUserId(UUID userId, Pageable pageable);
    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    /**
     * Job ids this user has already applied to. A projection rather than {@link #findByUserId}
     * because recommendation filtering needs the ids on every request and nothing else.
     */
    java.util.Set<UUID> findAppliedJobIds(UUID userId);
    /** Most recent non-empty outcome lessons for the user, newest first. */
    List<String> findRecentOutcomeLessons(UUID userId, int limit);
}
