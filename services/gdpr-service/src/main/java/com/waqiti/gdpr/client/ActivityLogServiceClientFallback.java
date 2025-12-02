package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserActivityLogsDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for Activity Log Service Client
 */
@Slf4j
@Component
public class ActivityLogServiceClientFallback implements ActivityLogServiceClient {

    @Override
    public UserActivityLogsDataDTO getUserActivityLogs(String userId, String correlationId) {
        log.error("Audit Service unavailable for GDPR data export - userId: {} correlationId: {}",
            userId, correlationId);

        return UserActivityLogsDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("Audit Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
