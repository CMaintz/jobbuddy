package com.autoapplicant.adapter.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The environment a CLI agent is allowed to inherit. It is an allowlist because the backend's env
 * holds secrets under names nobody can enumerate up front (SPRING_APPLICATION_JSON or
 * JAVA_TOOL_OPTIONS can carry a DB password), and an agent reading posting text must not see them.
 * Names are matched case-insensitively since Windows env names are. Which CLI's auth passes is
 * keyed on the command's executable name, so a wrapper such as {@code npx codex} gets Claude's.
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
            "LANG",
            "USER",
            "USERNAME",
            "HTTP_PROXY",
            "HTTPS_PROXY",
            "NO_PROXY",
            "NODE_EXTRA_CA_CERTS");

    private static final List<String> BASE_PREFIXES = List.of("LC_");

    /** Claude Code's own auth and config (ANTHROPIC_API_KEY, CLAUDE_CODE_OAUTH_TOKEN, ...). */
    private static final List<String> CLAUDE_PREFIXES = List.of("ANTHROPIC_", "CLAUDE_");

    private static final Set<String> CODEX_NAMES =
            Set.of("OPENAI_API_KEY", "CODEX_API_KEY", "CODEX_HOME");

    /** Claude Code switches that route it through a cloud provider's own credentials. */
    private static final Set<String> CLOUD_PROVIDER_FLAGS =
            Set.of("CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX");

    /** Bedrock/Vertex credentials; passed only when a cloud provider flag is on. */
    private static final List<String> CLOUD_PREFIXES = List.of("AWS_", "GOOGLE_", "CLOUD_ML_");

    private static final Set<String> DISABLED_FLAG_VALUES = Set.of("", "0", "false");

    private static final String CODEX_EXECUTABLE = "codex";

    private static final Pattern WINDOWS_LAUNCHER_SUFFIX =
            Pattern.compile("\\.(exe|cmd|bat|ps1)$");

    private CliAgentEnvironment() {}

    /** Removes, in place, every variable in {@code env} that {@code executable} doesn't need. */
    static void restrict(Map<String, String> env, String executable) {
        Predicate<String> allowed = allowedNames(env, executable);
        env.keySet().removeIf(name -> !allowed.test(name.toUpperCase(Locale.ROOT)));
    }

    private static Predicate<String> allowedNames(Map<String, String> env, String executable) {
        Predicate<String> allowed =
                name -> BASE_NAMES.contains(name) || hasPrefix(name, BASE_PREFIXES);
        if (isCodex(executable)) {
            return allowed.or(CODEX_NAMES::contains);
        }
        allowed = allowed.or(name -> hasPrefix(name, CLAUDE_PREFIXES));
        if (usesCloudProvider(env)) {
            allowed = allowed.or(name -> hasPrefix(name, CLOUD_PREFIXES));
        }
        return allowed;
    }

    private static boolean isCodex(String executable) {
        String lower = executable.toLowerCase(Locale.ROOT);
        String name = lower.substring(Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\')) + 1);
        return WINDOWS_LAUNCHER_SUFFIX.matcher(name).replaceFirst("").equals(CODEX_EXECUTABLE);
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
