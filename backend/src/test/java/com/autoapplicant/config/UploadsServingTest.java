package com.autoapplicant.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoapplicant.adapter.security.FirebaseTokenFilter;
import com.autoapplicant.adapter.security.SecurityContextHelper;
import com.autoapplicant.adapter.storage.LocalFileStorageAdapter;
import com.autoapplicant.adapter.web.controller.AuthController;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.in.user.CompleteOnboardingUseCase;
import com.autoapplicant.port.in.user.GetUserProfileUseCase;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** An upload is served from the same configured data directory it was written to. */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AppDataDirectory.class, LocalFileStorageAdapter.class})
class UploadsServingTest {

    private static final Path DATA_DIR = createDataDir();

    @Autowired MockMvc mvc;
    @Autowired LocalFileStorageAdapter storage;

    @MockitoBean GetUserProfileUseCase userProfile;
    @MockitoBean ResolveLinkedInUserUseCase resolveLinkedInUser;
    @MockitoBean CompleteOnboardingUseCase completeOnboarding;
    @MockitoBean SecurityContextHelper securityContext;
    @MockitoBean AppProperties appProperties;
    @MockitoBean FirebaseAuth firebaseAuth;
    @MockitoBean FirebaseTokenFilter firebaseTokenFilter;

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", DATA_DIR::toString);
    }

    @Test
    void a_stored_upload_is_served_from_the_data_dir() throws Exception {
        String url = storage.store(new byte[] {7, 8, 9}, "uploads/profile-photos", "p.png");

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {7, 8, 9}));
    }

    private static Path createDataDir() {
        try {
            Path dir = Files.createTempDirectory("jobbuddy-data");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
