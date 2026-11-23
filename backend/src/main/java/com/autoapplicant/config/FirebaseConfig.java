package com.autoapplicant.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!desktop")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private static final String OFFLINE_APP = "crawler-offline";

    /**
     * @param credentialsPath service-account file; blank falls back to the classpath. Bound to
     *     GOOGLE_APPLICATION_CREDENTIALS, the variable the Google SDKs read.
     */
    @Bean
    @Profile("!crawler")
    public FirebaseAuth firebaseAuth(@Value("${app.firebase.credentials-path:}") String credentialsPath)
            throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            InputStream serviceAccount;
            if (credentialsPath != null && !credentialsPath.isBlank()) {
                log.info("Loading Firebase credentials from: {}", credentialsPath);
                serviceAccount = new FileInputStream(credentialsPath);
            } else {
                log.info("GOOGLE_APPLICATION_CREDENTIALS not set, trying classpath firebase-service-account.json");
                serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
                if (serviceAccount == null) {
                    throw new IllegalStateException(
                            "Firebase credentials not found. Set GOOGLE_APPLICATION_CREDENTIALS or place " +
                            "firebase-service-account.json on the classpath.");
                }
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized successfully");
        }
        return FirebaseAuth.getInstance();
    }

    /**
     * The crawl has no users to sign in, but beans that hold a FirebaseAuth are still created in
     * this profile. An app with a placeholder token satisfies them without a service-account
     * file; any call it receives fails, and a crawl makes none.
     */
    @Bean
    @Profile("crawler")
    public FirebaseAuth offlineFirebaseAuth() {
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(existing -> OFFLINE_APP.equals(existing.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.newBuilder()
                                .setAccessToken(AccessToken.newBuilder().setTokenValue("none").build())
                                .build())
                        .setProjectId(OFFLINE_APP)
                        .build(), OFFLINE_APP));
        return FirebaseAuth.getInstance(app);
    }
}
