package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserPersonalDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for User Service Client
 * Returns safe default when user service is unavailable
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserPersonalDataDTO getUserPersonalData(String userId, String correlationId) {
        log.error("User Service unavailable for GDPR data export - userId: {} correlationId: {}",
            userId, correlationId);

        // Return partial data structure to allow export to continue
        // Mark as incomplete for manual review
        return UserPersonalDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("User Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
