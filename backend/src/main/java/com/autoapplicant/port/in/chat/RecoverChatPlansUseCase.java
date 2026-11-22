package com.autoapplicant.port.in.chat;

import java.time.Instant;

/** Puts right what a stopped app left behind in the chat's plans. */
public interface RecoverChatPlansUseCase {

    /**
     * Records every item that went running before {@code startedAt} and is still running as
     * interrupted: the app runs one backend, so it was cut off when the app last stopped, and
     * would otherwise show as running forever with no way to decide on it again. An item this
     * process approved itself, even before the sweep ran, is left alone.
     *
     * @param startedAt when this backend started, before it took any request
     * @return how many items were interrupted
     */
    int recoverInterrupted(Instant startedAt);
}
