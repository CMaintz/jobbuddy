package com.autoapplicant.adapter.storage;

import com.autoapplicant.port.out.storage.FileStoragePort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    @Override
    public String store(byte[] data, String directory, String filename) {
        Path dir = Path.of(directory);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + filename, e);
        }
        return "/" + directory + "/" + filename;
    }
}
