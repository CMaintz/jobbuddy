package com.autoapplicant.adapter.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes the copies of the Typst assets that earlier font sets installed beside the current one.
 * Best effort: whatever cannot be removed now is logged and retried on the next start, because a
 * full disk is a smaller problem than a backend that will not start.
 */
final class TypstAssetsPruner {

    private static final Logger log = LoggerFactory.getLogger(TypstAssetsPruner.class);

    /** Only names {@link TypstAssets} gives a finished copy; anything else here is not ours. */
    private static final Pattern COPY = Pattern.compile("[0-9a-f]{16}");

    /** A copy moved aside to be deleted; one left over from a failed delete is retried. */
    private static final String DOOMED_PREFIX = "pruning-";

    private TypstAssetsPruner() {}

    static void pruneOtherCopies(Path parent, Path current) {
        try {
            List<Path> entries = listDirectories(parent);
            entries.stream().filter(TypstAssetsPruner::isDoomed).forEach(TypstAssetsPruner::delete);
            entries.stream()
                    .filter(dir -> isCopy(dir) && !dir.equals(current))
                    .forEach(TypstAssetsPruner::moveAsideAndDelete);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Cannot look for old Typst font copies in {}: {}", parent, e.getMessage());
        }
    }

    /** Real directories only: a link or junction named like a copy could point anywhere. */
    private static List<Path> listDirectories(Path parent) throws IOException {
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.filter(TypstAssetsPruner::isPlainDirectory).toList();
        }
    }

    private static boolean isCopy(Path dir) {
        return COPY.matcher(name(dir)).matches();
    }

    /** Entries come from listing a directory, so each has a name. */
    private static String name(Path entry) {
        return String.valueOf(entry.getFileName());
    }

    private static boolean isDoomed(Path dir) {
        return name(dir).startsWith(DOOMED_PREFIX);
    }

    /**
     * Renames before deleting so a copy is either whole or no longer looks installed. On Windows
     * the rename fails while a render has one of its fonts open, which leaves that copy intact for
     * the next start.
     */
    private static void moveAsideAndDelete(Path copy) {
        Path doomed = copy.resolveSibling(DOOMED_PREFIX + name(copy));
        try {
            Files.move(copy, doomed, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Keeping old Typst font copy {} for now, it may be in use: {}", copy, e.getMessage());
            return;
        }
        delete(doomed);
    }

    private static void delete(Path dir) {
        try {
            Files.walkFileTree(dir, new LinkSafeDeleter());
        } catch (IOException | UncheckedIOException e) {
            log.warn("Could not fully delete old Typst font copy {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Deletes a tree without leaving it. Files.walk descends into a Windows junction (it reports
     * one as a directory), so a junction is unlinked instead of entered.
     */
    private static final class LinkSafeDeleter extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (!attrs.isOther()) return FileVisitResult.CONTINUE;
            Files.delete(dir);
            return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
            if (failure != null) throw failure;
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    private static boolean isPlainDirectory(Path entry) {
        try {
            BasicFileAttributes attrs =
                    Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isDirectory() && !attrs.isOther();
        } catch (IOException e) {
            return false;
        }
    }
}
