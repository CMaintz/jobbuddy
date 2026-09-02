package com.autoapplicant.port.out.ai;

import java.util.Optional;
import java.util.UUID;

/**
 * Who, if anyone, the work in flight belongs to. Background work (job enrichment,
 * the crawler, scheduled reminders) runs with no user, so this is an Optional
 * rather than a throwing lookup.
 */
public interface CurrentUserPort {
    Optional<UUID> currentUserId();
}
