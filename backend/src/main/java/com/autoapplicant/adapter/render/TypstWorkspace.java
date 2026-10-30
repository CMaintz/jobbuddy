package com.autoapplicant.adapter.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fresh directory per render, deleted on close. Typst may only read inside {@link #root()},
 * which holds the template, its data and any staged images; outputs and diagnostics are written
 * beside it so a template cannot read them back.
 */
final class TypstWorkspace implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TypstWorkspace.class);

    static final String MAIN_FILE = "main.typ";
    static final String DATA_FILE = "data.json";

    private final Path dir;

    private TypstWorkspace(Path dir) {
        this.dir = dir;
    }

    static TypstWorkspace create() throws IOException {
        Path dir = Files.createTempDirectory("typst-render-");
        Files.createDirectory(dir.resolve("root"));
        Files.createDirectory(dir.resolve("out"));
        return new TypstWorkspace(dir);
    }

    Path root() {
        return dir.resolve("root");
    }

    Path mainFile() {
        return root().resolve(MAIN_FILE);
    }

    Path dataFile() {
        return root().resolve(DATA_FILE);
    }

    Path outputDir() {
        return dir.resolve("out");
    }

    Path diagnosticsFile() {
        return dir.resolve("diagnostics.txt");
    }

    /** Deletes the whole workspace; a leftover file is logged, never thrown, so it cannot mask a render error. */
    @Override
    public void close() {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(TypstWorkspace::delete);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Could not delete Typst workspace {}: {}", dir, e.getMessage());
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
