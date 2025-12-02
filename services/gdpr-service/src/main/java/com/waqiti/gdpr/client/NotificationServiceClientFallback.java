package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserCommunicationsDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for Notification Service Client
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public UserCommunicationsDataDTO getUserCommunications(String userId, String correlationId) {
        log.error("Notification Service unavailable for GDPR data export - userId: {} correlationId: {}",
            userId, correlationId);

        return UserCommunicationsDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("Notification Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
