package com.autoapplicant.adapter.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The environment a CLI agent is allowed to inherit. It is an allowlist because the backend's env
 * holds secrets under names nobody can enumerate up front (SPRING_APPLICATION_JSON or
 * JAVA_TOOL_OPTIONS can carry a DB password), and an agent reading posting text must not see them.
 * Names are matched case-insensitively since Windows env names are (so {@code https_proxy} passes
 * too). Which CLI's auth passes is keyed on the configured provider, not the executable, so an
 * override such as {@code npx codex exec} still gets Codex's.
 */
final class CliAgentEnvironment {

    /** What a CLI needs to start, find its config dir, use temp files and reach its API. */
    private static final Set<String> BASE_NAMES = Set.of(
            "PATH",
            "PATHEXT",
            "HOME",
            "USERPROFILE",
            "APPDATA",
            "LOCALAPPDATA",
            "SYSTEMROOT",
            "SYSTEMDRIVE",
            "WINDIR",
            "COMSPEC",
            "TEMP",
            "TMP",
            "TMPDIR",
            "XDG_RUNTIME_DIR",
            "DBUS_SESSION_BUS_ADDRESS",
            "LANG",
            "USER",
            "USERNAME",
            "HTTP_PROXY",
            "HTTPS_PROXY",
            "ALL_PROXY",
            "NO_PROXY",
            "NODE_EXTRA_CA_CERTS",
            "SSL_CERT_FILE",
            "SSL_CERT_DIR");

    private static final List<String> BASE_PREFIXES = List.of("LC_");

    /** Claude Code's API auth, gateway and model settings (ANTHROPIC_API_KEY, ...). */
    private static final List<String> CLAUDE_PREFIXES = List.of("ANTHROPIC_");

    /**
     * Claude Code's own config and auth, by name. Not a {@code CLAUDE_} prefix: a backend started
     * from a Claude Code session inherits that session's identity (CLAUDE_CODE_SESSION_ID,
     * CLAUDE_CODE_MESSAGING_*, ...), and the agent must not attach to it.
     */
    private static final Set<String> CLAUDE_NAMES = Set.of(
            "CLAUDE_CONFIG_DIR",
            "CLAUDE_CODE_OAUTH_TOKEN",
            "CLAUDE_CODE_USE_BEDROCK",
            "CLAUDE_CODE_USE_VERTEX",
            "CLAUDE_CODE_SKIP_BEDROCK_AUTH",
            "CLAUDE_CODE_SKIP_VERTEX_AUTH",
            "CLAUDE_CODE_CLIENT_CERT",
            "CLAUDE_CODE_CLIENT_KEY",
            "CLAUDE_CODE_CLIENT_KEY_PASSPHRASE",
            "CLAUDE_CODE_MAX_OUTPUT_TOKENS");

    private static final Set<String> CODEX_NAMES =
            Set.of("OPENAI_API_KEY", "CODEX_API_KEY", "CODEX_HOME");

    /** Claude Code switches that route it through a cloud provider's own credentials. */
    private static final Set<String> CLOUD_PROVIDER_FLAGS =
            Set.of("CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX");

    /** Bedrock/Vertex credentials; passed only when a cloud provider flag is on. */
    private static final List<String> CLOUD_PREFIXES = List.of("AWS_", "GOOGLE_", "CLOUD_ML_");

    private static final Set<String> DISABLED_FLAG_VALUES = Set.of("", "0", "false");

    private CliAgentEnvironment() {}

    /** Removes, in place, every variable in {@code env} the agent for {@code provider} doesn't need. */
    static void restrict(Map<String, String> env, String provider) {
        Predicate<String> allowed = allowedNames(env, provider);
        env.keySet().removeIf(name -> !allowed.test(name.toUpperCase(Locale.ROOT)));
    }

    private static Predicate<String> allowedNames(Map<String, String> env, String provider) {
        Predicate<String> allowed =
                name -> BASE_NAMES.contains(name) || hasPrefix(name, BASE_PREFIXES);
        if (CliAgentCommands.CODEX_PROVIDER.equalsIgnoreCase(provider)) {
            return allowed.or(CODEX_NAMES::contains);
        }
        allowed = allowed.or(name -> CLAUDE_NAMES.contains(name) || hasPrefix(name, CLAUDE_PREFIXES));
        if (usesCloudProvider(env)) {
            allowed = allowed.or(name -> hasPrefix(name, CLOUD_PREFIXES));
        }
        return allowed;
    }

    private static boolean usesCloudProvider(Map<String, String> env) {
        return env.entrySet().stream()
                .anyMatch(e -> CLOUD_PROVIDER_FLAGS.contains(e.getKey().toUpperCase(Locale.ROOT))
                        && isEnabled(e.getValue()));
    }

    private static boolean isEnabled(String flagValue) {
        return !DISABLED_FLAG_VALUES.contains(flagValue.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean hasPrefix(String name, List<String> prefixes) {
        return prefixes.stream().anyMatch(name::startsWith);
    }
}
