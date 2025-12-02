package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Result of webhook validation.
 * Contains validation status and detailed information for audit and debugging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookValidationResult {
    
    /**
     * Unique identifier for this webhook validation
     */
    private String webhookId;
    
    /**
     * Whether the webhook is valid
     */
    private boolean valid;
    
    /**
     * Error message if validation failed
     */
    private String error;
    
    /**
     * The webhook provider
     */
    private String provider;
    
    /**
     * Timestamp of validation
     */
    private Instant timestamp;
    
    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;
    
    /**
     * Additional validation details
     */
    private java.util.Map<String, Object> metadata;
    
    /**
     * Risk score (0-1) based on various factors
     */
    private Double riskScore;
    
    /**
     * Whether this appears to be a replay attack
     */
    private boolean replayAttempt;
    
    /**
     * Whether rate limit was exceeded
     */
    private boolean rateLimitExceeded;
}