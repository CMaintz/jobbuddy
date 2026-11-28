package com.autoapplicant.adapter.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.autoapplicant.adapter.render.TypstCommand.Format;
import com.autoapplicant.port.out.document.DocumentRenderException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Templates are code, and later slices let users write them. Whatever a template tries, the
 * compile stays inside its workspace and off the network, and a runaway one is killed.
 */
class TypstSandboxTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(2);
    /** A real, published package: on a networked machine it would download if anything let it. */
    private static final String UNBUNDLED_IMPORT = "#import \"@preview/tablex:0.0.9\": tablex\nhi\n";

    /**
     * Ten billion iterations: legal Typst that runs for minutes. ({@code while true} is no good
     * here, Typst itself aborts loops that look infinite.)
     */
    private static final String RUNAWAY = "#for i in range(100000) { for j in range(100000) {} }\n";

    /** Bug caught: the workspace is not the root, so a template can read the server's files. */
    @Test
    void readingOutsideTheRootFailsWithDiagnostics() throws IOException {
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            Files.writeString(workspace.root().resolveSibling("secret.txt"), "secret");

            DocumentRenderException error = compileFailure(workspace, "#read(\"../secret.txt\")\n",
                    TypstFixtures.TIMEOUT);

            assertThat(error.diagnostics()).contains("escape the project root").contains("1:");
        }
    }

    /**
     * Bug caught: the proxy pin is dropped, so Typst downloads {@code @preview} packages from the
     * internet (empty package directories alone do not stop it).
     */
    @Test
    void unbundledPackageImportFailsWithoutReachingTheNetwork() throws IOException {
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            DocumentRenderException error = compileFailure(workspace, UNBUNDLED_IMPORT, TypstFixtures.TIMEOUT);

            assertThat(error.diagnostics()).contains("@preview/tablex");
            assertThat(isEmpty(TypstFixtures.assets().packageCacheDir())).isTrue();
        }
    }

    /** Bug caught: a template that never finishes ties up a request thread and a process forever. */
    @Test
    void runawayTemplateIsKilledAtTheTimeout() throws IOException {
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            long start = System.nanoTime();

            DocumentRenderException error = compileFailure(workspace, RUNAWAY, SHORT_TIMEOUT);

            assertThat(error).as(error.diagnostics()).hasMessageContaining("timed out");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(SHORT_TIMEOUT.multipliedBy(5));
        }
    }

    private static DocumentRenderException compileFailure(TypstWorkspace workspace, String source,
                                                          Duration timeout) throws IOException {
        Files.writeString(workspace.mainFile(), source, StandardCharsets.UTF_8);
        Files.writeString(workspace.dataFile(), "{}", StandardCharsets.UTF_8);
        Path output = workspace.outputDir().resolve("out.pdf");
        TypstCompiler compiler = TypstFixtures.compiler(timeout);

        DocumentRenderException error = catchThrowableOfType(DocumentRenderException.class,
                () -> compiler.compile(workspace, Format.PDF, output));
        assertThat(error).as("the compile should have failed").isNotNull();
        return error;
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    /** Bug caught: a non-zero exit is ignored and an empty or stale PDF is returned. */
    @Test
    void compileFailureIsATypedExceptionNotAnEmptyPdf() throws IOException {
        try (TypstWorkspace workspace = TypstWorkspace.create()) {
            Files.writeString(workspace.mainFile(), "#let x = (\n", StandardCharsets.UTF_8);
            Files.writeString(workspace.dataFile(), "{}", StandardCharsets.UTF_8);
            Path output = workspace.outputDir().resolve("out.pdf");

            assertThatThrownBy(() -> TypstFixtures.compiler(TypstFixtures.TIMEOUT)
                            .compile(workspace, Format.PDF, output))
                    .isInstanceOf(DocumentRenderException.class)
                    .satisfies(e -> assertThat(((DocumentRenderException) e).diagnostics()).contains("error"));
        }
    }
}
