package com.autoapplicant.adapter.cli;

import java.util.function.IntConsumer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A CLI command that does its work in {@link #run} and must then end the process.
 *
 * <p>Returning from {@code run} is not enough: the scheduler and executor pools hold non-daemon
 * threads, so the JVM stays up after the command is done. A scheduled job would then hang until
 * its timeout and show as cancelled rather than passed or failed.
 */
public interface OneShotCommand extends ApplicationRunner, ExitCodeGenerator {

    /** 0 unless the command overrides it; read once {@link #run} has returned. */
    @Override
    default int getExitCode() {
        return 0;
    }

    /**
     * Closes the context and hands the commands' exit code to {@code exit} when the context
     * holds a one-shot command; does nothing otherwise, so the web server keeps running.
     */
    static void exitWhenFinished(ConfigurableApplicationContext context, IntConsumer exit) {
        if (context.getBeansOfType(OneShotCommand.class).isEmpty()) {
            return;
        }
        exit.accept(SpringApplication.exit(context));
    }
}
