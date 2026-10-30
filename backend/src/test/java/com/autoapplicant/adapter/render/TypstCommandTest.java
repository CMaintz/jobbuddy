package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The compile command is hermetic no matter what the server's own environment says. */
class TypstCommandTest {

    /**
     * Bug caught: an inherited {@code NO_PROXY=*} or a lower-case proxy variable lets package
     * downloads bypass the dead proxy, or an inherited {@code TYPST_ROOT}/{@code TYPST_FONT_PATHS}
     * widens what the compile can read.
     */
    @Test
    void inheritedTypstAndProxySettingsAreReplaced() {
        Map<String, String> env = new HashMap<>(Map.of(
                "TYPST_ROOT", "/",
                "TYPST_FONT_PATHS", "/usr/share/fonts",
                "NO_PROXY", "*",
                "no_proxy", "*",
                "https_proxy", "http://corp-proxy:3128",
                "PATH", "/usr/bin"));

        TypstCommand.isolate(env, TypstCommand.DEAD_PROXY);

        assertThat(env).doesNotContainKeys("TYPST_ROOT", "TYPST_FONT_PATHS", "NO_PROXY", "no_proxy");
        assertThat(env).containsEntry("https_proxy", TypstCommand.DEAD_PROXY)
                .containsEntry("HTTPS_PROXY", TypstCommand.DEAD_PROXY)
                .containsEntry("ALL_PROXY", TypstCommand.DEAD_PROXY)
                .containsEntry("PATH", "/usr/bin");
    }

    /** Bug caught: a dropped flag lets the compile see system fonts or a shared package cache. */
    @Test
    void commandPinsRootFontsAndPackagesToTheBundledDirectories() throws IOException {
        TypstAssets assets = TypstFixtures.assets();
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            List<String> command = new TypstCommand("typst", assets)
                    .build(workspace, Format.PDF, workspace.outputDir().resolve("x.pdf"))
                    .command();

            assertThat(command).containsSubsequence("--root", workspace.root().toString())
                    .contains("--ignore-system-fonts")
                    .containsSubsequence("--font-path", assets.fontDir().toString())
                    .containsSubsequence("--package-path", assets.packageDir().toString())
                    .containsSubsequence("--package-cache-path", assets.packageCacheDir().toString());
        }
    }
}
