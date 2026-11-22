package com.autoapplicant.port.in.chat;

/** Puts right what a stopped app left behind in the chat's plans. */
public interface RecoverChatPlansUseCase {

    /**
     * Records every item still running as interrupted. Only for startup: the app runs one
     * backend, so an item running then was cut off when the app last stopped, and would otherwise
     * show as running forever with no way to decide on it again.
     *
     * @return how many items were interrupted
     */
    int recoverInterrupted();
}
