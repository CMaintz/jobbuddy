package com.autoapplicant.adapter.notification;

import com.autoapplicant.port.out.notification.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Logs notifications instead of sending them. Replace with an email/SMTP
 * adapter when notification delivery is needed.
 */
@Component
public class StubNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubNotificationAdapter.class);

    @Override
    public void send(UUID userId, String subject, String body) {
        log.info("[NOTIFICATION STUB] userId={} subject='{}' body='{}'", userId, subject,
                body.length() > 80 ? body.substring(0, 80) + "…" : body);
    }
}
