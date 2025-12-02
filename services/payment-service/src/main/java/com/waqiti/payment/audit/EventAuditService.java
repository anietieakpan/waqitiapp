package com.waqiti.payment.audit;

import com.waqiti.common.events.EventAuditService as CommonEventAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Payment Service Event Audit Service Adapter
 *
 * Delegates to common EventAuditService for centralized audit logging
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-09
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventAuditService {

    private final CommonEventAuditService commonEventAuditService;

    /**
     * Log event publication for compliance and debugging
     */
    public void logEventPublication(
            String eventType,
            String topic,
            String key,
            String idempotencyKey,
            boolean success,
            String errorMessage,
            Instant timestamp) {

        try {
            commonEventAuditService.logEvent(
                    "EVENT_PUBLICATION",
                    eventType,
                    topic,
                    key,
                    idempotencyKey,
                    success ? "SUCCESS" : "FAILURE",
                    errorMessage,
                    timestamp
            );
        } catch (Exception e) {
            // Don't fail the operation if audit logging fails
            log.warn("Failed to audit event publication: eventType={}, topic={}, error={}",
                    eventType, topic, e.getMessage());
        }
    }
}
