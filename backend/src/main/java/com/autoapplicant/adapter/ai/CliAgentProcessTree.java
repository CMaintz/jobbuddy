package com.autoapplicant.adapter.ai;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An agent process and every descendant seen while it ran. Descendants have to be recorded while
 * the agent lives: once it exits, Linux reparents its orphans and {@code descendants()} no longer
 * finds them.
 */
final class CliAgentProcessTree {

    private static final Logger log = LoggerFactory.getLogger(CliAgentProcessTree.class);

    /** How long a killed process tree gets to exit, so its files in the work dir are released. */
    private static final Duration KILL_WAIT = Duration.ofSeconds(5);

    private final Process root;
    private final Set<ProcessHandle> seen = ConcurrentHashMap.newKeySet();

    CliAgentProcessTree(Process root) {
        this.root = root;
    }

    /** Adds the descendants alive now; call it periodically while the agent runs. */
    void recordDescendants() {
        root.descendants().forEach(seen::add);
    }

    /**
     * Force-kills the process, the descendants recorded earlier and any still attached, then waits
     * for them to exit. {@code destroyForcibly} alone neither reaches grandchildren nor waits, and
     * on Windows a live child keeps the work dir locked so it cannot be deleted.
     */
    void killAll() throws InterruptedException {
        recordDescendants();
        seen.add(root.toHandle());
        seen.forEach(ProcessHandle::destroyForcibly);
        CompletableFuture<?>[] exits =
                seen.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(exits).get(KILL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("CLI agent process tree did not exit within {}s", KILL_WAIT.toSeconds());
        }
    }
}
