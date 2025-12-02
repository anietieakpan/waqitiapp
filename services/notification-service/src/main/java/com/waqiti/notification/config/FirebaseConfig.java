/**
 * File: src/main/java/com/waqiti/notification/config/FirebaseConfig.java
 */
package com.waqiti.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.config-file:firebase-service-account.json}")
    private String firebaseConfigFile;

    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Bean
    @Profile("!test") // Use real implementation in non-test environments
    public FirebaseMessaging firebaseMessaging() {
        if (!firebaseEnabled) {
            log.info("Firebase is disabled by configuration");
            return null;
        }

        try {
            InputStream serviceAccount = new ClassPathResource(firebaseConfigFile).getInputStream();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("Firebase application has been initialized");
                return FirebaseMessaging.getInstance(app);
            }

            return FirebaseMessaging.getInstance();
        } catch (Exception e) {
            log.error("Failed to initialize Firebase", e);
            // Return null which our NotificationSenderService can handle gracefully
            return null;
        }
    }
}