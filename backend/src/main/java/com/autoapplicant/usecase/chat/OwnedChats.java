package com.autoapplicant.usecase.chat;

import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import java.util.UUID;

/** Looks a chat up for its owner; another user's chat is reported as missing. */
final class OwnedChats {

    private OwnedChats() {}

    static Chat require(ChatRepositoryPort chats, UUID userId, UUID chatId) {
        return chats.findById(chatId)
                .filter(chat -> chat.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Chat not found"));
    }
}
