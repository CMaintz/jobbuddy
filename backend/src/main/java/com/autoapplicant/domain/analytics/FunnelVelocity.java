package com.autoapplicant.domain.analytics;

import java.util.List;

/**
 * Average time spent moving between application stages, derived from the append-only
 * status-event ledger. Answers "how long does a role sit in X before reaching Y?".
 */
public record FunnelVelocity(List<Transition> transitions) {
    public record Transition(String fromStatus, String toStatus, int count, double avgDays) {}
}
