package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Runs {@link FakeCliAgent} as a real child process; each JVM start costs about a second. */
class CliAgentProcessRunnerTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void returnsStdoutForThePromptOnStdin() throws Exception {
        CliAgentProcessRunner.Result result = CliAgentProcessRunner.run(
                agent(FakeCliAgent.ECHO), "hello", GENEROUS_TIMEOUT);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("answer:hello");
    }

    @Test
    void reportsANonZeroExitWithStderr() throws Exception {
        CliAgentProcessRunner.Result result = CliAgentProcessRunner.run(
                agent(FakeCliAgent.FAIL), "hello", GENEROUS_TIMEOUT);

        assertThat(result.exitCode()).isEqualTo(FakeCliAgent.FAIL_EXIT_CODE);
        assertThat(result.stderr()).isEqualTo("bad prompt");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void timeoutFiresWhileTheAgentHoldsStdoutOpen() {
        ProcessBuilder pb = agent(FakeCliAgent.HANG);

        assertThatThrownBy(() -> CliAgentProcessRunner.run(pb, "hello", SHORT_TIMEOUT))
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void timeoutKillsTheAgentsChildrenAndReleasesTheWorkDir() throws IOException {
        Path workDir = CliAgentProcessFactory.createWorkDir();
        Path pidFile = workDir.resolve("child.pid");
        ProcessBuilder pb = agent(FakeCliAgent.SPAWN_AND_HANG, pidFile.toString());
        pb.directory(workDir.toFile());

        assertThatThrownBy(() -> CliAgentProcessRunner.run(pb, "hello", SHORT_TIMEOUT))
                .isInstanceOf(TimeoutException.class);

        long childPid = Long.parseLong(Files.readString(pidFile));
        assertThat(ProcessHandle.of(childPid).filter(ProcessHandle::isAlive)).isEmpty();
        CliAgentProcessFactory.deleteWorkDir(workDir);
        assertThat(workDir).doesNotExist();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aChildLeftHoldingStdoutIsKilledAndTheAnswerStillReturned() throws Exception {
        Path pidFile = Files.createTempFile("cli-agent-child", ".pid");
        ProcessBuilder pb = agent(FakeCliAgent.ANSWER_AND_ORPHAN, pidFile.toString());

        CliAgentProcessRunner.Result result = CliAgentProcessRunner.run(pb, "", GENEROUS_TIMEOUT);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo(FakeCliAgent.ANSWER);
        long childPid = Long.parseLong(Files.readString(pidFile));
        assertThat(ProcessHandle.of(childPid).filter(ProcessHandle::isAlive)).isEmpty();
        Files.delete(pidFile);
    }

    @Test
    void aProgramThatCannotStartIsALaunchFailure() {
        ProcessBuilder pb = new ProcessBuilder("no-such-agent-cli-for-tests");

        assertThatThrownBy(() -> CliAgentProcessRunner.run(pb, "", GENEROUS_TIMEOUT))
                .isInstanceOf(CliAgentProcessRunner.LaunchException.class);
    }

    private static ProcessBuilder agent(String... args) {
        return new ProcessBuilder(FakeCliAgent.command(args));
    }
}
