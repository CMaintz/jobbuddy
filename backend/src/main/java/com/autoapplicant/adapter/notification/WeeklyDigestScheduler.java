package com.autoapplicant.adapter.notification;

import com.autoapplicant.port.in.notification.SendWeeklyDigestUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklyDigestScheduler {

    private final SendWeeklyDigestUseCase digestUseCase;

    public WeeklyDigestScheduler(SendWeeklyDigestUseCase digestUseCase) {
        this.digestUseCase = digestUseCase;
    }

    @Scheduled(cron = "${app.digest.cron:0 0 8 * * MON}")
    public void run() {
        digestUseCase.sendDigests();
    }
}
