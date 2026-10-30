package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Drives the adapter against {@link FakeCliAgent} run as a real child JVM. */
class CliAgentAdapterTest {

    private static final PromptComposition PROMPT =
            new PromptComposition("sys", "tpl", "", "", "", "", "final");

    @Test
    void aMissingProgramIsReportedAsALaunchFailure() {
        AppProperties props = new AppProperties();
        props.getAi().getCli().setCommand("no-such-agent-cli-for-tests -p");
        CliAgentAdapter adapter = new CliAgentAdapter(props, "cli");

        assertThatThrownBy(() -> adapter.complete(PROMPT, false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not launch")
                .hasMessageContaining("on PATH");
    }

    @Test
    void theAgentGetsSystemThenFinalPromptOnStdinAndItsAnswerIsReturned() {
        assertThat(adapter(FakeCliAgent.ECHO, 30).complete(PROMPT, false, "test").text())
                .isEqualTo("answer:sys\n\nfinal");
    }

    @Test
    void aNonZeroExitFailsWithTheAgentsStderr() {
        CliAgentAdapter agent = adapter(FakeCliAgent.FAIL, 30);

        assertThatThrownBy(() -> agent.complete(PROMPT, false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exited 3")
                .hasMessageContaining("bad prompt");
    }

    @Test
    void whitespaceOnlyOutputIsAFailureNotAnAnswer() {
        CliAgentAdapter agent = adapter(FakeCliAgent.BLANK, 30);

        assertThatThrownBy(() -> agent.complete(PROMPT, false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned empty output");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aHungAgentTimesOutAtTheConfiguredLimit() {
        CliAgentAdapter agent = adapter(FakeCliAgent.HANG, 2);

        assertThatThrownBy(() -> agent.complete(PROMPT, false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out after 2s");
    }

    /**
     * The adapter takes its command as one whitespace-split string, so a JDK or build directory with
     * a space in its path cannot be expressed; such a machine skips rather than fails.
     */
    private static CliAgentAdapter adapter(String mode, int timeoutSeconds) {
        List<String> argv = FakeCliAgent.command(mode);
        assumeTrue(argv.stream().noneMatch(arg -> arg.chars().anyMatch(Character::isWhitespace)),
                "a path in the fake agent's command line contains whitespace");
        AppProperties props = new AppProperties();
        props.getAi().getCli().setCommand(String.join(" ", argv));
        props.getAi().getCli().setTimeoutSeconds(timeoutSeconds);
        return new CliAgentAdapter(props, "cli");
    }
}
