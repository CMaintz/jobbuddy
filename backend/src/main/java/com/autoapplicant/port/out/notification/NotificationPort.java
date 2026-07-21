package com.autoapplicant.port.out.notification;

public interface NotificationPort {
    void send(String recipientEmail, String subject, String body);
}
