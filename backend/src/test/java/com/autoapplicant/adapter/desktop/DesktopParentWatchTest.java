package com.autoapplicant.adapter.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DesktopParentWatchTest {

    @Test
    void stops_the_backend_once_the_shell_is_gone() throws InterruptedException {
        // The shell's end of stdin closes when its process ends, however it ends: a crash or a
        // Task Manager kill included. EOF is that signal.
        CountDownLatch stopped = new CountDownLatch(1);

        new DesktopParentWatch(new ByteArrayInputStream(new byte[0]), stopped::countDown).watch();

        assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void keeps_running_while_the_shell_holds_stdin_open() throws IOException, InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);
        try (PipedOutputStream shell = new PipedOutputStream();
                PipedInputStream stdin = new PipedInputStream(shell)) {

            new DesktopParentWatch(stdin, stopped::countDown).watch();

            assertThat(stopped.await(300, TimeUnit.MILLISECONDS)).isFalse();
        }
    }
}
