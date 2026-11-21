package com.autoapplicant.adapter.desktop;

import java.io.PrintStream;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells the desktop shell, on stdout, which port the server bound once it takes requests.
 *
 * <p>The shell starts the backend on port 0 and connects only to the port announced here. It
 * reads it from the child's own stdout, which no other process can write to, so a process that
 * grabbed a port first can never receive the shell's token or the user's data. The line format
 * is matched by desktop/src/backend-process.js.
 */
@Component
@Profile("desktop")
public class DesktopReadyAnnouncer {

    static final String READY_PREFIX = "JOBBUDDY_DESKTOP_READY port=";

    private final PrintStream out;

    public DesktopReadyAnnouncer() {
        this(System.out);
    }

    DesktopReadyAnnouncer(PrintStream out) {
        this.out = out;
    }

    /** After startup work (the local user included) is done, so the UI can load straight away. */
    @EventListener
    public void announce(ApplicationReadyEvent event) {
        // No web server (a MockMvc test context) means nothing to connect to, so nothing to say.
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext web)) {
            return;
        }
        // Straight to stdout rather than through logback: a log pattern would wrap the line, and
        // the shell matches it exactly.
        out.println(READY_PREFIX + web.getWebServer().getPort());
        out.flush();
    }
}
