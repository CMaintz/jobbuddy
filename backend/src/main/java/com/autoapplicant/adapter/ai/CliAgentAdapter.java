package com.autoapplicant.adapter.ai;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.ai.AiCompletion;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
 */
public class CliAgentAdapter implements ChatProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CliAgentAdapter.class);

    private final AppProperties props;

    public CliAgentAdapter(AppProperties props) {
        this.props = props;
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
        AppProperties.Cli cfg = props.getAi().getCli();
        List<String> command = Arrays.stream(cfg.getCommand().trim().split("\\s+")).toList();
        String prompt = buildPrompt(composition, jsonObject);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process proc = pb.start();

            // Drain stdin/stderr on separate threads so a full pipe buffer can never deadlock
            // the stdout read (prompts with a CV + JD can exceed the OS pipe buffer).
            Thread stdinWriter = new Thread(() -> {
                try (OutputStream os = proc.getOutputStream()) {
                    os.write(prompt.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.debug("CLI agent stdin closed early: {}", e.getMessage());
                }
            });
            StringBuilder stderr = new StringBuilder();
            Thread stderrDrainer = new Thread(() -> {
                try {
                    stderr.append(new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.debug("CLI agent stderr drain ended: {}", e.getMessage());
                }
            });
            stdinWriter.start();
            stderrDrainer.start();

            String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean finished = proc.waitFor(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IllegalStateException(
                        "CLI agent '" + cfg.getCommand() + "' timed out after " + cfg.getTimeoutSeconds() + "s");
            }
            stdinWriter.join(2000);
            stderrDrainer.join(2000);

            if (proc.exitValue() != 0) {
                throw new IllegalStateException("CLI agent '" + cfg.getCommand() + "' exited "
                        + proc.exitValue() + ": " + stderr.toString().strip());
            }
            String out = stdout.strip();
            if (out.isEmpty()) {
                throw new IllegalStateException("CLI agent '" + cfg.getCommand() + "' returned empty output");
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted running CLI agent", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not launch CLI agent '" + cfg.getCommand()
                    + "' — is it installed and on PATH?", e);
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
