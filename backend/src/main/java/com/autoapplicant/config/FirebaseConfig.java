package com.autoapplicant.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            // GOOGLE_APPLICATION_CREDENTIALS is the standard env var used by Google SDKs
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
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
}
