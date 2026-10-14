package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.document.PromptComposition;
import org.junit.jupiter.api.Test;

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
}
