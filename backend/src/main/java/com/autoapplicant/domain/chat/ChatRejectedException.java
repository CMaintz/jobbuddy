package com.autoapplicant.domain.chat;

/** A turn was refused before it started, e.g. because the chat is already answering. */
public class ChatRejectedException extends RuntimeException {

    private final ChatFailure failure;

    public ChatRejectedException(ChatFailure failure) {
        super("Chat turn refused: " + failure);
        this.failure = failure;
    }

    public ChatFailure failure() {
        return failure;
    }
}
