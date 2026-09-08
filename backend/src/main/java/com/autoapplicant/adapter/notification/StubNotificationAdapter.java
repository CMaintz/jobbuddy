package com.autoapplicant.adapter.notification;

import com.autoapplicant.port.out.notification.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs notifications instead of sending them. Active as the fallback when no
 * SMTP host is configured; {@link MailNotificationAdapter} takes precedence
 * once {@code spring.mail.host} is set.
 */
@Component
public class StubNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(StubNotificationAdapter.class);

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("[NOTIFICATION STUB] to={} subject='{}' body='{}'", recipientEmail, subject,
                body.length() > 80 ? body.substring(0, 80) + "…" : body);
    }
}
