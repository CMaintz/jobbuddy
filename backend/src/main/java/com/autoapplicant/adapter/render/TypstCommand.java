package com.autoapplicant.adapter.render;

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the {@code typst compile} process for one workspace. Everything that makes the compile
 * hermetic is decided here: the root is the workspace, fonts are only the bundled ones, packages
 * resolve only from the bundled directories, and the process gets no route to the network.
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
    /** Typst reads its root, font and package settings from these too; ours must win. */
    private static final String TYPST_ENV_PREFIX = "TYPST_";
    private static final String PROXY_ENV_SUFFIX = "_PROXY";

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

    TypstCommand(String typstPath, TypstAssets assets) {
        this.typstPath = typstPath;
        this.assets = assets;
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
                workspace.mainFile().toString(),
                output.toString()));
        pb.directory(workspace.root().toFile());
        pb.redirectOutput(Redirect.DISCARD);
        pb.redirectError(workspace.diagnosticsFile().toFile());
        isolate(pb.environment(), DEAD_PROXY);
        return pb;
    }

    /**
     * Removes inherited Typst settings and proxy configuration, then routes every proxy to
     * {@code proxy}. {@code NO_PROXY} goes too, since {@code NO_PROXY=*} would bypass it. Keys are
     * matched case-insensitively: on Windows the map is, on Linux the lower-case proxy variables
     * are read too.
     */
    static void isolate(Map<String, String> env, String proxy) {
        env.keySet().removeIf(TypstCommand::isOverridable);
        for (String variable : PROXY_VARIABLES) {
            env.put(variable, proxy);
        }
    }

    private static boolean isOverridable(String key) {
        String upper = key.toUpperCase(Locale.ROOT);
        return upper.startsWith(TYPST_ENV_PREFIX) || upper.endsWith(PROXY_ENV_SUFFIX);
    }
}
