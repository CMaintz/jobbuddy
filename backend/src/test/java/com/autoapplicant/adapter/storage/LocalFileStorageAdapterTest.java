package com.autoapplicant.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.config.AppDataDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterTest {

    @TempDir Path dataDir;

    @Test
    void stores_under_the_configured_data_dir_and_returns_the_url_path() throws Exception {
        LocalFileStorageAdapter storage =
                new LocalFileStorageAdapter(new AppDataDirectory(dataDir.toString()));

        String url = storage.store(new byte[] {1, 2, 3}, "uploads/profile-photos", "u.jpg");

        assertThat(url).isEqualTo("/uploads/profile-photos/u.jpg");
        assertThat(Files.readAllBytes(dataDir.resolve("uploads/profile-photos/u.jpg")))
                .containsExactly(1, 2, 3);
    }
}
