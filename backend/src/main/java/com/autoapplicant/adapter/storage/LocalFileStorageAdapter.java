package com.autoapplicant.adapter.storage;

import com.autoapplicant.config.AppDataDirectory;
import com.autoapplicant.port.out.storage.FileStoragePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final AppDataDirectory dataDirectory;

    public LocalFileStorageAdapter(AppDataDirectory dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public String store(byte[] data, String directory, String filename) {
        Path dir = dataDirectory.root().resolve(directory);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + filename, e);
        }
        return "/" + directory + "/" + filename;
    }
}
