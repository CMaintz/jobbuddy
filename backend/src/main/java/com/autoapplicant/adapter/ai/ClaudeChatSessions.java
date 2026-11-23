package com.autoapplicant.adapter.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The session files Claude Code keeps, one JSON object per line, at
 * {@code <config dir>/projects/<slug of the cwd>/<session id>.jsonl}. The file is found by its
 * session id rather than by recomputing the slug, whose rule is Claude Code's own. JobBuddy keeps
 * its own copy of what she sees, so these files only decide new-or-resume and are deleted with
 * the chat.
 */
final class ClaudeChatSessions {

    private static final String PROJECTS = "projects";
    private static final String EXTENSION = ".jsonl";

    private final Path configDir;

    ClaudeChatSessions(Path configDir) {
        this.configDir = configDir;
    }

    /** True once a turn of the session has started; Claude Code then refuses its id as new. */
    boolean exists(UUID sessionId) {
        return find(sessionId).isPresent();
    }

    /** Deletes the session file and the folder of tool output Claude Code may keep beside it. */
    void delete(UUID sessionId) {
        find(sessionId).ifPresent(file -> {
            CliAgentProcessFactory.deleteWorkDir(sibling(file, sessionId));
            CliAgentProcessFactory.deleteWorkDir(file);
        });
    }

    private Optional<Path> find(UUID sessionId) {
        Path projects = configDir.resolve(PROJECTS);
        if (!Files.isDirectory(projects)) {
            return Optional.empty();
        }
        try (Stream<Path> dirs = Files.list(projects)) {
            return dirs.map(dir -> dir.resolve(sessionId + EXTENSION))
                    .filter(Files::isRegularFile)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sibling(Path file, UUID sessionId) {
        return file.resolveSibling(sessionId.toString());
    }
}
