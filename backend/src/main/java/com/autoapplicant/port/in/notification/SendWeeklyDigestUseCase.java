package com.autoapplicant.port.in.notification;

public interface SendWeeklyDigestUseCase {
    /** Sends the weekly job digest to every opted-in user. Returns the number of digests sent. */
    int sendDigests();
}
