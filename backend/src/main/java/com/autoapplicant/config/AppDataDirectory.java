package com.autoapplicant.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The writable directory the app keeps its own files in (uploads; logs are placed by
 * logback-spring.xml from the same property). Hosted mode leaves it at the working directory. The
 * desktop app is installed under Program Files, which is not writable, so its profile moves this
 * to the user's local app data.
 */
@Component
public class AppDataDirectory {

    /** The setting, resolved against the working directory when relative. */
    static final String PROPERTY = "${app.data-dir:.}";

    private final Path root;

    public AppDataDirectory(@Value(PROPERTY) String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** Absolute, normalised path of the data directory. */
    public Path root() {
        return root;
    }

    /** Where uploads live on disk; their URL path is {@link UploadsLocation#URL_PREFIX}. */
    public Path uploads() {
        return root.resolve(UploadsLocation.DIRECTORY);
    }
}
