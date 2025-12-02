package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserConsentsDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for Consent Service Client
 */
@Slf4j
@Component
public class ConsentServiceClientFallback implements ConsentServiceClient {

    @Override
    public UserConsentsDataDTO getUserConsents(String userId, String correlationId) {
        log.error("GDPR Service (Consents) unavailable for data export - userId: {} correlationId: {}",
            userId, correlationId);

        return UserConsentsDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("Consent Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
