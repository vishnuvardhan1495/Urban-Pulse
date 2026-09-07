package com.urbanpulse.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials.path:}")
    private String firebaseCredentialsPath;

    @PostConstruct
    public void initializeFirebase() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("FirebaseApp is already initialized.");
            return;
        }

        InputStream serviceAccountStream = null;

        // 1. Check property firebase.credentials.path if configured
        if (firebaseCredentialsPath != null && !firebaseCredentialsPath.trim().isEmpty()) {
            File configFile = new File(firebaseCredentialsPath.trim());
            if (configFile.exists() && configFile.isFile()) {
                serviceAccountStream = new FileInputStream(configFile);
                logger.info("Loading Firebase credentials from configured path property.");
            } else {
                logger.warn("Configured firebase.credentials.path file not found: {}", firebaseCredentialsPath);
            }
        }

        // 2. Check GOOGLE_APPLICATION_CREDENTIALS environment variable
        if (serviceAccountStream == null) {
            String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (envPath != null && !envPath.trim().isEmpty()) {
                File envFile = new File(envPath.trim());
                if (envFile.exists() && envFile.isFile()) {
                    serviceAccountStream = new FileInputStream(envFile);
                    logger.info("Loading Firebase credentials from GOOGLE_APPLICATION_CREDENTIALS environment variable.");
                } else {
                    logger.warn("GOOGLE_APPLICATION_CREDENTIALS file not found at path: {}", envPath);
                }
            }
        }

        GoogleCredentials credentials;
        if (serviceAccountStream != null) {
            try (InputStream stream = serviceAccountStream) {
                credentials = GoogleCredentials.fromStream(stream);
            }
        } else {
            // 3. Fallback to Google Application Default Credentials or skip initialization if not available
            try {
                credentials = GoogleCredentials.getApplicationDefault();
                logger.info("Loading Firebase credentials from Google Application Default Credentials.");
            } catch (Exception ex) {
                logger.warn("Firebase credentials not found; skipping Firebase initialization. Reason: {}", ex.getMessage());
                return; // Skip initialization when credentials are unavailable
            }
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp.initializeApp(options);
        logger.info("Firebase Admin SDK initialized successfully.");
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}
