package com.autoapplicant.adapter.desktop;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Stops the desktop backend when the shell that started it is gone.
 *
 * <p>The shell keeps the write end of this process's stdin open and never writes to it. Windows
 * closes that pipe when the shell's process ends, however it ends, so EOF here means nobody is
 * left to talk to. Without this, a crashed or force-killed shell leaves a JVM running its sweeps,
 * and the next launch starts a second one against the same database.
 */
@Component
@Profile("desktop")
public class DesktopParentWatch {

    private static final Logger log = LoggerFactory.getLogger(DesktopParentWatch.class);

    private final InputStream stdin;
    private final Runnable stop;

    @Autowired
    public DesktopParentWatch(ConfigurableApplicationContext context) {
        this(System.in, () -> System.exit(SpringApplication.exit(context)));
    }

    DesktopParentWatch(InputStream stdin, Runnable stop) {
        this.stdin = stdin;
        this.stop = stop;
    }

    /**
     * Only with a real server: a test context (MockMvc, no server) has no shell, and a test
     * runner's stdin may already be at EOF.
     */
    @EventListener
    public void start(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext) {
            watch();
        }
    }

    void watch() {
        Thread watcher = new Thread(this::waitForEof, "desktop-parent-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void waitForEof() {
        byte[] buffer = new byte[256];
        try {
            while (stdin.read(buffer) >= 0) {
                // The shell never writes; anything read is ignored.
            }
            log.info("The desktop shell is gone (stdin closed); stopping the backend");
        } catch (IOException e) {
            // A broken pipe is how Windows reports the shell's end of stdin vanishing mid-read:
            // the same event as EOF, so it stops the backend the same way.
            log.info("The desktop shell is gone (stdin broke: {}); stopping the backend", e.getMessage());
        }
        stop.run();
    }
}
