package com.autoapplicant.adapter.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * The files the chat's {@code claude} reads from its working directory. They are passed by path
 * rather than on the command line: Windows hands a {@code .cmd} program's arguments to cmd.exe,
 * which would mangle JSON and prose (see {@link CliAgentExecutable}).
 */
final class ClaudeChatFiles {

    /** The variable the MCP config names instead of the token itself. */
    static final String MCP_TOKEN_VARIABLE = "JOBBUDDY_MCP_TOKEN";
    static final String MCP_SERVER_NAME = "jobbuddy";

    private static final String SYSTEM_PROMPT_RESOURCE = "/chat/system-prompt.md";
    private static final String SYSTEM_PROMPT_FILE = "system-prompt.md";
    private static final String MCP_CONFIG_FILE = "mcp.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dir;

    ClaudeChatFiles(Path dir) {
        this.dir = dir;
    }

    Path systemPrompt() {
        return write(SYSTEM_PROMPT_FILE, resource());
    }

    /**
     * An MCP config naming JobBuddy's server at {@code url}. The token is left as a variable that
     * Claude Code expands from its own environment, so it is never written to disk, never on the
     * command line, and never in what the model reads.
     */
    Path mcpConfig(String url) {
        Map<String, Object> server = Map.of("type", "http", "url", url,
                "headers", Map.of("Authorization", "Bearer ${" + MCP_TOKEN_VARIABLE + "}"));
        try {
            return write(MCP_CONFIG_FILE, JSON.writeValueAsString(
                    Map.of("mcpServers", Map.of(MCP_SERVER_NAME, server)))
                    .getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Only rewritten when it changed: another chat's {@code claude} may be reading it. */
    private Path write(String name, byte[] content) {
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), content)) {
                Files.write(file, content);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] resource() {
        try (InputStream prompt = ClaudeChatFiles.class.getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
            if (prompt == null) {
                throw new IllegalStateException("Missing resource " + SYSTEM_PROMPT_RESOURCE);
            }
            return prompt.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
