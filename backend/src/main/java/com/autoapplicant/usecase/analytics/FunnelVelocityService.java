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

        // key "FROM->TO" -> [count, totalSeconds]; insertion-ordered for stable output
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, String[]> labels = new LinkedHashMap<>();

        for (List<ApplicationStatusEvent> events : byApp.values()) {
            events.sort(Comparator.comparing(ApplicationStatusEvent::occurredAt));
            for (int i = 1; i < events.size(); i++) {
                ApplicationStatusEvent prev = events.get(i - 1);
                ApplicationStatusEvent cur = events.get(i);
                String from = cur.fromStatus() != null ? cur.fromStatus().name()
                        : (prev.toStatus() != null ? prev.toStatus().name() : "UNKNOWN");
                String to = cur.toStatus().name();
                String key = from + "->" + to;
                long seconds = Math.max(0, Duration.between(prev.occurredAt(), cur.occurredAt()).getSeconds());
                long[] a = agg.computeIfAbsent(key, k -> new long[2]);
                a[0]++;
                a[1] += seconds;
                labels.putIfAbsent(key, new String[]{from, to});
            }
        }

        List<FunnelVelocity.Transition> transitions = new ArrayList<>();
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long[] a = e.getValue();
            String[] fl = labels.get(e.getKey());
            double avgDays = a[0] == 0 ? 0 : (a[1] / (double) a[0]) / 86_400.0;
            transitions.add(new FunnelVelocity.Transition(fl[0], fl[1], (int) a[0],
                    Math.round(avgDays * 10.0) / 10.0));
        }
        transitions.sort(Comparator.comparingInt(FunnelVelocity.Transition::count).reversed());
        return new FunnelVelocity(transitions);
    }
}
