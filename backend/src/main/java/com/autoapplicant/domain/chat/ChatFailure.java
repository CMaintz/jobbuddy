package com.autoapplicant.domain.chat;

/** Why a chat turn did not produce an answer. The UI turns each into its own message. */
public enum ChatFailure {
    /** {@code claude} could not be started: not installed, or not on PATH. */
    CLAUDE_MISSING,
    /** Claude Code runs but has no login. */
    NOT_LOGGED_IN,
    /** The subscription's usage or rate limit was reached. */
    USAGE_LIMIT,
    /** The turn ran past its time limit and was stopped. */
    TIMEOUT,
    /** This chat is already answering. */
    CHAT_BUSY,
    /** Too many chats are answering at once. */
    TOO_MANY_CHATS,
    /** Anything else; the details are in the server log. */
    FAILED
}
