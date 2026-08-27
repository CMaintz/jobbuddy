package com.autoapplicant.port.in.notification;

public interface SendDueRemindersUseCase {

    /**
     * Emails every opted-in user whatever they have due today. Returns how many were sent.
     *
     * <p>Sends nothing to a user with nothing due — a daily email that is usually empty trains
     * people to ignore it, which costs the one day it mattered.
     */
    int sendDueReminders();
}
