package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Runs {@link FakeCliAgent} as a real child process. */
class CliAgentTimeoutTest {

    private static final int SHARED_TIMEOUT_SECONDS = 30;

    @Test
    void anAdapterGivenItsOwnTimeoutUsesItInsteadOfTheSharedOne() {
        // Bug: the enrichment adapter keeps the shared app.ai.cli.timeout-seconds (120 s, sized
        // for one generation call), so a batch of 25 postings is killed before it can answer.
        AppProperties props = new AppProperties();
        props.getAi().getCli().setTimeoutSeconds(SHARED_TIMEOUT_SECONDS);
        props.getAi().getCli().setCommand(String.join(" ", FakeCliAgent.command(FakeCliAgent.HANG)));
        CliAgentAdapter adapter = new CliAgentAdapter(props, "cli", Duration.ofSeconds(1));

        assertThatThrownBy(() -> adapter.complete(
                new PromptComposition("sys", "tpl", "", "", "", "", "final"), false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out after 1s");
    }
}
