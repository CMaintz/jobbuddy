package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.port.in.chat.RecoverChatPlansUseCase;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * At startup, records approved changes the last run was cut off in as interrupted. The cutoff is
 * when this bean was made, which is before the server takes requests, so an approval that
 * reaches this run before the sweep does is never mistaken for a leftover.
 */
@Component
@Profile("desktop")
public class InterruptedPlanSweep {

    private static final Logger log = LoggerFactory.getLogger(InterruptedPlanSweep.class);

    private final RecoverChatPlansUseCase plans;
    private final Instant startedAt;

    public InterruptedPlanSweep(RecoverChatPlansUseCase plans, Clock clock) {
        this.plans = plans;
        this.startedAt = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int interrupted = plans.recoverInterrupted(startedAt);
        if (interrupted > 0) {
            log.info("Marked {} chat plan item(s) interrupted by the last shutdown", interrupted);
        }
    }
}
