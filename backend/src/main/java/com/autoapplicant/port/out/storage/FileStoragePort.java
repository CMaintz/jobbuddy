package com.autoapplicant.port.out.storage;

public interface FileStoragePort {
    /**
     * Persists {@code data} under {@code directory/filename} and returns the public URL.
     */
    String store(byte[] data, String directory, String filename);
}
