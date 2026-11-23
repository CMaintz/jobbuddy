package com.autoapplicant.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OneShotCommandTest {

    private final List<Integer> exits = new ArrayList<>();

    private static final class FailedCommand implements OneShotCommand {
        @Override
        public void run(ApplicationArguments args) { }

        @Override
        public int getExitCode() {
            return 1;
        }
    }

    @Test
    void aFinishedCommandEndsTheProcessWithItsOwnExitCode() {
        // Bug: closing the context and then exiting with a hard-coded 0 loses the failure
        // the command computed, so a broken crawl still shows green in CI.
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(FailedCommand.class);
        context.refresh();

        OneShotCommand.exitWhenFinished(context, exits::add);

        assertThat(exits).containsExactly(1);
        assertThat(context.isActive()).isFalse();
    }

    @Test
    void theServerIsNotStoppedWhenNoCommandRuns() {
        // Bug: exiting unconditionally after startup stops the HTTP server the moment it boots.
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.refresh();

        OneShotCommand.exitWhenFinished(context, exits::add);

        assertThat(exits).isEmpty();
        assertThat(context.isActive()).isTrue();
        context.close();
    }
}
