package com.autoapplicant.usecase.analytics;

import com.autoapplicant.domain.analytics.FunnelVelocity;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.port.in.analytics.GetFunnelVelocityUseCase;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        TransitionTally tally = new TransitionTally();
        byApp.values().forEach(events -> tallyTransitions(events, tally));
        return new FunnelVelocity(tally.toTransitions());
    }

    /** Records each consecutive status change within one application's history, oldest first. */
    private static void tallyTransitions(List<ApplicationStatusEvent> events, TransitionTally tally) {
        events.sort(Comparator.comparing(ApplicationStatusEvent::occurredAt));
        for (int i = 1; i < events.size(); i++) {
            ApplicationStatusEvent prev = events.get(i - 1);
            ApplicationStatusEvent cur = events.get(i);
            String from = cur.fromStatus() != null ? cur.fromStatus().name()
                    : (prev.toStatus() != null ? prev.toStatus().name() : "UNKNOWN");
            long seconds = Math.max(0, Duration.between(prev.occurredAt(), cur.occurredAt()).getSeconds());
            tally.record(from, cur.toStatus().name(), seconds);
        }
    }

    /**
     * Accumulates count and total time-in-stage per {@code FROM->TO} transition, insertion-ordered
     * for stable output, and renders them as transitions sorted by frequency.
     */
    private static final class TransitionTally {
        // key "FROM->TO" -> [count, totalSeconds]
        private final Map<String, long[]> countAndSeconds = new LinkedHashMap<>();
        private final Map<String, String[]> labels = new LinkedHashMap<>();

        void record(String from, String to, long seconds) {
            String key = from + "->" + to;
            long[] a = countAndSeconds.computeIfAbsent(key, k -> new long[2]);
            a[0]++;
            a[1] += seconds;
            labels.putIfAbsent(key, new String[]{from, to});
        }

        List<FunnelVelocity.Transition> toTransitions() {
            List<FunnelVelocity.Transition> transitions = new ArrayList<>();
            for (Map.Entry<String, long[]> e : countAndSeconds.entrySet()) {
                long[] a = e.getValue();
                String[] fl = labels.get(e.getKey());
                double avgDays = a[0] == 0 ? 0 : (a[1] / (double) a[0]) / 86_400.0;
                transitions.add(new FunnelVelocity.Transition(fl[0], fl[1], (int) a[0],
                        Math.round(avgDays * 10.0) / 10.0));
            }
            transitions.sort(Comparator.comparingInt(FunnelVelocity.Transition::count).reversed());
            return transitions;
        }
    }
}
