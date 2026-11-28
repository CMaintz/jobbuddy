package com.autoapplicant.adapter.render;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the {@code typst compile} process for one workspace. Everything that makes the compile
 * hermetic is decided here: the root is the workspace, fonts are only the bundled ones, packages
 * resolve only from the bundled directories, the process gets no route to the network, and it
 * sees none of the backend's environment beyond what it needs to start.
 */
final class TypstCommand {

    /**
     * Typst downloads a missing {@code @preview} package even when both package directories are
     * set, and has no offline switch. Pointing every proxy variable at a closed loopback port
     * makes that download fail at once instead of reaching the internet.
     */
    static final String DEAD_PROXY = "http://127.0.0.1:1";

    private static final List<String> PROXY_VARIABLES =
            List.of("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy");
    /**
     * The only variables passed through, upper-cased. The backend's environment holds the
     * database password, API keys and the desktop token; a template must not be able to read them
     * even if a later Typst learns to read the environment. SystemRoot is what Windows needs to
     * load system DLLs, and the temp variables are where Typst may write scratch files.
     */
    private static final Set<String> PASSED_VARIABLES = Set.of("PATH", "SYSTEMROOT", "TEMP", "TMP", "TMPDIR");

    enum Format {
        PDF("pdf"),
        SVG("svg");

        final String flag;

        Format(String flag) {
            this.flag = flag;
        }
    }

    private final String typstPath;
    private final TypstAssets assets;
    private final int jobs;
    private final Map<String, String> parentEnv;

    TypstCommand(String typstPath, TypstAssets assets, int jobs, Map<String, String> parentEnv) {
        this.typstPath = typstPath;
        this.assets = assets;
        this.jobs = jobs;
        this.parentEnv = parentEnv;
    }

    ProcessBuilder build(TypstWorkspace workspace, Format format, Path output) {
        ProcessBuilder pb = new ProcessBuilder(List.of(
                typstPath, "compile",
                "--root", workspace.root().toString(),
                "--ignore-system-fonts",
                "--font-path", assets.fontDir().toString(),
                "--package-path", assets.packageDir().toString(),
                "--package-cache-path", assets.packageCacheDir().toString(),
                "--input", "data=" + TypstWorkspace.DATA_FILE,
                "--format", format.flag,
                "--jobs", Integer.toString(jobs),
                workspace.mainFile().toString(),
                output.toString()));
        pb.directory(workspace.root().toFile());
        pb.redirectOutput(Redirect.DISCARD);
        pb.redirectError(workspace.diagnosticsFile().toFile());
        Map<String, String> env = pb.environment();
        env.clear();
        env.putAll(childEnvironment(parentEnv));
        return pb;
    }

    /**
     * The allowlisted entries of {@code parent}, plus every proxy variable routed to the dead
     * proxy. Keys are matched case-insensitively and keep their spelling, because on Windows
     * {@code PATH} is usually {@code Path}.
     */
    private static Map<String, String> childEnvironment(Map<String, String> parent) {
        Map<String, String> child = new HashMap<>();
        parent.forEach((key, value) -> {
            if (PASSED_VARIABLES.contains(key.toUpperCase(Locale.ROOT))) {
                child.put(key, value);
            }
        });
        for (String variable : PROXY_VARIABLES) {
            child.put(variable, DEAD_PROXY);
        }
        return child;
    }
}
