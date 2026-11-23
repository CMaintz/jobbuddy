package com.autoapplicant.port.out.storage;

public interface FileStoragePort {
    /**
     * Persists {@code data} under {@code directory/filename} and returns the public URL.
     *
     * @param directory relative to the app's data directory, and also the URL path the file is
     *     served under
     */
    String store(byte[] data, String directory, String filename);
}
