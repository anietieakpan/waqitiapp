package com.waqiti.common.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Audit service for webhook events.
 * Provides comprehensive logging and monitoring for webhook security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookAuditService {
    
    public void logSuccessfulValidation(String provider, String sourceIp, String webhookId, long processingTime) {
        log.info("WEBHOOK_AUDIT: Successful validation - Provider: {}, IP: {}, WebhookId: {}, ProcessingTime: {}ms",
                provider, sourceIp, webhookId, processingTime);
    }
    
    public void logInvalidSignature(String provider, String sourceIp) {
        log.error("WEBHOOK_SECURITY: Invalid signature - Provider: {}, IP: {}, Timestamp: {}",
                provider, sourceIp, Instant.now());
    }
    
    public void logTimestampViolation(String provider, String sourceIp, String timestamp) {
        log.error("WEBHOOK_SECURITY: Timestamp violation (possible replay attack) - Provider: {}, IP: {}, Timestamp: {}",
                provider, sourceIp, timestamp);
    }
    
    public void logNonceReuse(String provider, String nonce) {
        log.error("WEBHOOK_SECURITY: Nonce reuse detected (replay attack) - Provider: {}, Nonce: {}",
                provider, nonce);
    }
    
    public void logRateLimitExceeded(String provider, String sourceIp) {
        log.warn("WEBHOOK_SECURITY: Rate limit exceeded - Provider: {}, IP: {}",
                provider, sourceIp);
    }
    
    public void logValidationError(String provider, String sourceIp, String error) {
        log.error("WEBHOOK_ERROR: Validation error - Provider: {}, IP: {}, Error: {}",
                provider, sourceIp, error);
    }
    
    public void logSuspiciousContent(String provider, String sourceIp, String pattern) {
        log.error("WEBHOOK_SECURITY: Suspicious content detected - Provider: {}, IP: {}, Pattern: {}",
                provider, sourceIp, pattern);
    }
}