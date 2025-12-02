package com.waqiti.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Audit Trail Service for basic audit trail operations
 * Delegates to comprehensive audit trail service for full functionality
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditTrailService {
    
    private final ComprehensiveAuditTrailService comprehensiveAuditTrailService;
    
    public void createAuditTrail(String eventId, String eventType, String entityId, Object eventData) {
        log.debug("Creating audit trail for eventId: {}, eventType: {}", eventId, eventType);
        // Delegate to comprehensive service
        comprehensiveAuditTrailService.logAuditEvent(eventId, eventType, entityId, eventData);
    }
    
    public void logPaymentFailure(String paymentId, String reason, Object failureData) {
        log.info("Logging payment failure for paymentId: {}, reason: {}", paymentId, reason);
        comprehensiveAuditTrailService.logAuditEvent(paymentId, "PAYMENT_FAILED", paymentId, failureData);
    }
    
    public void logSystemEvent(String eventType, String description, Object eventData) {
        log.debug("Logging system event: {}, description: {}", eventType, description);
        comprehensiveAuditTrailService.logAuditEvent(null, eventType, null, eventData);
    }
}