package com.autoapplicant.adapter.notification;

import com.autoapplicant.port.out.notification.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP delivery via Spring Mail. Only active when {@code spring.mail.host} is
 * configured; without it the {@link StubNotificationAdapter} logs instead.
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.mail.host")
public class MailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailNotificationAdapter(JavaMailSender mailSender,
                                   @Value("${app.mail.from:no-reply@jobbuddy.dk}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Mail delivery to {} failed: {}", recipientEmail, e.getMessage());
        }
    }
}
