package com.autoapplicant.port.out.notification;

import java.util.UUID;

public interface NotificationPort {
    void send(UUID userId, String subject, String body);
}
