package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generation provider that shells out to a locally-installed AI coding CLI
 * (Claude Code {@code claude -p}, Codex {@code codex exec}, etc.) instead of a paid API.
 * The prompt is piped to the process's stdin and the answer read from stdout, so text
 * generation runs on a flat-fee subscription — the cheap path for personal use.
 *
 * <p>Only {@code generate}/{@code generateJson} are supported. CLI agents produce text,
 * not embedding vectors, so {@link #embed} throws — the enrichment/embeddings provider
 * ({@code app.ai.enrichment-provider}) must stay a real API (OpenAI/Gemini). Because each
 * call spawns a process (seconds of latency), this is meant for on-demand generation, not
 * high-throughput bulk enrichment.
 *
 * <p>Prompts carry crawled job-posting text, so the agent is treated as untrusted: see
 * {@link CliAgentProcessFactory} for the sandbox (tools off, allowlisted env, empty cwd).
 */
public class CliAgentAdapter implements ChatProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CliAgentAdapter.class);

    private final AppProperties props;
    private final String provider;
    private final String command;

    /**
     * {@code provider} picks the default command line when {@code app.ai.cli.command} is blank,
     * and which CLI's auth variables the agent inherits.
     */
    public CliAgentAdapter(AppProperties props, String provider) {
        this.props = props;
        this.provider = provider;
        this.command = CliAgentCommands.resolve(provider, props.getAi().getCli().getCommand());
    }

    /**
     * A CLI agent bills a flat subscription and reports no token counts, so the
     * completion carries zeros — the usage log records the request, not a cost.
     */
    @Override
    public AiCompletion complete(PromptComposition composition, boolean jsonObject, String operation) {
        return AiCompletion.untracked(run(composition, jsonObject), chatModelName());
    }

    private String run(PromptComposition composition, boolean jsonObject) {
        int timeoutSeconds = props.getAi().getCli().getTimeoutSeconds();
        String prompt = buildPrompt(composition, jsonObject);
        Path workDir = newWorkDir();
        try {
            ProcessBuilder pb = CliAgentProcessFactory.create(command, workDir, provider);
            return requireAnswer(
                    CliAgentProcessRunner.run(pb, prompt, Duration.ofSeconds(timeoutSeconds)));
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "CLI agent '" + command + "' timed out after " + timeoutSeconds + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted running CLI agent", e);
        } catch (CliAgentProcessRunner.LaunchException e) {
            throw new IllegalStateException("Could not launch CLI agent '" + command
                    + "' - is it installed and on PATH?", e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read the output of CLI agent '" + command + "'", e);
        } finally {
            removeWorkDir(workDir);
        }
    }

    private static Path newWorkDir() {
        try {
            return CliAgentProcessFactory.createWorkDir();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create a work dir for the CLI agent", e);
        }
    }

    private String requireAnswer(CliAgentProcessRunner.Result result) {
        if (result.exitCode() != 0) {
            throw new IllegalStateException("CLI agent '" + command + "' exited "
                    + result.exitCode() + ": " + result.stderr().strip());
        }
        String out = result.stdout().strip();
        if (out.isEmpty()) {
            throw new IllegalStateException("CLI agent '" + command + "' returned empty output");
        }
        return out;
    }

    private static void removeWorkDir(Path workDir) {
        try {
            CliAgentProcessFactory.deleteWorkDir(workDir);
        } catch (UncheckedIOException e) {
            log.warn("Could not delete CLI agent work dir {}: {}", workDir, e.getMessage());
        }
    }

    /** Combines system + user prompt into one stdin payload; JSON callers get an explicit shape instruction. */
    private String buildPrompt(PromptComposition composition, boolean jsonObject) {
        StringBuilder sb = new StringBuilder();
        if (composition.systemPrompt() != null && !composition.systemPrompt().isBlank()) {
            sb.append(composition.systemPrompt()).append("\n\n");
        }
        sb.append(composition.resolvedFinalPrompt());
        if (jsonObject) {
            sb.append("\n\nIMPORTANT: Respond with ONLY valid JSON — no prose, no explanation, "
                    + "no markdown code fences.");
        }
        return sb.toString();
    }

    @Override
    public String chatModelName() {
        return props.getAi().getCli().getModelLabel();
    }
}
