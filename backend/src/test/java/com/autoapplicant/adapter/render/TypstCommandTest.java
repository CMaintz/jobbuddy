package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The compile command is hermetic no matter what the server's own environment says. */
class TypstCommandTest {

    private static final Set<String> CHILD_VARIABLES = Set.of("PATH", "SYSTEMROOT", "TEMP", "TMP", "TMPDIR",
            "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY");

    /**
     * Bug caught: the child inherits the backend's environment, so {@code DB_PASS} or the desktop
     * token reaches a template, an inherited {@code NO_PROXY=*} lets package downloads bypass the
     * dead proxy, or an inherited {@code TYPST_ROOT} widens what the compile can read.
     */
    @Test
    void childGetsOnlyAllowlistedVariablesAndTheDeadProxy() throws IOException {
        Map<String, String> parent = Map.of(
                "DB_PASS", "hunter2",
                "JOBBUDDY_DESKTOP_TOKEN", "desktop-secret",
                "TYPST_ROOT", "/",
                "NO_PROXY", "*",
                "https_proxy", "http://corp-proxy:3128",
                "Path", "/usr/bin",
                "TEMP", "/tmp");

        Map<String, String> child = build(parent).environment();

        assertThat(child.values()).doesNotContain("hunter2", "desktop-secret", "*", "http://corp-proxy:3128");
        assertThat(child.keySet()).allSatisfy(key ->
                assertThat(CHILD_VARIABLES).contains(key.toUpperCase(Locale.ROOT)));
        assertThat(child).containsEntry("Path", "/usr/bin")
                .containsEntry("TEMP", "/tmp")
                .containsEntry("HTTPS_PROXY", TypstCommand.DEAD_PROXY)
                .containsEntry("ALL_PROXY", TypstCommand.DEAD_PROXY);
    }

    /**
     * Bug caught: a dropped flag lets the compile see system fonts or a shared package cache, or
     * use every core of the host.
     */
    @Test
    void commandPinsRootFontsPackagesAndJobs() throws IOException {
        TypstAssets assets = TypstFixtures.assets();
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            List<String> command = new TypstCommand("typst", assets, 3, Map.of())
                    .build(workspace, Format.PDF, workspace.outputDir().resolve("x.pdf"))
                    .command();

            assertThat(command).containsSubsequence("--root", workspace.root().toString())
                    .contains("--ignore-system-fonts")
                    .containsSubsequence("--font-path", assets.fontDir().toString())
                    .containsSubsequence("--package-path", assets.packageDir().toString())
                    .containsSubsequence("--package-cache-path", assets.packageCacheDir().toString())
                    .containsSubsequence("--jobs", "3");
        }
    }

    private static ProcessBuilder build(Map<String, String> parent) throws IOException {
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            return new TypstCommand("typst", TypstFixtures.assets(), 1, parent)
                    .build(workspace, Format.PDF, workspace.outputDir().resolve("x.pdf"));
        }
    }
}
