package com.autoapplicant.port.out.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** When the user did something for their job search: application moves, generated documents, outreach. */
public interface JobSearchActivityPort {
    List<Instant> findActivitySince(UUID userId, Instant since);
}
