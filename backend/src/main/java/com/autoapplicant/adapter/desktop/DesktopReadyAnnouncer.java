package com.autoapplicant.adapter.desktop;

import com.autoapplicant.adapter.ai.EmbeddingKeyGuard;
import com.autoapplicant.adapter.ai.EmbeddingKeyGuard.MissingKey;
import java.io.PrintStream;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells the desktop shell, on stdout, which port the server bound once it takes requests, or why
 * it cannot.
 *
 * <p>The shell starts the backend on port 0 and connects only to the port announced here. It
 * reads it from the child's own stdout, which no other process can write to, so a process that
 * grabbed a port first can never receive the shell's token or the user's data. Both line formats
 * are matched by desktop/src/backend-process.js.
 *
 * <p>Checking the embeddings key here also settles the stored embedding choice at startup, which
 * is where the desktop app writes her setup choice for the crawl to follow.
 */
@Component
@Profile("desktop")
public class DesktopReadyAnnouncer {

    static final String READY_PREFIX = "JOBBUDDY_DESKTOP_READY port=";
    static final String MISSING_KEY_PREFIX = "JOBBUDDY_DESKTOP_ERROR embedding-key-missing provider=";
    private static final int FAILED = 1;

    private final PrintStream out;
    private final Supplier<Optional<MissingKey>> missingKey;
    private final IntConsumer exit;

    @Autowired
    public DesktopReadyAnnouncer(EmbeddingKeyGuard guard, ConfigurableApplicationContext context) {
        this(System.out, guard::missingKey, code -> System.exit(SpringApplication.exit(context, () -> code)));
    }

    DesktopReadyAnnouncer(PrintStream out, Supplier<Optional<MissingKey>> missingKey, IntConsumer exit) {
        this.out = out;
        this.missingKey = missingKey;
        this.exit = exit;
    }

    /** After startup work (the local user included) is done, so the UI can load straight away. */
    @EventListener
    public void announce(ApplicationReadyEvent event) {
        // No web server (a MockMvc test context) means nothing to connect to, so nothing to say.
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext web)) {
            return;
        }
        Optional<MissingKey> missing = missingKey.get();
        // Straight to stdout rather than through logback: a log pattern would wrap the line, and
        // the shell matches it exactly.
        if (missing.isPresent()) {
            out.println(MISSING_KEY_PREFIX + missing.get().provider());
            out.flush();
            exit.accept(FAILED);
            return;
        }
        out.println(READY_PREFIX + web.getWebServer().getPort());
        out.flush();
    }
}
