package com.autoapplicant.adapter.chatplan;

import com.autoapplicant.port.in.chat.RecoverChatPlansUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** At startup, records approved changes the last run was cut off in as interrupted. */
@Component
@Profile("desktop")
public class InterruptedPlanSweep {

    private static final Logger log = LoggerFactory.getLogger(InterruptedPlanSweep.class);

    private final RecoverChatPlansUseCase plans;

    public InterruptedPlanSweep(RecoverChatPlansUseCase plans) {
        this.plans = plans;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int interrupted = plans.recoverInterrupted();
        if (interrupted > 0) {
            log.info("Marked {} chat plan item(s) interrupted by the last shutdown", interrupted);
        }
    }
}
