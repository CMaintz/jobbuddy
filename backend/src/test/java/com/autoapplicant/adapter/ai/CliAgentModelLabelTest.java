package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.config.AppProperties;
import org.junit.jupiter.api.Test;

/** The usage log and saved-document provenance record the model the CLI was told to run. */
class CliAgentModelLabelTest {

    private static String labelFor(String provider, String model, String explicitLabel) {
        AppProperties props = new AppProperties();
        props.getAi().getCli().setModel(model);
        props.getAi().getCli().setModelLabel(explicitLabel);
        return new CliAgentAdapter(props, provider).chatModelName();
    }

    @Test
    void theDefaultClaudeRunIsLabelledWithItsPinnedModel() {
        assertThat(labelFor("claude-cli", "", "")).isEqualTo("sonnet");
    }

    @Test
    void aConfiguredModelIsWhatTheLogRecords() {
        assertThat(labelFor("claude-cli", "opus", "")).isEqualTo("opus");
    }

    @Test
    void anExplicitLabelWins() {
        assertThat(labelFor("claude-cli", "opus", "team-claude")).isEqualTo("team-claude");
    }

    @Test
    void aRunWithNoPinnedModelKeepsTheGenericLabel() {
        assertThat(labelFor("codex", "", "")).isEqualTo("cli-agent");
    }
}
