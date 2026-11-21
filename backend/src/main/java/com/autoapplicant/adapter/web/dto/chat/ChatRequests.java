package com.autoapplicant.adapter.web.dto.chat;

/** Request bodies of the chat endpoints. The use cases check them: blank or overlong is a 400. */
public final class ChatRequests {

    private ChatRequests() {}

    public record Rename(String title) {}

    public record Message(String text) {}
}
