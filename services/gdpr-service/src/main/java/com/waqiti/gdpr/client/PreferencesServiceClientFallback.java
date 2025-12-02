package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserPreferencesDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for Preferences Service Client
 */
@Slf4j
@Component
public class PreferencesServiceClientFallback implements PreferencesServiceClient {

    @Override
    public UserPreferencesDataDTO getUserPreferences(String userId, String correlationId) {
        log.error("User Service (Preferences) unavailable for GDPR data export - userId: {} correlationId: {}",
            userId, correlationId);

        return UserPreferencesDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("User Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
