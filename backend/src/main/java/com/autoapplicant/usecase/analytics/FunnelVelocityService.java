package com.autoapplicant.usecase.analytics;

import com.autoapplicant.domain.analytics.FunnelVelocity;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.port.in.analytics.GetFunnelVelocityUseCase;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Computes average time-in-stage between consecutive status transitions across a user's
 * applications, from the append-only {@link ApplicationStatusEvent} ledger.
 */
@Service
public class FunnelVelocityService implements GetFunnelVelocityUseCase {

    private final ApplicationStatusEventRepositoryPort eventRepo;

    public FunnelVelocityService(ApplicationStatusEventRepositoryPort eventRepo) {
        this.eventRepo = eventRepo;
    }

    @Override
    public FunnelVelocity getFunnelVelocity(UUID userId) {
        Map<UUID, List<ApplicationStatusEvent>> byApp = eventRepo.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(ApplicationStatusEvent::applicationId));
        return new FunnelVelocity(toTransitions(aggregate(byApp.values())));
    }

    /**
     * Accumulates elapsed time per {@code FROM->TO} transition across every application's event
     * stream. Keyed by {@code "FROM->TO"} in a {@link LinkedHashMap} so the output order is stable.
     */
    private static Map<String, TransitionAccumulator> aggregate(
            Collection<List<ApplicationStatusEvent>> byApp) {
        Map<String, TransitionAccumulator> agg = new LinkedHashMap<>();
        for (List<ApplicationStatusEvent> events : byApp) {
            accumulateApplication(events, agg);
        }
        return agg;
    }

    /** Folds one application's status ledger, in chronological order, into the shared accumulators. */
    private static void accumulateApplication(
            List<ApplicationStatusEvent> events, Map<String, TransitionAccumulator> agg) {
        events.sort(Comparator.comparing(ApplicationStatusEvent::occurredAt));
        for (int i = 1; i < events.size(); i++) {
            ApplicationStatusEvent prev = events.get(i - 1);
            ApplicationStatusEvent cur = events.get(i);
            String from = cur.fromStatus() != null ? cur.fromStatus().name()
                    : (prev.toStatus() != null ? prev.toStatus().name() : "UNKNOWN");
            String to = cur.toStatus().name();
            long seconds = Math.max(0, Duration.between(prev.occurredAt(), cur.occurredAt()).getSeconds());
            agg.computeIfAbsent(from + "->" + to, k -> new TransitionAccumulator(from, to)).add(seconds);
        }
    }

    /** Renders the accumulated stats into transitions, busiest first. */
    private static List<FunnelVelocity.Transition> toTransitions(Map<String, TransitionAccumulator> agg) {
        List<FunnelVelocity.Transition> transitions = agg.values().stream()
                .map(TransitionAccumulator::toTransition)
                .collect(Collectors.toCollection(ArrayList::new));
        transitions.sort(Comparator.comparingInt(FunnelVelocity.Transition::count).reversed());
        return transitions;
    }

    /** Mutable running total of time-in-stage for one {@code FROM->TO} transition. */
    private static final class TransitionAccumulator {
        private final String from;
        private final String to;
        private int count;
        private long totalSeconds;

        TransitionAccumulator(String from, String to) {
            this.from = from;
            this.to = to;
        }

        void add(long seconds) {
            count++;
            totalSeconds += seconds;
        }

        FunnelVelocity.Transition toTransition() {
            double avgDays = count == 0 ? 0 : (totalSeconds / (double) count) / 86_400.0;
            return new FunnelVelocity.Transition(from, to, count, Math.round(avgDays * 10.0) / 10.0);
        }
    }
}
